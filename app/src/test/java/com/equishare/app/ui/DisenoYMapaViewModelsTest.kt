package com.equishare.app.ui

import androidx.lifecycle.SavedStateHandle
import com.equishare.app.domain.mapa.TipoMapa
import com.equishare.app.domain.model.Coordenadas
import com.equishare.app.domain.model.Empresa
import com.equishare.app.domain.model.ErrorApp
import com.equishare.app.domain.model.Resultado
import com.equishare.app.domain.model.TipoError
import com.equishare.app.domain.repositorio.BuscadorDirecciones
import com.equishare.app.ui.bienvenida.BienvenidaViewModel
import com.equishare.app.ui.mapa.MapaViewModel
import com.equishare.app.ui.panel.PanelViewModel
import com.equishare.app.ui.publicar.PublicarViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Pruebas de la versión 0.16.0: bienvenida, mapa, panel y búsqueda de direcciones. */
@OptIn(ExperimentalCoroutinesApi::class)
class DisenoYMapaViewModelsTest {
    private val despachador = UnconfinedTestDispatcher()

    @Before
    fun preparar() = Dispatchers.setMain(despachador)

    @After
    fun limpiar() = Dispatchers.resetMain()

    @Test
    fun bienvenida_seMuestraUnaSolaVez() = runTest(despachador) {
        val ajustes = FakeAjustes()
        val vm = BienvenidaViewModel(ajustes)
        assertEquals(true, vm.mostrar.value)
        vm.terminar()
        assertEquals(false, vm.mostrar.value)
        assertEquals(false, BienvenidaViewModel(ajustes).mostrar.value)
    }

    @Test
    fun mapa_recuerdaLaCapaYSeleccionaEquipos() = runTest(despachador) {
        val ajustes = FakeAjustes()
        val repo = FakeEquipoRepository().apply { catalogo.value = listOf(equipoDePrueba("1"), equipoDePrueba("2")) }
        val vm = MapaViewModel(repo, FakeUbicacion(), ajustes)
        backgroundScope.launch { vm.ui.collect {} }
        assertEquals(TipoMapa.NORMAL, vm.ui.value.tipoMapa)

        vm.alElegirTipoMapa(TipoMapa.SATELITE)
        assertEquals(TipoMapa.SATELITE, ajustes.tipo.value)
        assertEquals(TipoMapa.SATELITE, vm.ui.value.tipoMapa)

        vm.alSeleccionar("2")
        assertEquals("2", vm.ui.value.seleccionado)
        // Si el equipo sale del catálogo (por un filtro), se quita la selección.
        repo.catalogo.value = listOf(equipoDePrueba("1"))
        assertNull(vm.ui.value.seleccionado)
    }

    @Test
    fun panel_muestraEmpresaNumerosYFavoritos_yConservaLosDatosSiFallaLaRed() = runTest(despachador) {
        val empresas = FakeEmpresaRepository().apply {
            miEmpresaActual = Empresa("uid-1", "Constructora Norte S.A.C.", "20131312955", "949123456", null, true, 0.0, 0)
        }
        val favoritos = FakeFavoritos()
        val vm = PanelViewModel(empresas, favoritos)
        assertEquals("Constructora Norte S.A.C.", vm.ui.value.empresa)
        assertEquals(2, vm.ui.value.metricas?.reservasPendientes)

        favoritos.establecer(equipoDePrueba("9"), true)
        assertEquals(1, vm.ui.value.favoritos)

        empresas.metricasActuales = Resultado.Error(ErrorApp(TipoError.SIN_CONEXION, "Sin conexión."))
        vm.cargar()
        assertEquals("Sin conexión.", vm.ui.value.error)
        assertNotNull(vm.ui.value.metricas)
    }

    @Test
    fun publicar_buscaLaDireccionYUbicaElPunto() = runTest(despachador) {
        val pedidas = mutableListOf<String>()
        val buscador = object : BuscadorDirecciones {
            override suspend fun buscar(direccion: String): Coordenadas? {
                pedidas += direccion
                return if ("España" in direccion) Coordenadas(-8.1093, -79.0289) else null
            }
        }
        val vm = PublicarViewModel(SavedStateHandle(), FakeEquipoRepository(), FakeUbicacion(), FakeAnalizadorFoto(), buscador)

        vm.buscarDireccion()
        assertEquals("Escribe la dirección para buscarla en el mapa.", vm.ui.value.errorDireccion)
        assertEquals(0, pedidas.size)

        vm.alCambiarDireccion("Av. España 1234, Trujillo")
        vm.buscarDireccion()
        assertEquals(-8.1093, vm.ui.value.lat!!, 1e-9)
        assertEquals(PublicarViewModel.DIRECCION_ENCONTRADA, vm.ui.value.mensaje)

        vm.mensajeMostrado()
        vm.alCambiarDireccion("Calle que no existe 999")
        vm.buscarDireccion()
        assertEquals(PublicarViewModel.DIRECCION_NO_ENCONTRADA, vm.ui.value.mensaje)
        assertEquals(-8.1093, vm.ui.value.lat!!, 1e-9)
    }
}
