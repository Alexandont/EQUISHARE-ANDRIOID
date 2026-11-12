package com.equishare.app.data.auth

import com.equishare.app.data.local.AjustesRepositoryImpl
import com.equishare.app.domain.model.ErrorApp
import com.equishare.app.domain.model.Resultado
import com.equishare.app.domain.model.Sesion
import com.equishare.app.domain.model.TipoError
import com.equishare.app.domain.repositorio.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * MODO DEMOSTRACIÓN (cuando no existe app/google-services.json).
 * No valida contraseñas contra un proveedor: el identificador se deriva del correo para poder usar
 * las empresas de prueba del servidor (constructora@demo.pe, eventos@demo.pe, taller@demo.pe).
 * Solo para desarrollo y exposición; con Firebase configurado se usa [FirebaseAuthRepository].
 */
class DemoAuthRepository(
    private val ajustes: AjustesRepositoryImpl,
    alcance: CoroutineScope,
) : AuthRepository {

    private val _sesion = MutableStateFlow<Sesion?>(null)
    override val sesion: StateFlow<Sesion?> = _sesion.asStateFlow()

    private val _sesionCargada = MutableStateFlow(false)
    override val sesionCargada: StateFlow<Boolean> = _sesionCargada.asStateFlow()

    override val esModoDemo: Boolean = true

    init {
        alcance.launch {
            ajustes.sesionDemo.collect { guardada ->
                _sesion.value = guardada
                _sesionCargada.value = true
            }
        }
    }

    override suspend fun iniciarSesion(email: String, password: String): Resultado<Sesion> {
        // Esperar la restauración evita que su primera emisión (null) borre el nuevo login.
        sesionCargada.first { it }
        val sesion = Sesion(uid = uidDesdeEmail(email), email = email.trim().lowercase(), esDemo = true)
        ajustes.guardarSesionDemo(sesion)
        // El colector es la única fuente de sesión; publicar antes de guardar permitía
        // consultar /empresas/me sin token al llegar una emisión anterior de DataStore.
        this.sesion.first { it == sesion }
        return Resultado.Exito(sesion)
    }

    override suspend fun registrar(email: String, password: String): Resultado<Sesion> =
        iniciarSesion(email, password)

    override suspend fun cerrarSesion() {
        sesionCargada.first { it }
        ajustes.guardarSesionDemo(null)
        sesion.first { it == null }
    }

    override fun tokenParaApi(): String? = _sesion.value?.let { "demo:${it.uid}" }

    // En el modo demostración no hay un proveedor real: no se envían correos ni hay contraseñas guardadas.
    override suspend fun enviarVerificacion(): Resultado<Unit> = Resultado.Exito(Unit)

    override suspend fun revisarVerificacion(): Resultado<Boolean> = Resultado.Exito(true)

    override suspend fun recuperarContrasena(email: String): Resultado<Unit> =
        Resultado.Error(ErrorApp(TipoError.VALIDACION, SIN_CORREOS))

    override suspend fun confirmarContrasena(password: String): Resultado<Unit> = Resultado.Exito(Unit)

    override suspend fun cambiarContrasena(actual: String, nueva: String): Resultado<Unit> =
        Resultado.Error(ErrorApp(TipoError.VALIDACION, "En el modo demostración no se guardan contraseñas."))

    override suspend fun eliminarUsuario(): Resultado<Unit> {
        cerrarSesion()
        return Resultado.Exito(Unit)
    }

    companion object {
        const val SIN_CORREOS = "En el modo demostración no se envían correos."

        fun uidDesdeEmail(email: String): String {
            val local = email.trim().lowercase().substringBefore('@')
                .replace(Regex("[^a-z0-9-]"), "-")
                .trim('-')
                .take(40)
                .ifBlank { "usuario" }
            return "demo-$local"
        }
    }
}
