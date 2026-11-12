package com.equishare.app.domain.ia

import com.equishare.app.domain.validacion.Validadores

/** Datos encontrados en la foto de la ficha RUC. Cada uno es null si no se encontró. */
data class DatosRuc(val ruc: String?, val razonSocial: String?)

/**
 * Busca el RUC y la razón social en el texto que la IA leyó de una foto (ficha RUC de SUNAT
 * o captura de la "Consulta RUC"). Es Kotlin puro y se prueba con JUnit.
 *
 * Para no inventar datos, solo acepta un RUC que pase la misma validación del formulario
 * (11 dígitos, prefijo 10, 15, 16, 17 o 20 y dígito verificador correcto).
 */
object LectorRuc {

    /** 11 dígitos seguidos; acepta un espacio o guion suelto entre grupos, porque la IA a veces los agrega. */
    private val NUMERO_RUC = Regex("(?<!\\d)(\\d{2})[ -]?(\\d{8})[ -]?(\\d)(?!\\d)")
    private val ETIQUETA_RAZON = Regex("raz[oó0]n\\s+social", RegexOption.IGNORE_CASE)
    private val SEPARADOR_INICIAL = Regex("^[\\s:\\-–—]+")
    private val SEPARADOR_FINAL = Regex("[\\s:\\-–—]+$")
    private val ESPACIOS = Regex("\\s+")

    private class Candidato(val ruc: String, val linea: Int, val fin: Int, val conEtiqueta: Boolean)

    fun extraer(texto: String?): DatosRuc {
        if (texto.isNullOrBlank()) return DatosRuc(null, null)
        val lineas = texto.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val candidatos = lineas.flatMapIndexed { i, linea ->
            NUMERO_RUC.findAll(linea).mapNotNull { m ->
                val ruc = m.groupValues[1] + m.groupValues[2] + m.groupValues[3]
                if (Validadores.ruc(ruc) == null) {
                    Candidato(ruc, i, m.range.last + 1, linea.contains("RUC", ignoreCase = true))
                } else {
                    null
                }
            }.toList()
        }
        // Se prefiere el número que está en la línea que dice "RUC".
        val elegido = candidatos.firstOrNull { it.conEtiqueta } ?: candidatos.firstOrNull()
            ?: return DatosRuc(null, null)

        val razon = razonJuntoAlRuc(lineas[elegido.linea], elegido.fin) ?: razonPorEtiqueta(lineas)
        return DatosRuc(elegido.ruc, razon)
    }

    /** Formato de la Consulta RUC: "Número de RUC: 20131312955 - EMPRESA S.A.C.". */
    private fun razonJuntoAlRuc(linea: String, finDelNumero: Int): String? {
        val resto = linea.substring(finDelNumero)
        val despuesDelGuion = resto.trimStart()
        if (despuesDelGuion.isEmpty() || despuesDelGuion.first() !in "-–—") return null
        return limpiar(despuesDelGuion)
    }

    /** Formato de la ficha RUC: "Razón Social: EMPRESA S.A.C." o el nombre en la línea siguiente. */
    private fun razonPorEtiqueta(lineas: List<String>): String? {
        lineas.forEachIndexed { i, linea ->
            val m = ETIQUETA_RAZON.find(linea) ?: return@forEachIndexed
            limpiar(linea.substring(m.range.last + 1))?.let { return it }
            lineas.getOrNull(i + 1)?.let { siguiente -> limpiar(siguiente)?.let { return it } }
        }
        return null
    }

    private fun limpiar(valor: String): String? {
        val texto = valor
            .replace(SEPARADOR_INICIAL, "")
            .replace(SEPARADOR_FINAL, "")
            .replace(ESPACIOS, " ")
            .trim()
        return when {
            texto.count { it.isLetter() } < 3 -> null
            ':' in texto -> null // Es otra etiqueta del documento, no un nombre.
            ETIQUETA_RAZON.containsMatchIn(texto) -> null
            Validadores.razonSocial(texto) != null -> null
            else -> texto
        }
    }
}
