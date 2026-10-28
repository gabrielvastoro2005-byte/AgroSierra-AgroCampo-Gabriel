package pe.upeu.agrosierra

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import pe.upeu.agrosierra.data.local.AgroLocalDataSource
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.AgroRemoteDataSource
import pe.upeu.agrosierra.data.remote.MapeadorErrorHttp
import pe.upeu.agrosierra.data.remote.dto.LaborDto
import pe.upeu.agrosierra.data.remote.dto.LoginResponseDto
import pe.upeu.agrosierra.data.remote.dto.LoteDto
import pe.upeu.agrosierra.data.remote.dto.UsuarioActualDto
import pe.upeu.agrosierra.data.repository.AgroRepositorioOfflineFirst
import pe.upeu.agrosierra.db.AgroDb
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.NuevaLabor
import pe.upeu.agrosierra.domain.model.Resultado
import pe.upeu.agrosierra.domain.model.Sesion
import pe.upeu.agrosierra.domain.model.TipoLabor

class AgroCampoOfflineTest {
    @Test
    fun dtoSeConvierteAlModeloDeDominio() {
        val lote = LoteDto(7, "LT-007", "Las Lomas", 2.4, "Maíz", "Chanchamayo", true, 2.2).toDomain()
        assertEquals("LT-007", lote.codigo)
        assertTrue(lote.campaniaCerrada)
        assertEquals(2.2, lote.dosisMaximaPorHectarea)
    }

    @Test
    fun error400SeTraduceConDetalleDelCampo() {
        val error = MapeadorErrorHttp.traducir(400,
            """{"status":400,"message":"Datos inválidos","validationErrors":{"cantidad":"Supera la dosis máxima"}}""")
        val validacion = assertIs<ErrorApi.Validacion>(error)
        assertEquals("Supera la dosis máxima", validacion.campos["cantidad"])
    }

    @Test
    fun altaSinConexionSeGuardaConIdNegativoYPendiente() = runTest {
        val db = memoria()
        val local = AgroLocalDataSource(db)
        local.guardarLotes(listOf(lote))
        val remote = FakeRemote(createResult = Resultado.Fallo(ErrorApi.SinConexion))
        val repo = AgroRepositorioOfflineFirst(local, remote, FakeSecureStore(session))

        val result = repo.registrarLabor(nueva)

        val success = assertIs<Resultado.Exito<*>>(result)
        assertTrue((success.dato as Labor).pendiente)
        val pending = local.pendientes().single()
        assertTrue(pending.idLocal < 0)
        assertTrue(pending.labor.pendiente)
        assertEquals("CREAR", pending.operacion)
        assertEquals(listOf("POST"), remote.calls)
    }

    @Test
    fun sincronizacionEnviaPendientesAntesDeDescargar() = runTest {
        val db = memoria()
        val local = AgroLocalDataSource(db)
        local.guardarLotes(listOf(lote))
        val saved = local.registrarLocal(nueva, session.usuario)
        val remote = FakeRemote(createResult = Resultado.Exito(laborDto(id = 91)),
            laborResults = listOf(laborDto(id = 91)))
        val repo = AgroRepositorioOfflineFirst(local, remote, FakeSecureStore(session))

        val result = repo.sincronizar()

        assertIs<Resultado.Exito<*>>(result)
        assertEquals(listOf("POST", "GET_LOTES", "GET_LABORES:1"), remote.calls)
        assertTrue(local.pendientes().isEmpty())
        assertEquals(91L, local.observarLabores(1).first().single().id)
    }

    private fun memoria(): AgroDb {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AgroDb.Schema.create(driver)
        return AgroDb(driver)
    }

    private val lote = Lote(1, "LT-001", "El Mirador", 4.5, "Café", "Chanchamayo", false, 2.0)
    private val nueva = NuevaLabor(1, TipoLabor.RIEGO, "2026-10-27", 120.0, "Riego de prueba")
    private val session = Sesion("jwt-test", "tecnico1", "TECNICO", setOf("LABOR_CREAR", "LABOR_EDITAR"), Long.MAX_VALUE)

    private fun laborDto(id: Long) = LaborDto(id, 1, TipoLabor.RIEGO, "2026-10-27", "tecnico1", 120.0,
        "Riego de prueba", false, 1_798_000_000_000L)

    private class FakeSecureStore(initial: Sesion?) : AlmacenSeguro {
        private val state = MutableStateFlow(initial)
        override val sesion = state
        override suspend fun leerSesion(): Sesion? = state.value
        override suspend fun guardarSesion(sesion: Sesion) { state.value = sesion }
        override suspend fun borrarSesion() { state.value = null }
    }

    private inner class FakeRemote(
        private val createResult: Resultado<LaborDto>,
        private val laborResults: List<LaborDto> = emptyList()
    ) : AgroRemoteDataSource {
        val calls = mutableListOf<String>()
        override suspend fun login(usuario: String, contrasena: String) =
            Resultado.Fallo(ErrorApi.NoAutorizado)
        override suspend fun me() = Resultado.Exito(UsuarioActualDto("tecnico1", "TECNICO", setOf("LABOR_CREAR")))
        override suspend fun lotes(): Resultado<List<LoteDto>> {
            calls += "GET_LOTES"
            return Resultado.Exito(listOf(LoteDto(1, "LT-001", "El Mirador", 4.5, "Café", "Chanchamayo", false, 2.0)))
        }
        override suspend fun labores(loteId: Long): Resultado<List<LaborDto>> {
            calls += "GET_LABORES:$loteId"
            return Resultado.Exito(laborResults)
        }
        override suspend fun registrar(nueva: NuevaLabor): Resultado<LaborDto> {
            calls += "POST"
            return createResult
        }
        override suspend fun actualizar(id: Long, nueva: NuevaLabor) = Resultado.Exito(laborDto(id))
        override suspend fun eliminar(id: Long) = Resultado.Exito(Unit)
        override suspend fun cerrarCampania(loteId: Long) = Resultado.Exito(Unit)
    }
}
