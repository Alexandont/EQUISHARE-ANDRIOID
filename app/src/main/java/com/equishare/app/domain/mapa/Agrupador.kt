package com.equishare.app.domain.mapa

import com.equishare.app.domain.model.Equipo
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Capas del mapa que la empresa puede elegir. Se guarda la elección en el teléfono. */
enum class TipoMapa(val etiqueta: String) {
    NORMAL("Normal"),
    SATELITE("Satélite"),
    HIBRIDO("Híbrido"),
    RELIEVE("Relieve");

    companion object {
        fun desde(valor: String?): TipoMapa = entries.firstOrNull { it.name == valor } ?: NORMAL
    }
}

/** Un grupo de equipos cercanos que el mapa dibuja como un solo círculo con el número. */
data class GrupoEquipos(val equipos: List<Equipo>, val lat: Double, val lng: Double) {
    val cantidad: Int get() = equipos.size
    val esUnSolo: Boolean get() = equipos.size == 1
}

/**
 * Agrupa los marcadores del mapa para que no se amontonen. Divide el mapa en cuadros de
 * [tamanoCeldaPx] píxeles de pantalla (según el zoom) y junta los equipos de un mismo cuadro.
 * Al acercar el mapa, los cuadros cubren menos terreno y los grupos se separan solos.
 * Es Kotlin puro: se prueba con JUnit.
 */
object Agrupador {
    private const val TAMANO_MUNDO_PX = 256.0
    const val ZOOM_SIN_GRUPOS = 16f

    fun agrupar(equipos: List<Equipo>, zoom: Float, tamanoCeldaPx: Double = 90.0): List<GrupoEquipos> {
        if (equipos.isEmpty()) return emptyList()
        if (zoom >= ZOOM_SIN_GRUPOS) return equipos.map { GrupoEquipos(listOf(it), it.lat, it.lng) }
        val escala = TAMANO_MUNDO_PX * 2.0.pow(zoom.toDouble())
        return equipos
            .groupBy { e ->
                val (x, y) = aPixeles(e.lat, e.lng, escala)
                floor(x / tamanoCeldaPx).toLong() to floor(y / tamanoCeldaPx).toLong()
            }
            .values
            .map { grupo ->
                GrupoEquipos(
                    equipos = grupo.sortedBy { it.id },
                    lat = grupo.sumOf { it.lat } / grupo.size,
                    lng = grupo.sumOf { it.lng } / grupo.size,
                )
            }
            .sortedWith(compareByDescending<GrupoEquipos> { it.cantidad }.thenBy { it.equipos.first().id })
    }

    /** Proyección Web Mercator, la misma que usa Google Maps. */
    private fun aPixeles(lat: Double, lng: Double, escala: Double): Pair<Double, Double> {
        val latLimitada = lat.coerceIn(-85.0, 85.0)
        val x = (lng + 180.0) / 360.0 * escala
        val rad = latLimitada * PI / 180.0
        val y = (1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / PI) / 2.0 * escala
        return x to y
    }
}
