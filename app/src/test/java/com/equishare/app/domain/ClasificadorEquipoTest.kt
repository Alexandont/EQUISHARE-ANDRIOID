package com.equishare.app.domain

import com.equishare.app.domain.ia.ClasificadorEquipo
import com.equishare.app.domain.ia.EtiquetaFoto
import com.equishare.app.domain.ia.PistaIa
import com.equishare.app.domain.model.Categoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del clasificador de la IA: convierte lo que ve ML Kit en una categoría de EQUISHARE. */
class ClasificadorEquipoTest {

    private fun e(texto: String, confianza: Float) = EtiquetaFoto(texto, confianza)

    @Test
    fun laptop_sugiereTecnologia() {
        val r = ClasificadorEquipo.clasificar(listOf(e("Computer", 0.92f), e("Mobile phone", 0.6f)))
        assertEquals(Categoria.TECNOLOGIA, r.categoria)
        assertEquals(listOf(PistaIa("computadora", 92), PistaIa("celular", 60)), r.pistas)
        assertNull(r.pareceOtraCosa)
    }

    @Test
    fun sillasYMantel_sugiereEventos() {
        val r = ClasificadorEquipo.clasificar(listOf(e("Chair", 0.88f), e("Tablecloth", 0.75f), e("Event", 0.62f)))
        assertEquals(Categoria.EVENTOS, r.categoria)
        assertEquals(listOf("sillas", "mantel", "evento"), r.pistas.map { it.nombre })
    }

    @Test
    fun excavadora_sugiereConstruccionConLasPistasDeMayorConfianza() {
        val r = ClasificadorEquipo.clasificar(
            listOf(e("Construction", 0.7f), e("Vehicle", 0.9f), e("Tractor", 0.82f), e("Soil", 0.6f)),
        )
        assertEquals(Categoria.CONSTRUCCION, r.categoria)
        assertEquals(
            listOf(PistaIa("vehículo", 90), PistaIa("maquinaria pesada", 82), PistaIa("obra de construcción", 70)),
            r.pistas,
        )
    }

    @Test
    fun autoComun_noAdivinaUnaCategoria() {
        // "Vehicle" solo vale medio punto: un auto no basta para decir "Construcción".
        val r = ClasificadorEquipo.clasificar(listOf(e("Car", 0.95f), e("Vehicle", 0.93f), e("Wheel", 0.9f), e("Tire", 0.88f)))
        assertNull(r.categoria)
        assertTrue(r.pistas.isEmpty())
        assertNull(r.pareceOtraCosa)
    }

    @Test
    fun selfieYComida_avisanQueNoEsUnEquipo() {
        val selfie = ClasificadorEquipo.clasificar(listOf(e("Selfie", 0.9f), e("Smile", 0.85f), e("Person", 0.8f)))
        assertNull(selfie.categoria)
        assertEquals("una selfie", selfie.pareceOtraCosa)

        val comida = ClasificadorEquipo.clasificar(listOf(e("Food", 0.95f), e("Cuisine", 0.9f)))
        assertEquals("comida", comida.pareceOtraCosa)
    }

    @Test
    fun etiquetasConPocaConfianzaOInvalidas_seIgnoran() {
        assertNull(ClasificadorEquipo.clasificar(listOf(e("Computer", 0.45f))).categoria)
        assertNull(ClasificadorEquipo.clasificar(listOf(e("Computer", Float.NaN))).categoria)
        val vacia = ClasificadorEquipo.clasificar(emptyList())
        assertNull(vacia.categoria)
        assertNull(vacia.pareceOtraCosa)
        assertTrue(vacia.pistas.isEmpty())
    }

    @Test
    fun noImportanMayusculasNiEspacios_yUnaEtiquetaRepetidaCuentaUnaVez() {
        assertEquals(Categoria.TECNOLOGIA, ClasificadorEquipo.clasificar(listOf(e("  mobile PHONE ", 0.8f))).categoria)
        // Dos veces "Vehicle" (0.9 × 0.5) siguen siendo 0.45 puntos: no llega al mínimo.
        assertNull(ClasificadorEquipo.clasificar(listOf(e("Vehicle", 0.9f), e("vehicle", 0.9f))).categoria)
    }

    @Test
    fun muestraComoMaximoTresPistasSinRepetirNombres() {
        val r = ClasificadorEquipo.clasificar(
            listOf(e("Couch", 0.9f), e("Loveseat", 0.85f), e("Chair", 0.8f), e("Bench", 0.7f), e("Party", 0.65f)),
        )
        assertEquals(Categoria.EVENTOS, r.categoria)
        assertEquals(listOf("sofá", "sillas", "bancas"), r.pistas.map { it.nombre })
        assertEquals("sofá (90 %), sillas (80 %), bancas (70 %)", r.textoPistas())
    }

    @Test
    fun ganaLaCategoriaConMasPuntos_yElEmpateSeResuelveSiempreIgual() {
        val r = ClasificadorEquipo.clasificar(listOf(e("Computer", 0.7f), e("Chair", 0.9f), e("Television", 0.6f)))
        // Tecnología: 0.7 + 0.6 = 1.3 puntos; Eventos: 0.9 puntos.
        assertEquals(Categoria.TECNOLOGIA, r.categoria)

        val empate = ClasificadorEquipo.clasificar(listOf(e("Computer", 0.7f), e("Chair", 0.7f)))
        assertEquals(Categoria.EVENTOS, empate.categoria)
    }

    @Test
    fun herramientaQueElModeloNoConoce_noInventaNada() {
        val r = ClasificadorEquipo.clasificar(listOf(e("Metal", 0.8f), e("Product", 0.7f)))
        assertNull(r.categoria)
        assertNull(r.pareceOtraCosa)
    }
}
