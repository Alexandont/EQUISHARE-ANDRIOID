package com.equishare.app.data.sistema

import com.equishare.app.domain.ia.ClasificadorEquipo
import com.equishare.app.domain.ia.EtiquetaFoto
import com.equishare.app.domain.repositorio.AnalizadorFoto
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * IA en el teléfono con ML Kit Image Labeling (modelo base incluido en la app).
 * Funciona sin internet, no envía la foto a ningún servidor y no necesita Firebase.
 * La foto se reduce a [LADO_IA] px antes de revisarla para que sea rápida y use poca memoria.
 */
class AnalizadorFotoMlKit(private val lector: LectorImagen) : AnalizadorFoto {

    private val etiquetador: ImageLabeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(ClasificadorEquipo.CONFIANZA_MINIMA)
                .build(),
        )
    }

    override suspend fun etiquetar(uri: String): List<EtiquetaFoto>? {
        val bitmap = lector.bitmapReducido(uri, LADO_IA) ?: return null
        return try {
            etiquetador.process(InputImage.fromBitmap(bitmap, 0))
                .await()
                .map { EtiquetaFoto(it.text, it.confidence) }
        } catch (e: CancellationException) {
            // Si se canceló la corrutina (se eligió otra foto) se respeta; si la canceló ML Kit, es un fallo.
            if (currentCoroutineContext().isActive) null else throw e
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val LADO_IA = 1024
    }
}
