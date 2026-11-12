package com.equishare.app.ui.bienvenida

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class PaginaBienvenida(val icono: ImageVector, val titulo: String, val texto: String)

private val PAGINAS = listOf(
    PaginaBienvenida(
        Icons.Filled.Handshake,
        "Bienvenido a EQUISHARE",
        "Alquila y comparte equipos entre empresas de Trujillo. Lo que tu empresa no usa, otra lo necesita.",
    ),
    PaginaBienvenida(
        Icons.Filled.Map,
        "Encuentra equipos cerca",
        "Busca en el catálogo o en el mapa. Cambia la capa a Satélite para ver la obra y toca un equipo para centrarlo.",
    ),
    PaginaBienvenida(
        Icons.Filled.CameraAlt,
        "Publica en un minuto",
        "Toma la foto del equipo y la IA del teléfono sugiere la categoría. Escribe la dirección y el mapa la ubica.",
    ),
    PaginaBienvenida(
        Icons.Filled.Verified,
        "Empresas verificadas",
        "Cada empresa verifica su correo y su RUC. Tu cuenta y tus datos están protegidos, y puedes borrarlos cuando quieras.",
    ),
)

/** Cuatro pantallas que se deslizan con el dedo y explican la app la primera vez que se abre. */
@Composable
fun BienvenidaScreen(alTerminar: () -> Unit) {
    val estado = rememberPagerState { PAGINAS.size }
    val alcance = rememberCoroutineScope()
    val esUltima = estado.currentPage == PAGINAS.lastIndex

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = alTerminar) { Text("Saltar") }
        }
        HorizontalPager(state = estado, modifier = Modifier.weight(1f)) { indice ->
            val pagina = PAGINAS[indice]
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(pagina.icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                }
                Spacer(Modifier.height(32.dp))
                Text(pagina.titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    pagina.texto,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 16.dp)) {
            PAGINAS.indices.forEach { i -> Punto(activo = i == estado.currentPage) }
        }
        Button(
            onClick = {
                if (esUltima) alTerminar() else alcance.launch { estado.animateScrollToPage(estado.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (esUltima) "Empezar" else "Siguiente")
        }
    }
}

@Composable
private fun Punto(activo: Boolean) {
    val ancho by animateDpAsState(if (activo) 24.dp else 8.dp, label = "ancho del punto")
    val color by animateColorAsState(
        if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "color del punto",
    )
    Box(Modifier.height(8.dp).width(ancho).clip(RoundedCornerShape(4.dp)).background(color))
}
