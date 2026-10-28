package pe.upeu.agrosierra.data.local

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.domain.model.Sesion

interface AlmacenSeguro {
    val sesion: Flow<Sesion?>
    suspend fun leerSesion(): Sesion?
    suspend fun guardarSesion(sesion: Sesion)
    suspend fun borrarSesion()
}
