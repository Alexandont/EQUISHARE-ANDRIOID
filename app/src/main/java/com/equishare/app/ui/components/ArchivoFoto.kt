package com.equishare.app.ui.components

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Archivo vacío en la caché de la app donde la app de cámara guardará la foto
 * (ver res/xml/rutas_archivos.xml). No necesita el permiso de cámara.
 */
fun crearUriParaFoto(contexto: Context, prefijo: String): Uri {
    val carpeta = File(contexto.cacheDir, "fotos").apply { mkdirs() }
    val archivo = File(carpeta, "${prefijo}_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
}
