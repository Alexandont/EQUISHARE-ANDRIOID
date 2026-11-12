package com.equishare.app.ui.publicar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.equishare.app.domain.model.Categoria

/**
 * Parte de la pantalla Publicar con la foto del equipo y la tarjeta de la IA.
 * Está separada para poder probarla sola en las pruebas funcionales.
 */
@Composable
fun SeccionFotoConIa(
    ui: PublicarUiState,
    alPedirFoto: () -> Unit,
    alUsarSugerencia: () -> Unit,
    /** Si no es null, aparece también el botón "Tomar foto" (cámara del teléfono). */
    alTomarFoto: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val modelo = ui.fotoUriLocal ?: ui.fotoUrlRemota
            if (modelo != null) {
                AsyncImage(
                    model = modelo,
                    contentDescription = "Foto del equipo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Card(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(40.dp))
                        Text("Agrega una foto clara del equipo", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (alTomarFoto != null) {
                OutlinedButton(onClick = alTomarFoto, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tomar foto")
                }
            }
            OutlinedButton(onClick = alPedirFoto, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (ui.fotoUriLocal != null || ui.fotoUrlRemota != null) "Cambiar foto" else "Elegir foto")
            }
        }
        Text(
            "La foto se valida automáticamente al guardar: JPG, PNG o WebP, mínimo 640×480 px y máximo 5 MB.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TarjetaIa(ui.revisionIa, ui.categoria, ui.categoriaPorIa, alUsarSugerencia)
        if (ui.motivosFoto.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("La foto fue rechazada:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    ui.motivosFoto.forEach { Text("• $it", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun TarjetaIa(
    revision: RevisionIa,
    categoriaActual: Categoria?,
    categoriaPorIa: Boolean,
    alUsarSugerencia: () -> Unit,
) {
    if (revision == RevisionIa.SinRevisar) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        val color = MaterialTheme.colorScheme.onSecondaryContainer
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (revision) {
                RevisionIa.SinRevisar -> Unit
                RevisionIa.Revisando -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = color)
                    Spacer(Modifier.width(10.dp))
                    Text("Revisando la foto con IA…", color = color, style = MaterialTheme.typography.bodyMedium)
                }
                RevisionIa.NoDisponible -> Text(
                    "No se pudo revisar la foto con IA. Puedes publicar igual.",
                    color = color,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is RevisionIa.Lista -> {
                    val sugerencia = revision.sugerencia
                    Text("Revisión con IA", color = color, style = MaterialTheme.typography.titleSmall)
                    val sugerida = sugerencia.categoria
                    if (sugerida != null) {
                        Text(
                            "Categoría sugerida: ${sugerida.etiqueta}",
                            color = color,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (sugerencia.pistas.isNotEmpty()) {
                            Text("La IA ve: ${sugerencia.textoPistas()}", color = color, style = MaterialTheme.typography.bodySmall)
                        }
                        when {
                            categoriaActual == sugerida && categoriaPorIa -> Text(
                                "La elegimos por ti. Puedes cambiarla en Categoría.",
                                color = color,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            categoriaActual == sugerida -> Text(
                                "Coincide con la categoría que elegiste.",
                                color = color,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            else -> TextButton(onClick = alUsarSugerencia) { Text("Usar ${sugerida.etiqueta}") }
                        }
                    } else {
                        val otraCosa = sugerencia.pareceOtraCosa
                        Text(
                            if (otraCosa != null) {
                                "La foto parece mostrar $otraCosa, no un equipo. Toma la foto al equipo completo."
                            } else {
                                "La IA no pudo identificar la categoría. Elígela tú."
                            },
                            color = color,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("Puedes publicar igual.", color = color, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
