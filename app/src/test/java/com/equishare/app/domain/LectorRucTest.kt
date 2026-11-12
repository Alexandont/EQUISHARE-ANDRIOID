package com.equishare.app.domain

import com.equishare.app.domain.ia.DatosRuc
import com.equishare.app.domain.ia.LectorRuc
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pruebas del lector de la ficha RUC: busca el RUC y la razón social en el texto leído por la IA. */
class LectorRucTest {

    @Test
    fun capturaDeConsultaRuc_leeRucYRazonSocialDeLaMismaLinea() {
        val texto = """
            Resultado de la Búsqueda
            Número de RUC: 20131312955 - CONSTRUCTORA EJEMPLO S.A.C.
            Tipo Contribuyente: SOCIEDAD ANONIMA CERRADA
        """.trimIndent()
        assertEquals(DatosRuc("20131312955", "CONSTRUCTORA EJEMPLO S.A.C."), LectorRuc.extraer(texto))
    }

    @Test
    fun fichaRuc_leeLaRazonSocialDespuesDeSuEtiqueta() {
        val texto = """
            FICHA RUC : 20600123450
            Apellidos y Nombres, Denominación o Razón Social : INVERSIONES TRUJILLO E.I.R.L.
            Tipo de Contribuyente : EMPRESA INDIVIDUAL DE RESP. LTDA
        """.trimIndent()
        assertEquals(DatosRuc("20600123450", "INVERSIONES TRUJILLO E.I.R.L."), LectorRuc.extraer(texto))
    }

    @Test
    fun fichaRuc_conLosDatosEnLaLineaSiguiente() {
        val texto = """
            Número de RUC
            20485673211
            Razón Social
            ALQUILERES DEL NORTE S.A.C.
            Nombre Comercial: -
        """.trimIndent()
        assertEquals(DatosRuc("20485673211", "ALQUILERES DEL NORTE S.A.C."), LectorRuc.extraer(texto))
    }

    @Test
    fun aceptaUnEspacioQueLaIaMetioDentroDelNumero() {
        assertEquals(DatosRuc("20131312955", "EMPRESA SAC"), LectorRuc.extraer("RUC: 20 131312955 - EMPRESA SAC"))
    }

    @Test
    fun unNumeroConDigitoVerificadorIncorrecto_noSeUsa() {
        assertEquals(DatosRuc(null, null), LectorRuc.extraer("RUC: 20131312956 - EMPRESA SAC"))
    }

    @Test
    fun prefiereElNumeroDeLaLineaQueDiceRuc() {
        val texto = """
            Documento del representante: 10456789124
            Teléfono 949123456
            RUC 20600123450
        """.trimIndent()
        assertEquals(DatosRuc("20600123450", null), LectorRuc.extraer(texto))
    }

    @Test
    fun sinTextoOSinRuc_noInventaNada() {
        assertEquals(DatosRuc(null, null), LectorRuc.extraer(null))
        assertEquals(DatosRuc(null, null), LectorRuc.extraer(""))
        assertEquals(DatosRuc(null, null), LectorRuc.extraer("Factura 001-12345 del 10/11/2026"))
    }

    @Test
    fun noTomaOtraEtiquetaComoRazonSocial() {
        val texto = """
            Número de RUC: 20131312955
            Razón Social:
            Tipo de Contribuyente: SOCIEDAD ANONIMA
        """.trimIndent()
        assertEquals(DatosRuc("20131312955", null), LectorRuc.extraer(texto))
        assertEquals(DatosRuc("20131312955", null), LectorRuc.extraer("RUC: 20131312955 - 12"))
    }

    @Test
    fun leeLaEtiquetaSinTilde_yLimpiaEspaciosRepetidos() {
        val texto = """
            RUC 20100030005
            RAZON SOCIAL:   ALQUILER   DE   EQUIPOS  SAC
        """.trimIndent()
        assertEquals(DatosRuc("20100030005", "ALQUILER DE EQUIPOS SAC"), LectorRuc.extraer(texto))
    }
}
