package com.equishare.app.funcional

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.equishare.app.data.sistema.LectorImagen
import com.equishare.app.data.sistema.LectorTextoMlKit
import com.equishare.app.domain.ia.LectorRuc
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Prueba la IA REAL que lee texto (ML Kit Text Recognition) dentro del emulador: se dibuja una ficha RUC
 * sencilla, se guarda como foto y la IA debe leer el RUC sin internet.
 */
@RunWith(AndroidJUnit4::class)
class IaTextoInstrumentadoTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun laIaLeeElRucDeUnaFichaDibujada() = runBlocking<Unit> {
        val archivo = File(contexto.cacheDir, "prueba_ficha_ruc.jpg")
        val imagen = Bitmap.createBitmap(1400, 500, Bitmap.Config.ARGB_8888)
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 56f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        Canvas(imagen).apply {
            drawColor(Color.WHITE)
            drawText("FICHA RUC", 60f, 120f, pincel)
            drawText("Numero de RUC: 20131312955", 60f, 240f, pincel)
            drawText("Razon Social: CONSTRUCTORA EJEMPLO SAC", 60f, 360f, pincel)
        }
        archivo.outputStream().use { imagen.compress(Bitmap.CompressFormat.JPEG, 95, it) }

        val texto = withTimeout(30_000) { LectorTextoMlKit(LectorImagen(contexto)).leerTexto(Uri.fromFile(archivo).toString()) }
        archivo.delete()

        assertNotNull("La IA no pudo leer la imagen", texto)
        assertEquals("Texto leído: $texto", "20131312955", LectorRuc.extraer(texto).ruc)
    }

    @Test
    fun fotoQueNoSePuedeLeer_devuelveNullSinRomper() = runBlocking<Unit> {
        val texto = withTimeout(30_000) {
            LectorTextoMlKit(LectorImagen(contexto)).leerTexto("content://com.equishare.app.noexiste/ficha.jpg")
        }
        assertNull(texto)
    }
}
