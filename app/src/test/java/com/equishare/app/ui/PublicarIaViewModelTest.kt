package com.equishare.app.ui

import androidx.lifecycle.SavedStateHandle
import com.equishare.app.domain.ia.EtiquetaFoto
import com.equishare.app.domain.model.Categoria
import com.equishare.app.ui.publicar.PublicarViewModel
import com.equishare.app.ui.publicar.RevisionIa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pruebas de la revisión de la foto con IA en la pantalla Publicar (con una IA falsa). */
@OptIn(ExperimentalCoroutinesApi::class)
class PublicarIaViewModelTest {
    private val despachador = UnconfinedTestDispatcher()

    private val laptop = listOf(EtiquetaFoto("Computer", 0.92f))
    private val sillas = listOf(EtiquetaFoto("Chair", 0.9f), EtiquetaFoto("Tablecloth", 0.7f))

    @Before
    fun preparar() = Dispatchers.setMain(despachador)

    @After
    fun limpiar() = Dispatchers.resetMain()

    private fun crear(ia: FakeAnalizadorFoto) =
        PublicarViewModel(SavedStateHandle(), FakeEquipoRepository(), FakeUbicacion(), ia)

    private fun PublicarViewModel.categoriaSugerida(): Categoria? =
        (ui.value.revisionIa as? RevisionIa.Lista)?.sugerencia?.categoria

    @Test
    fun sinCategoriaElegida_laIaLaEligeSola() = runTest(despachador) {
        val ia = FakeAnalizadorFoto(laptop)
        val vm = crear(ia)

        vm.alElegirFoto("content://fotos/laptop.jpg")

        assertEquals(listOf("content://fotos/laptop.jpg"), ia.pedidas)
        assertEquals(Categoria.TECNOLOGIA, vm.categoriaSugerida())
        assertEquals(Categoria.TECNOLOGIA, vm.ui.value.categoria)
        assertTrue(vm.ui.value.categoriaPorIa)
    }

    @Test
    fun conCategoriaElegida_soloSugiere_yElBotonLaAplica() = runTest(despachador) {
        val vm = crear(FakeAnalizadorFoto(laptop))
        vm.alElegirCategoria(Categoria.EVENTOS)

        vm.alElegirFoto("content://fotos/laptop.jpg")
        assertEquals(Categoria.EVENTOS, vm.ui.value.categoria)
        assertFalse(vm.ui.value.categoriaPorIa)
        assertEquals(Categoria.TECNOLOGIA, vm.categoriaSugerida())

        vm.usarSugerenciaIa()
        assertEquals(Categoria.TECNOLOGIA, vm.ui.value.categoria)
        assertTrue(vm.ui.value.categoriaPorIa)
    }

    @Test
    fun siLaIaEligioYLlegaOtraFoto_seActualiza_peroNoSiElUsuarioLaCambio() = runTest(despachador) {
        val ia = FakeAnalizadorFoto(laptop)
        val vm = crear(ia)
        vm.alElegirFoto("content://fotos/1.jpg")
        assertEquals(Categoria.TECNOLOGIA, vm.ui.value.categoria)

        ia.respuesta = sillas
        vm.alElegirFoto("content://fotos/2.jpg")
        assertEquals(Categoria.EVENTOS, vm.ui.value.categoria)

        vm.alElegirCategoria(Categoria.CONSTRUCCION)
        ia.respuesta = laptop
        vm.alElegirFoto("content://fotos/3.jpg")
        assertEquals(Categoria.CONSTRUCCION, vm.ui.value.categoria)
        assertFalse(vm.ui.value.categoriaPorIa)
    }

    @Test
    fun mientrasPiensa_muestraRevisando_yUnResultadoViejoSeIgnora() = runTest(despachador) {
        val ia = FakeAnalizadorFoto(sillas).apply { esperarEn = setOf("content://fotos/lenta.jpg") }
        val vm = crear(ia)

        vm.alElegirFoto("content://fotos/lenta.jpg")
        assertEquals(RevisionIa.Revisando, vm.ui.value.revisionIa)
        assertNull(vm.ui.value.categoria)

        // Se elige otra foto antes de que la primera termine.
        vm.alElegirFoto("content://fotos/rapida.jpg")
        assertEquals(Categoria.EVENTOS, vm.ui.value.categoria)

        // La respuesta de la foto vieja llega tarde y no debe cambiar nada.
        ia.responder("content://fotos/lenta.jpg", laptop)
        assertEquals(Categoria.EVENTOS, vm.ui.value.categoria)
        assertEquals("content://fotos/rapida.jpg", vm.ui.value.fotoUriLocal)
    }

    @Test
    fun siLaIaFallaOLanzaUnError_noBloqueaNiRompeLaPantalla() = runTest(despachador) {
        val falla = crear(FakeAnalizadorFoto(respuesta = null))
        falla.alElegirFoto("content://fotos/x.jpg")
        assertEquals(RevisionIa.NoDisponible, falla.ui.value.revisionIa)
        assertNull(falla.ui.value.categoria)

        val ia = FakeAnalizadorFoto(laptop).apply { lanzar = IllegalStateException("modelo dañado") }
        val explota = crear(ia)
        explota.alElegirFoto("content://fotos/y.jpg")
        assertEquals(RevisionIa.NoDisponible, explota.ui.value.revisionIa)
        assertEquals("content://fotos/y.jpg", explota.ui.value.fotoUriLocal)
    }

    @Test
    fun fotoQueNoEsUnEquipo_noEligeCategoria() = runTest(despachador) {
        val vm = crear(FakeAnalizadorFoto(listOf(EtiquetaFoto("Selfie", 0.9f))))
        vm.alElegirFoto("content://fotos/selfie.jpg")

        val revision = vm.ui.value.revisionIa as RevisionIa.Lista
        assertNull(revision.sugerencia.categoria)
        assertEquals("una selfie", revision.sugerencia.pareceOtraCosa)
        assertNull(vm.ui.value.categoria)
        assertFalse(vm.ui.value.categoriaPorIa)
    }

    @Test
    fun cancelarLaSeleccionDeFoto_noLlamaALaIa() = runTest(despachador) {
        val ia = FakeAnalizadorFoto(laptop)
        val vm = crear(ia)
        vm.alElegirFoto(null)
        assertTrue(ia.pedidas.isEmpty())
        assertEquals(RevisionIa.SinRevisar, vm.ui.value.revisionIa)
    }
}
