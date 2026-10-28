package pe.upeu.agrosierra.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.NuevaLabor
import pe.upeu.agrosierra.domain.model.Resultado
import pe.upeu.agrosierra.domain.model.ResumenSincronizacion

interface AgroRepository {
    fun observarLotes(filtro: FiltroLotes): Flow<List<Lote>>
    fun observarLabores(loteId: Long): Flow<List<Labor>>
    fun observarPendientesPorLote(): Flow<Map<Long, Long>>
    fun ultimaSincronizacion(): Flow<String?>
    suspend fun sincronizar(): Resultado<ResumenSincronizacion>
    suspend fun registrarLabor(nueva: NuevaLabor): Resultado<Labor>
    suspend fun actualizarLabor(id: Long, nueva: NuevaLabor): Resultado<Labor>
    suspend fun eliminarLabor(id: Long): Resultado<Unit>
    suspend fun cerrarCampania(loteId: Long): Resultado<Unit>
}
