package com.equishare.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equishare.app.domain.model.Resultado
import com.equishare.app.domain.repositorio.AuthRepository
import com.equishare.app.domain.validacion.FuerzaPassword
import com.equishare.app.domain.validacion.Validadores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val confirmacion: String = "",
    val modoRegistro: Boolean = false,
    /** Casilla de consentimiento SIN marcar por defecto (diseño ético, Anexo de marketing). */
    val aceptaPrivacidad: Boolean = false,
    val errorEmail: String? = null,
    val errorPassword: String? = null,
    val errorConfirmacion: String? = null,
    val errorPrivacidad: String? = null,
    val cargando: Boolean = false,
    val errorGeneral: String? = null,
    val esModoDemo: Boolean = false,
    /** Correo corregido cuando el dominio tiene un error común (por ejemplo "@gmai.com"). */
    val sugerenciaCorreo: String? = null,
    val fuerzaPassword: FuerzaPassword = FuerzaPassword.VACIA,
    // "¿Olvidaste tu contraseña?"
    val mostrarRecuperar: Boolean = false,
    val emailRecuperar: String = "",
    val enviandoRecuperar: Boolean = false,
    val errorRecuperar: String? = null,
    val mensajeRecuperar: String? = null,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _ui = MutableStateFlow(LoginUiState(esModoDemo = auth.esModoDemo))
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    fun alCambiarEmail(valor: String) = _ui.update {
        it.copy(email = valor, errorEmail = null, errorGeneral = null, sugerenciaCorreo = Validadores.sugerenciaCorreo(valor))
    }
    fun alCambiarPassword(valor: String) = _ui.update {
        it.copy(password = valor, errorPassword = null, errorGeneral = null, fuerzaPassword = Validadores.fuerzaPassword(valor))
    }

    /** Toque en "¿Quisiste decir ...?": cambia el correo por el corregido. */
    fun usarSugerenciaCorreo() = _ui.update {
        val sugerencia = it.sugerenciaCorreo ?: return@update it
        it.copy(email = sugerencia, sugerenciaCorreo = null, errorEmail = null)
    }

    fun abrirRecuperar() = _ui.update {
        it.copy(mostrarRecuperar = true, emailRecuperar = it.email.trim(), errorRecuperar = null, mensajeRecuperar = null)
    }
    fun alCambiarEmailRecuperar(valor: String) = _ui.update { it.copy(emailRecuperar = valor, errorRecuperar = null) }
    fun cerrarRecuperar() = _ui.update { if (it.enviandoRecuperar) it else it.copy(mostrarRecuperar = false) }

    /** Envía el enlace para crear una contraseña nueva. Responde igual exista o no la cuenta. */
    fun enviarRecuperar() {
        val estado = _ui.value
        if (estado.enviandoRecuperar) return
        val error = Validadores.email(estado.emailRecuperar)
        if (error != null) {
            _ui.update { it.copy(errorRecuperar = error) }
            return
        }
        _ui.update { it.copy(enviandoRecuperar = true, errorRecuperar = null, mensajeRecuperar = null) }
        viewModelScope.launch {
            when (val r = auth.recuperarContrasena(estado.emailRecuperar.trim())) {
                is Resultado.Exito -> _ui.update { it.copy(enviandoRecuperar = false, mensajeRecuperar = MENSAJE_RECUPERAR) }
                is Resultado.Error -> _ui.update { it.copy(enviandoRecuperar = false, errorRecuperar = r.error.mensaje) }
            }
        }
    }
    fun alCambiarConfirmacion(valor: String) = _ui.update { it.copy(confirmacion = valor, errorConfirmacion = null) }
    fun alCambiarPrivacidad(valor: Boolean) = _ui.update { it.copy(aceptaPrivacidad = valor, errorPrivacidad = null) }
    fun alternarModo() = _ui.update {
        it.copy(modoRegistro = !it.modoRegistro, errorGeneral = null, errorConfirmacion = null, errorPrivacidad = null)
    }

    /** Valida el formulario; devuelve true si se puede enviar. */
    fun validar(): Boolean {
        val estado = _ui.value
        // Al crear la cuenta no se acepta un dominio mal escrito: a ese correo nunca llegaría la verificación.
        val errorEmail = Validadores.email(estado.email)
            ?: estado.sugerenciaCorreo?.takeIf { estado.modoRegistro }?.let { "Revisa el correo. ¿Quisiste decir $it?" }
        val errorPassword = Validadores.password(estado.password)
        val errorConfirmacion = if (estado.modoRegistro) Validadores.confirmacion(estado.password, estado.confirmacion) else null
        val errorPrivacidad = if (estado.modoRegistro && !estado.aceptaPrivacidad) {
            "Debes leer y aceptar la política de privacidad para crear la cuenta."
        } else {
            null
        }
        _ui.update {
            it.copy(
                errorEmail = errorEmail,
                errorPassword = errorPassword,
                errorConfirmacion = errorConfirmacion,
                errorPrivacidad = errorPrivacidad,
            )
        }
        return listOf(errorEmail, errorPassword, errorConfirmacion, errorPrivacidad).all { it == null }
    }

    fun enviar(alRegistrar: (String) -> Unit) {
        if (_ui.value.cargando || !validar()) return
        val estado = _ui.value
        _ui.update { it.copy(cargando = true, errorGeneral = null) }
        viewModelScope.launch {
            val resultado = if (estado.modoRegistro) {
                auth.registrar(estado.email, estado.password)
            } else {
                auth.iniciarSesion(estado.email, estado.password)
            }
            when (resultado) {
                is Resultado.Exito -> {
                    // Se limpia el formulario: la contraseña no queda en memoria tras ingresar.
                    _ui.value = LoginUiState(esModoDemo = auth.esModoDemo)
                    if (estado.modoRegistro) alRegistrar(estado.email.trim())
                }
                is Resultado.Error -> _ui.update { it.copy(cargando = false, errorGeneral = resultado.error.mensaje) }
            }
        }
    }

    companion object {
        const val MENSAJE_RECUPERAR =
            "Si existe una cuenta con ese correo, te enviamos un enlace para crear una contraseña nueva. Revisa también la carpeta de spam."
    }
}
