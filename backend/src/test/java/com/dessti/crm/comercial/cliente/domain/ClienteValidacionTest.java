package com.dessti.crm.comercial.cliente.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias de las reglas de dominio de {@link Cliente} (Req 5.1, 5.2,
 * 5.4, 5.9), centradas en la exigencia de al menos un dato de contacto y en la
 * revalidacion al actualizar. No usan mocks ni contexto de Spring.
 */
class ClienteValidacionTest {

    private static final String RFC = "ABC120101AB1";
    private static final String ACTOR = "ventas";

    @Test
    @DisplayName("crear rechaza cuando no hay ningun dato de contacto (email y telefono nulos) con 422")
    void crearSinContactoRechazado() {
        assertThatThrownBy(() -> Cliente.crear("Acme S.A.", RFC, null, null, ACTOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("contacto");
    }

    @Test
    @DisplayName("crear acepta un Cliente activo con solo email")
    void crearSoloEmail() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "acme@example.com", null, ACTOR);

        assertThat(cliente.estaActivo()).isTrue();
        assertThat(cliente.getEmail()).isEqualTo("acme@example.com");
        assertThat(cliente.getTelefono()).isNull();
        assertThat(cliente.getRfc()).isEqualTo(RFC);
        assertThat(cliente.getId()).isNotNull();
    }

    @Test
    @DisplayName("crear acepta un Cliente activo con solo telefono")
    void crearSoloTelefono() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, null, "1234567890", ACTOR);

        assertThat(cliente.estaActivo()).isTrue();
        assertThat(cliente.getTelefono()).isEqualTo("1234567890");
        assertThat(cliente.getEmail()).isNull();
    }

    @Test
    @DisplayName("crear normaliza el RFC a mayusculas")
    void crearNormalizaRfc() {
        Cliente cliente = Cliente.crear("Acme S.A.", "abc120101ab1", "acme@example.com", null, ACTOR);

        assertThat(cliente.getRfc()).isEqualTo(RFC);
    }

    @Test
    @DisplayName("actualizar revalida y rechaza cuando quedaria sin ningun dato de contacto con 422")
    void actualizarRevalidaContacto() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "acme@example.com", null, ACTOR);

        assertThatThrownBy(() -> cliente.actualizar("Acme S.A.", RFC, null, null, ACTOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("contacto");
    }

    @Test
    @DisplayName("actualizar revalida y rechaza un RFC con formato invalido con 422")
    void actualizarRevalidaRfc() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "acme@example.com", null, ACTOR);

        assertThatThrownBy(() -> cliente.actualizar("Acme S.A.", "1234567890AB", "acme@example.com", null, ACTOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("formato");
    }

    @Test
    @DisplayName("actualizar persiste los nuevos datos validos")
    void actualizarAplicaCambios() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "acme@example.com", null, ACTOR);

        cliente.actualizar("Acme Nueva S.A.", RFC, null, "1234567890", ACTOR);

        assertThat(cliente.getNombre()).isEqualTo("Acme Nueva S.A.");
        assertThat(cliente.getEmail()).isNull();
        assertThat(cliente.getTelefono()).isEqualTo("1234567890");
        assertThat(cliente.estaActivo()).isTrue();
    }

    @Test
    @DisplayName("desactivar marca el Cliente como inactivo conservando sus datos (Req 5.9)")
    void desactivarMarcaInactivo() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "acme@example.com", null, ACTOR);

        cliente.desactivar(ACTOR);

        assertThat(cliente.estaActivo()).isFalse();
        assertThat(cliente.getNombre()).isEqualTo("Acme S.A.");
        assertThat(cliente.getRfc()).isEqualTo(RFC);
    }
}
