package com.dessti.crm.comercial.cliente.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias del dominio de {@link Cliente} (Req 5.1, 5.2, 5.9). No
 * requieren contexto de Spring ni base de datos: validan las reglas de creacion,
 * actualizacion y borrado logico directamente sobre la entidad.
 */
class ClienteTest {

    private static final String ACTOR = "ventas@acme.com";

    @Test
    @DisplayName("crear normaliza el RFC a mayusculas y deja el Cliente activo (Req 5.1)")
    void crearNormalizaYActiva() {
        Cliente cliente = Cliente.crear("Acme S.A.", "abc120101ab1", "contacto@acme.com", null, ACTOR);

        assertThat(cliente.getNombre()).isEqualTo("Acme S.A.");
        assertThat(cliente.getRfc()).isEqualTo("ABC120101AB1");
        assertThat(cliente.getEmail()).isEqualTo("contacto@acme.com");
        assertThat(cliente.getTelefono()).isNull();
        assertThat(cliente.estaActivo()).isTrue();
        assertThat(cliente.getCreatedBy()).isEqualTo(ACTOR);
    }

    @Test
    @DisplayName("crear acepta un Cliente con solo telefono como dato de contacto (Req 5.1)")
    void crearConSoloTelefono() {
        Cliente cliente = Cliente.crear("Acme", "ABC120101AB1", null, "5512345678", ACTOR);

        assertThat(cliente.getTelefono()).isEqualTo("5512345678");
        assertThat(cliente.getEmail()).isNull();
    }

    @Test
    @DisplayName("crear rechaza el nombre vacio (Req 5.2)")
    void crearRechazaNombreVacio() {
        assertThatThrownBy(() -> Cliente.crear("   ", "ABC120101AB1", "a@b.com", null, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear rechaza el RFC con formato invalido (Req 5.2)")
    void crearRechazaRfcInvalido() {
        assertThatThrownBy(() -> Cliente.crear("Acme", "123", "a@b.com", null, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear rechaza el email con formato invalido (Req 5.2)")
    void crearRechazaEmailInvalido() {
        assertThatThrownBy(() -> Cliente.crear("Acme", "ABC120101AB1", "no-es-email", null, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear rechaza el telefono con menos de 10 digitos (Req 5.2)")
    void crearRechazaTelefonoCorto() {
        assertThatThrownBy(() -> Cliente.crear("Acme", "ABC120101AB1", null, "123", ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear rechaza el Cliente sin ningun dato de contacto (Req 5.1)")
    void crearRechazaSinContacto() {
        assertThatThrownBy(() -> Cliente.crear("Acme", "ABC120101AB1", null, null, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("actualizar revalida y aplica los nuevos datos (Req 5.4)")
    void actualizaDatos() {
        Cliente cliente = Cliente.crear("Acme", "ABC120101AB1", "a@b.com", null, ACTOR);

        cliente.actualizar("Acme Renombrada", "XYZ990909QQ9", null, "5512345678", "otro@acme.com");

        assertThat(cliente.getNombre()).isEqualTo("Acme Renombrada");
        assertThat(cliente.getRfc()).isEqualTo("XYZ990909QQ9");
        assertThat(cliente.getEmail()).isNull();
        assertThat(cliente.getTelefono()).isEqualTo("5512345678");
        assertThat(cliente.getUpdatedBy()).isEqualTo("otro@acme.com");
    }

    @Test
    @DisplayName("desactivar realiza el borrado logico conservando los datos (Req 5.9)")
    void desactivaLogicamente() {
        Cliente cliente = Cliente.crear("Acme", "ABC120101AB1", "a@b.com", null, ACTOR);

        cliente.desactivar("admin@acme.com");

        assertThat(cliente.estaActivo()).isFalse();
        // Los datos historicos se conservan.
        assertThat(cliente.getNombre()).isEqualTo("Acme");
        assertThat(cliente.getRfc()).isEqualTo("ABC120101AB1");
        assertThat(cliente.getUpdatedBy()).isEqualTo("admin@acme.com");
    }
}
