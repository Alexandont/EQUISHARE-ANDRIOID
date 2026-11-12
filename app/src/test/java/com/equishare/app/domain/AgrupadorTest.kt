package com.equishare.app.domain

import com.equishare.app.domain.mapa.Agrupador
import com.equishare.app.domain.mapa.TipoMapa
import com.equishare.app.ui.equipoDePrueba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del agrupado de marcadores del mapa, de las capas y de las iniciales del perfil. */
class AgrupadorTest {

    private fun equipo(id: String, lat: Double, lng: Double) = equipoDePrueba(id).copy(lat = lat, lng = lng)

    // Dos equipos a ~60 m en el centro de Trujillo y uno en Laredo (~8 km).
    private val centro1 = equipo("a", -8.11160, -79.02870)
    private val centro2 = equipo("b", -8.11180, -79.02910)
    private val laredo = equipo("c", -8.08890, -78.96010)

    @Test
    fun alejado_juntaLosEquiposCercanos() {
        val grupos = Agrupador.agrupar(listOf(centro1, centro2, laredo), zoom = 12f)
        assertEquals(2, grupos.size)
        assertEquals(listOf("a", "b"), grupos.first().equipos.map { it.id })
        assertEquals((centro1.lat + centro2.lat) / 2, grupos.first().lat, 1e-9)
        assertTrue(grupos.last().esUnSolo)
    }

    @Test
    fun cercaDeLaCalle_cadaEquipoVaSolo() {
        val grupos = Agrupador.agrupar(listOf(centro1, centro2, laredo), zoom = 16f)
        assertEquals(3, grupos.size)
        assertTrue(grupos.all { it.esUnSolo })
    }

    @Test
    fun muyLejos_todoEnUnGrupo_ySinEquiposNoHayGrupos() {
        val grupos = Agrupador.agrupar(listOf(centro1, centro2, laredo), zoom = 5f)
        assertEquals(1, grupos.size)
        assertEquals(3, grupos.first().cantidad)
        assertTrue(Agrupador.agrupar(emptyList(), zoom = 12f).isEmpty())
    }

    @Test
    fun nuncaSePierdeNiSeRepiteUnEquipo() {
        val muchos = (0 until 40).map { i -> equipo("e$i", -8.05 - i * 0.003, -79.05 + (i % 7) * 0.004) }
        for (zoom in listOf(8f, 10f, 12f, 14f, 15f, 16f, 18f)) {
            val ids = Agrupador.agrupar(muchos, zoom).flatMap { g -> g.equipos.map { it.id } }
            assertEquals(muchos.map { it.id }.sorted(), ids.sorted())
        }
    }

    @Test
    fun capaGuardadaDesconocida_vuelveANormal() {
        assertEquals(TipoMapa.SATELITE, TipoMapa.desde("SATELITE"))
        assertEquals(TipoMapa.NORMAL, TipoMapa.desde("OTRA"))
        assertEquals(TipoMapa.NORMAL, TipoMapa.desde(null))
    }

    @Test
    fun inicialesDelPerfil_ignoranElTipoDeSociedad() {
        assertEquals("CN", Formato.iniciales("Constructora Norte S.A.C."))
        assertEquals("ED", Formato.iniciales("Eventos Demo Libertad E.I.R.L."))
        assertEquals("ST", Formato.iniciales("Servicios Técnicos Demo S.R.L."))
        assertEquals("T", Formato.iniciales("taller"))
        assertEquals("EQ", Formato.iniciales("S.A.C."))
        assertEquals("EQ", Formato.iniciales(null))
    }
}
