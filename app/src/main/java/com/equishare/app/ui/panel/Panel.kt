package com.equishare.app.ui.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.equishare.app.domain.Formato
import com.equishare.app.ui.AppViewModelProvider

/** Saludo arriba del catálogo: "Hola, <empresa>" con tres números rápidos. */
@Composable
fun SaludoInicio(viewModel: PanelViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    AnimatedVisibility(visible = !ui.cargando && ui.empresa != null, enter = fadeIn() + expandVertically()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Hola, ${ui.empresa.orEmpty()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Dato("${ui.metricas?.reservasPendientes ?: 0}", "por aceptar", Modifier.weight(1f))
                    Dato("${ui.metricas?.equiposPublicados ?: 0}", "publicados", Modifier.weight(1f))
                    Dato("${ui.favoritos}", "favoritos", Modifier.weight(1f))
                }
            }
        }
    }
}

/** Panel de Mis equipos (HU-10): cuánto se alquilan los equipos de la empresa. */
@Composable
fun PanelMetricas(viewModel: PanelViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val m = ui.metricas
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tu panel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (m == null) {
                Text(
                    if (ui.cargando) "Cargando tus números…" else ui.error ?: "Todavía no hay datos.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!ui.cargando) TextButton(onClick = viewModel::cargar) { Text("Reintentar") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Dato("${m.equiposPublicados} de ${m.equiposTotales}", "equipos publicados", Modifier.weight(1f))
                    Dato("${m.reservasPendientes}", "reservas por aceptar", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Dato("${m.alquileresCompletados}", "alquileres completados", Modifier.weight(1f))
                    Dato(Formato.soles(m.ingresosEstimados), "ingresos estimados", Modifier.weight(1f))
                }
                m.equipoMasAlquilado?.let {
                    Text(
                        "Más alquilado: $it (${m.vecesMasAlquilado} ${if (m.vecesMasAlquilado == 1) "vez" else "veces"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Dato(valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
