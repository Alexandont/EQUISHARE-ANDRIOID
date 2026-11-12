package com.equishare.app.domain.repositorio

import com.equishare.app.domain.ia.EtiquetaFoto
import com.equishare.app.domain.mapa.TipoMapa
import com.equishare.app.domain.model.Categoria
import com.equishare.app.domain.model.Coordenadas
import com.equishare.app.domain.model.Cotizacion
import com.equishare.app.domain.model.Empresa
import com.equishare.app.domain.model.Equipo
import com.equishare.app.domain.model.EquipoEntrada
import com.equishare.app.domain.model.EstadoReserva
import com.equishare.app.domain.model.FotoSubida
import com.equishare.app.domain.model.MetricasEmpresa
import com.equishare.app.domain.model.RangoOcupado
import com.equishare.app.domain.model.Resena
import com.equishare.app.domain.model.Reserva
import com.equishare.app.domain.model.Resultado
import com.equishare.app.domain.model.Rol
import com.equishare.app.domain.model.Sesion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * Contratos del patrón Repository. Los ViewModel dependen de estas interfaces y no de Retrofit,
 * Room, DataStore o Firebase; así cada capa se puede probar por separado con implementaciones falsas.
 */

data class FiltroCatalogo(
    val texto: String = "",
    val categoria: Categoria? = null,
    val ubicacion: Coordenadas? = null,
    val radioKm: Double? = null,
)

interface EquipoRepository {
    /** Catálogo guardado en Room (fuente única de verdad para la UI, funciona sin conexión). */
    fun observarCatalogo(): Flow<List<Equipo>>
    suspend fun refrescarCatalogo(filtro: FiltroCatalogo): Resultado<Unit>
    fun observarPropios(): Flow<List<Equipo>>
    suspend fun refrescarPropios(): Resultado<Unit>
    suspend fun obtener(id: String): Resultado<Equipo>
    suspend fun crear(entrada: EquipoEntrada): Resultado<Equipo>
    suspend fun actualizar(id: String, entrada: EquipoEntrada): Resultado<Equipo>
    suspend fun eliminar(id: String): Resultado<Unit>
    suspend fun subirFoto(uri: String): Resultado<FotoSubida>
    suspend fun disponibilidad(id: String): Resultado<List<RangoOcupado>>
    suspend fun resenas(id: String): Resultado<List<Resena>>
}

interface ReservaRepository {
    fun observarMias(rol: Rol): Flow<List<Reserva>>
    suspend fun refrescarMias(rol: Rol): Resultado<Unit>
    suspend fun cotizar(equipoId: String, inicio: String, fin: String): Resultado<Cotizacion>
    suspend fun crear(equipoId: String, inicio: String, fin: String): Resultado<Reserva>
    suspend fun cancelar(id: String): Resultado<Reserva>
    suspend fun cambiarEstado(id: String, estado: EstadoReserva): Resultado<Reserva>
    suspend fun calificar(id: String, estrellas: Int, comentario: String): Resultado<Unit>
}

interface EmpresaRepository {
    /** Devuelve null si la empresa aún no completó su registro (HTTP 404). */
    suspend fun miEmpresa(): Resultado<Empresa?>
    suspend fun guardar(ruc: String, razonSocial: String, telefono: String): Resultado<Empresa>
    /** Borra la empresa y sus equipos en el servidor (derecho de cancelación). Falla si hay reservas activas. */
    suspend fun eliminarMiCuenta(): Resultado<Unit>
    /** Números del panel: equipos, reservas e ingresos estimados. */
    suspend fun metricas(): Resultado<MetricasEmpresa>
}

interface AuthRepository {
    val sesion: StateFlow<Sesion?>
    /** true cuando ya se sabe si hay una sesión guardada (evita mostrar el login por un instante). */
    val sesionCargada: StateFlow<Boolean>
    val esModoDemo: Boolean
    suspend fun iniciarSesion(email: String, password: String): Resultado<Sesion>
    suspend fun registrar(email: String, password: String): Resultado<Sesion>
    suspend fun cerrarSesion()
    /** Token para la cabecera Authorization. Se llama desde hilos de red (no desde el hilo principal). */
    fun tokenParaApi(): String?

    /** Envía (o vuelve a enviar) el correo con el enlace para verificar la cuenta. */
    suspend fun enviarVerificacion(): Resultado<Unit>

    /** Pregunta otra vez si el correo ya se verificó; si es así, actualiza la sesión. Devuelve si está verificado. */
    suspend fun revisarVerificacion(): Resultado<Boolean>

    /** Envía el correo para crear una contraseña nueva. */
    suspend fun recuperarContrasena(email: String): Resultado<Unit>

    /** Confirma la identidad con la contraseña actual (se pide antes de borrar la cuenta). */
    suspend fun confirmarContrasena(password: String): Resultado<Unit>

    /** Cambia la contraseña después de confirmar la actual. */
    suspend fun cambiarContrasena(actual: String, nueva: String): Resultado<Unit>

    /** Borra el usuario del proveedor de inicio de sesión y cierra la sesión. Es lo último al eliminar la cuenta. */
    suspend fun eliminarUsuario(): Resultado<Unit>
}

interface FavoritosRepository {
    fun observar(): Flow<Set<String>>
    suspend fun establecer(equipo: Equipo, favorito: Boolean): Resultado<Unit>
    /** Registra el consentimiento de privacidad del usuario (Ley N.° 29733). */
    suspend fun registrarConsentimiento(email: String?): Resultado<Unit>

    /** Borra los favoritos y el consentimiento guardados del usuario (al eliminar la cuenta). */
    suspend fun borrarMisDatos(): Resultado<Unit>
}

interface AjustesRepository {
    val urlServidor: Flow<String>
    val categoriaFiltro: Flow<Categoria?>
    suspend fun guardarUrlServidor(url: String)
    suspend fun guardarCategoriaFiltro(categoria: Categoria?)
    suspend fun probarServidor(url: String): Resultado<String>
    /** true después de ver las pantallas de bienvenida una vez. */
    val bienvenidaVista: Flow<Boolean>
    suspend fun marcarBienvenidaVista()
    /** Capa del mapa elegida la última vez. */
    val tipoMapa: Flow<TipoMapa>
    suspend fun guardarTipoMapa(tipo: TipoMapa)
}

interface MonitorRed {
    val enLinea: Flow<Boolean>
}

interface ProveedorUbicacion {
    fun tienePermiso(): Boolean
    suspend fun ubicacionActual(): Coordenadas?
}

/**
 * IA en el teléfono: mira la foto (sin internet) y dice qué ve.
 * Devuelve null si no se pudo revisar; nunca lanza excepciones a la interfaz.
 */
interface AnalizadorFoto {
    suspend fun etiquetar(uri: String): List<EtiquetaFoto>?
}

/**
 * IA en el teléfono que lee el texto de una foto (por ejemplo, la ficha RUC), sin internet.
 * Devuelve null si no se pudo leer; nunca lanza excepciones a la interfaz.
 */
interface LectorTextoFoto {
    suspend fun leerTexto(uri: String): String?
}

/** Convierte una dirección escrita en coordenadas (para ubicar el equipo al publicar). */
interface BuscadorDirecciones {
    /** Devuelve null si no encontró la dirección o no hay servicio de búsqueda. */
    suspend fun buscar(direccion: String): Coordenadas?

    companion object {
        /** Buscador vacío para pruebas o teléfonos sin servicio de direcciones. */
        val NINGUNO: BuscadorDirecciones = object : BuscadorDirecciones {
            override suspend fun buscar(direccion: String): Coordenadas? = null
        }
    }
}
