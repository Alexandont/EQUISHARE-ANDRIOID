package com.equishare.app.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equishare.app.domain.model.Empresa
import com.equishare.app.domain.model.ErrorApp
import com.equishare.app.domain.model.Resultado
import com.equishare.app.domain.repositorio.AuthRepository
import com.equishare.app.domain.repositorio.EmpresaRepository
import com.equishare.app.domain.repositorio.FavoritosRepository
import com.equishare.app.domain.validacion.Validadores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ventanas de "Mi cuenta" en Perfil. */
enum class DialogoCuenta { EDITAR_DATOS, CAMBIAR_CONTRASENA, ELIMINAR_CUENTA }

data class PerfilUiState(
    val empresa: Empresa? = null,
    val email: String? = null,
    val esDemo: Boolean = false,
    val correoVerificado: Boolean = true,
    val cargando: Boolean = true,
    val error: ErrorApp? = null,
    val dialogo: DialogoCuenta? = null,
    val procesando: Boolean = false,
    val errorDialogo: String? = null,
    /** Aviso corto después de un cambio correcto (por ejemplo "Datos actualizados."). */
    val mensaje: String? = null,
)

class PerfilViewModel(
    private val empresas: EmpresaRepository,
    private val auth: AuthRepository,
    private val favoritos: FavoritosRepository,
    private val limpiarDatosLocales: suspend () -> Unit,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        PerfilUiState(
            email = auth.sesion.value?.email,
            esDemo = auth.esModoDemo,
            correoVerificado = auth.sesion.value?.correoVerificado ?: false,
        ),
    )
    val ui: StateFlow<PerfilUiState> = _ui.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _ui.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val r = empresas.miEmpresa()) {
                is Resultado.Exito -> _ui.update { it.copy(cargando = false, empresa = r.datos) }
                is Resultado.Error -> _ui.update { it.copy(cargando = false, error = r.error) }
            }
        }
    }

    /** Cierra sesión y borra la caché privada del teléfono (reservas y equipos propios). */
    fun cerrarSesion() {
        viewModelScope.launch {
            limpiarDatosLocales()
            auth.cerrarSesion()
        }
    }

    fun abrirDialogo(dialogo: DialogoCuenta) = _ui.update { it.copy(dialogo = dialogo, errorDialogo = null, mensaje = null) }

    fun cerrarDialogo() = _ui.update { if (it.procesando) it else it.copy(dialogo = null, errorDialogo = null) }

    /** Derecho de rectificación: corrige la razón social y el celular. El RUC no cambia. */
    fun guardarDatos(razonSocial: String, telefono: String) {
        val empresa = _ui.value.empresa ?: return
        if (_ui.value.procesando) return
        val error = Validadores.razonSocial(razonSocial) ?: Validadores.telefono(telefono)
        if (error != null) {
            _ui.update { it.copy(errorDialogo = error) }
            return
        }
        _ui.update { it.copy(procesando = true, errorDialogo = null) }
        viewModelScope.launch {
            when (val r = empresas.guardar(empresa.ruc, razonSocial.trim(), telefono.trim())) {
                is Resultado.Exito -> _ui.update {
                    it.copy(procesando = false, empresa = r.datos, dialogo = null, mensaje = DATOS_ACTUALIZADOS)
                }
                is Resultado.Error -> _ui.update { it.copy(procesando = false, errorDialogo = r.error.mensaje) }
            }
        }
    }

    fun cambiarContrasena(actual: String, nueva: String, confirmacion: String) {
        if (_ui.value.procesando) return
        val error = when {
            actual.isEmpty() -> "Escribe tu contraseña actual."
            else -> Validadores.password(nueva)
                ?: Validadores.confirmacion(nueva, confirmacion)
                ?: if (nueva == actual) "La contraseña nueva debe ser distinta de la actual." else null
        }
        if (error != null) {
            _ui.update { it.copy(errorDialogo = error) }
            return
        }
        _ui.update { it.copy(procesando = true, errorDialogo = null) }
        viewModelScope.launch {
            when (val r = auth.cambiarContrasena(actual, nueva)) {
                is Resultado.Exito -> _ui.update {
                    it.copy(procesando = false, dialogo = null, mensaje = CONTRASENA_CAMBIADA)
                }
                is Resultado.Error -> _ui.update { it.copy(procesando = false, errorDialogo = r.error.mensaje) }
            }
        }
    }

    /**
     * Derecho de cancelación: borra la cuenta en este orden, deteniéndose en el primer error:
     * 1) confirma la contraseña, 2) borra la empresa y sus equipos en el servidor (falla si hay
     * reservas activas), 3) borra favoritos y consentimiento, 4) limpia el teléfono y
     * 5) borra el usuario de inicio de sesión. Los pasos 2 y 3 se pueden repetir sin problema.
     */
    fun eliminarCuenta(password: String) {
        if (_ui.value.procesando) return
        val esDemo = _ui.value.esDemo
        if (!esDemo && password.isEmpty()) {
            _ui.update { it.copy(errorDialogo = "Escribe tu contraseña para confirmar.") }
            return
        }
        _ui.update { it.copy(procesando = true, errorDialogo = null) }
        viewModelScope.launch {
            val error = (if (esDemo) null else auth.confirmarContrasena(password).mensajeDeError())
                ?: empresas.eliminarMiCuenta().mensajeDeError()
                ?: favoritos.borrarMisDatos().mensajeDeError()
                ?: run {
                    limpiarDatosLocales()
                    auth.eliminarUsuario().mensajeDeError()
                }
            _ui.update {
                if (error == null) it.copy(procesando = false, dialogo = null) else it.copy(procesando = false, errorDialogo = error)
            }
        }
    }

    private fun Resultado<*>.mensajeDeError(): String? = (this as? Resultado.Error)?.error?.mensaje

    companion object {
        const val DATOS_ACTUALIZADOS = "Datos actualizados."
        const val CONTRASENA_CAMBIADA = "Contraseña cambiada. Úsala la próxima vez que ingreses."
    }
}
