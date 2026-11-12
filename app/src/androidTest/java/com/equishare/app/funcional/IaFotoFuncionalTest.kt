package com.equishare.app.funcional

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.equishare.app.domain.ia.EtiquetaFoto
import com.equishare.app.domain.model.Categoria
import com.equishare.app.ui.publicar.PublicarViewModel
import com.equishare.app.ui.publicar.SeccionFotoConIa
import com.equishare.app.ui.theme.EquishareTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas funcionales de la IA de la foto en Publicar: se toca "Elegir foto", la IA revisa la foto
 * y la tarjeta muestra la categoría sugerida. Se usa el ViewModel real con una IA falsa para que el
 * resultado sea siempre el mismo. (El modelo real se prueba en IaModeloInstrumentadoTest.)
 */
@RunWith(AndroidJUnit4::class)
class IaFotoFuncionalTest {

    @get:Rule
    val regla = createComposeRule()

    private val almacen = ViewModelStore()

    @After
    fun limpiar() {
        almacen.clear()
    }

    private fun abrir(ia: AnalizadorFotoFalso): PublicarViewModel {
        val vm = almacen.crear { PublicarViewModel(SavedStateHandle(), EquiposFalsos(), UbicacionFalsa(), ia) }
        regla.setContent {
            EquishareTheme {
                val ui by vm.ui.collectAsStateWithLifecycle()
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // El selector de fotos del sistema se reemplaza por una foto fija de prueba.
                    SeccionFotoConIa(
                        ui = ui,
                        alPedirFoto = { vm.alElegirFoto(FOTO) },
                        alUsarSugerencia = vm::usarSugerenciaIa,
                    )
                }
            }
        }
        return vm
    }

    private fun boton(texto: String): SemanticsNodeInteraction =
        regla.onNode(hasText(texto) and hasClickAction())

    @Test
    fun fotoDeLaptop_laIaSugiereTecnologiaYLaElige() {
        val ia = AnalizadorFotoFalso(listOf(EtiquetaFoto("Computer", 0.92f)))
        val vm = abrir(ia)

        boton("Elegir foto").performScrollTo().performClick()

        regla.esperarTexto("Categoría sugerida: Tecnología")
        regla.onNodeWithText("La IA ve: computadora (92 %)").assertExists()
        regla.onNodeWithText("La elegimos por ti. Puedes cambiarla en Categoría.").assertExists()
        regla.runOnIdle {
            assertEquals(listOf(FOTO), ia.pedidas.toList())
            assertEquals(Categoria.TECNOLOGIA, vm.ui.value.categoria)
        }
    }

    @Test
    fun siYaHabiaOtraCategoria_elBotonUsarAplicaLaSugerencia() {
        val vm = abrir(AnalizadorFotoFalso(listOf(EtiquetaFoto("Chair", 0.9f), EtiquetaFoto("Tablecloth", 0.8f))))
        regla.runOnIdle { vm.alElegirCategoria(Categoria.CONSTRUCCION) }

        boton("Elegir foto").performScrollTo().performClick()
        regla.esperarTexto("Usar Eventos")
        regla.runOnIdle { assertEquals(Categoria.CONSTRUCCION, vm.ui.value.categoria) }

        boton("Usar Eventos").performScrollTo().performClick()
        regla.esperarTexto("La elegimos por ti. Puedes cambiarla en Categoría.")
        regla.runOnIdle { assertEquals(Categoria.EVENTOS, vm.ui.value.categoria) }
    }

    @Test
    fun selfie_avisaQueNoEsUnEquipoYNoEligeCategoria() {
        val vm = abrir(AnalizadorFotoFalso(listOf(EtiquetaFoto("Selfie", 0.9f), EtiquetaFoto("Smile", 0.8f))))

        boton("Elegir foto").performScrollTo().performClick()

        regla.esperarTexto("La foto parece mostrar una selfie, no un equipo. Toma la foto al equipo completo.")
        regla.onNodeWithText("Puedes publicar igual.").assertExists()
        regla.runOnIdle { assertNull(vm.ui.value.categoria) }
    }

    @Test
    fun siLaIaNoPuedeRevisar_avisaYNoBloquea() {
        val vm = abrir(AnalizadorFotoFalso(respuesta = null))

        boton("Elegir foto").performScrollTo().performClick()

        regla.esperarTexto("No se pudo revisar la foto con IA. Puedes publicar igual.")
        regla.onNode(hasText("Cambiar foto") and hasClickAction()).assertExists()
        regla.runOnIdle { assertEquals(FOTO, vm.ui.value.fotoUriLocal) }
    }

    private companion object {
        const val FOTO = "content://com.equishare.prueba/foto_equipo.jpg"
    }
}
