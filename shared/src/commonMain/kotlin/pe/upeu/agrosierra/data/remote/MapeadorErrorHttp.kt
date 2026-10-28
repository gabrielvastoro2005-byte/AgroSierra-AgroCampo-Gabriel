package pe.upeu.agrosierra.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import pe.upeu.agrosierra.data.remote.dto.ErrorResponseDto
import pe.upeu.agrosierra.domain.model.ErrorApi

object MapeadorErrorHttp {
    private val json = Json { ignoreUnknownKeys = true }

    fun traducir(status: Int, body: String): ErrorApi {
        val response = runCatching { json.decodeFromString<ErrorResponseDto>(body) }.getOrNull()
        val message = response?.message ?: "La operación no se pudo completar"
        return when (status) {
            400 -> ErrorApi.Validacion(response?.validationErrors.orEmpty(), message)
            401 -> ErrorApi.NoAutorizado
            403 -> ErrorApi.SinPrivilegio
            404 -> ErrorApi.NoEncontrado
            409 -> ErrorApi.Conflicto(message)
            in 500..599 -> ErrorApi.Servidor
            else -> ErrorApi.Desconocido(message)
        }
    }
}
