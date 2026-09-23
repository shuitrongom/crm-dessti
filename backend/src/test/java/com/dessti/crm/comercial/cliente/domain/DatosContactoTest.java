package com.dessti.crm.comercial.cliente.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias de casos limite de {@link DatosContacto} (Req 5.1, 5.2).
 *
 * <p>Ejercen los limites exactos de cada regla de validacion/normalizacion sin
 * mocks ni contexto de Spring: nombre (1..200), RFC (12..13 con formato y
 * mayusculas), email opcional (formato y &le;320), telefono opcional (10..15
 * digitos) y la exigencia de al menos un dato de contacto. Complementan la
 * cobertura de {@code ServicioClientesTest} centrandose en las fronteras de
 * datos obligatorios y RFC invalido.</p>
 */
class DatosContactoTest {

    @Nested
    @DisplayName("normalizarNombre (Req 5.1, 5.2)")
    class Nombre {

        @Test
        @DisplayName("rechaza nombre nulo con 422")
        void nombreNulo() {
            assertThatThrownBy(() -> DatosContacto.normalizarNombre(null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("nombre");
        }

        @Test
        @DisplayName("rechaza nombre en blanco con 422")
        void nombreEnBlanco() {
            assertThatThrownBy(() -> DatosContacto.normalizarNombre("   "))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("nombre");
        }

        @Test
        @DisplayName("acepta el limite inferior de 1 caracter y recorta espacios")
        void nombreLongitud1() {
            assertThat(DatosContacto.normalizarNombre("  A  ")).isEqualTo("A");
        }

        @Test
        @DisplayName("acepta exactamente 200 caracteres")
        void nombreLongitud200() {
            String nombre = "N".repeat(200);
            assertThat(DatosContacto.normalizarNombre(nombre)).isEqualTo(nombre);
        }

        @Test
        @DisplayName("rechaza 201 caracteres con 422")
        void nombreLongitud201() {
            String nombre = "N".repeat(201);
            assertThatThrownBy(() -> DatosContacto.normalizarNombre(nombre))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("200");
        }
    }

    @Nested
    @DisplayName("normalizarRfc (Req 5.1, 5.2)")
    class Rfc {

        @Test
        @DisplayName("rechaza RFC nulo con 422")
        void rfcNulo() {
            assertThatThrownBy(() -> DatosContacto.normalizarRfc(null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("RFC");
        }

        @Test
        @DisplayName("rechaza RFC en blanco con 422")
        void rfcEnBlanco() {
            assertThatThrownBy(() -> DatosContacto.normalizarRfc("  "))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("RFC");
        }

        @Test
        @DisplayName("rechaza longitud 11 (por debajo del minimo) con 422")
        void rfcLongitud11() {
            // 11 caracteres: por debajo del minimo de 12.
            assertThatThrownBy(() -> DatosContacto.normalizarRfc("ABC1201011A"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("12");
        }

        @Test
        @DisplayName("acepta RFC de 12 caracteres (persona moral)")
        void rfcMoral12() {
            assertThat(DatosContacto.normalizarRfc("ABC120101AB1")).isEqualTo("ABC120101AB1");
        }

        @Test
        @DisplayName("acepta RFC de 13 caracteres (persona fisica)")
        void rfcFisica13() {
            assertThat(DatosContacto.normalizarRfc("ABCD120101AB1")).isEqualTo("ABCD120101AB1");
        }

        @Test
        @DisplayName("rechaza longitud 14 (por encima del maximo) con 422")
        void rfcLongitud14() {
            assertThatThrownBy(() -> DatosContacto.normalizarRfc("ABCDE120101AB1"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("13");
        }

        @Test
        @DisplayName("rechaza formato invalido (longitud correcta pero patron no coincide)")
        void rfcFormatoInvalido() {
            // 12 caracteres pero empieza con digitos: no cumple el patron del RFC.
            assertThatThrownBy(() -> DatosContacto.normalizarRfc("1234567890AB"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("formato");
        }

        @Test
        @DisplayName("normaliza a mayusculas un RFC valido en minusculas")
        void rfcSeNormalizaAMayusculas() {
            assertThat(DatosContacto.normalizarRfc("abc120101ab1")).isEqualTo("ABC120101AB1");
        }
    }

    @Nested
    @DisplayName("normalizarEmail (opcional, Req 5.1)")
    class Email {

        @Test
        @DisplayName("email nulo se interpreta como ausente y devuelve null")
        void emailNulo() {
            assertThat(DatosContacto.normalizarEmail(null)).isNull();
        }

        @Test
        @DisplayName("email en blanco se interpreta como ausente y devuelve null")
        void emailEnBlanco() {
            assertThat(DatosContacto.normalizarEmail("   ")).isNull();
        }

        @Test
        @DisplayName("email valido se recorta y devuelve normalizado")
        void emailValido() {
            assertThat(DatosContacto.normalizarEmail("  user@example.com  "))
                    .isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("rechaza email sin arroba con 422")
        void emailSinArroba() {
            assertThatThrownBy(() -> DatosContacto.normalizarEmail("abc"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("correo");
        }

        @Test
        @DisplayName("rechaza email sin dominio con punto con 422")
        void emailSinPuntoDominio() {
            assertThatThrownBy(() -> DatosContacto.normalizarEmail("a@b"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("correo");
        }

        @Test
        @DisplayName("rechaza email con doble arroba con 422")
        void emailDobleArroba() {
            assertThatThrownBy(() -> DatosContacto.normalizarEmail("a@b@c.com"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("correo");
        }

        @Test
        @DisplayName("rechaza email que excede 320 caracteres con 422")
        void emailDemasiadoLargo() {
            // Parte local de 316 chars + "@x.io" (5) = 321 caracteres, con formato valido.
            String largo = "a".repeat(316) + "@x.io";
            assertThat(largo.length()).isEqualTo(321);
            assertThatThrownBy(() -> DatosContacto.normalizarEmail(largo))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("320");
        }
    }

    @Nested
    @DisplayName("normalizarTelefono (opcional, Req 5.1)")
    class Telefono {

        @Test
        @DisplayName("telefono nulo se interpreta como ausente y devuelve null")
        void telefonoNulo() {
            assertThat(DatosContacto.normalizarTelefono(null)).isNull();
        }

        @Test
        @DisplayName("telefono en blanco se interpreta como ausente y devuelve null")
        void telefonoEnBlanco() {
            assertThat(DatosContacto.normalizarTelefono("  ")).isNull();
        }

        @Test
        @DisplayName("rechaza 9 digitos (por debajo del minimo) con 422")
        void telefono9Digitos() {
            assertThatThrownBy(() -> DatosContacto.normalizarTelefono("123456789"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("telefono");
        }

        @Test
        @DisplayName("acepta exactamente 10 digitos")
        void telefono10Digitos() {
            assertThat(DatosContacto.normalizarTelefono("1234567890")).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("acepta exactamente 15 digitos")
        void telefono15Digitos() {
            assertThat(DatosContacto.normalizarTelefono("123456789012345"))
                    .isEqualTo("123456789012345");
        }

        @Test
        @DisplayName("rechaza 16 digitos (por encima del maximo) con 422")
        void telefono16Digitos() {
            assertThatThrownBy(() -> DatosContacto.normalizarTelefono("1234567890123456"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("telefono");
        }

        @Test
        @DisplayName("rechaza telefono con caracteres no numericos con 422")
        void telefonoNoNumerico() {
            assertThatThrownBy(() -> DatosContacto.normalizarTelefono("12345abc90"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("telefono");
        }
    }

    @Nested
    @DisplayName("exigirAlMenosUnContacto (Req 5.1)")
    class AlMenosUnContacto {

        @Test
        @DisplayName("rechaza cuando email y telefono son ambos null con 422")
        void ambosNulos() {
            assertThatThrownBy(() -> DatosContacto.exigirAlMenosUnContacto(null, null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("contacto");
        }

        @Test
        @DisplayName("acepta cuando solo hay email")
        void soloEmail() {
            DatosContacto.exigirAlMenosUnContacto("user@example.com", null);
        }

        @Test
        @DisplayName("acepta cuando solo hay telefono")
        void soloTelefono() {
            DatosContacto.exigirAlMenosUnContacto(null, "1234567890");
        }
    }
}
