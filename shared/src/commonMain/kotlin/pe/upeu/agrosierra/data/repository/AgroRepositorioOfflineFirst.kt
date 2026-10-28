package pe.upeu.agrosierra.data.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.data.local.AgroLocalDataSource
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.local.LaborPendiente
import pe.upeu.agrosierra.data.remote.AgroRemoteDataSource
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.NuevaLabor
import pe.upeu.agrosierra.domain.model.Resultado
import pe.upeu.agrosierra.domain.model.ResumenSincronizacion
import pe.upeu.agrosierra.domain.repository.AgroRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AgroRepositorioOfflineFirst(
    private val local: AgroLocalDataSource,
    private val remoto: AgroRemoteDataSource,
    private val sesion: AlmacenSeguro
) : AgroRepository {
    override fun observarLotes(filtro: FiltroLotes): Flow<List<Lote>> = local.observarLotes(filtro)
    override fun observarLabores(loteId: Long): Flow<List<Labor>> = local.observarLabores(loteId)
    override fun observarPendientesPorLote(): Flow<Map<Long, Long>> = local.observarPendientes()
    override fun ultimaSincronizacion(): Flow<String?> = local.observarUltimaSincronizacion()

    override suspend fun registrarLabor(nueva: NuevaLabor): Resultado<Labor> {
        val session = sesion.leerSesion() ?: return Resultado.Fallo(ErrorApi.NoAutorizado)
        if ("LABOR_CREAR" !in session.privilegios) return Resultado.Fallo(ErrorApi.SinPrivilegio)
        val validation = validarLocal(nueva)
        if (validation != null) return Resultado.Fallo(validation)
        val pendiente = local.registrarLocal(nueva, session.usuario)
        return when (val sync = sincronizar()) {
            is Resultado.Exito -> Resultado.Exito(pendiente.copy(pendiente = false))
            is Resultado.Fallo -> if (esTemporal(sync.error)) {
                Resultado.Exito(pendiente)
            } else {
                local.eliminarPendiente(pendiente.id)
                Resultado.Fallo(sync.error)
            }
        }
    }

    override suspend fun actualizarLabor(id: Long, nueva: NuevaLabor): Resultado<Labor> {
        val session = sesion.leerSesion() ?: return Resultado.Fallo(ErrorApi.NoAutorizado)
        if ("LABOR_EDITAR" !in session.privilegios) return Resultado.Fallo(ErrorApi.SinPrivilegio)
        val validation = validarLocal(nueva)
        if (validation != null) return Resultado.Fallo(validation)
        val row = local.labor(id) ?: return Resultado.Fallo(ErrorApi.NoEncontrado)
        val updated = if (id < 0 || row.operacion != "CREAR") {
            local.actualizarPendiente(id, nueva, session.usuario)
        } else {
            local.encolarActualizacion(id, nueva, session.usuario)
        } ?: return Resultado.Fallo(ErrorApi.NoEncontrado)
        return when (val result = sincronizar()) {
            is Resultado.Exito -> Resultado.Exito(updated.copy(pendiente = false))
            is Resultado.Fallo -> if (esTemporal(result.error)) Resultado.Exito(updated)
                else Resultado.Fallo(result.error)
        }
    }

    override suspend fun eliminarLabor(id: Long): Resultado<Unit> {
        val session = sesion.leerSesion() ?: return Resultado.Fallo(ErrorApi.NoAutorizado)
        if ("LABOR_ELIMINAR" !in session.privilegios) return Resultado.Fallo(ErrorApi.SinPrivilegio)
        val row = local.labor(id) ?: return Resultado.Fallo(ErrorApi.NoEncontrado)
        local.encolarEliminacion(id)
        return when (val result = sincronizar()) {
            is Resultado.Exito -> Resultado.Exito(Unit)
            is Resultado.Fallo -> if (esTemporal(result.error)) Resultado.Exito(Unit) else {
                // Se conserva el pendiente para reintento; el servidor sigue siendo quien autoriza.
                Resultado.Fallo(result.error)
            }
        }
    }

    override suspend fun cerrarCampania(loteId: Long): Resultado<Unit> {
        val session = sesion.leerSesion() ?: return Resultado.Fallo(ErrorApi.NoAutorizado)
        if ("CAMPANIA_CERRAR" !in session.privilegios) return Resultado.Fallo(ErrorApi.SinPrivilegio)
        return when (val result = remoto.cerrarCampania(loteId)) {
            is Resultado.Exito -> {
                local.marcarCampaniaCerrada(loteId)
                when (val sync = sincronizar()) {
                    is Resultado.Exito -> Resultado.Exito(Unit)
                    is Resultado.Fallo -> Resultado.Fallo(sync.error)
                }
            }
            is Resultado.Fallo -> gestionarError(result.error)
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun sincronizar(): Resultado<ResumenSincronizacion> {
        val session = sesion.leerSesion() ?: return Resultado.Fallo(ErrorApi.NoAutorizado)
        val enviados = local.pendientes()
        for (pending in enviados) {
            val result = enviar(pending)
            when (result) {
                is Resultado.Exito -> Unit
                is Resultado.Fallo -> return gestionarError(result.error)
            }
        }
        val remoteLots = when (val response = remoto.lotes()) {
            is Resultado.Exito -> response.dato.map { it.toDomain() }
            is Resultado.Fallo -> return gestionarError(response.error)
        }
        local.guardarLotes(remoteLots)
        var recibidas = 0
        for (lote in remoteLots) {
            when (val response = remoto.labores(lote.id)) {
                is Resultado.Exito -> {
                    local.guardarLaboresRemotas(lote.id, response.dato.map { it.toDomain() })
                    recibidas += response.dato.size
                }
                is Resultado.Fallo -> return gestionarError(response.error)
            }
        }
        local.guardarUltimaSincronizacion(Clock.System.now().toString())
        @Suppress("UNUSED_VARIABLE") val resumenEnviadas = enviados.size
        @Suppress("UNUSED_VARIABLE") val resumenRecibidas = recibidas
        @Suppress("UNUSED_VARIABLE") val usuarioActual = session.usuario
        return Resultado.Exito(ResumenSincronizacion(enviados.size, recibidas))
    }

    private suspend fun enviar(pendiente: LaborPendiente): Resultado<Unit> {
        val labor = pendiente.labor
        return when (pendiente.operacion) {
            "CREAR" -> when (val result = remoto.registrar(
                NuevaLabor(labor.loteId, labor.tipo, labor.fecha, labor.cantidad, labor.observacion))) {
                is Resultado.Exito -> {
                    local.confirmarPendiente(pendiente.idLocal, result.dato.toDomain())
                    Resultado.Exito(Unit)
                }
                is Resultado.Fallo -> Resultado.Fallo(result.error)
            }
            "ACTUALIZAR" -> {
                val serverId = pendiente.servidorId ?: return Resultado.Fallo(ErrorApi.Desconocido("Falta el identificador remoto"))
                when (val result = remoto.actualizar(serverId,
                    NuevaLabor(labor.loteId, labor.tipo, labor.fecha, labor.cantidad, labor.observacion))) {
                    is Resultado.Exito -> {
                        local.confirmarPendiente(pendiente.idLocal, result.dato.toDomain())
                        Resultado.Exito(Unit)
                    }
                    is Resultado.Fallo -> Resultado.Fallo(result.error)
                }
            }
            "ELIMINAR" -> {
                val serverId = pendiente.servidorId ?: return Resultado.Fallo(ErrorApi.Desconocido("Falta el identificador remoto"))
                when (val result = remoto.eliminar(serverId)) {
                    is Resultado.Exito -> { local.confirmarEliminacion(pendiente.idLocal); Resultado.Exito(Unit) }
                    is Resultado.Fallo -> Resultado.Fallo(result.error)
                }
            }
            else -> Resultado.Fallo(ErrorApi.Desconocido("Operación pendiente desconocida"))
        }
    }

    private suspend fun gestionarError(error: ErrorApi): Resultado.Fallo {
        if (error == ErrorApi.NoAutorizado) sesion.borrarSesion()
        return Resultado.Fallo(error)
    }

    private fun esTemporal(error: ErrorApi) = error is ErrorApi.SinConexion ||
        error is ErrorApi.TiempoAgotado || error is ErrorApi.Servidor

    private fun validarLocal(nueva: NuevaLabor): ErrorApi? {
        val lote = local.lote(nueva.loteId) ?: return ErrorApi.NoEncontrado
        if (lote.campaniaCerrada) return ErrorApi.Conflicto("La campaña del lote está cerrada")
        if (nueva.fecha.isBlank()) return ErrorApi.Validacion(mapOf("fecha" to "Ingrese una fecha"), "Datos inválidos")
        if (nueva.observacion.isBlank()) return ErrorApi.Validacion(mapOf("observacion" to "Ingrese una observación"), "Datos inválidos")
        if (nueva.cantidad <= 0) return ErrorApi.Validacion(mapOf("cantidad" to "Debe ser mayor que cero"), "Datos inválidos")
        if (nueva.tipo.name == "FUMIGACION") {
            val max = lote.dosisMaximaPorHectarea * lote.hectareas
            if (nueva.cantidad > max) return ErrorApi.Validacion(mapOf("cantidad" to "Máximo para el lote: $max"), "La dosis supera el límite")
        }
        return null
    }
}
