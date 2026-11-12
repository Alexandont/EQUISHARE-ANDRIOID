package com.equishare.app.funcional

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.equishare.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de humo de la app integrada: abre MainActivity con TODAS las piezas reales
 * (Room, DataStore, Retrofit, Firebase o modo demo, ubicación) y comprueba que la app arranca
 * sin cerrarse y llega a una pantalla válida. Funciona con o sin el servidor Node.js encendido.
 */
@RunWith(AndroidJUnit4::class)
class ArranqueAppTest {

    @get:Rule
    val regla = createAndroidComposeRule<MainActivity>()

    @Test
    fun laAppIntegradaArrancaYMuestraUnaPantallaValida() {
        val pantallaValida =
            hasText("Ingresar") or // inicio de sesión
                hasText("Revisa tu correo") or // cuenta con el correo sin verificar
                hasText("Saltar") or // pantallas de bienvenida (primera vez)
                hasText("Registra tu empresa") or // perfil incompleto
                hasText("No se pudo conectar con el servidor") or // servidor apagado
                hasContentDescription("Actualizar catálogo") // catálogo (sesión ya iniciada)

        regla.waitUntil(30_000) {
            regla.onAllNodes(pantallaValida).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        regla.onAllNodesWithText("Preparando EQUISHARE…").assertCountEquals(0)
    }
}
