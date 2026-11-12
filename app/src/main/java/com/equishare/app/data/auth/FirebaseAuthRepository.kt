package com.equishare.app.data.auth

import com.equishare.app.domain.model.ErrorApp
import com.equishare.app.domain.model.Resultado
import com.equishare.app.domain.model.Sesion
import com.equishare.app.domain.model.TipoError
import com.equishare.app.domain.repositorio.AuthRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** Autenticación con Firebase Authentication (correo y contraseña). */
class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthRepository {

    private val _sesion = MutableStateFlow(firebaseAuth.currentUser?.aSesion())
    override val sesion: StateFlow<Sesion?> = _sesion.asStateFlow()
    override val sesionCargada: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
    override val esModoDemo: Boolean = false

    init {
        // Los correos de verificación y de cambio de contraseña llegan en español.
        firebaseAuth.setLanguageCode("es")
        firebaseAuth.addAuthStateListener { auth -> _sesion.value = auth.currentUser?.aSesion() }
    }

    override suspend fun iniciarSesion(email: String, password: String): Resultado<Sesion> = ejecutar {
        firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await().user
    }

    override suspend fun registrar(email: String, password: String): Resultado<Sesion> = ejecutar {
        val usuario = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await().user
        // Seguridad: se envía el enlace de verificación. Si falla, la pantalla "Revisa tu correo" permite reenviarlo.
        runCatching { usuario?.sendEmailVerification()?.await() }
        usuario
    }

    override suspend fun cerrarSesion() {
        firebaseAuth.signOut()
    }

    override suspend fun enviarVerificacion(): Resultado<Unit> = operacion {
        val usuario = firebaseAuth.currentUser ?: throw IllegalStateException(SIN_SESION)
        usuario.sendEmailVerification().await()
        Unit
    }

    override suspend fun revisarVerificacion(): Resultado<Boolean> = operacion {
        val usuario = firebaseAuth.currentUser ?: throw IllegalStateException(SIN_SESION)
        usuario.reload().await()
        val actual = firebaseAuth.currentUser ?: throw IllegalStateException(SIN_SESION)
        if (actual.isEmailVerified) {
            // Se pide un token nuevo para que el servidor también vea el correo como verificado.
            actual.getIdToken(true).await()
            _sesion.value = actual.aSesion()
        }
        actual.isEmailVerified
    }

    override suspend fun recuperarContrasena(email: String): Resultado<Unit> = try {
        firebaseAuth.sendPasswordResetEmail(email.trim()).await()
        Resultado.Exito(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseAuthInvalidUserException) {
        // Misma respuesta exista o no la cuenta: así nadie puede averiguar qué correos están registrados.
        Resultado.Exito(Unit)
    } catch (e: Exception) {
        Resultado.Error(traducir(e))
    }

    override suspend fun confirmarContrasena(password: String): Resultado<Unit> = operacion {
        val usuario = firebaseAuth.currentUser ?: throw IllegalStateException(SIN_SESION)
        val email = usuario.email ?: throw IllegalStateException(SIN_SESION)
        usuario.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        Unit
    }

    override suspend fun cambiarContrasena(actual: String, nueva: String): Resultado<Unit> = operacion {
        val usuario = firebaseAuth.currentUser ?: throw IllegalStateException(SIN_SESION)
        val email = usuario.email ?: throw IllegalStateException(SIN_SESION)
        usuario.reauthenticate(EmailAuthProvider.getCredential(email, actual)).await()
        usuario.updatePassword(nueva).await()
        Unit
    }

    override suspend fun eliminarUsuario(): Resultado<Unit> = operacion {
        val usuario = firebaseAuth.currentUser ?: throw IllegalStateException(SIN_SESION)
        usuario.delete().await()
        firebaseAuth.signOut()
        _sesion.value = null
    }

    override fun tokenParaApi(): String? {
        val usuario = firebaseAuth.currentUser ?: return null
        return runCatching { Tasks.await(usuario.getIdToken(false), 10, TimeUnit.SECONDS).token }.getOrNull()
    }

    /** Ejecuta una operación de la cuenta y convierte los errores de Firebase en mensajes claros. */
    private suspend fun <T> operacion(accion: suspend () -> T): Resultado<T> = try {
        Resultado.Exito(accion())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Resultado.Error(traducir(e))
    }

    private fun traducir(e: Exception): ErrorApp = when (e) {
        is FirebaseAuthWeakPasswordException ->
            ErrorApp(TipoError.VALIDACION, "La contraseña es muy débil. Usa al menos 8 caracteres con letras y números.")
        is FirebaseAuthInvalidCredentialsException -> ErrorApp(TipoError.NO_AUTENTICADO, "La contraseña no es correcta.")
        is FirebaseAuthInvalidUserException ->
            ErrorApp(TipoError.NO_AUTENTICADO, "Tu sesión ya no es válida. Vuelve a iniciar sesión.")
        is FirebaseTooManyRequestsException ->
            ErrorApp(TipoError.VALIDACION, "Hiciste muchos intentos seguidos. Espera unos minutos y vuelve a intentarlo.")
        is FirebaseNetworkException -> ErrorApp(TipoError.SIN_CONEXION, "Sin conexión a internet. Revisa tu conexión e inténtalo de nuevo.")
        is IllegalStateException -> ErrorApp(TipoError.NO_AUTENTICADO, e.message ?: SIN_SESION)
        else -> ErrorApp(TipoError.DESCONOCIDO, e.localizedMessage ?: "No se pudo completar la operación.")
    }

    private suspend fun ejecutar(accion: suspend () -> FirebaseUser?): Resultado<Sesion> = try {
        val usuario = accion()
        if (usuario == null) {
            Resultado.Error(ErrorApp(TipoError.DESCONOCIDO, "No se pudo iniciar la sesión."))
        } else {
            val sesion = usuario.aSesion()
            _sesion.value = sesion
            Resultado.Exito(sesion)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseAuthWeakPasswordException) {
        Resultado.Error(ErrorApp(TipoError.VALIDACION, "La contraseña es muy débil. Usa al menos 8 caracteres con letras y números."))
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        Resultado.Error(ErrorApp(TipoError.NO_AUTENTICADO, "Correo o contraseña incorrectos."))
    } catch (e: FirebaseAuthInvalidUserException) {
        Resultado.Error(ErrorApp(TipoError.NO_AUTENTICADO, "No existe una cuenta con ese correo o fue deshabilitada."))
    } catch (e: FirebaseAuthUserCollisionException) {
        Resultado.Error(ErrorApp(TipoError.CONFLICTO, "Ya existe una cuenta con ese correo. Inicia sesión."))
    } catch (e: FirebaseNetworkException) {
        Resultado.Error(ErrorApp(TipoError.SIN_CONEXION, "Sin conexión a internet para validar tu cuenta."))
    } catch (e: Exception) {
        Resultado.Error(ErrorApp(TipoError.DESCONOCIDO, e.localizedMessage ?: "No se pudo completar la autenticación."))
    }

    private fun FirebaseUser.aSesion(): Sesion =
        Sesion(uid = uid, email = email, esDemo = false, correoVerificado = isEmailVerified)

    private companion object {
        const val SIN_SESION = "Tu sesión terminó. Vuelve a iniciar sesión."
    }
}
