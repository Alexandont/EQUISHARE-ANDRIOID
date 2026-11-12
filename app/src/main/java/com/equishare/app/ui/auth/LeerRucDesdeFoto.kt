package com.equishare.app.ui.auth

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.equishare.app.ui.components.crearUriParaFoto

/**
 * Tarjeta "Leer RUC desde una foto": la empresa toma una foto de su ficha RUC (o elige una captura)
 * y la IA del teléfono llena el RUC y la razón social. La foto no sale del teléfono.
 */
@Composable
fun LeerRucDesdeFoto(
    leyendo: Boolean,
    aviso: String?,
    alElegirFoto: (String?) -> Unit,
    alFaltarCamara: () -> Unit,
) {
    val contexto = LocalContext.current
    // Se guarda como texto para no perderla si Android recrea la pantalla mientras la cámara está abierta.
    var uriCamara by rememberSaveable { mutableStateOf<String?>(null) }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { tomada ->
        if (tomada) alElegirFoto(uriCamara)
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        alElegirFoto(uri?.toString())
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("¿Tienes tu ficha RUC a la mano?", style = MaterialTheme.typography.titleSmall)
            Text(
                "Toma una foto o elige una captura y la IA del teléfono llenará el RUC y la razón social.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val uri = crearUriParaFoto(contexto, "ficha_ruc")
                        uriCamara = uri.toString()
                        try {
                            camara.launch(uri)
                        } catch (e: ActivityNotFoundException) {
                            alFaltarCamara()
                        }
                    },
                    enabled = !leyendo,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Tomar foto")
                }
                OutlinedButton(
                    onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !leyendo,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Elegir imagen")
                }
            }
            if (leyendo) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Leyendo la foto con IA…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
