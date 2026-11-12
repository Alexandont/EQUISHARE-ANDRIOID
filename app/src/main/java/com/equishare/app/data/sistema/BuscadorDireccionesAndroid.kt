package com.equishare.app.data.sistema

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.equishare.app.domain.model.Coordenadas
import com.equishare.app.domain.repositorio.BuscadorDirecciones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Busca una dirección escrita con el Geocoder de Android (servicio de Google Play en el teléfono).
 * Solo busca dentro del Perú, para que "Av. España 1234" no termine en España.
 */
class BuscadorDireccionesAndroid(private val context: Context) : BuscadorDirecciones {

    override suspend fun buscar(direccion: String): Coordenadas? {
        val texto = direccion.trim()
        if (texto.length < 3 || !Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale("es", "PE"))
        val resultado: Address? = withTimeoutOrNull<Address?>(10_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<Address?> { continuacion ->
                    geocoder.getFromLocationName(
                        texto, 1, SUR, OESTE, NORTE, ESTE,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(direcciones: MutableList<Address>) {
                                if (continuacion.isActive) continuacion.resume(direcciones.firstOrNull())
                            }

                            override fun onError(mensaje: String?) {
                                if (continuacion.isActive) continuacion.resume(null)
                            }
                        },
                    )
                }
            } else {
                withContext<Address?>(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocationName(texto, 1, SUR, OESTE, NORTE, ESTE)?.firstOrNull() }.getOrNull()
                }
            }
        }
        return resultado?.let { Coordenadas(it.latitude, it.longitude) }
    }

    private companion object {
        // Rectángulo que cubre el Perú (sur, oeste, norte, este).
        const val SUR = -18.4
        const val OESTE = -81.4
        const val NORTE = -0.03
        const val ESTE = -68.6
    }
}
