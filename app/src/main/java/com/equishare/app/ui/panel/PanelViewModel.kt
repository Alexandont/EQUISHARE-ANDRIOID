package com.equishare.app.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equishare.app.domain.model.MetricasEmpresa
import com.equishare.app.domain.model.Resultado
import com.equishare.app.domain.repositorio.EmpresaRepository
import com.equishare.app.domain.repositorio.FavoritosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PanelUiState(
    val cargando: Boolean = true,
    val empresa: String? = null,
    val metricas: MetricasEmpresa? = null,
    val favoritos: Int = 0,
    val error: String? = null,
)

/** Saludo del inicio y panel de Mis equipos (HU-10): nombre de la empresa y sus números. */
class PanelViewModel(
    private val empresas: EmpresaRepository,
    favoritos: FavoritosRepository,
) : ViewModel() {
    private val datos = MutableStateFlow(PanelUiState())

    val ui: StateFlow<PanelUiState> = combine(datos, favoritos.observar()) { d, favs -> d.copy(favoritos = favs.size) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PanelUiState())

    init {
        cargar()
    }

    fun cargar() {
        datos.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val empresa = empresas.miEmpresa()
            val metricas = empresas.metricas()
            datos.update {
                it.copy(
                    cargando = false,
                    empresa = (empresa as? Resultado.Exito)?.datos?.razonSocial ?: it.empresa,
                    metricas = (metricas as? Resultado.Exito)?.datos ?: it.metricas,
                    error = (metricas as? Resultado.Error)?.error?.mensaje,
                )
            }
        }
    }
}
