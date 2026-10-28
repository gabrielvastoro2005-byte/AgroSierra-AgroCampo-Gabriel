package pe.upeu.agrosierra.domain.usecase

import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.NuevaLabor
import pe.upeu.agrosierra.domain.repository.AgroRepository
import pe.upeu.agrosierra.domain.repository.AuthRepository

class ObservarLotesUseCase(private val repository: AgroRepository) {
    operator fun invoke(filtro: FiltroLotes) = repository.observarLotes(filtro)
}

class RegistrarLaborUseCase(private val repository: AgroRepository) {
    suspend operator fun invoke(nueva: NuevaLabor) = repository.registrarLabor(nueva)
}

class SincronizarUseCase(private val repository: AgroRepository) {
    suspend operator fun invoke() = repository.sincronizar()
}

class IniciarSesionUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(usuario: String, contrasena: String) =
        repository.iniciarSesion(usuario, contrasena)
}
