package com.dessti.crm.comercial.cliente.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias del dominio de {@link Contacto} (Req 5.5, 5.6): la
 * asociacion solo procede sobre un Cliente activo.
 */
class ContactoTest {

    private static final String ACTOR = "ventas@acme.com";

    private Cliente clienteActivo() {
        return Cliente.crear("Acme", "ABC120101AB1", "a@b.com", null, ACTOR);
    }

    @Test
    @DisplayName("paraCliente crea el Contacto asociado a un Cliente activo (Req 5.5)")
    void asociaAClienteActivo() {
        Cliente cliente = clienteActivo();

        Contacto contacto = Contacto.paraCliente(cliente, "Juan Perez", "juan@acme.com", "5512345678", ACTOR);

        assertThat(contacto.getClienteId()).isEqualTo(cliente.getId());
        assertThat(contacto.getNombre()).isEqualTo("Juan Perez");
        assertThat(contacto.getEmail()).isEqualTo("juan@acme.com");
        assertThat(contacto.getTelefono()).isEqualTo("5512345678");
        assertThat(contacto.isActivo()).isTrue();
    }

    @Test
    @DisplayName("paraCliente permite un Contacto sin email ni telefono (solo nombre)")
    void permiteContactoSoloNombre() {
        Contacto contacto = Contacto.paraCliente(clienteActivo(), "Juan Perez", null, null, ACTOR);

        assertThat(contacto.getEmail()).isNull();
        assertThat(contacto.getTelefono()).isNull();
    }

    @Test
    @DisplayName("paraCliente rechaza la asociacion a un Cliente inactivo (Req 5.6 -> 422)")
    void rechazaClienteInactivo() {
        Cliente cliente = clienteActivo();
        cliente.desactivar(ACTOR);

        assertThatThrownBy(() -> Contacto.paraCliente(cliente, "Juan", "j@a.com", null, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("paraCliente rechaza un nombre invalido (Req 5.2)")
    void rechazaNombreInvalido() {
        assertThatThrownBy(() -> Contacto.paraCliente(clienteActivo(), "  ", "j@a.com", null, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
