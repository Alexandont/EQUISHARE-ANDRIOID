package com.equishare.app.funcional

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.equishare.app.domain.model.Categoria
import com.equishare.app.domain.model.ErrorApp
import com.equishare.app.domain.model.TipoError
import com.equishare.app.ui.catalogo.CatalogoScreen
import com.equishare.app.ui.catalogo.CatalogoViewModel
import com.equishare.app.ui.theme.EquishareTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas funcionales del catálogo: búsqueda, filtros, "Cerca de mí" (geolocalización),
 * favoritos y el comportamiento sin conexión (datos guardados + recuperación automática).
 */
@RunWith(AndroidJUnit4::class)
class CatalogoFuncionalTest {

    @get:Rule
    val regla = createComposeRule()

    private val almacen = ViewModelStore()

    @Volatile
    private var equipoAbierto: String? = null

    @After
    fun limpiar() {
        almacen.clear()
    }

    private fun abrirCatalogo(
        equipos: EquiposFalsos = EquiposFalsos(),
        red: RedFalsa = RedFalsa(),
        ubicacion: UbicacionFalsa = UbicacionFalsa(),
        favoritos: FavoritosFalsos = FavoritosFalsos(),
    ) {
        val vm = almacen.crear { CatalogoViewModel(equipos, favoritos, red, ubicacion, AjustesFalsos()) }
        regla.setContent {
            EquishareTheme { CatalogoScreen(alAbrirEquipo = { equipoAbierto = it }, viewModel = vm) }
        }
    }

    private fun boton(texto: String): SemanticsNodeInteraction =
        regla.onNode(hasText(texto) and hasClickAction())

    @Test
    fun alAbrir_muestraLosEquiposDelServidor() {
        abrirCatalogo()

        regla.esperarTexto("Mezcladora de concreto")
        regla.onNodeWithText("Equipo de sonido profesional").assertExists()
        regla.onNodeWithText("Taladro percutor").assertExists()
    }

    @Test
    fun filtroDeCategoria_muestraSoloEsaCategoria() {
        val equipos = EquiposFalsos()
        abrirCatalogo(equipos)
        regla.esperarTexto("Mezcladora de concreto")

        boton("Eventos").performScrollTo().performClick()

        regla.esperarQueDesaparezca("Mezcladora de concreto")
        regla.onNodeWithText("Equipo de sonido profesional").assertExists()
        regla.onNodeWithText("Taladro percutor").assertDoesNotExist()
        boton("Eventos").assertIsSelected()
        assertEquals(Categoria.EVENTOS, equipos.filtrosRecibidos.last().categoria)
    }

    @Test
    fun busquedaPorTexto_filtraYSePuedeBorrar() {
        val equipos = EquiposFalsos()
        abrirCatalogo(equipos)
        regla.esperarTexto("Mezcladora de concreto")

        regla.onNode(hasSetTextAction()).performTextInput("taladro")

        regla.esperarQueDesaparezca("Mezcladora de concreto")
        regla.onNodeWithText("Taladro percutor").assertExists()
        assertEquals("taladro", equipos.filtrosRecibidos.last().texto)

        regla.onNodeWithContentDescription("Borrar búsqueda").performClick()
        regla.esperarTexto("Mezcladora de concreto")
    }

    @Test
    fun tocarUnEquipo_abreSuDetalle() {
        abrirCatalogo()
        regla.esperarTexto("Mezcladora de concreto")

        regla.onNodeWithText("Mezcladora de concreto").performClick()

        regla.waitUntil(5_000) { equipoAbierto != null }
        assertEquals("eq-1", equipoAbierto)
    }

    @Test
    fun corazon_agregaElEquipoAFavoritos() {
        val favoritos = FavoritosFalsos()
        abrirCatalogo(favoritos = favoritos)
        regla.esperarTexto("Mezcladora de concreto")

        regla.onAllNodesWithContentDescription("Agregar a favoritos").onFirst().performClick()

        regla.waitUntil(5_000) { favoritos.ids.value.size == 1 }
        regla.onNodeWithContentDescription("Quitar de favoritos").assertExists()
    }

    @Test
    fun cercaDeMi_enviaLaUbicacionYElRadioAlServidor() {
        val equipos = EquiposFalsos()
        val ubicacion = UbicacionFalsa()
        abrirCatalogo(equipos, ubicacion = ubicacion)
        regla.esperarTexto("Mezcladora de concreto")

        boton("Cerca de mí").performScrollTo().performClick()

        regla.waitUntil(5_000) { equipos.filtrosRecibidos.any { it.ubicacion != null } }
        val filtro = equipos.filtrosRecibidos.last { it.ubicacion != null }
        assertEquals(ubicacion.coordenadas, filtro.ubicacion)
        assertEquals(CatalogoViewModel.RADIO_CERCANIA_KM, filtro.radioKm!!, 0.0)
        boton("Cerca de mí").assertIsSelected()
    }

    @Test
    fun cercaDeMiSinGps_muestraAvisoYNoFiltra() {
        val equipos = EquiposFalsos()
        abrirCatalogo(equipos, ubicacion = UbicacionFalsa(coordenadas = null))
        regla.esperarTexto("Mezcladora de concreto")

        boton("Cerca de mí").performScrollTo().performClick()

        regla.esperarTexto("No se pudo obtener tu ubicación. Revisa el permiso y el GPS.")
        assertTrue(equipos.filtrosRecibidos.none { it.ubicacion != null })
    }

    @Test
    fun sinConexion_muestraDatosGuardadosYSeRecuperaSolo() {
        val equipos = EquiposFalsos().apply {
            catalogo.value = listOf(MEZCLADORA) // lo que ya estaba guardado en Room
            fallarCon = ErrorApp(TipoError.SIN_CONEXION)
        }
        val red = RedFalsa(enLinea = false)
        abrirCatalogo(equipos, red)

        // Modo avión: aviso visible y el equipo guardado se sigue mostrando.
        regla.esperarTexto(TEXTO_SIN_CONEXION)
        regla.onNodeWithText("Mezcladora de concreto").assertExists()
        regla.onNodeWithText("Taladro percutor").assertDoesNotExist()

        // Vuelve la conexión: el aviso se va y el catálogo se recarga sin tocar nada.
        equipos.fallarCon = null
        red.estado.value = true

        regla.esperarQueDesaparezca(TEXTO_SIN_CONEXION)
        regla.esperarTexto("Taladro percutor")
    }

    @Test
    fun errorDelServidorSinDatos_permiteReintentar() {
        val equipos = EquiposFalsos().apply { fallarCon = ErrorApp(TipoError.SERVIDOR) }
        abrirCatalogo(equipos)

        regla.esperarTexto(TipoError.mensajePorDefecto(TipoError.SERVIDOR))

        equipos.fallarCon = null
        boton("Reintentar").performClick()

        regla.esperarTexto("Mezcladora de concreto")
    }

    @Test
    fun sinResultados_muestraEstadoVacio() {
        abrirCatalogo(EquiposFalsos(respuestaServidor = emptyList()))

        regla.esperarTexto("No hay equipos con esos filtros")
    }
}
