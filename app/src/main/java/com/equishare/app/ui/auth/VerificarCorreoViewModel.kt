package com.equishare.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equishare.app.domain.model.Resultado
import com.equishare.app.domain.repositorio.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VerificarCorreoUiState(
    val email: String? = null,
    val revisando: Boolean = false,
    val enviando: Boolean = false,
    val mensaje: String? = null,
    val esError: Boolean = false,
    /** Momento del último reenvío, para no mandar muchos correos seguidos. */
    val ultimoEnvioMs: Long? = null,
)

/**
 * Pantalla "Revisa tu correo": la cuenta no se puede usar hasta abrir el enlace de verificación.
 * Así no quedan cuentas con correos falsos o mal escritos (por ejemplo "@gmai.com").
 */
class VerificarCorreoViewModel(
    private val auth: AuthRepository,
    private val reloj: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _ui = MutableStateFlow(VerificarCorreoUiState(email = auth.sesion.value?.email))
    val ui: StateFlow<VerificarCorreoUiState> = _ui.asStateFlow()

    init {
        // Si la persona ya abrió el enlace con la app cerrada, entra directo sin tocar nada.
        revisar(silencioso = true)
    }

    /** Botón "Ya lo verifiqué". Si está verificado, la app pasa sola a la siguiente pantalla. */
    fun revisar(silencioso: Boolean = false) {
        if (_ui.value.revisando) return
        _ui.update { it.copy(revisando = true) }
        viewModelScope.launch {
            when (val r = auth.revisarVerificacion()) {
                is Resultado.Exito -> _ui.update {
                    when {
                        r.datos -> it.copy(revisando = false, mensaje = VERIFICADO, esError = false)
                        silencioso -> it.copy(revisando = false)
                        else -> it.copy(revisando = false, mensaje = TODAVIA_NO, esError = true)
                    }
                }
                is Resultado.Error -> _ui.update {
                    if (silencioso) it.copy(revisando = false) else it.copy(revisando = false, mensaje = r.error.mensaje, esError = true)
                }
            }
        }
    }

    /** Segundos que faltan para poder pedir otro correo (0 si ya se puede). */
    fun segundosParaReenviar(): Int {
        val ultimo = _ui.value.ultimoEnvioMs ?: return 0
        val restante = ESPERA_MS - (reloj() - ultimo)
        return if (restante <= 0) 0 else ((restante + 999) / 1000).toInt()
    }

    fun reenviar() {
        if (_ui.value.enviando) return
        val faltan = segundosParaReenviar()
        if (faltan > 0) {
            _ui.update { it.copy(mensaje = "Espera $faltan s para pedir otro correo.", esError = true) }
            return
        }
        _ui.update { it.copy(enviando = true) }
        viewModelScope.launch {
            when (val r = auth.enviarVerificacion()) {
                is Resultado.Exito -> _ui.update {
                    it.copy(enviando = false, ultimoEnvioMs = reloj(), mensaje = REENVIADO, esError = false)
                }
                is Resultado.Error -> _ui.update { it.copy(enviando = false, mensaje = r.error.mensaje, esError = true) }
            }
        }
    }

    /** Para quien escribió mal su correo: sale y crea la cuenta con el correo correcto. */
    fun cerrarSesion() {
        viewModelScope.launch { auth.cerrarSesion() }
    }

    companion object {
        const val ESPERA_MS = 60_000L
        const val VERIFICADO = "¡Correo verificado! Entrando…"
        const val TODAVIA_NO = "Todavía no está verificado. Abre el enlace del correo y vuelve a tocar «Ya lo verifiqué»."
        const val REENVIADO = "Te enviamos un correo nuevo. Revisa también la carpeta de spam."
    }
}
