package com.equishare.app.funcional

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.equishare.app.domain.model.Empresa
import com.equishare.app.domain.model.Sesion
import com.equishare.app.ui.auth.LoginScreen
import com.equishare.app.ui.auth.LoginViewModel
import com.equishare.app.ui.auth.VerificarCorreoScreen
import com.equishare.app.ui.auth.VerificarCorreoViewModel
import com.equishare.app.ui.perfil.PerfilScreen
import com.equishare.app.ui.perfil.PerfilViewModel
import com.equishare.app.ui.theme.EquishareTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas funcionales de la versión de seguridad: correo mal escrito, fuerza de la contraseña,
 * "¿Olvidaste tu contraseña?", verificación del correo y eliminar la cuenta desde Perfil.
 */
@RunWith(AndroidJUnit4::class)
class SeguridadFuncionalTest {

    @get:Rule
    val regla = createComposeRule()

    private val almacen = ViewModelStore()
    private val sesion = Sesion("uid-1", "ventas@empresa.pe", esDemo = false, correoVerificado = true)

    @After
    fun limpiar() {
        almacen.clear()
    }

    private fun campo(etiqueta: String): SemanticsNodeInteraction = regla.onNode(hasSetTextAction() and hasText(etiqueta))

    private fun boton(texto: String): SemanticsNodeInteraction = regla.onNode(hasText(texto) and hasClickAction())

    private fun abrirLogin(auth: AuthFalso) {
        val vm = almacen.crear { LoginViewModel(auth) }
        regla.setContent { EquishareTheme { LoginScreen(alRegistrarse = {}, viewModel = vm) } }
    }

    @Test
    fun registro_correoMalEscrito_ofreceLaCorreccion() {
        abrirLogin(AuthFalso())
        boton("¿Tu empresa es nueva? Crea una cuenta").performScrollTo().performClick()

        campo("Correo de la empresa").performTextInput("ventas@gmai.com")
        regla.esperarTexto("¿Quisiste decir ventas@gmail.com?")
        boton("¿Quisiste decir ventas@gmail.com?").performClick()

        regla.esperarTexto("ventas@gmail.com")
        regla.esperarQueDesaparezca("¿Quisiste decir ventas@gmail.com?")
    }

    @Test
    fun registro_muestraLaFuerzaDeLaContrasena() {
        abrirLogin(AuthFalso())
        boton("¿Tu empresa es nueva? Crea una cuenta").performScrollTo().performClick()

        campo("Contraseña").performTextInput("clave2026")
        regla.esperarTexto("Seguridad de la contraseña: Media")
        campo("Contraseña").performTextInput("Z!")
        regla.esperarTexto("Seguridad de la contraseña: Fuerte")
    }

    @Test
    fun olvidasteTuContrasena_enviaElEnlace() {
        val auth = AuthFalso()
        abrirLogin(auth)
        campo("Correo de la empresa").performTextInput("ventas@empresa.pe")

        boton("¿Olvidaste tu contraseña?").performScrollTo().performClick()
        regla.esperarTexto("Recuperar contraseña")
        boton("Enviar enlace").performClick()

        regla.esperarTexto(LoginViewModel.MENSAJE_RECUPERAR)
        assertTrue(auth.acciones.contains("recuperarContrasena:ventas@empresa.pe"))
    }

    @Test
    fun verificarCorreo_noDejaPasarHastaAbrirElEnlace() {
        val auth = AuthFalso(sesionInicial = sesion.copy(correoVerificado = false), esModoDemo = false)
        val vm = almacen.crear { VerificarCorreoViewModel(auth) }
        regla.setContent { EquishareTheme { VerificarCorreoScreen(viewModel = vm) } }

        regla.esperarTexto("Te enviamos un enlace a ventas@empresa.pe. Ábrelo y luego toca «Ya lo verifiqué».")
        boton("Ya lo verifiqué").performClick()
        regla.esperarTexto(VerificarCorreoViewModel.TODAVIA_NO)

        auth.correoYaVerificado = true
        boton("Ya lo verifiqué").performClick()
        regla.esperarTexto(VerificarCorreoViewModel.VERIFICADO)
        assertEquals(true, auth.sesion.value?.correoVerificado)
    }

    private fun abrirPerfil(auth: AuthFalso, empresas: EmpresasFalsas, favoritos: FavoritosFalsos = FavoritosFalsos()) {
        val vm = almacen.crear { PerfilViewModel(empresas, auth, favoritos) {} }
        regla.setContent { EquishareTheme { PerfilScreen(alVerPrivacidad = {}, viewModel = vm) } }
    }

    @Test
    fun perfil_editarDatos_guardaLaNuevaRazonSocial() {
        val empresas = EmpresasFalsas().apply {
            empresaActual = Empresa("uid-1", "Constructora Norte S.A.C.", "20131312955", "949123456", null, true, 0.0, 0)
        }
        abrirPerfil(AuthFalso(sesionInicial = sesion, esModoDemo = false), empresas)
        regla.esperarTexto("Constructora Norte S.A.C.")

        boton("Editar datos de la empresa").performScrollTo().performClick()
        campo("Razón social").performTextClearance()
        campo("Razón social").performTextInput("Constructora del Norte S.A.C.")
        boton("Guardar").performClick()

        regla.esperarTexto(PerfilViewModel.DATOS_ACTUALIZADOS)
        assertEquals(listOf(Triple("20131312955", "Constructora del Norte S.A.C.", "949123456")), empresas.guardadas.toList())
    }

    @Test
    fun perfil_eliminarCuenta_pideLaContrasenaYBorraTodo() {
        val auth = AuthFalso(sesionInicial = sesion, esModoDemo = false)
        val empresas = EmpresasFalsas().apply {
            empresaActual = Empresa("uid-1", "Constructora Norte S.A.C.", "20131312955", "949123456", null, true, 0.0, 0)
        }
        val favoritos = FavoritosFalsos()
        abrirPerfil(auth, empresas, favoritos)

        boton("Eliminar mi cuenta").performScrollTo().performClick()
        regla.esperarTexto("¿Eliminar tu cuenta?")
        boton("Eliminar definitivamente").performClick()
        regla.esperarTexto("Escribe tu contraseña para confirmar.")

        campo("Tu contraseña").performTextInput("Clave2026")
        boton("Eliminar definitivamente").performClick()

        regla.waitUntil(5_000) { auth.sesion.value == null }
        assertEquals(listOf("confirmarContrasena", "eliminarUsuario"), auth.acciones.toList())
        assertTrue(empresas.eliminada)
        assertTrue(favoritos.borrado)
    }
}
