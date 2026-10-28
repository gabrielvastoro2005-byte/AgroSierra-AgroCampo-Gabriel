package pe.upeu.agrosierra.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.upeu.agrosierra.db.AgroDb
import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.NuevaLabor
import pe.upeu.agrosierra.domain.model.TipoLabor
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class LaborPendiente(val idLocal: Long, val servidorId: Long?, val operacion: String, val labor: Labor)

class AgroLocalDataSource(private val db: AgroDb) {
    fun observarLotes(filtro: FiltroLotes): Flow<List<Lote>> {
        val valle = filtro.valle?.trim()?.takeIf { it.isNotEmpty() }
        val texto = filtro.texto.trim().lowercase()
        return db.agroQueries.observarLotes().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.asSequence().map { it.toLote() }
                .filter { valle == null || it.valle.equals(valle, ignoreCase = true) }
                .filter { !filtro.soloAbiertas || !it.campaniaCerrada }
                .filter { texto.isBlank() || it.codigo.lowercase().contains(texto) || it.nombre.lowercase().contains(texto) }
                .toList()
        }
    }

    fun observarLabores(loteId: Long): Flow<List<Labor>> =
        db.agroQueries.observarLaboresPorLote(loteId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toLabor() } }

    fun observarPendientes() = db.agroQueries.pendientesPorLote().asFlow()
        .mapToList(Dispatchers.Default).map { rows -> rows.associate { it.loteId to it.cantidad } }

    fun observarUltimaSincronizacion(): Flow<String?> = db.agroQueries.observarUltimaSincronizacion()
        .asFlow().mapToList(Dispatchers.Default).map { rows -> rows.firstOrNull() }

    fun lote(id: Long): Lote? = db.agroQueries.observarLote(id).executeAsOneOrNull()?.toLote()
    fun marcarCampaniaCerrada(id: Long) = db.agroQueries.marcarCampaniaCerrada(id)
    fun labor(id: Long): LaborPendiente? = db.agroQueries.observarIdLaborLocal(id).executeAsOneOrNull()?.let {
        LaborPendiente(it.id, it.servidorId, it.operacion, it.toLabor())
    }

    fun guardarLotes(lotes: List<Lote>) = db.transaction {
        lotes.forEach { l -> db.agroQueries.reemplazarLote(l.id, l.codigo, l.nombre, l.hectareas,
            l.cultivo, l.valle, l.campaniaCerrada.toDbLong(), l.dosisMaximaPorHectarea) }
    }

    fun guardarLaboresRemotas(loteId: Long, labores: List<Labor>) = db.transaction {
        db.agroQueries.eliminarLaboresSincronizadasPorLote(loteId)
        labores.forEach { l -> guardarLabor(l, l.id, false, null, "CREAR") }
    }

    @OptIn(ExperimentalTime::class)
    fun registrarLocal(nueva: NuevaLabor, responsable: String): Labor {
        val localId = nuevoIdLocal()
        val labor = Labor(localId, nueva.loteId, nueva.tipo, nueva.fecha, responsable,
            nueva.cantidad, nueva.observacion, pendiente = true,
            creadoEnEpochMillis = Clock.System.now().toEpochMilliseconds())
        db.transaction { guardarLabor(labor, localId, true, null, "CREAR") }
        return labor
    }

    fun encolarActualizacion(idServidor: Long, nueva: NuevaLabor, responsable: String): Labor? {
        val original = labor(idServidor)?.labor ?: return null
        val localId = nuevoIdLocal()
        val actualizada = Labor(localId, original.loteId, nueva.tipo, nueva.fecha, responsable,
            nueva.cantidad, nueva.observacion, pendiente = true)
        db.transaction {
            db.agroQueries.eliminarLabor(idServidor)
            guardarLabor(actualizada, localId, true, idServidor, "ACTUALIZAR")
        }
        return actualizada
    }

    fun actualizarPendiente(idActual: Long, nueva: NuevaLabor, responsable: String): Labor? {
        val existente = labor(idActual) ?: return null
        if (existente.operacion == "CREAR") {
            val editada = existente.labor.copy(tipo = nueva.tipo, fecha = nueva.fecha,
                cantidad = nueva.cantidad, observacion = nueva.observacion, responsable = responsable)
            db.transaction { guardarLabor(editada, idActual, true, null, "CREAR") }
            return editada
        }
        val serverId = existente.servidorId ?: return null
        val nuevoId = nuevoIdLocal()
        val editada = existente.labor.copy(id = nuevoId, tipo = nueva.tipo, fecha = nueva.fecha,
            cantidad = nueva.cantidad, observacion = nueva.observacion, responsable = responsable, pendiente = true)
        db.transaction {
            db.agroQueries.eliminarLabor(idActual)
            guardarLabor(editada, nuevoId, true, serverId, "ACTUALIZAR")
        }
        return editada
    }

    fun encolarEliminacion(id: Long): Boolean {
        val existente = labor(id) ?: return false
        if (existente.operacion == "CREAR") {
            db.agroQueries.eliminarLabor(id)
            return true
        }
        val localId = nuevoIdLocal()
        val marcada = existente.labor.copy(id = localId, pendiente = true)
        db.transaction {
            db.agroQueries.eliminarLabor(id)
            guardarLabor(marcada, localId, true, existente.servidorId ?: id, "ELIMINAR")
        }
        return true
    }

    fun pendientes(): List<LaborPendiente> = db.agroQueries.pendientes().executeAsList().map {
        LaborPendiente(it.id, it.servidorId, it.operacion, it.toLabor())
    }

    fun confirmarPendiente(idLocal: Long, remoto: Labor) = db.transaction {
        db.agroQueries.eliminarLabor(idLocal)
        guardarLabor(remoto, remoto.id, false, null, "CREAR")
    }

    fun confirmarEliminacion(idLocal: Long) = db.agroQueries.eliminarLabor(idLocal)
    fun eliminarPendiente(idLocal: Long) = db.agroQueries.eliminarLabor(idLocal)

    fun guardarUltimaSincronizacion(instant: String) =
        db.agroQueries.guardarSincronizacion("lotes_labores", instant)

    private fun nuevoIdLocal(): Long {
        val minimum = db.agroQueries.siguienteIdLocal().executeAsOne()
        return if (minimum >= 0) -1L else minimum - 1L
    }

    private fun guardarLabor(l: Labor, id: Long, pendiente: Boolean, servidorId: Long?, operacion: String) {
        db.agroQueries.reemplazarLabor(id, l.loteId, l.tipo.name, l.fecha, l.responsable,
            l.cantidad, l.observacion, pendiente.toDbLong(), servidorId, operacion, l.creadoEnEpochMillis)
    }
}

private fun Boolean.toDbLong() = if (this) 1L else 0L

private fun pe.upeu.agrosierra.db.Lote.toLote() = Lote(id, codigo, nombre, hectareas, cultivo,
    valle, campaniaCerrada != 0L, dosisMaxima)

private fun pe.upeu.agrosierra.db.Labor.toLabor() = Labor(id, loteId, TipoLabor.valueOf(tipo),
    fecha, responsable, cantidad, observacion, pendiente != 0L, creadoEnEpochMillis)
