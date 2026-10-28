package pe.upeu.agrosierra.data.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.AgroRemoteDataSource
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.Resultado
import pe.upeu.agrosierra.domain.model.Sesion
import pe.upeu.agrosierra.domain.repository.AuthRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AuthRepositorioRest(private val api: AgroRemoteDataSource, private val almacen: AlmacenSeguro) : AuthRepository {
    override fun sesionActual(): Flow<Sesion?> = almacen.sesion

    override suspend fun iniciarSesion(usuario: String, contrasena: String): Resultado<Sesion> =
        when (val response = api.login(usuario.trim(), contrasena)) {
            is Resultado.Exito -> {
                val session = response.dato.toDomain()
                almacen.guardarSesion(session)
                Resultado.Exito(session)
            }
            is Resultado.Fallo -> response
        }

    @OptIn(ExperimentalTime::class)
    override suspend fun restaurarSesion(): Resultado<Sesion?> {
        val cached = almacen.leerSesion() ?: return Resultado.Exito(null)
        if (cached.expiraEnEpochMillis <= Clock.System.now().toEpochMilliseconds()) {
            almacen.borrarSesion()
            return Resultado.Exito(null)
        }
        return when (val response = api.me()) {
            is Resultado.Exito -> {
                val restored = cached.copy(usuario = response.dato.usuario, rol = response.dato.rol,
                    privilegios = response.dato.privilegios)
                almacen.guardarSesion(restored)
                Resultado.Exito(restored)
            }
            is Resultado.Fallo -> when (response.error) {
                ErrorApi.NoAutorizado -> { almacen.borrarSesion(); Resultado.Exito(null) }
                else -> Resultado.Exito(cached) // Red/servidor no disponible: solo se habilita operación local.
            }
        }
    }

    override suspend fun cerrarSesion() = almacen.borrarSesion()
}
