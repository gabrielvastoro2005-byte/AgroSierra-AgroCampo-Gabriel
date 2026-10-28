package pe.upeu.agrosierra.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.NuevaLabor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.Resultado
import pe.upeu.agrosierra.domain.repository.EstadoRed
import pe.upeu.agrosierra.domain.usecase.AgroCampoUseCases

class AgroViewModel(
    private val useCases: AgroCampoUseCases,
    estadoRed: EstadoRed
) : ViewModel() {
    private val mutableState = MutableStateFlow(AgroUiState())
    val uiState = mutableState.stateIn(viewModelScope, SharingStarted.Eagerly, AgroUiState())

    init {
        observarFuentesLocales()
        restaurarSesion()
        observarConectividad(estadoRed)
        viewModelScope.launch {
            useCases.sesion().collectLatest { session -> mutableState.update { it.copy(sesion = session) } }
        }
    }

    private fun observarFuentesLocales() {
        viewModelScope.launch {
            mutableState.map { it.filtro }.distinctUntilChanged()
                .flatMapLatest(useCases::lotes).collectLatest { items ->
                    mutableState.update { it.copy(lotes = items, valles = items.map(Lote::valle).distinct().sorted()) }
                }
        }
        viewModelScope.launch {
            mutableState.map { it.loteSeleccionado?.id }.distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else useCases.labores(id) }
                .collectLatest { items -> mutableState.update { it.copy(labores = items) } }
        }
        viewModelScope.launch {
            useCases.pendientes().collectLatest { map -> mutableState.update { it.copy(pendientesPorLote = map) } }
        }
        viewModelScope.launch {
            useCases.ultimaSync().collectLatest { at -> mutableState.update { it.copy(ultimaSincronizacion = at) } }
        }
    }

    private fun restaurarSesion() = viewModelScope.launch {
        when (val result = useCases.restaurarSesion()) {
            is Resultado.Exito -> {
                mutableState.update { it.copy(sesion = result.dato, restaurando = false) }
                if (result.dato != null) sincronizar()
            }
            is Resultado.Fallo -> mutableState.update { it.copy(restaurando = false, error = mensaje(result.error)) }
        }
    }

    private fun observarConectividad(estadoRed: EstadoRed) = viewModelScope.launch {
        var previous: Boolean? = null
        estadoRed.conectado.collectLatest { online ->
            mutableState.update { it.copy(enLinea = online) }
            if (previous == false && online && mutableState.value.sesion != null) sincronizar()
            previous = online
        }
    }

    fun cambiarUsuario(value: String) = mutableState.update { it.copy(usuario = value) }
    fun cambiarContrasena(value: String) = mutableState.update { it.copy(contrasena = value) }
    fun cambiarTexto(value: String) = mutableState.update { it.copy(filtro = it.filtro.copy(texto = value)) }
    fun cambiarValle(value: String?) = mutableState.update { it.copy(filtro = it.filtro.copy(valle = value)) }
    fun soloAbiertas(value: Boolean) = mutableState.update { it.copy(filtro = it.filtro.copy(soloAbiertas = value)) }
    fun seleccionarLote(lote: pe.upeu.agrosierra.domain.model.Lote?) = mutableState.update {
        it.copy(loteSeleccionado = lote, mostrarPerfil = false, mensaje = null, error = null)
    }
    fun verPerfil(value: Boolean) = mutableState.update { it.copy(mostrarPerfil = value, loteSeleccionado = null) }
    fun mostrarFormulario(labor: pe.upeu.agrosierra.domain.model.Labor? = null) =
        mutableState.update { it.copy(mostrarFormularioLabor = true, laborEditando = labor, erroresCampos = emptyMap(), error = null) }
    fun cerrarFormulario() = mutableState.update { it.copy(mostrarFormularioLabor = false, laborEditando = null, erroresCampos = emptyMap()) }
    fun limpiarMensajes() = mutableState.update { it.copy(mensaje = null, error = null, erroresCampos = emptyMap()) }

    fun iniciarSesion() = viewModelScope.launch {
        val state = mutableState.value
        if (state.usuario.isBlank() || state.contrasena.isBlank()) {
            mutableState.update { it.copy(error = "Ingresa usuario y contraseña") }; return@launch
        }
        mutableState.update { it.copy(cargando = true, error = null) }
        when (val result = useCases.login(state.usuario, state.contrasena)) {
            is Resultado.Exito -> {
                mutableState.update { it.copy(sesion = result.dato, contrasena = "", cargando = false, error = null) }
                sincronizar()
            }
            is Resultado.Fallo -> mutableState.update { it.copy(cargando = false, error = mensaje(result.error)) }
        }
    }

    fun cerrarSesion() = viewModelScope.launch {
        useCases.logout()
        mutableState.update { AgroUiState(restaurando = false) }
    }

    fun sincronizar() = viewModelScope.launch {
        if (mutableState.value.sincronizando) return@launch
        mutableState.update { it.copy(sincronizando = true, error = null) }
        when (val result = useCases.sync()) {
            is Resultado.Exito -> mutableState.update { it.copy(sincronizando = false,
                mensaje = "Sincronización terminada: ${result.dato.enviados} enviados y ${result.dato.recibidos} recibidos") }
            is Resultado.Fallo -> mutableState.update { it.copy(sincronizando = false, error = mensaje(result.error)) }
        }
    }

    fun guardarLabor(nueva: NuevaLabor) = viewModelScope.launch {
        mutableState.update { it.copy(cargando = true, erroresCampos = emptyMap(), error = null) }
        val editing = mutableState.value.laborEditando
        val result = if (editing == null) useCases.registrar(nueva) else useCases.actualizar(editing.id, nueva)
        when (result) {
            is Resultado.Exito -> {
                mutableState.update { it.copy(cargando = false, mostrarFormularioLabor = false, laborEditando = null,
                    mensaje = if (result.dato.pendiente) "Guardada localmente; queda pendiente de sincronización" else "Labor guardada") }
                if (!result.dato.pendiente) sincronizar()
            }
            is Resultado.Fallo -> mutableState.update { it.copy(cargando = false, error = mensaje(result.error),
                erroresCampos = (result.error as? ErrorApi.Validacion)?.campos.orEmpty()) }
        }
    }

    fun eliminarLabor(id: Long) = viewModelScope.launch {
        mutableState.update { it.copy(cargando = true, error = null) }
        when (val result = useCases.eliminar(id)) {
            is Resultado.Exito -> mutableState.update { it.copy(cargando = false, mensaje = "Eliminación procesada; revisa pendientes de sincronización") }
            is Resultado.Fallo -> mutableState.update { it.copy(cargando = false, error = mensaje(result.error)) }
        }
    }

    fun cerrarCampania() = viewModelScope.launch {
        val id = mutableState.value.loteSeleccionado?.id ?: return@launch
        mutableState.update { it.copy(cargando = true, error = null) }
        when (val result = useCases.cerrarCampania(id)) {
            is Resultado.Exito -> mutableState.update { it.copy(cargando = false, mensaje = "Campaña cerrada") }
            is Resultado.Fallo -> mutableState.update { it.copy(cargando = false, error = mensaje(result.error)) }
        }
    }

    private fun mensaje(error: ErrorApi): String = when (error) {
        is ErrorApi.Validacion -> error.mensaje
        ErrorApi.NoAutorizado -> "Sesión vencida o credenciales incorrectas. Inicia sesión nuevamente."
        ErrorApi.SinPrivilegio -> "Tu usuario no tiene privilegio para realizar esta operación (403)."
        ErrorApi.NoEncontrado -> "El registro ya no existe (404). Se actualizará al sincronizar."
        is ErrorApi.Conflicto -> error.mensaje
        ErrorApi.Servidor -> "El servicio no está disponible (500). Intenta nuevamente."
        ErrorApi.SinConexion -> "Sin conexión. Los datos locales se mantienen y puedes reintentar."
        ErrorApi.TiempoAgotado -> "La solicitud tardó demasiado. Intenta nuevamente."
        is ErrorApi.Desconocido -> error.mensaje
    }
}
