package pe.upeu.agrosierra.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import pe.upeu.agrosierra.data.remote.dto.ErrorResponseDto
import pe.upeu.agrosierra.data.remote.dto.LaborDto
import pe.upeu.agrosierra.data.remote.dto.LaborUpdateDto
import pe.upeu.agrosierra.data.remote.dto.LaborWriteDto
import pe.upeu.agrosierra.data.remote.dto.LoginRequestDto
import pe.upeu.agrosierra.data.remote.dto.LoginResponseDto
import pe.upeu.agrosierra.data.remote.dto.LoteDto
import pe.upeu.agrosierra.data.remote.dto.PageDto
import pe.upeu.agrosierra.data.remote.dto.UsuarioActualDto
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.NuevaLabor
import pe.upeu.agrosierra.domain.model.Resultado

object ApiConfig {
    // Android emulator routes 10.0.2.2 to the host where docker compose publishes AgroAPI.
    const val BASE_URL = "http://10.0.2.2:8081/api/v1/"
}

class AgroApi(private val client: HttpClient) : AgroRemoteDataSource {
    override suspend fun login(usuario: String, contrasena: String): Resultado<LoginResponseDto> = call {
        client.post("auth/login") { setBody(LoginRequestDto(usuario, contrasena)) }.body()
    }

    override suspend fun me(): Resultado<UsuarioActualDto> = call { client.get("usuarios/me").body() }

    override suspend fun lotes(): Resultado<List<LoteDto>> = call {
        val resultado = mutableListOf<LoteDto>()
        var page = 0
        var last = false
        while (!last) {
            val response: PageDto<LoteDto> = client.get("lotes") {
                parameter("pagina", page)
                parameter("tamanio", 100)
            }.body()
            resultado += response.contenido
            last = response.ultima
            page++
        }
        resultado
    }

    override suspend fun labores(loteId: Long): Resultado<List<LaborDto>> = call {
        val resultado = mutableListOf<LaborDto>()
        var page = 0
        var last = false
        while (!last) {
            val response: PageDto<LaborDto> = client.get("lotes/$loteId/labores") {
                parameter("pagina", page)
                parameter("tamanio", 100)
            }.body()
            resultado += response.contenido
            last = response.ultima
            page++
        }
        resultado
    }

    override suspend fun registrar(nueva: NuevaLabor): Resultado<LaborDto> = call {
        client.post("labores") {
            setBody(LaborWriteDto(nueva.loteId, nueva.tipo, nueva.fecha, nueva.cantidad, nueva.observacion))
        }.body()
    }

    override suspend fun actualizar(id: Long, nueva: NuevaLabor): Resultado<LaborDto> = call {
        client.put("labores/$id") {
            setBody(LaborUpdateDto(nueva.tipo, nueva.fecha, nueva.cantidad, nueva.observacion))
        }.body()
    }

    override suspend fun eliminar(id: Long): Resultado<Unit> = call {
        client.delete("labores/$id")
        Unit
    }

    override suspend fun cerrarCampania(loteId: Long): Resultado<Unit> = call {
        client.post("lotes/$loteId/cerrar-campania")
        Unit
    }

    private suspend inline fun <reified T> call(block: suspend () -> T): Resultado<T> = try {
        Resultado.Exito(block())
    } catch (e: ResponseException) {
        Resultado.Fallo(mapResponseError(e))
    } catch (_: HttpRequestTimeoutException) {
        Resultado.Fallo(ErrorApi.TiempoAgotado)
    } catch (_: Exception) {
        Resultado.Fallo(ErrorApi.SinConexion)
    }

    private suspend fun mapResponseError(exception: ResponseException): ErrorApi =
        MapeadorErrorHttp.traducir(exception.response.status.value, exception.response.bodyAsText())
}
