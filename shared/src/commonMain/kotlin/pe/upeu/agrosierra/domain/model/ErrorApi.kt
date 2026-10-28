package pe.upeu.agrosierra.domain.model

sealed interface ErrorApi {
    data class Validacion(val campos: Map<String, String>, val mensaje: String) : ErrorApi
    data object NoAutorizado : ErrorApi
    data object SinPrivilegio : ErrorApi
    data object NoEncontrado : ErrorApi
    data class Conflicto(val mensaje: String) : ErrorApi
    data object Servidor : ErrorApi
    data object SinConexion : ErrorApi
    data object TiempoAgotado : ErrorApi
    data class Desconocido(val mensaje: String) : ErrorApi
}

sealed interface Resultado<out T> {
    data class Exito<T>(val dato: T) : Resultado<T>
    data class Fallo(val error: ErrorApi) : Resultado<Nothing>
}
