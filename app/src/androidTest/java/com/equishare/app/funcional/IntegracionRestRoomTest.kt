package com.equishare.app.funcional

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.equishare.app.data.local.EquishareDatabase
import com.equishare.app.data.remote.ApiProvider
import com.equishare.app.data.repositorio.EquipoRepositoryImpl
import com.equishare.app.data.sistema.LectorImagen
import com.equishare.app.domain.model.Categoria
import com.equishare.app.domain.model.Coordenadas
import com.equishare.app.domain.model.datosONull
import com.equishare.app.domain.model.errorONull
import com.equishare.app.domain.model.Sesion
import com.equishare.app.domain.model.TipoError
import com.equishare.app.domain.repositorio.FiltroCatalogo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Prueba de integración del sprint final: Retrofit (REST) + Room (persistencia local) + filtros de
 * geolocalización, usando el repositorio REAL de la app contra un servidor HTTP de prueba (MockWebServer).
 * Así se verifica el patrón "offline-first": la red actualiza Room y, si la red falla, se usa lo guardado.
 */
@RunWith(AndroidJUnit4::class)
class IntegracionRestRoomTest {

    private lateinit var servidor: MockWebServer
    private lateinit var db: EquishareDatabase
    private lateinit var repo: EquipoRepositoryImpl
    private var servidorApagado = false

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        servidor = MockWebServer().apply { start() }
        db = Room.inMemoryDatabaseBuilder(contexto, EquishareDatabase::class.java).build()
        val auth = AuthFalso(sesionInicial = Sesion("demo-prueba", "eventos@demo.pe", true))
        val api = ApiProvider(MutableStateFlow(servidor.url("/").toString()), auth)
        repo = EquipoRepositoryImpl(api, db.equipoDao(), LectorImagen(contexto))
    }

    @After
    fun cerrar() {
        db.close()
        apagarServidor()
    }

    private fun apagarServidor() {
        if (!servidorApagado) {
            servidor.shutdown()
            servidorApagado = true
        }
    }

    private fun responderCatalogo() {
        servidor.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(CATALOGO_JSON),
        )
    }

    @Test
    fun rest_guardaElCatalogoEnRoomYEnviaElToken() = runBlocking<Unit> {
        responderCatalogo()

        val resultado = repo.refrescarCatalogo(FiltroCatalogo())

        assertNull(resultado.errorONull())
        val guardados = repo.observarCatalogo().first()
        assertEquals(listOf("Mezcladora de concreto", "Equipo de sonido profesional"), guardados.map { it.nombre })
        assertEquals(Categoria.EVENTOS, guardados[1].categoria)

        val peticion = servidor.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(peticion)
        assertEquals("/api/equipos", peticion!!.requestUrl?.encodedPath)
        assertEquals("Bearer demo:demo-prueba", peticion.getHeader("Authorization"))
    }

    @Test
    fun rest_enviaBusquedaCategoriaYUbicacionComoParametros() = runBlocking<Unit> {
        responderCatalogo()

        repo.refrescarCatalogo(
            FiltroCatalogo(
                texto = "  mezcla ",
                categoria = Categoria.CONSTRUCCION,
                ubicacion = Coordenadas(-8.1116, -79.0287),
                radioKm = 15.0,
            ),
        )

        val url = servidor.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
        assertEquals("mezcla", url.queryParameter("q"))
        assertEquals("CONSTRUCCION", url.queryParameter("categoria"))
        assertEquals(-8.1116, url.queryParameter("lat")!!.toDouble(), 0.0001)
        assertEquals(-79.0287, url.queryParameter("lng")!!.toDouble(), 0.0001)
        assertEquals(15.0, url.queryParameter("radioKm")!!.toDouble(), 0.0001)
    }

    @Test
    fun sinServidor_seUsanLosDatosGuardadosEnRoom() = runBlocking<Unit> {
        responderCatalogo()
        repo.refrescarCatalogo(FiltroCatalogo())

        apagarServidor() // como si el celular perdiera la conexión

        val resultado = repo.refrescarCatalogo(FiltroCatalogo())
        val tipo = resultado.errorONull()?.tipo
        assertTrue(tipo == TipoError.SIN_CONEXION || tipo == TipoError.TIEMPO_AGOTADO)

        // El catálogo guardado sigue disponible y el detalle se lee desde Room.
        assertEquals(2, repo.observarCatalogo().first().size)
        val detalle = repo.obtener("eq-1")
        assertEquals("Mezcladora de concreto", detalle.datosONull()?.nombre)
    }

    @Test
    fun errorHttp_seTraduceAUnMensajeClaroYNoBorraLaCache() = runBlocking<Unit> {
        responderCatalogo()
        repo.refrescarCatalogo(FiltroCatalogo())

        servidor.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"codigo":"NO_ENCONTRADO","mensaje":"El equipo no existe."}}"""),
        )
        val noExiste = repo.obtener("no-existe").errorONull()
        assertEquals(TipoError.NO_ENCONTRADO, noExiste?.tipo)
        assertEquals("El equipo no existe.", noExiste?.mensaje)

        servidor.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"codigo":"ERROR_INTERNO"}}"""))
        val caido = repo.refrescarCatalogo(FiltroCatalogo()).errorONull()
        assertEquals(TipoError.SERVIDOR, caido?.tipo)

        // Un error del servidor no debe vaciar los datos guardados.
        assertEquals(2, repo.observarCatalogo().first().size)
    }

    private companion object {
        val CATALOGO_JSON = """
            {
              "items": [
                {
                  "id": "eq-1", "empresaId": "emp-1", "empresaNombre": "Constructora Demo",
                  "nombre": "Mezcladora de concreto", "categoria": "CONSTRUCCION",
                  "descripcion": "Mezcladora de una bolsa", "precioDia": 120.0,
                  "fotoUrl": null, "fotoId": null, "lat": -8.1116, "lng": -79.0287,
                  "direccion": "Av. España 123, Trujillo", "estado": "PUBLICADO",
                  "calificacionPromedio": 4.5, "totalResenas": 2, "distanciaKm": 1.2
                },
                {
                  "id": "eq-2", "empresaId": "emp-2", "empresaNombre": "Eventos Demo",
                  "nombre": "Equipo de sonido profesional", "categoria": "EVENTOS",
                  "descripcion": "Parlantes y consola", "precioDia": 150.0,
                  "fotoUrl": null, "fotoId": null, "lat": -8.1050, "lng": -79.0300,
                  "direccion": "Jr. Pizarro 456, Trujillo", "estado": "PUBLICADO",
                  "calificacionPromedio": 0.0, "totalResenas": 0, "distanciaKm": 2.5
                }
              ],
              "page": 1, "size": 2, "total": 2
            }
        """.trimIndent()
    }
}
