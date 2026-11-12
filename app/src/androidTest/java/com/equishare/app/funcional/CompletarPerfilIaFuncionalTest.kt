package com.equishare.app.funcional

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.equishare.app.ui.auth.CompletarPerfilScreen
import com.equishare.app.ui.auth.CompletarPerfilViewModel
import com.equishare.app.ui.theme.EquishareTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas funcionales de "Leer RUC desde una foto" en la pantalla Registra tu empresa.
 * La cámara y la galería son apps del sistema que una prueba no puede manejar, así que se entrega
 * al ViewModel la foto elegida directamente, como lo haría la pantalla. El texto lo da un lector falso.
 */
@RunWith(AndroidJUnit4::class)
class CompletarPerfilIaFuncionalTest {

    @get:Rule
    val regla = createComposeRule()

    private val almacen = ViewModelStore()

    @Volatile
    private var termino = false

    @After
    fun limpiar() {
        almacen.clear()
    }

    private fun abrir(lector: LectorTextoFalso, empresas: EmpresasFalsas = EmpresasFalsas()): CompletarPerfilViewModel {
        val vm = almacen.crear { CompletarPerfilViewModel(empresas, FavoritosFalsos(), AuthFalso(), lector) }
        regla.setContent {
            EquishareTheme { CompletarPerfilScreen(alTerminar = { termino = true }, viewModel = vm) }
        }
        return vm
    }

    private fun campo(etiqueta: String): SemanticsNodeInteraction =
        regla.onNode(hasSetTextAction() and hasText(etiqueta))

    private fun boton(texto: String): SemanticsNodeInteraction =
        regla.onNode(hasText(texto) and hasClickAction())

    @Test
    fun fotoDeLaFichaRuc_llenaElRucYLaRazonSocial_yPermiteContinuar() {
        val empresas = EmpresasFalsas()
        val vm = abrir(LectorTextoFalso(TEXTO_FICHA), empresas)
        boton("Tomar foto").assertExists()
        boton("Elegir imagen").assertExists()

        regla.runOnIdle { vm.alElegirFotoRuc(FOTO) }

        regla.esperarTexto("20131312955")
        regla.onNodeWithText("CONSTRUCTORA EJEMPLO S.A.C.").assertExists()
        regla.onNodeWithText(CompletarPerfilViewModel.AVISO_COMPLETO).assertExists()

        campo("Celular de contacto").performScrollTo().performTextInput("949123456")
        boton("Verificar y continuar").performScrollTo().performClick()

        regla.waitUntil(5_000) { termino }
        assertEquals(listOf(Triple("20131312955", "CONSTRUCTORA EJEMPLO S.A.C.", "949123456")), empresas.guardadas.toList())
    }

    @Test
    fun fotoSinRucValido_avisaYNoLlenaNada() {
        val lector = LectorTextoFalso("Foto borrosa sin número")
        val vm = abrir(lector)

        regla.runOnIdle { vm.alElegirFotoRuc(FOTO) }

        regla.esperarTexto(CompletarPerfilViewModel.AVISO_SIN_RUC)
        regla.runOnIdle {
            assertEquals("", vm.ui.value.ruc)
            assertTrue(lector.pedidas.contains(FOTO))
        }
    }

    private companion object {
        const val FOTO = "content://com.equishare.prueba/ficha_ruc.jpg"
        const val TEXTO_FICHA = "Número de RUC: 20131312955 - CONSTRUCTORA EJEMPLO S.A.C.\nTipo Contribuyente: SOCIEDAD ANONIMA CERRADA"
    }
}
