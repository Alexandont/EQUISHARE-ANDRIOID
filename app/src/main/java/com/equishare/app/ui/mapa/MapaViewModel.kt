package com.equishare.app.ui.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equishare.app.domain.mapa.TipoMapa
import com.equishare.app.domain.model.Coordenadas
import com.equishare.app.domain.model.Equipo
import com.equishare.app.domain.repositorio.AjustesRepository
import com.equishare.app.domain.repositorio.EquipoRepository
import com.equishare.app.domain.repositorio.ProveedorUbicacion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapaUiState(
    val equipos: List<Equipo> = emptyList(),
    val miUbicacion: Coordenadas? = null,
    val permisoConcedido: Boolean = false,
    val mensaje: String? = null,
    /** Capa elegida (Normal, Satélite, Híbrido o Relieve). Se recuerda al volver a abrir la app. */
    val tipoMapa: TipoMapa = TipoMapa.NORMAL,
    /** Equipo resaltado: el mapa se centra en él y su tarjeta queda al frente. */
    val seleccionado: String? = null,
)

class MapaViewModel(
    private val equipos: EquipoRepository,
    private val ubicacion: ProveedorUbicacion,
    private val ajustes: AjustesRepository,
) : ViewModel() {
    private val extra = MutableStateFlow(MapaUiState(permisoConcedido = ubicacion.tienePermiso()))

    val ui: StateFlow<MapaUiState> =
        combine(equipos.observarCatalogo(), extra, ajustes.tipoMapa) { lista, e, tipo ->
            e.copy(equipos = lista, tipoMapa = tipo, seleccionado = e.seleccionado?.takeIf { id -> lista.any { it.id == id } })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapaUiState())

    init {
        // El mapa muestra los mismos equipos del catálogo guardado en Room (con los filtros activos).
        if (ubicacion.tienePermiso()) actualizarUbicacion()
    }

    fun alResultadoPermiso(concedido: Boolean) {
        extra.update {
            it.copy(
                permisoConcedido = concedido,
                mensaje = if (concedido) null else "Sin permiso de ubicación: el mapa funciona, pero no muestra tu posición.",
            )
        }
        if (concedido) actualizarUbicacion()
    }

    fun actualizarUbicacion() {
        viewModelScope.launch {
            val c = ubicacion.ubicacionActual()
            extra.update { it.copy(miUbicacion = c, permisoConcedido = ubicacion.tienePermiso()) }
        }
    }

    fun tienePermiso(): Boolean = ubicacion.tienePermiso()

    fun alElegirTipoMapa(tipo: TipoMapa) {
        viewModelScope.launch { ajustes.guardarTipoMapa(tipo) }
    }

    /** Toque en un marcador o tarjeta, o tarjeta que quedó al frente al deslizar. */
    fun alSeleccionar(id: String?) = extra.update { it.copy(seleccionado = id) }
}
