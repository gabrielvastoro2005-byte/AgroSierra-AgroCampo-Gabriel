package pe.upeu.agrosierra.domain.repository

import kotlinx.coroutines.flow.Flow

interface EstadoRed {
    val conectado: Flow<Boolean>
}
