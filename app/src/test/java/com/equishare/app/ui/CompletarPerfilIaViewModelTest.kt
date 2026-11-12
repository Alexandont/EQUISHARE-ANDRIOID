package com.equishare.app.ui

import com.equishare.app.ui.auth.CompletarPerfilViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pruebas de "Leer RUC desde una foto" en el registro de la empresa (con un lector de texto falso). */
@OptIn(ExperimentalCoroutinesApi::class)
class CompletarPerfilIaViewModelTest {
    private val despachador = UnconfinedTestDispatcher()

    private val textoConsultaRuc = "Número de RUC: 20131312955 - CONSTRUCTORA EJEMPLO S.A.C.\nTipo Contribuyente: SOCIEDAD"

    @Before
    fun preparar() = Dispatchers.setMain(despachador)

    @After
    fun limpiar() = Dispatchers.resetMain()

    private fun crear(lector: FakeLectorTexto, empresas: FakeEmpresaRepository = FakeEmpresaRepository()) =
        CompletarPerfilViewModel(empresas, FakeFavoritos(), FakeAuth(), lector)

    @Test
    fun fotoConRucYRazonSocial_llenaAmbosYPideRevisarlos() = runTest(despachador) {
        val lector = FakeLectorTexto(textoConsultaRuc)
        val vm = crear(lector)

        vm.alElegirFotoRuc("content://fotos/ficha.jpg")

        assertEquals(listOf("content://fotos/ficha.jpg"), lector.pedidas)
        assertEquals("20131312955", vm.ui.value.ruc)
        assertEquals("CONSTRUCTORA EJEMPLO S.A.C.", vm.ui.value.razonSocial)
        assertEquals(CompletarPerfilViewModel.AVISO_COMPLETO, vm.ui.value.avisoFoto)
        assertFalse(vm.ui.value.leyendoFoto)
    }

    @Test
    fun fotoSoloConRuc_llenaElRucYConservaLaRazonEscrita() = runTest(despachador) {
        val vm = crear(FakeLectorTexto("RUC 20600123450"))
        vm.alCambiarRazon("Mi Empresa SAC")

        vm.alElegirFotoRuc("content://fotos/ficha.jpg")

        assertEquals("20600123450", vm.ui.value.ruc)
        assertEquals("Mi Empresa SAC", vm.ui.value.razonSocial)
        assertEquals(CompletarPerfilViewModel.AVISO_SOLO_RUC, vm.ui.value.avisoFoto)
    }

    @Test
    fun fotoSinUnRucValido_noCambiaLoEscritoYLoDice() = runTest(despachador) {
        val vm = crear(FakeLectorTexto("RUC: 20131312956 - EMPRESA SAC"))
        vm.alCambiarRuc("2013")

        vm.alElegirFotoRuc("content://fotos/borrosa.jpg")

        assertEquals("2013", vm.ui.value.ruc)
        assertEquals("", vm.ui.value.razonSocial)
        assertEquals(CompletarPerfilViewModel.AVISO_SIN_RUC, vm.ui.value.avisoFoto)
    }

    @Test
    fun siLaIaNoPuedeLeerOLanzaUnError_avisaSinRomper() = runTest(despachador) {
        val sinLectura = crear(FakeLectorTexto(null))
        sinLectura.alElegirFotoRuc("content://fotos/x.jpg")
        assertEquals(CompletarPerfilViewModel.AVISO_SIN_LECTURA, sinLectura.ui.value.avisoFoto)
        assertFalse(sinLectura.ui.value.leyendoFoto)

        val lector = FakeLectorTexto(textoConsultaRuc).apply { lanzar = IllegalStateException("modelo dañado") }
        val conError = crear(lector)
        conError.alElegirFotoRuc("content://fotos/y.jpg")
        assertEquals(CompletarPerfilViewModel.AVISO_SIN_LECTURA, conError.ui.value.avisoFoto)
        assertEquals("", conError.ui.value.ruc)
    }

    @Test
    fun sinAppDeCamara_sugiereUsarLaGaleria_yCancelarNoLlamaALaIa() = runTest(despachador) {
        val lector = FakeLectorTexto(textoConsultaRuc)
        val vm = crear(lector)

        vm.sinCamara()
        assertEquals(CompletarPerfilViewModel.AVISO_SIN_CAMARA, vm.ui.value.avisoFoto)

        vm.alElegirFotoRuc(null)
        assertTrue(lector.pedidas.isEmpty())
    }

    @Test
    fun losDatosLeidosSeGuardanConElBotonVerificar() = runTest(despachador) {
        val empresas = FakeEmpresaRepository()
        val vm = crear(FakeLectorTexto(textoConsultaRuc), empresas)
        var termino = false

        vm.alElegirFotoRuc("content://fotos/ficha.jpg")
        vm.alCambiarTelefono("949123456")
        vm.guardar { termino = true }

        assertTrue(termino)
        assertEquals(listOf(Triple("20131312955", "CONSTRUCTORA EJEMPLO S.A.C.", "949123456")), empresas.guardadas)
    }
}
