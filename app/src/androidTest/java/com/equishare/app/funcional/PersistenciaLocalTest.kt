package com.equishare.app.funcional

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.equishare.app.data.local.AjustesRepositoryImpl
import com.equishare.app.data.local.EquipoEntity
import com.equishare.app.data.local.EquishareDatabase
import com.equishare.app.data.local.ReservaEntity
import com.equishare.app.domain.model.Categoria
import com.equishare.app.domain.model.Resultado
import com.equishare.app.domain.model.Sesion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Persistencia local en el dispositivo real: Room (caché del catálogo y de las reservas)
 * y DataStore (preferencias). Room se prueba con una base en memoria para no tocar los datos de la app.
 */
@RunWith(AndroidJUnit4::class)
class PersistenciaLocalTest {

    private lateinit var db: EquishareDatabase

    @Before
    fun abrirBase() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            EquishareDatabase::class.java,
        ).build()
    }

    @After
    fun cerrarBase() {
        db.close()
    }

    private fun equipo(id: String, lista: String, orden: Int) = EquipoEntity(
        id = id,
        lista = lista,
        orden = orden,
        empresaId = "emp-1",
        empresaNombre = "Constructora Demo",
        nombre = "Equipo $id",
        categoria = "CONSTRUCCION",
        descripcion = "Equipo guardado para usar sin conexión",
        precioDia = 80.0,
        fotoUrl = null,
        fotoId = null,
        lat = -8.11,
        lng = -79.03,
        direccion = "Trujillo",
        estado = "PUBLICADO",
        calificacionPromedio = 4.5,
        totalResenas = 2,
        distanciaKm = null,
        guardadoEn = System.currentTimeMillis(),
    )

    private fun reserva(id: String, rol: String, orden: Int) = ReservaEntity(
        id = id,
        rol = rol,
        orden = orden,
        equipoId = "eq-1",
        equipoNombre = "Mezcladora de concreto",
        equipoFotoUrl = null,
        arrendatarioNombre = "Eventos Demo",
        arrendadorNombre = "Constructora Demo",
        fechaInicio = "2026-11-20",
        fechaFin = "2026-11-22",
        dias = 3,
        precioDia = 80.0,
        subtotal = 240.0,
        comision = 28.8,
        montoArrendador = 211.2,
        total = 240.0,
        penalidad = 0.0,
        estado = "PENDIENTE",
        resenaEnviada = false,
        devueltaEn = null,
    )

    @Test
    fun room_guardaElCatalogoYLoDevuelveEnOrden() = runBlocking<Unit> {
        val dao = db.equipoDao()
        dao.insertar(listOf(equipo("c", "CATALOGO", 2), equipo("a", "CATALOGO", 0), equipo("b", "CATALOGO", 1)))

        val guardados = dao.observar("CATALOGO").first()

        assertEquals(listOf("a", "b", "c"), guardados.map { it.id })
    }

    @Test
    fun room_reemplazarListaNoBorraLosEquiposPropios() = runBlocking<Unit> {
        val dao = db.equipoDao()
        dao.insertar(listOf(equipo("a", "CATALOGO", 0), equipo("b", "CATALOGO", 1), equipo("p", "PROPIOS", 0)))

        dao.reemplazarLista("CATALOGO", listOf(equipo("nuevo", "CATALOGO", 0)))

        assertEquals(listOf("nuevo"), dao.observar("CATALOGO").first().map { it.id })
        assertEquals(listOf("p"), dao.observar("PROPIOS").first().map { it.id })
    }

    @Test
    fun room_buscaYEliminaUnEquipo() = runBlocking<Unit> {
        val dao = db.equipoDao()
        dao.insertar(listOf(equipo("a", "CATALOGO", 0)))
        assertNotNull(dao.buscar("a"))

        dao.eliminar("a")

        assertNull(dao.buscar("a"))
    }

    @Test
    fun room_reservasPorRolYLimpiezaAlCerrarSesion() = runBlocking<Unit> {
        val dao = db.reservaDao()
        dao.reemplazarRol("ARRENDATARIO", listOf(reserva("r1", "ARRENDATARIO", 0)))
        dao.reemplazarRol("ARRENDADOR", listOf(reserva("r2", "ARRENDADOR", 0), reserva("r3", "ARRENDADOR", 1)))
        assertEquals(1, dao.observar("ARRENDATARIO").first().size)
        assertEquals(2, dao.observar("ARRENDADOR").first().size)

        dao.borrarTodo() // lo que hace la app al cerrar sesión

        assertTrue(dao.observar("ARRENDATARIO").first().isEmpty())
        assertTrue(dao.observar("ARRENDADOR").first().isEmpty())
    }

    @Test
    fun dataStore_guardaLasPreferenciasYLasRestaura() = runBlocking<Unit> {
        val ajustes = AjustesRepositoryImpl(ApplicationProvider.getApplicationContext<Context>()) { Resultado.Exito("demo") }
        // Se guardan los valores actuales para devolverlos al final y no alterar la app.
        val urlOriginal = ajustes.urlServidor.first()
        val categoriaOriginal = ajustes.categoriaFiltro.first()
        val sesionOriginal = ajustes.sesionDemo.first()
        try {
            ajustes.guardarUrlServidor("http://192.168.1.50:3000/")
            ajustes.guardarCategoriaFiltro(Categoria.EVENTOS)
            ajustes.guardarSesionDemo(Sesion("demo-prueba", "eventos@demo.pe", true))

            assertEquals("http://192.168.1.50:3000/", ajustes.urlServidor.first())
            assertEquals(Categoria.EVENTOS, ajustes.categoriaFiltro.first())
            assertEquals("demo-prueba", ajustes.sesionDemo.first()?.uid)

            ajustes.guardarCategoriaFiltro(null)
            assertNull(ajustes.categoriaFiltro.first())
        } finally {
            ajustes.guardarUrlServidor(urlOriginal)
            ajustes.guardarCategoriaFiltro(categoriaOriginal)
            ajustes.guardarSesionDemo(sesionOriginal)
        }
    }
}
