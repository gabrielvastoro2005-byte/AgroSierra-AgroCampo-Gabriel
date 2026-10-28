package pe.upeu.agrosierra.domain.model

data class Lote(
    val id: Long,
    val codigo: String,
    val nombre: String,
    val hectareas: Double,
    val cultivo: String,
    val valle: String,
    val campaniaCerrada: Boolean,
    val dosisMaximaPorHectarea: Double
)

enum class TipoLabor { RIEGO, FUMIGACION, COSECHA }

data class Labor(
    val id: Long,
    val loteId: Long,
    val tipo: TipoLabor,
    val fecha: String,
    val responsable: String,
    val cantidad: Double,
    val observacion: String,
    val pendiente: Boolean,
    val creadoEnEpochMillis: Long = 0L
)

data class Sesion(
    val token: String,
    val usuario: String,
    val rol: String,
    val privilegios: Set<String>,
    val expiraEnEpochMillis: Long
)

data class NuevaLabor(
    val loteId: Long,
    val tipo: TipoLabor,
    val fecha: String,
    val cantidad: Double,
    val observacion: String
)

data class FiltroLotes(
    val valle: String? = null,
    val soloAbiertas: Boolean = false,
    val texto: String = ""
)

data class ResumenSincronizacion(val enviados: Int, val recibidos: Int)
