package pe.upeu.agrosierra.domain.usecase

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.NuevaLabor
import pe.upeu.agrosierra.domain.model.Resultado
import pe.upeu.agrosierra.domain.model.ResumenSincronizacion
import pe.upeu.agrosierra.domain.model.Sesion
import pe.upeu.agrosierra.domain.repository.AgroRepository
import pe.upeu.agrosierra.domain.repository.AuthRepository

class AgroCampoUseCases(private val agro: AgroRepository, private val auth: AuthRepository) {
    fun sesion() = auth.sesionActual()
    suspend fun restaurarSesion() = auth.restaurarSesion()
    suspend fun login(usuario: String, contrasena: String) = auth.iniciarSesion(usuario, contrasena)
    suspend fun logout() = auth.cerrarSesion()
    fun lotes(filtro: FiltroLotes): Flow<List<Lote>> = agro.observarLotes(filtro)
    fun labores(loteId: Long): Flow<List<Labor>> = agro.observarLabores(loteId)
    fun pendientes() = agro.observarPendientesPorLote()
    fun ultimaSync() = agro.ultimaSincronizacion()
    suspend fun sync(): Resultado<ResumenSincronizacion> = agro.sincronizar()
    suspend fun registrar(nueva: NuevaLabor) = agro.registrarLabor(nueva)
    suspend fun actualizar(id: Long, nueva: NuevaLabor) = agro.actualizarLabor(id, nueva)
    suspend fun eliminar(id: Long) = agro.eliminarLabor(id)
    suspend fun cerrarCampania(id: Long) = agro.cerrarCampania(id)
}
