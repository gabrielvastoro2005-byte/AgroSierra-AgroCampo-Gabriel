package pe.upeu.agrosierra.data.remote

import pe.upeu.agrosierra.data.remote.dto.LaborDto
import pe.upeu.agrosierra.data.remote.dto.LoginResponseDto
import pe.upeu.agrosierra.data.remote.dto.LoteDto
import pe.upeu.agrosierra.data.remote.dto.UsuarioActualDto
import pe.upeu.agrosierra.domain.model.NuevaLabor
import pe.upeu.agrosierra.domain.model.Resultado

interface AgroRemoteDataSource {
    suspend fun login(usuario: String, contrasena: String): Resultado<LoginResponseDto>
    suspend fun me(): Resultado<UsuarioActualDto>
    suspend fun lotes(): Resultado<List<LoteDto>>
    suspend fun labores(loteId: Long): Resultado<List<LaborDto>>
    suspend fun registrar(nueva: NuevaLabor): Resultado<LaborDto>
    suspend fun actualizar(id: Long, nueva: NuevaLabor): Resultado<LaborDto>
    suspend fun eliminar(id: Long): Resultado<Unit>
    suspend fun cerrarCampania(loteId: Long): Resultado<Unit>
}
