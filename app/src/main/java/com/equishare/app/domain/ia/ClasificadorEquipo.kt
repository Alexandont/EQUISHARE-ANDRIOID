package com.equishare.app.domain.ia

import com.equishare.app.domain.model.Categoria
import java.util.Locale
import kotlin.math.roundToInt

/** Algo que el modelo de IA ve en la foto, con su confianza (de 0 a 1). Ejemplo: "Computer", 0.92. */
data class EtiquetaFoto(val texto: String, val confianza: Float)

/** Una pista que apoya la categoría sugerida, ya traducida. Ejemplo: "computadora", 92. */
data class PistaIa(val nombre: String, val porcentaje: Int)

/**
 * Resultado de revisar la foto con IA.
 * [categoria] es null cuando la IA no está segura. [pareceOtraCosa] dice qué parece la foto
 * cuando claramente no es un equipo (por ejemplo "una selfie" o "comida").
 */
data class SugerenciaFoto(
    val categoria: Categoria?,
    val pistas: List<PistaIa>,
    val pareceOtraCosa: String?,
) {
    /** Texto corto con lo que vio la IA. Ejemplo: "computadora (92 %), celular (71 %)". */
    fun textoPistas(): String = pistas.joinToString(", ") { "${it.nombre} (${it.porcentaje} %)" }
}

/**
 * Convierte lo que ve el modelo de IA del teléfono (ML Kit, etiquetas en inglés) en una categoría
 * de EQUISHARE. Es Kotlin puro: no depende de Android y se prueba con JUnit.
 *
 * Cómo decide: cada etiqueta conocida suma "confianza × peso" a su categoría. Gana la categoría
 * con más puntos, solo si llega a [PUNTAJE_MINIMO]. Si no llega, no adivina.
 * Los nombres de las etiquetas son los del modelo base de ML Kit (página "Label map").
 */
object ClasificadorEquipo {

    /** Las etiquetas con menos confianza que esto se ignoran (también se usa en ML Kit). */
    const val CONFIANZA_MINIMA = 0.5f

    /** Puntos que necesita una categoría para sugerirse. */
    const val PUNTAJE_MINIMO = 0.6

    /** Máximo de pistas que se muestran al usuario. */
    const val MAXIMO_PISTAS = 3

    private class Pista(val categoria: Categoria, val nombre: String, val peso: Double)

    private val PISTAS: Map<String, Pista> = mapOf(
        // Tecnología
        "computer" to Pista(Categoria.TECNOLOGIA, "computadora", 1.0),
        "mobile phone" to Pista(Categoria.TECNOLOGIA, "celular", 1.0),
        "television" to Pista(Categoria.TECNOLOGIA, "televisor", 1.0),
        // Eventos
        "chair" to Pista(Categoria.EVENTOS, "sillas", 1.0),
        "bench" to Pista(Categoria.EVENTOS, "bancas", 1.0),
        "couch" to Pista(Categoria.EVENTOS, "sofá", 1.0),
        "loveseat" to Pista(Categoria.EVENTOS, "sofá", 1.0),
        "tablecloth" to Pista(Categoria.EVENTOS, "mantel", 1.0),
        "centrepiece" to Pista(Categoria.EVENTOS, "centro de mesa", 1.0),
        "tableware" to Pista(Categoria.EVENTOS, "vajilla", 1.0),
        "cutlery" to Pista(Categoria.EVENTOS, "cubiertos", 1.0),
        "balloon" to Pista(Categoria.EVENTOS, "globos", 1.0),
        "party" to Pista(Categoria.EVENTOS, "fiesta", 1.0),
        "event" to Pista(Categoria.EVENTOS, "evento", 1.0),
        "concert" to Pista(Categoria.EVENTOS, "concierto", 1.0),
        "marriage" to Pista(Categoria.EVENTOS, "boda", 1.0),
        "ballroom" to Pista(Categoria.EVENTOS, "salón de eventos", 1.0),
        "subwoofer" to Pista(Categoria.EVENTOS, "parlante", 1.0),
        "musical instrument" to Pista(Categoria.EVENTOS, "instrumento musical", 1.0),
        "piano" to Pista(Categoria.EVENTOS, "piano", 1.0),
        "deejay" to Pista(Categoria.EVENTOS, "equipo de DJ", 1.0),
        "umbrella" to Pista(Categoria.EVENTOS, "sombrilla", 1.0),
        // Construcción
        "tractor" to Pista(Categoria.CONSTRUCCION, "maquinaria pesada", 1.0),
        "construction" to Pista(Categoria.CONSTRUCCION, "obra de construcción", 1.0),
        "wheelbarrow" to Pista(Categoria.CONSTRUCCION, "carretilla", 1.0),
        "brick" to Pista(Categoria.CONSTRUCCION, "ladrillos", 0.8),
        "vehicle" to Pista(Categoria.CONSTRUCCION, "vehículo", 0.5),
        "asphalt" to Pista(Categoria.CONSTRUCCION, "asfalto", 0.4),
        "infrastructure" to Pista(Categoria.CONSTRUCCION, "obra", 0.4),
    )

    /** Etiquetas que indican que la foto no muestra un equipo, con lo que parece en español. */
    private val OTRA_COSA: Map<String, String> = mapOf(
        "selfie" to "una selfie",
        "person" to "personas",
        "smile" to "personas",
        "dude" to "personas",
        "baby" to "personas",
        "crowd" to "personas",
        "hand" to "una mano",
        "food" to "comida",
        "cuisine" to "comida",
        "meal" to "comida",
        "fast food" to "comida",
        "pizza" to "comida",
        "lunch" to "comida",
        "supper" to "comida",
        "dog" to "una mascota",
        "cat" to "una mascota",
        "pet" to "una mascota",
        "bird" to "un animal",
        "horse" to "un animal",
        "screenshot" to "una captura de pantalla",
        "web page" to "una captura de pantalla",
        "sky" to "un paisaje",
        "sunset" to "un paisaje",
        "beach" to "un paisaje",
        "mountain" to "un paisaje",
    )

    private fun clave(texto: String): String = texto.trim().lowercase(Locale.ROOT)

    fun clasificar(etiquetas: List<EtiquetaFoto>): SugerenciaFoto {
        val validas = etiquetas
            .filter { it.confianza.isFinite() && it.confianza >= CONFIANZA_MINIMA }
            .sortedByDescending { it.confianza }

        // Cada etiqueta conocida suma puntos a su categoría (una etiqueta repetida cuenta una vez).
        val usadas = mutableSetOf<String>()
        val aciertos = validas.mapNotNull { etiqueta ->
            val k = clave(etiqueta.texto)
            val pista = PISTAS[k]
            if (pista != null && usadas.add(k)) etiqueta to pista else null
        }
        val puntajes = aciertos
            .groupBy { it.second.categoria }
            .mapValues { (_, lista) -> lista.sumOf { (e, p) -> e.confianza.toDouble() * p.peso } }

        val ganadora = puntajes.entries
            .sortedWith(compareByDescending<Map.Entry<Categoria, Double>> { it.value }.thenBy { it.key.ordinal })
            .firstOrNull()
            ?.takeIf { it.value >= PUNTAJE_MINIMO - 1e-9 }
            ?.key

        if (ganadora != null) {
            val pistas = aciertos
                .filter { it.second.categoria == ganadora }
                .map { (e, p) -> PistaIa(p.nombre, (e.confianza.coerceIn(0f, 1f) * 100).roundToInt()) }
                .distinctBy { it.nombre }
                .take(MAXIMO_PISTAS)
            return SugerenciaFoto(ganadora, pistas, pareceOtraCosa = null)
        }

        // Sin categoría: si lo más seguro que vio la IA es algo que no es un equipo, se avisa.
        val principal = validas.firstOrNull()?.let { OTRA_COSA[clave(it.texto)] }
        return SugerenciaFoto(categoria = null, pistas = emptyList(), pareceOtraCosa = principal)
    }
}
