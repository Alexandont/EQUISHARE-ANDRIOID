package com.equishare.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.equishare.app.ui.AppViewModelProvider
import kotlinx.coroutines.delay

@Composable
fun VerificarCorreoScreen(
    viewModel: VerificarCorreoViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var segundos by remember { mutableStateOf(0) }
    LaunchedEffect(ui.ultimoEnvioMs) {
        // Cuenta regresiva del botón "Reenviar" para no mandar muchos correos seguidos.
        segundos = viewModel.segundosParaReenviar()
        while (segundos > 0) {
            delay(1_000)
            segundos = viewModel.segundosParaReenviar()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Text("Revisa tu correo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Te enviamos un enlace a ${ui.email ?: "tu correo"}. Ábrelo y luego toca «Ya lo verifiqué».",
            style = MaterialTheme.typography.bodyMedium,
        )
        Card(Modifier.fillMaxWidth()) {
            Text(
                "Esto protege a todas las empresas: una cuenta solo se usa con un correo real.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
        Button(
            onClick = { viewModel.revisar() },
            enabled = !ui.revisando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (ui.revisando) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Ya lo verifiqué")
            }
        }
        OutlinedButton(
            onClick = viewModel::reenviar,
            enabled = !ui.enviando && segundos == 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (segundos > 0) "Reenviar en $segundos s" else "Reenviar correo")
        }
        ui.mensaje?.let {
            Text(
                it,
                color = if (ui.esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            "¿Escribiste mal tu correo? Cierra sesión y crea la cuenta otra vez con el correo correcto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = viewModel::cerrarSesion) { Text("Cerrar sesión") }
    }
}
