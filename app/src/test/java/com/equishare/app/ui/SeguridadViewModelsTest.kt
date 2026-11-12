package com.equishare.app.ui

import com.equishare.app.domain.model.Empresa
import com.equishare.app.domain.model.ErrorApp
import com.equishare.app.domain.model.Sesion
import com.equishare.app.domain.model.TipoError
import com.equishare.app.domain.validacion.FuerzaPassword
import com.equishare.app.ui.auth.LoginViewModel
import com.equishare.app.ui.auth.VerificarCorreoViewModel
import com.equishare.app.ui.perfil.DialogoCuenta
import com.equishare.app.ui.perfil.PerfilViewModel
import com.equishare.app.ui.raiz.EstadoRaiz
import com.equishare.app.ui.raiz.RaizViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pruebas de la versión de seguridad: verificación de correo, registro, contraseña y eliminar la cuenta. */
@OptIn(ExperimentalCoroutinesApi::class)
class SeguridadViewModelsTest {
    private val despachador = UnconfinedTestDispatcher()
    private val sinVerificar = Sesion("uid-1", "ventas@empresa.pe", esDemo = false, correoVerificado = false)
    private val verificada = sinVerificar.copy(correoVerificado = true)
    private val empresa = Empresa("uid-1", "Constructora Norte S.A.C.", "20131312955", "949123456", "ventas@empresa.pe", true, 0.0, 0)

    @Before
    fun preparar() = Dispatchers.setMain(despachador)

    @After
    fun limpiar() = Dispatchers.resetMain()

    // ---------- Verificación del correo ----------

    @Test
    fun raiz_unaCuentaSinVerificarVaALaPantallaRevisaTuCorreo() = runTest(despachador) {
        val auth = FakeAuth(sesionInicial = sinVerificar)
        val vm = RaizViewModel(auth, FakeEmpresaRepository().apply { miEmpresaActual = empresa }, FakeFavoritos())
        assertEquals(EstadoRaiz.CorreoSinVerificar(sinVerificar), vm.estado.value)

        // Al verificar, la sesión cambia y la app sigue sola a la pantalla principal.
        auth.correoYaVerificado = true
        auth.revisarVerificacion()
        assertEquals(EstadoRaiz.Listo(verificada, empresa), vm.estado.value)
    }

    @Test
    fun verificar_siTodaviaNoAbrioElEnlace_loDice() = runTest(despachador) {
        val auth = FakeAuth(sesionInicial = sinVerificar)
        val vm = VerificarCorreoViewModel(auth)
        assertNull(vm.ui.value.mensaje) // la revisión automática al abrir no muestra errores
        assertEquals("ventas@empresa.pe", vm.ui.value.email)

        vm.revisar()
        assertEquals(VerificarCorreoViewModel.TODAVIA_NO, vm.ui.value.mensaje)
        assertTrue(vm.ui.value.esError)

        auth.correoYaVerificado = true
        vm.revisar()
        assertEquals(VerificarCorreoViewModel.VERIFICADO, vm.ui.value.mensaje)
        assertFalse(vm.ui.value.esError)
    }

    @Test
    fun verificar_reenviarEsperaUnMinutoEntreCorreos() = runTest(despachador) {
        val auth = FakeAuth(sesionInicial = sinVerificar)
        var ahora = 1_000_000L
        val vm = VerificarCorreoViewModel(auth) { ahora }

        vm.reenviar()
        assertEquals(VerificarCorreoViewModel.REENVIADO, vm.ui.value.mensaje)
        assertEquals(60, vm.segundosParaReenviar())

        ahora += 15_000
        vm.reenviar()
        assertEquals("Espera 45 s para pedir otro correo.", vm.ui.value.mensaje)
        assertEquals(1, auth.acciones.count { it == "enviarVerificacion" })

        ahora += 45_000
        assertEquals(0, vm.segundosParaReenviar())
        vm.reenviar()
        assertEquals(2, auth.acciones.count { it == "enviarVerificacion" })
    }

    // ---------- Registro e inicio de sesión ----------

    @Test
    fun registro_conCorreoMalEscritoNoCreaLaCuentaYOfreceCorregirlo() = runTest(despachador) {
        val auth = FakeAuth()
        val vm = LoginViewModel(auth)
        vm.alternarModo()
        vm.alCambiarEmail("alexan.terronesmiranda9@gmai.com")
        vm.alCambiarPassword("Clave2026!")
        vm.alCambiarConfirmacion("Clave2026!")
        vm.alCambiarPrivacidad(true)
        assertEquals("alexan.terronesmiranda9@gmail.com", vm.ui.value.sugerenciaCorreo)
        assertEquals(FuerzaPassword.FUERTE, vm.ui.value.fuerzaPassword)

        vm.enviar {}
        assertEquals(0, auth.llamadas)
        assertEquals("Revisa el correo. ¿Quisiste decir alexan.terronesmiranda9@gmail.com?", vm.ui.value.errorEmail)

        vm.usarSugerenciaCorreo()
        assertEquals("alexan.terronesmiranda9@gmail.com", vm.ui.value.email)
        vm.enviar {}
        assertEquals(1, auth.llamadas)
    }

    @Test
    fun olvideMiContrasena_validaElCorreoYSiempreRespondeIgual() = runTest(despachador) {
        val auth = FakeAuth()
        val vm = LoginViewModel(auth)
        vm.alCambiarEmail("ventas@empresa.pe")
        vm.abrirRecuperar()
        assertTrue(vm.ui.value.mostrarRecuperar)
        assertEquals("ventas@empresa.pe", vm.ui.value.emailRecuperar)

        vm.alCambiarEmailRecuperar("correo-malo")
        vm.enviarRecuperar()
        assertEquals("El correo no tiene un formato válido.", vm.ui.value.errorRecuperar)
        assertNull(auth.ultimoCorreoRecuperar)

        vm.alCambiarEmailRecuperar(" ventas@empresa.pe ")
        vm.enviarRecuperar()
        assertEquals("ventas@empresa.pe", auth.ultimoCorreoRecuperar)
        assertEquals(LoginViewModel.MENSAJE_RECUPERAR, vm.ui.value.mensajeRecuperar)

        vm.cerrarRecuperar()
        assertFalse(vm.ui.value.mostrarRecuperar)
    }

    // ---------- Perfil: mi cuenta ----------

    private fun perfil(
        auth: FakeAuth = FakeAuth(sesionInicial = verificada, esModoDemo = false),
        empresas: FakeEmpresaRepository = FakeEmpresaRepository().apply { miEmpresaActual = empresa },
        favoritos: FakeFavoritos = FakeFavoritos(),
        alLimpiar: () -> Unit = {},
    ) = PerfilViewModel(empresas, auth, favoritos) { alLimpiar() }

    @Test
    fun editarDatos_validaYGuardaSinCambiarElRuc() = runTest(despachador) {
        val empresas = FakeEmpresaRepository().apply { miEmpresaActual = empresa }
        val vm = perfil(empresas = empresas)
        vm.abrirDialogo(DialogoCuenta.EDITAR_DATOS)

        vm.guardarDatos("Constructora Norte S.A.C.", "12345")
        assertEquals("Ingresa un celular de 9 dígitos que empiece con 9.", vm.ui.value.errorDialogo)
        assertTrue(empresas.guardadas.isEmpty())

        vm.guardarDatos("  Constructora del Norte S.A.C. ", "987654321")
        assertEquals(listOf(Triple("20131312955", "Constructora del Norte S.A.C.", "987654321")), empresas.guardadas)
        assertNull(vm.ui.value.dialogo)
        assertEquals(PerfilViewModel.DATOS_ACTUALIZADOS, vm.ui.value.mensaje)
        assertEquals("Constructora del Norte S.A.C.", vm.ui.value.empresa?.razonSocial)
    }

    @Test
    fun cambiarContrasena_validaAntesDeLlamarYMuestraElErrorDelProveedor() = runTest(despachador) {
        val auth = FakeAuth(sesionInicial = verificada, esModoDemo = false)
        val vm = perfil(auth = auth)
        vm.abrirDialogo(DialogoCuenta.CAMBIAR_CONTRASENA)

        vm.cambiarContrasena("Clave2026", "Clave2026", "Clave2026")
        assertEquals("La contraseña nueva debe ser distinta de la actual.", vm.ui.value.errorDialogo)
        vm.cambiarContrasena("Clave2026", "Nueva2027!", "Otra2027!")
        assertEquals("Las contraseñas no coinciden.", vm.ui.value.errorDialogo)
        assertTrue(auth.acciones.isEmpty())

        auth.errores["cambiarContrasena"] = ErrorApp(TipoError.NO_AUTENTICADO, "La contraseña no es correcta.")
        vm.cambiarContrasena("Mala2026", "Nueva2027!", "Nueva2027!")
        assertEquals("La contraseña no es correcta.", vm.ui.value.errorDialogo)
        assertEquals(DialogoCuenta.CAMBIAR_CONTRASENA, vm.ui.value.dialogo)

        auth.errores.clear()
        vm.cambiarContrasena("Clave2026", "Nueva2027!", "Nueva2027!")
        assertNull(vm.ui.value.dialogo)
        assertEquals(PerfilViewModel.CONTRASENA_CAMBIADA, vm.ui.value.mensaje)
    }

    @Test
    fun eliminarCuenta_conReservasActivasSeDetieneSinBorrarNada() = runTest(despachador) {
        val auth = FakeAuth(sesionInicial = verificada, esModoDemo = false)
        val empresas = FakeEmpresaRepository().apply {
            miEmpresaActual = empresa
            errorEliminar = ErrorApp(TipoError.CONFLICTO, "Tienes 1 reserva(s) activa(s). Termínalas o cancélalas antes de eliminar tu cuenta.")
        }
        val favoritos = FakeFavoritos()
        var limpiado = false
        val vm = perfil(auth, empresas, favoritos) { limpiado = true }
        vm.abrirDialogo(DialogoCuenta.ELIMINAR_CUENTA)

        vm.eliminarCuenta("")
        assertEquals("Escribe tu contraseña para confirmar.", vm.ui.value.errorDialogo)

        vm.eliminarCuenta("Clave2026")
        assertEquals("Tienes 1 reserva(s) activa(s). Termínalas o cancélalas antes de eliminar tu cuenta.", vm.ui.value.errorDialogo)
        assertEquals(listOf("confirmarContrasena"), auth.acciones)
        assertFalse(favoritos.borrado)
        assertFalse(limpiado)
        assertEquals(verificada, auth.sesion.value)
    }

    @Test
    fun eliminarCuenta_conContrasenaIncorrectaNoTocaElServidor() = runTest(despachador) {
        val auth = FakeAuth(sesionInicial = verificada, esModoDemo = false).apply {
            errores["confirmarContrasena"] = ErrorApp(TipoError.NO_AUTENTICADO, "La contraseña no es correcta.")
        }
        val empresas = FakeEmpresaRepository().apply { miEmpresaActual = empresa }
        val vm = perfil(auth, empresas)
        vm.eliminarCuenta("Mala2026")
        assertEquals("La contraseña no es correcta.", vm.ui.value.errorDialogo)
        assertFalse(empresas.eliminada)
    }

    @Test
    fun eliminarCuenta_borraTodoEnOrdenYCierraLaSesion() = runTest(despachador) {
        val auth = FakeAuth(sesionInicial = verificada, esModoDemo = false)
        val empresas = FakeEmpresaRepository().apply { miEmpresaActual = empresa }
        val favoritos = FakeFavoritos()
        var limpiado = false
        val vm = perfil(auth, empresas, favoritos) { limpiado = true }
        vm.abrirDialogo(DialogoCuenta.ELIMINAR_CUENTA)

        vm.eliminarCuenta("Clave2026")

        assertEquals(listOf("confirmarContrasena", "eliminarUsuario"), auth.acciones)
        assertTrue(empresas.eliminada)
        assertTrue(favoritos.borrado)
        assertTrue(limpiado)
        assertNull(auth.sesion.value)
        assertNull(vm.ui.value.dialogo)
    }
}
