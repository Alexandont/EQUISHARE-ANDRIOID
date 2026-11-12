package com.equishare.app.funcional

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.equishare.app.domain.model.Empresa
import com.equishare.app.ui.bienvenida.BienvenidaScreen
import com.equishare.app.ui.panel.PanelMetricas
import com.equishare.app.ui.panel.PanelViewModel
import com.equishare.app.ui.panel.SaludoInicio
import com.equishare.app.ui.theme.EquishareTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Pruebas funcionales del nuevo diseño: pantallas de bienvenida, saludo del inicio y panel de Mis equipos. */
@RunWith(AndroidJUnit4::class)
class DisenoFuncionalTest {

    @get:Rule
    val regla = createComposeRule()

    private val almacen = ViewModelStore()

    @After
    fun limpiar() {
        almacen.clear()
    }

    private fun boton(texto: String): SemanticsNodeInteraction = regla.onNode(hasText(texto) and hasClickAction())

    @Test
    fun bienvenida_seRecorreConSiguienteYTerminaConEmpezar() {
        val terminadas = AtomicInteger(0)
        regla.setContent { EquishareTheme { BienvenidaScreen(alTerminar = { terminadas.incrementAndGet() }) } }

        regla.esperarTexto("Bienvenido a EQUISHARE")
        boton("Siguiente").performClick()
        regla.esperarTexto("Encuentra equipos cerca")
        boton("Siguiente").performClick()
        regla.esperarTexto("Publica en un minuto")
        boton("Siguiente").performClick()
        regla.esperarTexto("Empresas verificadas")
        boton("Empezar").performClick()

        regla.runOnIdle { assertEquals(1, terminadas.get()) }
    }

    @Test
    fun bienvenida_saltarTerminaDeInmediato() {
        val terminadas = AtomicInteger(0)
        regla.setContent { EquishareTheme { BienvenidaScreen(alTerminar = { terminadas.incrementAndGet() }) } }

        boton("Saltar").performClick()
        regla.runOnIdle { assertEquals(1, terminadas.get()) }
    }

    @Test
    fun saludoYPanel_muestranLosNumerosDeLaEmpresa() {
        val empresas = EmpresasFalsas().apply {
            empresaActual = Empresa("uid-1", "Constructora Norte S.A.C.", "20131312955", "949123456", null, true, 0.0, 0)
        }
        val vm = almacen.crear { PanelViewModel(empresas, FavoritosFalsos()) }
        regla.setContent {
            EquishareTheme {
                androidx.compose.foundation.layout.Column {
                    SaludoInicio(viewModel = vm)
                    PanelMetricas(viewModel = vm)
                }
            }
        }

        regla.esperarTexto("Hola, Constructora Norte S.A.C.")
        regla.esperarTexto("Tu panel")
        regla.esperarTexto("3 de 4")
        regla.esperarTexto("Más alquilado: Mezcladora (3 veces)")
    }
}
