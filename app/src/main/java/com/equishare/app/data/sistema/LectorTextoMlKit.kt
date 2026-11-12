package com.equishare.app.data.sistema

import com.equishare.app.domain.repositorio.LectorTextoFoto
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Lee el texto de una foto con ML Kit Text Recognition (modelo para letras latinas incluido en la app).
 * Funciona sin internet y la foto no sale del teléfono. Se usa una imagen más grande que para
 * reconocer equipos ([LADO_TEXTO] px), porque las letras pequeñas necesitan más detalle.
 */
class LectorTextoMlKit(private val lector: LectorImagen) : LectorTextoFoto {

    private val reconocedor: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun leerTexto(uri: String): String? {
        val bitmap = lector.bitmapReducido(uri, LADO_TEXTO) ?: return null
        return try {
            reconocedor.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } catch (e: CancellationException) {
            // Si se canceló la corrutina (se eligió otra foto) se respeta; si la canceló ML Kit, es un fallo.
            if (currentCoroutineContext().isActive) null else throw e
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val LADO_TEXTO = 2048
    }
}
