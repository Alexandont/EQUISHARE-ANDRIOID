package com.equishare.app.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Tarjeta "Mi cuenta" de Perfil: editar datos, cambiar contraseña y eliminar la cuenta. */
@Composable
fun TarjetaMiCuenta(
    puedeEditar: Boolean,
    esDemo: Boolean,
    alEditar: () -> Unit,
    alCambiarContrasena: () -> Unit,
    alEliminar: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mi cuenta", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = alEditar, enabled = puedeEditar, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Editar datos de la empresa")
            }
            if (!esDemo) {
                OutlinedButton(onClick = alCambiarContrasena, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cambiar contraseña")
                }
            }
            OutlinedButton(
                onClick = alEliminar,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Eliminar mi cuenta")
            }
        }
    }
}

/** Muestra la ventana de "Mi cuenta" que esté abierta. */
@Composable
fun DialogosCuenta(ui: PerfilUiState, viewModel: PerfilViewModel) {
    when (ui.dialogo) {
        DialogoCuenta.EDITAR_DATOS -> DialogoEditarDatos(ui, viewModel)
        DialogoCuenta.CAMBIAR_CONTRASENA -> DialogoCambiarContrasena(ui, viewModel)
        DialogoCuenta.ELIMINAR_CUENTA -> DialogoEliminarCuenta(ui, viewModel)
        null -> Unit
    }
}

@Composable
private fun DialogoEditarDatos(ui: PerfilUiState, viewModel: PerfilViewModel) {
    var razon by rememberSaveable { mutableStateOf(ui.empresa?.razonSocial.orEmpty()) }
    var telefono by rememberSaveable { mutableStateOf(ui.empresa?.telefono.orEmpty()) }
    AlertDialog(
        onDismissRequest = viewModel::cerrarDialogo,
        title = { Text("Editar datos de la empresa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RUC: ${ui.empresa?.ruc.orEmpty()} (no se puede cambiar)", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = razon,
                    onValueChange = { razon = it },
                    label = { Text("Razón social") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it.filter(Char::isDigit).take(9) },
                    label = { Text("Celular de contacto") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorDialogo(ui.errorDialogo)
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.guardarDatos(razon, telefono) }, enabled = !ui.procesando) {
                Text(if (ui.procesando) "Guardando…" else "Guardar")
            }
        },
        dismissButton = { TextButton(onClick = viewModel::cerrarDialogo, enabled = !ui.procesando) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoCambiarContrasena(ui: PerfilUiState, viewModel: PerfilViewModel) {
    var actual by rememberSaveable { mutableStateOf("") }
    var nueva by rememberSaveable { mutableStateOf("") }
    var confirmacion by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = viewModel::cerrarDialogo,
        title = { Text("Cambiar contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoContrasena(actual, { actual = it }, "Contraseña actual")
                CampoContrasena(nueva, { nueva = it }, "Contraseña nueva")
                CampoContrasena(confirmacion, { confirmacion = it }, "Repite la contraseña nueva")
                ErrorDialogo(ui.errorDialogo)
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.cambiarContrasena(actual, nueva, confirmacion) }, enabled = !ui.procesando) {
                Text(if (ui.procesando) "Cambiando…" else "Cambiar")
            }
        },
        dismissButton = { TextButton(onClick = viewModel::cerrarDialogo, enabled = !ui.procesando) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoEliminarCuenta(ui: PerfilUiState, viewModel: PerfilViewModel) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = viewModel::cerrarDialogo,
        title = { Text("¿Eliminar tu cuenta?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Se borrarán tu empresa, tus equipos publicados, tus favoritos y tu usuario. " +
                        "Las reservas terminadas quedarán como «Empresa eliminada». Esto no se puede deshacer.",
                )
                if (!ui.esDemo) CampoContrasena(password, { password = it }, "Tu contraseña")
                ErrorDialogo(ui.errorDialogo)
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.eliminarCuenta(password) }, enabled = !ui.procesando) {
                Text(
                    if (ui.procesando) "Eliminando…" else "Eliminar definitivamente",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = viewModel::cerrarDialogo, enabled = !ui.procesando) { Text("Cancelar") } },
    )
}

@Composable
private fun CampoContrasena(valor: String, alCambiar: (String) -> Unit, etiqueta: String) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorDialogo(error: String?) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}
