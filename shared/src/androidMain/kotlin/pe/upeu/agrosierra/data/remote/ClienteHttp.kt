package pe.upeu.agrosierra.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pe.upeu.agrosierra.data.local.AlmacenSeguro

fun crearClienteHttp(almacen: AlmacenSeguro): HttpClient = HttpClient(CIO) {
    expectSuccess = true
    install(DefaultRequest) {
        url(ApiConfig.BASE_URL)
        contentType(ContentType.Application.Json)
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
    install(Auth) {
        bearer {
            loadTokens {
                almacen.leerSesion()?.let { BearerTokens(it.token, "") }
            }
            sendWithoutRequest { request -> !request.url.build().encodedPath.endsWith("/auth/login") }
        }
    }
}
