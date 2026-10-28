package pe.upeu.agrosierra.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.upeu.agrosierra.domain.model.Sesion

class AlmacenSeguroAndroid(context: Context) : AlmacenSeguro {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "agrosierra_sesion",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val state = MutableStateFlow(read())

    override val sesion = state.asStateFlow()
    override suspend fun leerSesion(): Sesion? = read()

    override suspend fun guardarSesion(sesion: Sesion) {
        preferences.edit()
            .putString("token", sesion.token)
            .putString("usuario", sesion.usuario)
            .putString("rol", sesion.rol)
            .putString("privilegios", sesion.privilegios.sorted().joinToString("|"))
            .putLong("expira", sesion.expiraEnEpochMillis)
            .apply()
        state.value = sesion
    }

    override suspend fun borrarSesion() {
        preferences.edit().clear().apply()
        state.value = null
    }

    private fun read(): Sesion? {
        val token = preferences.getString("token", null) ?: return null
        return Sesion(
            token = token,
            usuario = preferences.getString("usuario", "").orEmpty(),
            rol = preferences.getString("rol", "").orEmpty(),
            privilegios = preferences.getString("privilegios", "").orEmpty()
                .split('|').filter { it.isNotBlank() }.toSet(),
            expiraEnEpochMillis = preferences.getLong("expira", 0L)
        )
    }
}
