package com.equishare.app.ui

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun equipoDePrueba(id: String, categoria: Categoria = Categoria.CONSTRUCCION) = Equipo(
    id = id,
    empresaId = "demo-constructora",
    empresaNombre = "Constructora Demo",
    nombre = "Equipo $id",
    categoria = categoria,
    descripcion = "Descripción de prueba",
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

/** Repositorio falso: simula Room (catálogo) y la API (resultado configurable). */
class FakeEquipoRepository : EquipoRepository {
    val catalogo = MutableStateFlow<List<Equipo>>(emptyList())
    var respuestaServidor: List<Equipo> = listOf(equipoDePrueba("1"), equipoDePrueba("2", Categoria.EVENTOS))
    var fallarCon: ErrorApp? = null
    val filtrosRecibidos = mutableListOf<FiltroCatalogo>()

    override fun observarCatalogo(): Flow<List<Equipo>> = catalogo

    override suspend fun refrescarCatalogo(filtro: FiltroCatalogo): Resultado<Unit> {
        filtrosRecibidos += filtro
        fallarCon?.let { return Resultado.Error(it) }
        catalogo.value = respuestaServidor.filter { filtro.categoria == null || it.categoria == filtro.categoria }
        return Resultado.Exito(Unit)
    }

    override fun observarPropios(): Flow<List<Equipo>> = MutableStateFlow(emptyList())
    override suspend fun refrescarPropios(): Resultado<Unit> = Resultado.Exito(Unit)
    override suspend fun obtener(id: String): Resultado<Equipo> = Resultado.Exito(equipoDePrueba(id))
    override suspend fun crear(entrada: EquipoEntrada): Resultado<Equipo> = Resultado.Exito(equipoDePrueba("nuevo"))
    override suspend fun actualizar(id: String, entrada: EquipoEntrada): Resultado<Equipo> = Resultado.Exito(equipoDePrueba(id))
    override suspend fun eliminar(id: String): Resultado<Unit> = Resultado.Exito(Unit)
    override suspend fun subirFoto(uri: String): Resultado<FotoSubida> = Resultado.Error(ErrorApp(TipoError.VALIDACION))
    override suspend fun disponibilidad(id: String): Resultado<List<RangoOcupado>> = Resultado.Exito(emptyList())
    override suspend fun resenas(id: String): Resultado<List<Resena>> = Resultado.Exito(emptyList())
}

class FakeFavoritos : FavoritosRepository {
    private val ids = MutableStateFlow<Set<String>>(emptySet())
    override fun observar(): Flow<Set<String>> = ids
    override suspend fun establecer(equipo: Equipo, favorito: Boolean): Resultado<Unit> {
        ids.value = if (favorito) ids.value + equipo.id else ids.value - equipo.id
        return Resultado.Exito(Unit)
    }
    override suspend fun registrarConsentimiento(email: String?): Resultado<Unit> = Resultado.Exito(Unit)

    var borrado = false
    var errorBorrar: ErrorApp? = null
    override suspend fun borrarMisDatos(): Resultado<Unit> {
        errorBorrar?.let { return Resultado.Error(it) }
        borrado = true
        return Resultado.Exito(Unit)
    }
}

class FakeRed(inicial: Boolean = true) : MonitorRed {
    val estado = MutableStateFlow(inicial)
    override val enLinea: Flow<Boolean> = estado
}

class FakeUbicacion(var coordenadas: Coordenadas? = Coordenadas(-8.11, -79.03)) : ProveedorUbicacion {
    override fun tienePermiso(): Boolean = true
    override suspend fun ubicacionActual(): Coordenadas? = coordenadas
}

class FakeAjustes : AjustesRepository {
    private val url = MutableStateFlow("http://10.0.2.2:3000/")
    private val categoria = MutableStateFlow<Categoria?>(null)
    override val urlServidor: Flow<String> = url
    override val categoriaFiltro: Flow<Categoria?> = categoria
    override suspend fun guardarUrlServidor(url: String) { this.url.value = url }
    override suspend fun guardarCategoriaFiltro(categoria: Categoria?) { this.categoria.value = categoria }
    override suspend fun probarServidor(url: String): Resultado<String> = Resultado.Exito("demo")

    val vista = MutableStateFlow(false)
    override val bienvenidaVista: Flow<Boolean> = vista
    override suspend fun marcarBienvenidaVista() { vista.value = true }

    val tipo = MutableStateFlow(com.equishare.app.domain.mapa.TipoMapa.NORMAL)
    override val tipoMapa: Flow<com.equishare.app.domain.mapa.TipoMapa> = tipo
    override suspend fun guardarTipoMapa(tipo: com.equishare.app.domain.mapa.TipoMapa) { this.tipo.value = tipo }
}

class FakeAuth(
    private val fallar: Boolean = false,
    sesionInicial: Sesion? = null,
    override val esModoDemo: Boolean = true,
) : AuthRepository {
    private val _sesion = MutableStateFlow(sesionInicial)
    override val sesion: StateFlow<Sesion?> = _sesion.asStateFlow()
    override val sesionCargada: StateFlow<Boolean> = MutableStateFlow(true)
    var llamadas = 0

    override suspend fun iniciarSesion(email: String, password: String): Resultado<Sesion> {
        llamadas++
        if (fallar) return Resultado.Error(ErrorApp(TipoError.NO_AUTENTICADO, "Correo o contraseña incorrectos."))
        val s = Sesion("demo-x", email, true)
        _sesion.value = s
        return Resultado.Exito(s)
    }

    override suspend fun registrar(email: String, password: String): Resultado<Sesion> = iniciarSesion(email, password)
    override suspend fun cerrarSesion() { _sesion.value = null }
    override fun tokenParaApi(): String? = _sesion.value?.let { "demo:${it.uid}" }

    /** Nombre de cada operación de cuenta llamada, en orden. */
    val acciones = mutableListOf<String>()
    /** Operaciones que deben fallar, con su error. */
    val errores = mutableMapOf<String, ErrorApp>()
    /** Lo que responde "revisarVerificacion" (si el correo ya se verificó). */
    var correoYaVerificado = false
    var ultimoCorreoRecuperar: String? = null

    private fun <T> accion(nombre: String, valor: T): Resultado<T> {
        acciones += nombre
        return errores[nombre]?.let { Resultado.Error(it) } ?: Resultado.Exito(valor)
    }

    override suspend fun enviarVerificacion(): Resultado<Unit> = accion("enviarVerificacion", Unit)

    override suspend fun revisarVerificacion(): Resultado<Boolean> {
        val r = accion("revisarVerificacion", correoYaVerificado)
        if (r is Resultado.Exito && r.datos) _sesion.value = _sesion.value?.copy(correoVerificado = true)
        return r
    }

    override suspend fun recuperarContrasena(email: String): Resultado<Unit> {
        ultimoCorreoRecuperar = email
        return accion("recuperarContrasena", Unit)
    }

    override suspend fun confirmarContrasena(password: String): Resultado<Unit> = accion("confirmarContrasena", Unit)

    override suspend fun cambiarContrasena(actual: String, nueva: String): Resultado<Unit> = accion("cambiarContrasena", Unit)

    override suspend fun eliminarUsuario(): Resultado<Unit> {
        val r = accion("eliminarUsuario", Unit)
        if (r is Resultado.Exito) _sesion.value = null
        return r
    }
}

/**
 * IA falsa: responde lo que se le configure. Las fotos en [esperarEn] quedan "pensando" hasta que
 * la prueba llame a [responder], para simular una IA lenta.
 */
class FakeAnalizadorFoto(var respuesta: List<EtiquetaFoto>? = emptyList()) : AnalizadorFoto {
    var lanzar: Exception? = null
    var esperarEn: Set<String> = emptySet()
    val pedidas = mutableListOf<String>()
    private val pendientes = mutableMapOf<String, CompletableDeferred<List<EtiquetaFoto>?>>()

    override suspend fun etiquetar(uri: String): List<EtiquetaFoto>? {
        pedidas += uri
        lanzar?.let { throw it }
        if (uri in esperarEn) return pendientes.getOrPut(uri) { CompletableDeferred() }.await()
        return respuesta
    }

    fun responder(uri: String, etiquetas: List<EtiquetaFoto>?) {
        pendientes.getOrPut(uri) { CompletableDeferred() }.complete(etiquetas)
    }
}

/** Lector de texto falso: devuelve el texto configurado como si la IA lo hubiera leído de la foto. */
class FakeLectorTexto(var texto: String?) : LectorTextoFoto {
    var lanzar: Exception? = null
    val pedidas = mutableListOf<String>()

    override suspend fun leerTexto(uri: String): String? {
        pedidas += uri
        lanzar?.let { throw it }
        return texto
    }
}

/** Registro de empresa falso: guarda lo que recibe para revisarlo en la prueba. */
class FakeEmpresaRepository : EmpresaRepository {
    val guardadas = mutableListOf<Triple<String, String, String>>()

    override suspend fun miEmpresa(): Resultado<Empresa?> = Resultado.Exito(miEmpresaActual)

    override suspend fun guardar(ruc: String, razonSocial: String, telefono: String): Resultado<Empresa> {
        guardadas += Triple(ruc, razonSocial, telefono)
        return Resultado.Exito(Empresa("empresa-1", razonSocial, ruc, telefono, null, true, 0.0, 0))
    }

    var miEmpresaActual: Empresa? = null
    var eliminada = false
    var errorEliminar: ErrorApp? = null

    override suspend fun eliminarMiCuenta(): Resultado<Unit> {
        errorEliminar?.let { return Resultado.Error(it) }
        eliminada = true
        return Resultado.Exito(Unit)
    }

    var metricasActuales: Resultado<com.equishare.app.domain.model.MetricasEmpresa> =
        Resultado.Exito(com.equishare.app.domain.model.MetricasEmpresa(3, 4, 2, 1, 0, 5, 1250.5, "Mezcladora", 3))
    override suspend fun metricas(): Resultado<com.equishare.app.domain.model.MetricasEmpresa> = metricasActuales
}
