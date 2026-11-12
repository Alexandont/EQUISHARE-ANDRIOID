package com.equishare.app.ui.bienvenida

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equishare.app.domain.repositorio.AjustesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pantallas de bienvenida: se muestran solo la primera vez que se abre la app. */
class BienvenidaViewModel(private val ajustes: AjustesRepository) : ViewModel() {

    /** null mientras se lee el teléfono; true si hay que mostrar la bienvenida. */
    val mostrar: StateFlow<Boolean?> = ajustes.bienvenidaVista
        .map { vista -> !vista }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun terminar() {
        viewModelScope.launch { ajustes.marcarBienvenidaVista() }
    }
}
