package pe.upeu.agrosierra.data.remote.dto

import kotlinx.serialization.Serializable
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.Sesion
import pe.upeu.agrosierra.domain.model.TipoLabor

@Serializable
data class LoginRequestDto(val usuario: String, val contrasena: String)

@Serializable
data class LoginResponseDto(
    val token: String,
    val usuario: String,
    val rol: String,
    val privilegios: Set<String>,
    val expiraEnEpochMillis: Long
) {
    fun toDomain() = Sesion(token, usuario, rol, privilegios, expiraEnEpochMillis)
}

@Serializable
data class UsuarioActualDto(val usuario: String, val rol: String, val privilegios: Set<String>)

@Serializable
data class PageDto<T>(
    val contenido: List<T> = emptyList(),
    val pagina: Int = 0,
    val tamanio: Int = 20,
    val totalElementos: Long = 0,
    val totalPaginas: Int = 0,
    val ultima: Boolean = true
)

@Serializable
data class LoteDto(
    val id: Long,
    val codigo: String,
    val nombre: String,
    val hectareas: Double,
    val cultivo: String,
    val valle: String,
    val campaniaCerrada: Boolean,
    val dosisMaximaPorHectarea: Double
) {
    fun toDomain() = Lote(id, codigo, nombre, hectareas, cultivo, valle, campaniaCerrada, dosisMaximaPorHectarea)
}

@Serializable
data class LaborDto(
    val id: Long,
    val loteId: Long,
    val tipo: TipoLabor,
    val fecha: String,
    val responsable: String,
    val cantidad: Double,
    val observacion: String,
    val pendiente: Boolean = false,
    val creadoEnEpochMillis: Long = 0L
) {
    fun toDomain() = Labor(id, loteId, tipo, fecha, responsable, cantidad, observacion, pendiente, creadoEnEpochMillis)
}

@Serializable
data class LaborWriteDto(
    val loteId: Long,
    val tipo: TipoLabor,
    val fecha: String,
    val cantidad: Double,
    val observacion: String
)

@Serializable
data class LaborUpdateDto(
    val tipo: TipoLabor,
    val fecha: String,
    val cantidad: Double,
    val observacion: String
)

@Serializable
data class ErrorResponseDto(
    val timestamp: String? = null,
    val status: Int,
    val error: String? = null,
    val message: String = "Error inesperado",
    val path: String? = null,
    val validationErrors: Map<String, String>? = null
)
