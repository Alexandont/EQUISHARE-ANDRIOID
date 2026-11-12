package com.equishare.app.ui.mapa

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.equishare.app.BuildConfig
import com.equishare.app.domain.Formato
import com.equishare.app.domain.mapa.Agrupador
import com.equishare.app.domain.mapa.TipoMapa
import com.equishare.app.domain.model.Equipo
import com.equishare.app.ui.AppViewModelProvider
import com.equishare.app.ui.components.ImagenEquipo
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

private val TRUJILLO = LatLng(-8.1116, -79.0287)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaScreen(
    alAbrirEquipo: (String) -> Unit,
    viewModel: MapaViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        viewModel.alResultadoPermiso(r.values.any { it })
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Equipos en el mapa") }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            if (!BuildConfig.MAPS_ENABLED) {
                SinClaveMaps(ui.equipos, alAbrirEquipo)
            } else {
                MapaConEquipos(
                    ui = ui,
                    viewModel = viewModel,
                    alAbrirEquipo = alAbrirEquipo,
                    alPedirUbicacion = {
                        if (viewModel.tienePermiso()) {
                            viewModel.actualizarUbicacion()
                        } else {
                            permiso.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MapaConEquipos(
    ui: MapaUiState,
    viewModel: MapaViewModel,
    alAbrirEquipo: (String) -> Unit,
    alPedirUbicacion: () -> Unit,
) {
    val camara = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(TRUJILLO, 12f) }
    val listaEstado = rememberLazyListState()
    val alcance = rememberCoroutineScope()
    val equipos = ui.equipos
    val seleccionado = ui.seleccionado

    val miPosicion = ui.miUbicacion
    LaunchedEffect(miPosicion) {
        if (miPosicion != null) {
            camara.position = CameraPosition.fromLatLngZoom(LatLng(miPosicion.lat, miPosicion.lng), 13f)
        }
    }
    // Al elegir un equipo (marcador o tarjeta): su tarjeta pasa al frente y el mapa se centra en él.
    LaunchedEffect(seleccionado) {
        val indice = equipos.indexOfFirst { it.id == seleccionado }
        if (indice < 0) return@LaunchedEffect
        val equipo = equipos[indice]
        if (idAlCentro(listaEstado) != equipo.id) listaEstado.animateScrollToItem(indice)
        camara.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(equipo.lat, equipo.lng), max(camara.position.zoom, ZOOM_EQUIPO)),
            600,
        )
    }
    // Al deslizar las tarjetas con el dedo, la tarjeta que queda al centro se selecciona.
    // Solo cuenta el movimiento hecho con el dedo, no el que hace la app al tocar un marcador.
    LaunchedEffect(listaEstado, equipos) {
        var movidaConElDedo = false
        launch {
            listaEstado.interactionSource.interactions.collect { interaccion ->
                if (interaccion is DragInteraction.Start) movidaConElDedo = true
            }
        }
        snapshotFlow { listaEstado.isScrollInProgress }
            .filter { moviendo -> !moviendo }
            .collect {
                if (movidaConElDedo) {
                    movidaConElDedo = false
                    idAlCentro(listaEstado)?.let(viewModel::alSeleccionar)
                }
            }
    }

    // Los marcadores se agrupan según el zoom (se recalcula solo al cambiar de nivel de zoom).
    // derivedStateOf evita redibujar la pantalla en cada cuadro mientras el mapa se mueve.
    val nivelZoom by remember { derivedStateOf { floor(camara.position.zoom) } }
    val grupos = remember(equipos, nivelZoom) { Agrupador.agrupar(equipos, nivelZoom) }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camara,
        properties = MapProperties(isMyLocationEnabled = ui.permisoConcedido, mapType = ui.tipoMapa.aMapType()),
        uiSettings = MapUiSettings(myLocationButtonEnabled = false, mapToolbarEnabled = false, zoomControlsEnabled = false),
    ) {
        grupos.forEach { grupo ->
            key(grupo.equipos.joinToString(",") { it.id }) {
                val estado = remember(grupo.lat, grupo.lng) { MarkerState(position = LatLng(grupo.lat, grupo.lng)) }
                if (grupo.esUnSolo) {
                    val equipo = grupo.equipos.first()
                    val elegido = equipo.id == seleccionado
                    Marker(
                        state = estado,
                        title = equipo.nombre,
                        snippet = "${Formato.soles(equipo.precioDia)} / día · toca para ver",
                        icon = if (elegido) BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE) else null,
                        zIndex = if (elegido) 1f else 0f,
                        onClick = {
                            viewModel.alSeleccionar(equipo.id)
                            false
                        },
                        onInfoWindowClick = { alAbrirEquipo(equipo.id) },
                    )
                } else {
                    val icono = remember(grupo.cantidad) { iconoGrupo(grupo.cantidad) }
                    Marker(
                        state = estado,
                        title = "${grupo.cantidad} equipos",
                        icon = icono,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        onClick = {
                            // Tocar un grupo acerca el mapa para separar sus equipos.
                            alcance.launch {
                                camara.animate(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(grupo.lat, grupo.lng), camara.position.zoom + 2f),
                                    500,
                                )
                            }
                            true
                        },
                    )
                }
            }
        }
    }

    // Botones flotantes arriba a la derecha: capas y mi ubicación.
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BotonCapas(ui.tipoMapa, viewModel::alElegirTipoMapa)
            SmallFloatingActionButton(onClick = alPedirUbicacion) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Mostrar mi ubicación")
            }
        }
        ui.mensaje?.let {
            Card(
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        // Tarjetas deslizables abajo del mapa.
        LazyRow(
            state = listaEstado,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listaEstado),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            items(equipos, key = { it.id }) { equipo ->
                TarjetaMapa(
                    equipo = equipo,
                    elegido = equipo.id == seleccionado,
                    alPulsar = { viewModel.alSeleccionar(equipo.id) },
                    alVer = { alAbrirEquipo(equipo.id) },
                )
            }
        }
    }
}

/** Botón de capas: Normal, Satélite, Híbrido y Relieve. */
@Composable
private fun BotonCapas(actual: TipoMapa, alElegir: (TipoMapa) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        SmallFloatingActionButton(onClick = { abierto = true }) {
            Icon(Icons.Filled.Map, contentDescription = "Cambiar capa del mapa")
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            TipoMapa.entries.forEach { tipo ->
                DropdownMenuItem(
                    text = { Text(if (tipo == actual) "✓ ${tipo.etiqueta}" else "    ${tipo.etiqueta}") },
                    onClick = {
                        abierto = false
                        alElegir(tipo)
                    },
                )
            }
        }
    }
}

@Composable
private fun TarjetaMapa(equipo: Equipo, elegido: Boolean, alPulsar: () -> Unit, alVer: () -> Unit) {
    Card(
        modifier = Modifier.width(290.dp).clickable(onClick = alPulsar),
        border = if (elegido) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (elegido) 8.dp else 3.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ImagenEquipo(
                equipo.fotoUrl,
                equipo.categoria,
                "Foto de ${equipo.nombre}",
                Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(equipo.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Formato.soles(equipo.precioDia)} / día", style = MaterialTheme.typography.bodyMedium)
                Text(equipo.empresaNombre, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = alVer) { Text("Ver") }
        }
    }
}

private const val ZOOM_EQUIPO = 15f

/** Id del equipo cuya tarjeta está más cerca del centro de la fila de tarjetas. */
private fun idAlCentro(estado: LazyListState): String? {
    val info = estado.layoutInfo
    val centro = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - centro) }?.key as? String
}

private fun TipoMapa.aMapType(): MapType = when (this) {
    TipoMapa.NORMAL -> MapType.NORMAL
    TipoMapa.SATELITE -> MapType.SATELLITE
    TipoMapa.HIBRIDO -> MapType.HYBRID
    TipoMapa.RELIEVE -> MapType.TERRAIN
}

/** Círculo azul con el número de equipos del grupo. */
private fun iconoGrupo(cantidad: Int): BitmapDescriptor {
    val lado = 112
    val mapa = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val lienzo = android.graphics.Canvas(mapa)
    val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    val centro = lado / 2f
    pincel.color = android.graphics.Color.WHITE
    lienzo.drawCircle(centro, centro, centro - 2f, pincel)
    pincel.color = android.graphics.Color.parseColor("#1E3A5F")
    lienzo.drawCircle(centro, centro, centro - 10f, pincel)
    pincel.color = android.graphics.Color.WHITE
    pincel.textSize = 40f
    pincel.textAlign = Paint.Align.CENTER
    pincel.typeface = Typeface.DEFAULT_BOLD
    val y = centro - (pincel.descent() + pincel.ascent()) / 2f
    lienzo.drawText(cantidad.toString(), centro, y, pincel)
    return BitmapDescriptorFactory.fromBitmap(mapa)
}

/** Alternativa cuando el mapa no está disponible: lista de equipos con su dirección. */
@Composable
private fun SinClaveMaps(equipos: List<Equipo>, alAbrirEquipo: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Text(
                "El mapa no está disponible en este momento. Estas son las ubicaciones de los equipos:",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        equipos.take(20).forEach { e ->
            TextButton(onClick = { alAbrirEquipo(e.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("${e.nombre} · ${e.direccion}")
            }
        }
    }
}
