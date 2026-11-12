package com.equishare.app.funcional

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.equishare.app.data.sistema.AnalizadorFotoMlKit
import com.equishare.app.data.sistema.LectorImagen
import com.equishare.app.domain.ia.ClasificadorEquipo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Prueba el modelo REAL de IA (ML Kit) dentro del emulador: que viene incluido en la app,
 * que carga sin internet y que responde sin romper la app.
 */
@RunWith(AndroidJUnit4::class)
class IaModeloInstrumentadoTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun elModeloDeIaCargaYRespondeSinInternet() = runBlocking<Unit> {
        // Se dibuja una imagen simple y se guarda como JPG, igual que una foto del teléfono.
        val archivo = File(contexto.cacheDir, "prueba_ia.jpg")
        val imagen = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        Canvas(imagen).apply {
            drawColor(Color.rgb(230, 230, 230))
            drawRect(160f, 120f, 480f, 360f, Paint().apply { color = Color.rgb(200, 120, 40) })
        }
        archivo.outputStream().use { imagen.compress(Bitmap.CompressFormat.JPEG, 90, it) }

        val analizador = AnalizadorFotoMlKit(LectorImagen(contexto))
        val etiquetas = withTimeout(30_000) { analizador.etiquetar(Uri.fromFile(archivo).toString()) }
        archivo.delete()

        assertNotNull("El modelo de IA no respondió", etiquetas)
        assertTrue(etiquetas!!.all { it.confianza >= ClasificadorEquipo.CONFIANZA_MINIMA && it.confianza <= 1f })
        // Con lo que sea que vea, el clasificador responde sin errores.
        ClasificadorEquipo.clasificar(etiquetas)
    }

    @Test
    fun fotoQueNoSePuedeLeer_devuelveNullSinRomper() = runBlocking<Unit> {
        val analizador = AnalizadorFotoMlKit(LectorImagen(contexto))
        val etiquetas = withTimeout(30_000) {
            analizador.etiquetar("content://com.equishare.app.noexiste/foto.jpg")
        }
        assertNull(etiquetas)
    }
}
