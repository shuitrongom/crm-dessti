package com.dessti.crm.platform.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del value object {@link Permiso}: normalizacion, formato
 * textual {@code recurso:operacion} y validaciones (Req 3).
 */
class PermisoTest {

    @Test
    @DisplayName("Normaliza a minusculas y produce la authority recurso:operacion")
    void normalizaYFormatea() {
        Permiso permiso = Permiso.de("Cliente", "Crear");

        assertThat(permiso.recurso()).isEqualTo("cliente");
        assertThat(permiso.operacion()).isEqualTo("crear");
        assertThat(permiso.authority()).isEqualTo("cliente:crear");
        assertThat(permiso).hasToString("cliente:crear");
    }

    @Test
    @DisplayName("desdeAuthority parsea 'recurso:operacion'")
    void desdeAuthority() {
        Permiso permiso = Permiso.desdeAuthority("factura:timbrar");

        assertThat(permiso).isEqualTo(Permiso.de("factura", "timbrar"));
    }

    @Test
    @DisplayName("Rechaza componentes vacios o formato invalido")
    void rechazaInvalidos() {
        assertThatThrownBy(() -> Permiso.de("", "crear"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Permiso.de("cliente", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Permiso.desdeAuthority("cliente"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Permiso.desdeAuthority("a:b:c"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
