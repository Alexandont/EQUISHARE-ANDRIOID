package com.equishare.app.ui.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.equishare.app.BuildConfig
import com.equishare.app.domain.Formato
import com.equishare.app.ui.AppViewModelProvider
import com.equishare.app.ui.components.Estrellas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilScreen(
    alVerPrivacidad: () -> Unit,
    viewModel: PerfilViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var mostrarServidor by remember { mutableStateOf(false) }
    var confirmarSalida by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Perfil de la empresa") }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Círculo con las iniciales de la empresa.
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                Formato.iniciales(ui.empresa?.razonSocial),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(ui.empresa?.razonSocial ?: if (ui.cargando) "Cargando…" else "Empresa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    ui.empresa?.let { e ->
                        Text("RUC: ${e.ruc}" + if (e.verificada) "  ✓ verificado" else "", style = MaterialTheme.typography.bodyMedium)
                        Text("Celular: ${e.telefono}", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Como arrendatario: ", style = MaterialTheme.typography.bodySmall)
                            Estrellas(e.calificacionComoArrendatario, 14.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(Formato.calificacion(e.calificacionComoArrendatario, e.totalResenasComoArrendatario), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "Correo: ${ui.email ?: "-"}" + if (ui.correoVerificado && !ui.esDemo) "  ✓ verificado" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ui.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    ui.error?.let {
                        Text(it.mensaje, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = viewModel::cargar) { Text("Reintentar") }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Acerca de EQUISHARE", style = MaterialTheme.typography.titleSmall)
                    Text("Alquiler y préstamo de equipos entre empresas de Trujillo.", style = MaterialTheme.typography.bodySmall)
                    Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                }
            }
            TarjetaMiCuenta(
                puedeEditar = ui.empresa != null,
                esDemo = ui.esDemo,
                alEditar = { viewModel.abrirDialogo(DialogoCuenta.EDITAR_DATOS) },
                alCambiarContrasena = { viewModel.abrirDialogo(DialogoCuenta.CAMBIAR_CONTRASENA) },
                alEliminar = { viewModel.abrirDialogo(DialogoCuenta.ELIMINAR_CUENTA) },
            )
            OutlinedButton(onClick = { mostrarServidor = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Dns, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Conexión con el servidor")
            }
            OutlinedButton(onClick = alVerPrivacidad, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PrivacyTip, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Privacidad y uso ético")
            }
            OutlinedButton(onClick = { confirmarSalida = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar sesión")
            }
        }
    }

    if (mostrarServidor) DialogoServidor(alCerrar = { mostrarServidor = false }, alGuardar = { viewModel.cargar() })
    DialogosCuenta(ui, viewModel)
    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Se borrarán de este teléfono tus reservas y equipos guardados para proteger tu información.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalida = false
                    viewModel.cerrarSesion()
                }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacidadScreen(alVolver: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacidad y uso ético") },
                navigationIcon = { IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(16.dp)) {
            TextoPoliticaPrivacidad()
        }
    }
}
