package com.equishare.app.funcional

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.equishare.app.ui.auth.LoginScreen
import com.equishare.app.ui.auth.LoginViewModel
import com.equishare.app.ui.theme.EquishareTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pruebas funcionales del inicio de sesión y registro: se escribe y se pulsa en la pantalla real,
 * como lo haría un usuario, y se verifica lo que aparece.
 */
@RunWith(AndroidJUnit4::class)
class LoginFuncionalTest {

    @get:Rule
    val regla = createComposeRule()

    private val almacen = ViewModelStore()

    @After
    fun limpiar() {
        almacen.clear()
    }

    private fun abrirLogin(auth: AuthFalso, alRegistrarse: (String) -> Unit = {}) {
        val vm = almacen.crear { LoginViewModel(auth) }
        regla.setContent {
            EquishareTheme { LoginScreen(alRegistrarse = alRegistrarse, viewModel = vm) }
        }
    }

    private fun campo(etiqueta: String): SemanticsNodeInteraction =
        regla.onNode(hasSetTextAction() and hasText(etiqueta))

    private fun boton(texto: String): SemanticsNodeInteraction =
        regla.onNode(hasText(texto) and hasClickAction())

    @Test
    fun formularioVacio_muestraErroresYNoLlamaAlServidor() {
        val auth = AuthFalso()
        abrirLogin(auth)

        boton("Ingresar").performScrollTo().performClick()

        regla.onNodeWithText("Ingresa tu correo.").assertExists()
        regla.onNodeWithText("La contraseña debe tener al menos 8 caracteres.").assertExists()
        assertEquals(0, auth.llamadas.get())
    }

    @Test
    fun datosConFormatoIncorrecto_muestranMensajesClaros() {
        val auth = AuthFalso()
        abrirLogin(auth)

        campo("Correo de la empresa").performTextInput("correo-sin-arroba")
        campo("Contraseña").performTextInput("solotexto")
        boton("Ingresar").performScrollTo().performClick()

        regla.onNodeWithText("El correo no tiene un formato válido.").assertExists()
        regla.onNodeWithText("Usa letras y números en la contraseña.").assertExists()
        assertEquals(0, auth.llamadas.get())
    }

    @Test
    fun datosValidos_inicianSesionYLimpianElFormulario() {
        val auth = AuthFalso()
        abrirLogin(auth)

        campo("Correo de la empresa").performTextInput("eventos@demo.pe")
        campo("Contraseña").performTextInput("clave2026")
        boton("Ingresar").performScrollTo().performClick()

        regla.waitUntil(5_000) { auth.llamadas.get() == 1 }
        assertEquals("eventos@demo.pe", auth.ultimoEmail)
        // Por privacidad, el formulario se vacía después de ingresar.
        regla.waitUntil(5_000) {
            regla.onAllNodesWithText("eventos@demo.pe").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
    }

    @Test
    fun credencialesIncorrectas_muestranElErrorDelServidor() {
        abrirLogin(AuthFalso(fallar = true))

        campo("Correo de la empresa").performTextInput("eventos@demo.pe")
        campo("Contraseña").performTextInput("clave9999")
        boton("Ingresar").performScrollTo().performClick()

        regla.esperarTexto("Correo o contraseña incorrectos.")
    }

    @Test
    fun botonDelOjo_muestraYOcultaLaContrasena() {
        abrirLogin(AuthFalso())

        regla.onNodeWithContentDescription("Mostrar contraseña").performClick()
        regla.onNodeWithContentDescription("Ocultar contraseña").assertExists()
        regla.onNodeWithContentDescription("Ocultar contraseña").performClick()
        regla.onNodeWithContentDescription("Mostrar contraseña").assertExists()
    }

    @Test
    fun registro_exigeContrasenasIgualesYAceptarLaPrivacidad() {
        val auth = AuthFalso()
        val registrados = CopyOnWriteArrayList<String>()
        abrirLogin(auth) { registrados += it }

        boton("¿Tu empresa es nueva? Crea una cuenta").performScrollTo().performClick()
        campo("Correo de la empresa").performTextInput("nueva@empresa.pe")
        campo("Contraseña").performTextInput("clave2026")
        campo("Repite la contraseña").performScrollTo().performTextInput("clave2027")
        boton("Crear cuenta").performScrollTo().performClick()

        // 1) Contraseñas distintas y privacidad sin aceptar: no se envía nada.
        regla.onNodeWithText("Las contraseñas no coinciden.").assertExists()
        regla.onNodeWithText("Debes leer y aceptar la política de privacidad para crear la cuenta.").assertExists()
        assertEquals(0, auth.llamadas.get())

        // 2) Se corrige la confirmación y se marca la casilla de privacidad (está desmarcada por defecto).
        campo("Repite la contraseña").performScrollTo().performTextReplacement("clave2026")
        regla.onNode(isToggleable()).performScrollTo().performClick()
        boton("Crear cuenta").performScrollTo().performClick()

        regla.waitUntil(5_000) { registrados.isNotEmpty() }
        assertEquals("nueva@empresa.pe", registrados.single())
        assertEquals(1, auth.llamadas.get())
    }

    @Test
    fun enlaceDePrivacidad_abreYCierraLaPolitica() {
        abrirLogin(AuthFalso())

        boton("¿Tu empresa es nueva? Crea una cuenta").performScrollTo().performClick()
        boton("política de privacidad").performScrollTo().performClick()

        regla.onAllNodesWithText("Política de privacidad").onFirst().assertExists()
        boton("Cerrar").performClick()
        regla.onNode(hasText("Cerrar") and hasClickAction()).assertDoesNotExist()
    }
}
