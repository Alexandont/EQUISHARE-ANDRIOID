package com.equishare.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.equishare.app.ui.AppViewModelProvider
import com.equishare.app.ui.components.textoAyuda

@Composable
fun CompletarPerfilScreen(
    alTerminar: () -> Unit,
    viewModel: CompletarPerfilViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text("Registra tu empresa", style = MaterialTheme.typography.headlineSmall)
        Card {
            Text(
                "Para publicar o alquilar equipos debes registrar el RUC de tu empresa. " +
                    "Antes de continuar verificamos que el número de RUC sea válido.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
        LeerRucDesdeFoto(
            leyendo = ui.leyendoFoto,
            aviso = ui.avisoFoto,
            alElegirFoto = viewModel::alElegirFotoRuc,
            alFaltarCamara = viewModel::sinCamara,
        )
        OutlinedTextField(
            value = ui.ruc,
            onValueChange = viewModel::alCambiarRuc,
            label = { Text("RUC (11 dígitos)") },
            leadingIcon = { Icon(Icons.Filled.Verified, contentDescription = null) },
            isError = ui.errorRuc != null,
            supportingText = textoAyuda(ui.errorRuc),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ui.razonSocial,
            onValueChange = viewModel::alCambiarRazon,
            label = { Text("Razón social") },
            leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null) },
            isError = ui.errorRazon != null,
            supportingText = textoAyuda(ui.errorRazon),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ui.telefono,
            onValueChange = viewModel::alCambiarTelefono,
            label = { Text("Celular de contacto") },
            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
            isError = ui.errorTelefono != null,
            supportingText = textoAyuda(ui.errorTelefono),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        ui.errorGeneral?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { viewModel.guardar(alTerminar) },
            enabled = !ui.guardando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (ui.guardando) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Verificar y continuar")
            }
        }
        TextButton(onClick = viewModel::cerrarSesion, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
    }
}
