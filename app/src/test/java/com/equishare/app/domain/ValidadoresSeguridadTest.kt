package com.equishare.app.domain

import com.equishare.app.domain.validacion.FuerzaPassword
import com.equishare.app.domain.validacion.Validadores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas de seguridad del registro: fuerza de la contraseña y correos mal escritos. */
class ValidadoresSeguridadTest {

    @Test
    fun fuerzaDeLaContrasena_vaDeDebilAFuerte() {
        assertEquals(FuerzaPassword.VACIA, Validadores.fuerzaPassword(""))
        assertEquals(FuerzaPassword.DEBIL, Validadores.fuerzaPassword("abc12"))
        assertEquals(FuerzaPassword.DEBIL, Validadores.fuerzaPassword("solotexto"))
        assertEquals(FuerzaPassword.MEDIA, Validadores.fuerzaPassword("clave2026"))
        assertEquals(FuerzaPassword.MEDIA, Validadores.fuerzaPassword("Clave2026"))
        assertEquals(FuerzaPassword.FUERTE, Validadores.fuerzaPassword("Clave2026!"))
        assertEquals(FuerzaPassword.FUERTE, Validadores.fuerzaPassword("equishare2026trujillo!"))
    }

    @Test
    fun correoConDominioMalEscrito_sugiereElCorrecto() {
        assertEquals(
            "alexan.terronesmiranda9@gmail.com",
            Validadores.sugerenciaCorreo("alexan.terronesmiranda9@gmai.com"),
        )
        assertEquals("ventas@gmail.com", Validadores.sugerenciaCorreo(" Ventas@GMIAL.com "))
        assertEquals("ventas@hotmail.com", Validadores.sugerenciaCorreo("ventas@hotmial.com"))
        assertEquals("ventas@outlook.com", Validadores.sugerenciaCorreo("ventas@outlok.com"))
        assertEquals("ventas@gmail.com", Validadores.sugerenciaCorreo("ventas@gmail.con"))
    }

    @Test
    fun correoCorrectoODeEmpresa_noSugiereNada() {
        assertNull(Validadores.sugerenciaCorreo("alexan.terronesmiranda9@gmail.com"))
        assertNull(Validadores.sugerenciaCorreo("ventas@constructora.com.pe"))
        assertNull(Validadores.sugerenciaCorreo("eventos@demo.pe"))
        assertNull(Validadores.sugerenciaCorreo("sin-arroba"))
        assertNull(Validadores.sugerenciaCorreo("@gmai.com"))
        assertNull(Validadores.sugerenciaCorreo("ventas@"))
    }
}
