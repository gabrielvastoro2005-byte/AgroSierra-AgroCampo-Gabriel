package pe.upeu.agrosierra.presentation

import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.Sesion

data class AgroUiState(
    val sesion: Sesion? = null,
    val restaurando: Boolean = true,
    val usuario: String = "",
    val contrasena: String = "",
    val lotes: List<Lote> = emptyList(),
    val labores: List<Labor> = emptyList(),
    val loteSeleccionado: Lote? = null,
    val filtro: FiltroLotes = FiltroLotes(),
    val valles: List<String> = emptyList(),
    val pendientesPorLote: Map<Long, Long> = emptyMap(),
    val ultimaSincronizacion: String? = null,
    val enLinea: Boolean = false,
    val cargando: Boolean = false,
    val sincronizando: Boolean = false,
    val mensaje: String? = null,
    val error: String? = null,
    val erroresCampos: Map<String, String> = emptyMap(),
    val mostrarPerfil: Boolean = false,
    val mostrarFormularioLabor: Boolean = false,
    val laborEditando: Labor? = null
)
