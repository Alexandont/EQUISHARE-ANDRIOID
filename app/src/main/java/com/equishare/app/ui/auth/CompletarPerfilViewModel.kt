package com.equishare.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equishare.app.domain.ia.LectorRuc
import com.equishare.app.domain.model.Resultado
import com.equishare.app.domain.repositorio.AuthRepository
import com.equishare.app.domain.repositorio.EmpresaRepository
import com.equishare.app.domain.repositorio.FavoritosRepository
import com.equishare.app.domain.repositorio.LectorTextoFoto
import com.equishare.app.domain.validacion.Validadores
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class CompletarPerfilUiState(
    val ruc: String = "",
    val razonSocial: String = "",
    val telefono: String = "",
    val errorRuc: String? = null,
    val errorRazon: String? = null,
    val errorTelefono: String? = null,
    val guardando: Boolean = false,
    val errorGeneral: String? = null,
    /** true mientras la IA del teléfono lee la foto de la ficha RUC. */
    val leyendoFoto: Boolean = false,
    /** Resultado de leer la foto, para mostrarlo debajo de los botones. */
    val avisoFoto: String? = null,
)

/** La empresa registra y verifica su RUC antes de publicar o alquilar. */
class CompletarPerfilViewModel(
    private val empresas: EmpresaRepository,
    private val favoritos: FavoritosRepository,
    private val auth: AuthRepository,
    private val lectorTexto: LectorTextoFoto,
) : ViewModel() {
    private val _ui = MutableStateFlow(CompletarPerfilUiState())
    val ui: StateFlow<CompletarPerfilUiState> = _ui.asStateFlow()

    private var trabajoFoto: Job? = null

    fun alCambiarRuc(v: String) = _ui.update { it.copy(ruc = v.filter(Char::isDigit).take(11), errorRuc = null) }
    fun alCambiarRazon(v: String) = _ui.update { it.copy(razonSocial = v, errorRazon = null) }
    fun alCambiarTelefono(v: String) = _ui.update { it.copy(telefono = v.filter(Char::isDigit).take(9), errorTelefono = null) }

    /**
     * La IA del teléfono lee la foto de la ficha RUC y llena el RUC (y la razón social si la encuentra).
     * Solo usa un RUC válido; si no lo encuentra, no cambia nada y lo dice.
     */
    fun alElegirFotoRuc(uri: String?) {
        if (uri == null) return
        trabajoFoto?.cancel()
        _ui.update { it.copy(leyendoFoto = true, avisoFoto = null) }
        trabajoFoto = viewModelScope.launch {
            val texto = try {
                lectorTexto.leerTexto(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val datos = LectorRuc.extraer(texto)
            val ruc = datos.ruc
            val razon = datos.razonSocial
            _ui.update { actual ->
                val base = actual.copy(leyendoFoto = false)
                when {
                    texto == null -> base.copy(avisoFoto = AVISO_SIN_LECTURA)
                    ruc == null -> base.copy(avisoFoto = AVISO_SIN_RUC)
                    razon == null -> base.copy(ruc = ruc, errorRuc = null, avisoFoto = AVISO_SOLO_RUC)
                    else -> base.copy(
                        ruc = ruc,
                        razonSocial = razon,
                        errorRuc = null,
                        errorRazon = null,
                        avisoFoto = AVISO_COMPLETO,
                    )
                }
            }
        }
    }

    /** El teléfono no tiene una app de cámara: se sugiere usar la galería. */
    fun sinCamara() = _ui.update { it.copy(avisoFoto = AVISO_SIN_CAMARA) }

    fun guardar(alTerminar: () -> Unit) {
        val e = _ui.value
        val errores = e.copy(
            errorRuc = Validadores.ruc(e.ruc),
            errorRazon = Validadores.razonSocial(e.razonSocial),
            errorTelefono = Validadores.telefono(e.telefono),
        )
        _ui.value = errores
        if (errores.errorRuc != null || errores.errorRazon != null || errores.errorTelefono != null || e.guardando) return
        _ui.update { it.copy(guardando = true, errorGeneral = null) }
        viewModelScope.launch {
            when (val r = empresas.guardar(e.ruc, e.razonSocial, e.telefono)) {
                is Resultado.Exito -> {
                    favoritos.registrarConsentimiento(auth.sesion.value?.email)
                    _ui.value = CompletarPerfilUiState()
                    alTerminar()
                }
                is Resultado.Error -> _ui.update {
                    it.copy(guardando = false, errorGeneral = (listOf(r.error.mensaje) + r.error.detalles).joinToString("\n"))
                }
            }
        }
    }

    fun cerrarSesion() {
        viewModelScope.launch { auth.cerrarSesion() }
    }

    companion object {
        const val AVISO_COMPLETO = "Leímos el RUC y la razón social. Revísalos antes de continuar."
        const val AVISO_SOLO_RUC = "Leímos el RUC. Escribe la razón social y revisa los datos."
        const val AVISO_SIN_RUC =
            "No encontramos un RUC válido en la foto. Toma una foto más nítida de la ficha RUC o escríbelo a mano."
        const val AVISO_SIN_LECTURA = "No se pudo leer la foto. Escribe el RUC a mano."
        const val AVISO_SIN_CAMARA = "No se encontró una app de cámara. Elige la foto de la galería."
    }
}
