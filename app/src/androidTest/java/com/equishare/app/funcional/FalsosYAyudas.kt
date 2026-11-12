package com.equishare.app.funcional

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.equishare.app.domain.ia.EtiquetaFoto
import com.equishare.app.domain.model.Categoria
import com.equishare.app.domain.model.Coordenadas
import com.equishare.app.domain.model.Empresa
import com.equishare.app.domain.model.Equipo
import com.equishare.app.domain.model.EquipoEntrada
import com.equishare.app.domain.model.ErrorApp
import com.equishare.app.domain.model.EstadoEquipo
import com.equishare.app.domain.model.FotoSubida
import com.equishare.app.domain.model.RangoOcupado
import com.equishare.app.domain.model.Resena
import com.equishare.app.domain.model.Resultado
import com.equishare.app.domain.model.Sesion
import com.equishare.app.domain.model.TipoError
import com.equishare.app.domain.repositorio.AjustesRepository
import com.equishare.app.domain.repositorio.AnalizadorFoto
import com.equishare.app.domain.repositorio.AuthRepository
import com.equishare.app.domain.repositorio.EmpresaRepository
import com.equishare.app.domain.repositorio.EquipoRepository
import com.equishare.app.domain.repositorio.FavoritosRepository
import com.equishare.app.domain.repositorio.FiltroCatalogo
import com.equishare.app.domain.repositorio.LectorTextoFoto
import com.equishare.app.domain.repositorio.MonitorRed
import com.equishare.app.domain.repositorio.ProveedorUbicacion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/*
 * Repositorios falsos y ayudas para las pruebas funcionales (instrumentadas).
 * Permiten probar las pantallas reales con sus ViewModel reales, sin depender de que el
 * servidor Node.js, Firebase o el GPS estén disponibles en el emulador.
 */

const val TEXTO_SIN_CONEXION = "Sin conexión. Mostrando datos guardados en el teléfono."

fun equipoDePrueba(id: String, nombre: String, categoria: Categoria) = Equipo(
    id = id,
    empresaId = "demo-constructora",
    empresaNombre = "Constructora Demo",
    nombre = nombre,
    categoria = categoria,
    descripcion = "Equipo de prueba para las pruebas funcionales",
    precioDia = 80.0,
    fotoUrl = null,
    fotoId = null,
    lat = -8.11,
    lng = -79.03,
    direccion = "Trujillo",
    estado = EstadoEquipo.PUBLICADO,
    calificacionPromedio = 0.0,
    totalResenas = 0,
    distanciaKm = null,
)

val MEZCLADORA = equipoDePrueba("eq-1", "Mezcladora de concreto", Categoria.CONSTRUCCION)
val SONIDO = equipoDePrueba("eq-2", "Equipo de sonido profesional", Categoria.EVENTOS)
val TALADRO = equipoDePrueba("eq-3", "Taladro percutor", Categoria.HERRAMIENTAS)

/**
 * Simula el repositorio real: [catalogo] hace el papel de Room (lo que la pantalla dibuja) y
 * [refrescarCatalogo] hace el papel de la API REST (filtra por categoría y texto como el servidor).
 */
class EquiposFalsos(
    @Volatile var respuestaServidor: List<Equipo> = listOf(MEZCLADORA, SONIDO, TALADRO),
) : EquipoRepository {
    val catalogo = MutableStateFlow<List<Equipo>>(emptyList())

    @Volatile
    var fallarCon: ErrorApp? = null
    val filtrosRecibidos = CopyOnWriteArrayList<FiltroCatalogo>()

    override fun observarCatalogo(): Flow<List<Equipo>> = catalogo

    override suspend fun refrescarCatalogo(filtro: FiltroCatalogo): Resultado<Unit> {
        filtrosRecibidos += filtro
        fallarCon?.let { return Resultado.Error(it) }
        val texto = filtro.texto.trim()
        catalogo.value = respuestaServidor.filter { equipo ->
            (filtro.categoria == null || equipo.categoria == filtro.categoria) &&
                (texto.isEmpty() || equipo.nombre.contains(texto, ignoreCase = true))
        }
        return Resultado.Exito(Unit)
    }

    override fun observarPropios(): Flow<List<Equipo>> = MutableStateFlow(emptyList())
    override suspend fun refrescarPropios(): Resultado<Unit> = Resultado.Exito(Unit)
    override suspend fun obtener(id: String): Resultado<Equipo> = Resultado.Exito(MEZCLADORA)
    override suspend fun crear(entrada: EquipoEntrada): Resultado<Equipo> = Resultado.Exito(MEZCLADORA)
    override suspend fun actualizar(id: String, entrada: EquipoEntrada): Resultado<Equipo> = Resultado.Exito(MEZCLADORA)
    override suspend fun eliminar(id: String): Resultado<Unit> = Resultado.Exito(Unit)
    override suspend fun subirFoto(uri: String): Resultado<FotoSubida> = Resultado.Error(ErrorApp(TipoError.VALIDACION))
    override suspend fun disponibilidad(id: String): Resultado<List<RangoOcupado>> = Resultado.Exito(emptyList())
    override suspend fun resenas(id: String): Resultado<List<Resena>> = Resultado.Exito(emptyList())
}

class FavoritosFalsos : FavoritosRepository {
    val ids = MutableStateFlow<Set<String>>(emptySet())
    override fun observar(): Flow<Set<String>> = ids
    override suspend fun establecer(equipo: Equipo, favorito: Boolean): Resultado<Unit> {
        ids.value = if (favorito) ids.value + equipo.id else ids.value - equipo.id
        return Resultado.Exito(Unit)
    }
    override suspend fun registrarConsentimiento(email: String?): Resultado<Unit> = Resultado.Exito(Unit)

    @Volatile
    var borrado = false
    override suspend fun borrarMisDatos(): Resultado<Unit> {
        ids.value = emptySet()
        borrado = true
        return Resultado.Exito(Unit)
    }
}

/** Simula el estado de la red (como si se activara o desactivara el modo avión). */
class RedFalsa(enLinea: Boolean = true) : MonitorRed {
    val estado = MutableStateFlow(enLinea)
    override val enLinea: Flow<Boolean> = estado
}

/** Simula el GPS. Con coordenadas null se comporta como un GPS apagado. */
class UbicacionFalsa(
    @Volatile var coordenadas: Coordenadas? = Coordenadas(-8.1116, -79.0287),
) : ProveedorUbicacion {
    override fun tienePermiso(): Boolean = true
    override suspend fun ubicacionActual(): Coordenadas? = coordenadas
}

class AjustesFalsos : AjustesRepository {
    private val url = MutableStateFlow("http://10.0.2.2:3000/")
    private val categoria = MutableStateFlow<Categoria?>(null)
    override val urlServidor: Flow<String> = url
    override val categoriaFiltro: Flow<Categoria?> = categoria
    override suspend fun guardarUrlServidor(url: String) {
        this.url.value = url
    }
    override suspend fun guardarCategoriaFiltro(categoria: Categoria?) {
        this.categoria.value = categoria
    }
    override suspend fun probarServidor(url: String): Resultado<String> = Resultado.Exito("demo")

    val vista = MutableStateFlow(false)
    override val bienvenidaVista: Flow<Boolean> = vista
    override suspend fun marcarBienvenidaVista() {
        vista.value = true
    }

    val tipo = MutableStateFlow(com.equishare.app.domain.mapa.TipoMapa.NORMAL)
    override val tipoMapa: Flow<com.equishare.app.domain.mapa.TipoMapa> = tipo
    override suspend fun guardarTipoMapa(tipo: com.equishare.app.domain.mapa.TipoMapa) {
        this.tipo.value = tipo
    }
}

class AuthFalso(
    private val fallar: Boolean = false,
    sesionInicial: Sesion? = null,
    override val esModoDemo: Boolean = true,
) : AuthRepository {
    private val _sesion = MutableStateFlow(sesionInicial)
    override val sesion: StateFlow<Sesion?> = _sesion.asStateFlow()
    override val sesionCargada: StateFlow<Boolean> = MutableStateFlow(true)
    val llamadas = AtomicInteger(0)

    @Volatile
    var ultimoEmail: String? = null

    override suspend fun iniciarSesion(email: String, password: String): Resultado<Sesion> {
        llamadas.incrementAndGet()
        ultimoEmail = email
        if (fallar) return Resultado.Error(ErrorApp(TipoError.NO_AUTENTICADO, "Correo o contraseña incorrectos."))
        val nueva = Sesion("demo-prueba", email, true)
        _sesion.value = nueva
        return Resultado.Exito(nueva)
    }

    override suspend fun registrar(email: String, password: String): Resultado<Sesion> = iniciarSesion(email, password)

    override suspend fun cerrarSesion() {
        _sesion.value = null
    }

    override fun tokenParaApi(): String? = _sesion.value?.let { "demo:${it.uid}" }

    /** Lo que responde "Ya lo verifiqué" (si el correo ya se verificó). */
    @Volatile
    var correoYaVerificado = false
    val acciones = CopyOnWriteArrayList<String>()

    override suspend fun enviarVerificacion(): Resultado<Unit> {
        acciones += "enviarVerificacion"
        return Resultado.Exito(Unit)
    }

    override suspend fun revisarVerificacion(): Resultado<Boolean> {
        acciones += "revisarVerificacion"
        if (correoYaVerificado) _sesion.value = _sesion.value?.copy(correoVerificado = true)
        return Resultado.Exito(correoYaVerificado)
    }

    override suspend fun recuperarContrasena(email: String): Resultado<Unit> {
        acciones += "recuperarContrasena:$email"
        return Resultado.Exito(Unit)
    }

    override suspend fun confirmarContrasena(password: String): Resultado<Unit> {
        acciones += "confirmarContrasena"
        return Resultado.Exito(Unit)
    }

    override suspend fun cambiarContrasena(actual: String, nueva: String): Resultado<Unit> {
        acciones += "cambiarContrasena"
        return Resultado.Exito(Unit)
    }

    override suspend fun eliminarUsuario(): Resultado<Unit> {
        acciones += "eliminarUsuario"
        _sesion.value = null
        return Resultado.Exito(Unit)
    }
}

/** IA falsa: responde lo configurado sin usar el modelo real, para probar la pantalla con resultados fijos. */
class AnalizadorFotoFalso(@Volatile var respuesta: List<EtiquetaFoto>?) : AnalizadorFoto {
    val pedidas = CopyOnWriteArrayList<String>()
    override suspend fun etiquetar(uri: String): List<EtiquetaFoto>? {
        pedidas += uri
        return respuesta
    }
}

/** Lector de texto falso: devuelve el texto configurado como si la IA lo hubiera leído de la foto. */
class LectorTextoFalso(@Volatile var texto: String?) : LectorTextoFoto {
    val pedidas = CopyOnWriteArrayList<String>()
    override suspend fun leerTexto(uri: String): String? {
        pedidas += uri
        return texto
    }
}

/** Registro de empresa falso: guarda lo que recibe para revisarlo en la prueba. */
class EmpresasFalsas : EmpresaRepository {
    val guardadas = CopyOnWriteArrayList<Triple<String, String, String>>()
    @Volatile
    var empresaActual: Empresa? = null
    override suspend fun miEmpresa(): Resultado<Empresa?> = Resultado.Exito(empresaActual)
    override suspend fun guardar(ruc: String, razonSocial: String, telefono: String): Resultado<Empresa> {
        guardadas += Triple(ruc, razonSocial, telefono)
        return Resultado.Exito(Empresa("empresa-1", razonSocial, ruc, telefono, null, true, 0.0, 0))
    }

    @Volatile
    var eliminada = false
    override suspend fun eliminarMiCuenta(): Resultado<Unit> {
        eliminada = true
        return Resultado.Exito(Unit)
    }

    override suspend fun metricas(): Resultado<com.equishare.app.domain.model.MetricasEmpresa> =
        Resultado.Exito(com.equishare.app.domain.model.MetricasEmpresa(3, 4, 2, 1, 0, 5, 1250.5, "Mezcladora", 3))
}

/** Crea un ViewModel dentro de un ViewModelStore para poder liberarlo al final de cada prueba. */
inline fun <reified VM : ViewModel> ViewModelStore.crear(noinline fabrica: () -> VM): VM =
    ViewModelProvider(this, viewModelFactory { initializer { fabrica() } })[VM::class.java]

/** Espera (hasta [tiempoMs]) a que aparezca un texto en pantalla. */
fun ComposeTestRule.esperarTexto(texto: String, tiempoMs: Long = 5_000) {
    waitUntil(tiempoMs) {
        onAllNodesWithText(texto).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}

/** Espera (hasta [tiempoMs]) a que un texto desaparezca de la pantalla. */
fun ComposeTestRule.esperarQueDesaparezca(texto: String, tiempoMs: Long = 5_000) {
    waitUntil(tiempoMs) {
        onAllNodesWithText(texto).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
    }
}
