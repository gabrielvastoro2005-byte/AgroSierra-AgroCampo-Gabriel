package pe.upeu.agrosierra.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.domain.model.Resultado
import pe.upeu.agrosierra.domain.model.Sesion

interface AuthRepository {
    fun sesionActual(): Flow<Sesion?>
    suspend fun iniciarSesion(usuario: String, contrasena: String): Resultado<Sesion>
    suspend fun restaurarSesion(): Resultado<Sesion?>
    suspend fun cerrarSesion()
}
