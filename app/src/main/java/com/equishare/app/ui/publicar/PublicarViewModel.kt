package com.equishare.app.ui.publicar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equishare.app.domain.ia.ClasificadorEquipo
import com.equishare.app.domain.ia.SugerenciaFoto
import com.equishare.app.domain.model.Categoria
import com.equishare.app.domain.model.EquipoEntrada
import com.equishare.app.domain.model.EstadoEquipo
import com.equishare.app.domain.model.Resultado
import com.equishare.app.domain.repositorio.AnalizadorFoto
import com.equishare.app.domain.repositorio.BuscadorDirecciones
import com.equishare.app.domain.repositorio.EquipoRepository
import com.equishare.app.domain.repositorio.ProveedorUbicacion
import com.equishare.app.domain.validacion.Validadores
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Estado de la revisión de la foto con la IA del teléfono. */
sealed interface RevisionIa {
    /** Todavía no se eligió una foto nueva (o se está editando la foto ya guardada). */
    data object SinRevisar : RevisionIa
    data object Revisando : RevisionIa
    data class Lista(val sugerencia: SugerenciaFoto) : RevisionIa
    /** La IA no pudo revisar la foto. No bloquea: se puede publicar igual. */
    data object NoDisponible : RevisionIa
}

data class PublicarUiState(
    val esEdicion: Boolean = false,
    val cargandoEquipo: Boolean = false,
    val nombre: String = "",
    val categoria: Categoria? = null,
    val descripcion: String = "",
    val precio: String = "",
    val direccion: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val visible: Boolean = true,
    val fotoUriLocal: String? = null,
    val fotoUrlRemota: String? = null,
    val fotoIdActual: String? = null,
    val estadoActual: EstadoEquipo? = null,
    val errorNombre: String? = null,
    val errorCategoria: String? = null,
    val errorDescripcion: String? = null,
    val errorPrecio: String? = null,
    val errorDireccion: String? = null,
    val errorUbicacion: String? = null,
    val motivosFoto: List<String> = emptyList(),
    val revisionIa: RevisionIa = RevisionIa.SinRevisar,
    /** true cuando la categoría la eligió la IA y el usuario todavía no la cambió. */
    val categoriaPorIa: Boolean = false,
    val buscandoDireccion: Boolean = false,
    val obteniendoUbicacion: Boolean = false,
    val guardando: Boolean = false,
    val pasoGuardado: String? = null,
    val mensaje: String? = null,
    val guardadoOk: Boolean = false,
)

class PublicarViewModel(
    estadoGuardado: SavedStateHandle,
    private val equipos: EquipoRepository,
    private val ubicacion: ProveedorUbicacion,
    private val analizador: AnalizadorFoto,
    private val buscador: BuscadorDirecciones = BuscadorDirecciones.NINGUNO,
) : ViewModel() {

    private val equipoId: String? = estadoGuardado.get<String>("equipoId")?.takeIf { it.isNotBlank() }

    private val _ui = MutableStateFlow(PublicarUiState(esEdicion = equipoId != null, cargandoEquipo = equipoId != null))
    val ui: StateFlow<PublicarUiState> = _ui.asStateFlow()

    private var trabajoIa: Job? = null

    init {
        if (equipoId != null) cargarEquipo(equipoId)
    }

    private fun cargarEquipo(id: String) {
        viewModelScope.launch {
            when (val r = equipos.obtener(id)) {
                is Resultado.Exito -> {
                    val e = r.datos
                    _ui.update {
                        it.copy(
                            cargandoEquipo = false,
                            nombre = e.nombre,
                            categoria = e.categoria,
                            descripcion = e.descripcion,
                            precio = e.precioDia.toString(),
                            direccion = e.direccion,
                            lat = e.lat,
                            lng = e.lng,
                            visible = e.estado != EstadoEquipo.OCULTO,
                            fotoUrlRemota = e.fotoUrl,
                            fotoIdActual = e.fotoId,
                            estadoActual = e.estado,
                        )
                    }
                }
                is Resultado.Error -> _ui.update { it.copy(cargandoEquipo = false, mensaje = r.error.mensaje) }
            }
        }
    }

    fun alCambiarNombre(v: String) = _ui.update { it.copy(nombre = v, errorNombre = null) }
    fun alElegirCategoria(c: Categoria) = _ui.update { it.copy(categoria = c, errorCategoria = null, categoriaPorIa = false) }
    fun alCambiarDescripcion(v: String) = _ui.update { it.copy(descripcion = v, errorDescripcion = null) }
    fun alCambiarPrecio(v: String) = _ui.update { it.copy(precio = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(9), errorPrecio = null) }
    fun alCambiarDireccion(v: String) = _ui.update { it.copy(direccion = v, errorDireccion = null) }
    fun alCambiarVisible(v: Boolean) = _ui.update { it.copy(visible = v) }
    fun alElegirFoto(uri: String?) {
        if (uri == null) return
        _ui.update { it.copy(fotoUriLocal = uri, motivosFoto = emptyList(), revisionIa = RevisionIa.Revisando) }
        revisarConIa(uri)
    }

    /**
     * La IA del teléfono revisa la foto y sugiere la categoría. Si el usuario aún no eligió una,
     * se elige sola; si ya eligió otra, solo se sugiere. Nunca bloquea la publicación.
     */
    private fun revisarConIa(uri: String) {
        trabajoIa?.cancel()
        trabajoIa = viewModelScope.launch {
            val etiquetas = try {
                analizador.etiquetar(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val revision = if (etiquetas == null) {
                RevisionIa.NoDisponible
            } else {
                RevisionIa.Lista(ClasificadorEquipo.clasificar(etiquetas))
            }
            _ui.update { actual ->
                // Si mientras tanto se eligió otra foto, este resultado ya no sirve.
                if (actual.fotoUriLocal != uri) return@update actual
                val sugerida = (revision as? RevisionIa.Lista)?.sugerencia?.categoria
                if (sugerida != null && (actual.categoria == null || actual.categoriaPorIa)) {
                    actual.copy(revisionIa = revision, categoria = sugerida, categoriaPorIa = true, errorCategoria = null)
                } else {
                    actual.copy(revisionIa = revision)
                }
            }
        }
    }

    /** Botón "Buscar en el mapa": convierte la dirección escrita en un punto del mapa. */
    fun buscarDireccion() {
        val texto = _ui.value.direccion.trim()
        if (_ui.value.buscandoDireccion) return
        if (Validadores.direccion(texto) != null) {
            _ui.update { it.copy(errorDireccion = "Escribe la dirección para buscarla en el mapa.") }
            return
        }
        _ui.update { it.copy(buscandoDireccion = true) }
        viewModelScope.launch {
            val coordenadas = try {
                buscador.buscar(texto)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _ui.update {
                if (coordenadas == null) {
                    it.copy(buscandoDireccion = false, mensaje = DIRECCION_NO_ENCONTRADA)
                } else {
                    it.copy(buscandoDireccion = false, lat = coordenadas.lat, lng = coordenadas.lng, errorUbicacion = null, mensaje = DIRECCION_ENCONTRADA)
                }
            }
        }
    }

    /** Botón "Usar ..." de la tarjeta de la IA: pone la categoría sugerida. */
    fun usarSugerenciaIa() {
        val sugerida = ((_ui.value.revisionIa as? RevisionIa.Lista)?.sugerencia?.categoria) ?: return
        _ui.update { it.copy(categoria = sugerida, errorCategoria = null, categoriaPorIa = true) }
    }

    fun alElegirPunto(lat: Double, lng: Double) = _ui.update { it.copy(lat = lat, lng = lng, errorUbicacion = null) }

    fun usarMiUbicacion() {
        _ui.update { it.copy(obteniendoUbicacion = true) }
        viewModelScope.launch {
            val c = ubicacion.ubicacionActual()
            _ui.update {
                if (c == null) {
                    it.copy(obteniendoUbicacion = false, mensaje = "No se pudo obtener la ubicación. Activa el GPS o elige el punto en el mapa.")
                } else {
                    it.copy(obteniendoUbicacion = false, lat = c.lat, lng = c.lng, errorUbicacion = null)
                }
            }
        }
    }

    fun tienePermisoUbicacion(): Boolean = ubicacion.tienePermiso()

    private fun validar(): Boolean {
        val e = _ui.value
        val validado = e.copy(
            errorNombre = Validadores.nombreEquipo(e.nombre),
            errorCategoria = if (e.categoria == null) "Elige una categoría." else null,
            errorDescripcion = Validadores.descripcion(e.descripcion),
            errorPrecio = Validadores.precio(e.precio),
            errorDireccion = Validadores.direccion(e.direccion),
            errorUbicacion = if (e.lat == null || e.lng == null) "Indica la ubicación del equipo." else null,
        )
        _ui.value = validado
        return listOf(
            validado.errorNombre, validado.errorCategoria, validado.errorDescripcion,
            validado.errorPrecio, validado.errorDireccion, validado.errorUbicacion,
        ).all { it == null }
    }

    fun guardar() {
        if (_ui.value.guardando || !validar()) return
        _ui.update { it.copy(guardando = true, motivosFoto = emptyList()) }
        viewModelScope.launch {
            val e = _ui.value
            var fotoId = e.fotoIdActual
            val uriLocal = e.fotoUriLocal
            if (uriLocal != null) {
                _ui.update { it.copy(pasoGuardado = "Validando la foto…") }
                when (val subida = equipos.subirFoto(uriLocal)) {
                    is Resultado.Exito -> fotoId = subida.datos.id
                    is Resultado.Error -> {
                        // La foto rechazada explica el motivo y no se publica.
                        _ui.update {
                            it.copy(
                                guardando = false,
                                pasoGuardado = null,
                                motivosFoto = subida.error.detalles.ifEmpty { listOf(subida.error.mensaje) },
                            )
                        }
                        return@launch
                    }
                }
            }
            _ui.update { it.copy(pasoGuardado = "Guardando el equipo…") }
            val entrada = EquipoEntrada(
                nombre = e.nombre,
                categoria = e.categoria ?: Categoria.HERRAMIENTAS,
                descripcion = e.descripcion,
                precioDia = Validadores.precioComoNumero(e.precio) ?: 0.0,
                lat = e.lat ?: 0.0,
                lng = e.lng ?: 0.0,
                direccion = e.direccion,
                fotoId = fotoId,
                visible = e.visible,
            )
            val resultado = if (equipoId == null) equipos.crear(entrada) else equipos.actualizar(equipoId, entrada)
            when (resultado) {
                is Resultado.Exito -> {
                    val aviso = when (resultado.datos.estado) {
                        EstadoEquipo.PENDIENTE_FOTO -> "Guardado. Agrega una foto aprobada para que aparezca en el catálogo."
                        EstadoEquipo.EN_REVISION -> "Guardado. El equipo sigue en revisión por calificaciones bajas."
                        EstadoEquipo.OCULTO -> "Guardado como oculto."
                        EstadoEquipo.PUBLICADO -> "¡Equipo publicado en el catálogo!"
                    }
                    _ui.update { it.copy(guardando = false, pasoGuardado = null, guardadoOk = true, mensaje = aviso) }
                }
                is Resultado.Error -> _ui.update {
                    it.copy(
                        guardando = false,
                        pasoGuardado = null,
                        mensaje = (listOf(resultado.error.mensaje) + resultado.error.detalles).joinToString(" "),
                    )
                }
            }
        }
    }

    fun mensajeMostrado() = _ui.update { it.copy(mensaje = null) }

    /** Evita que la pantalla procese dos veces el cierre tras guardar. */
    fun cierreProcesado() = _ui.update { it.copy(mensaje = null, guardadoOk = false) }

    companion object {
        const val DIRECCION_ENCONTRADA = "Ubicamos la dirección en el mapa. Si hace falta, ajusta el punto tocando el mapa."
        const val DIRECCION_NO_ENCONTRADA =
            "No encontramos esa dirección. Agrega el distrito y la ciudad (por ejemplo, «Av. España 1234, Trujillo») o toca el mapa."
    }
}
