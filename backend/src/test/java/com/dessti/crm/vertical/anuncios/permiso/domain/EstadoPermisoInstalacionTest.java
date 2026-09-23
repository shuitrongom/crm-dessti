package com.dessti.crm.vertical.anuncios.permiso.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la maquina de estados pura {@link EstadoPermisoInstalacion}
 * (Req 17.2, 17.3) y de las etiquetas ASCII persistidas ({@link
 * EstadoPermisoInstalacion#valorBd()}) y su tipo asociado
 * {@link TipoPermisoInstalacion}. No arrancan Spring ni base de datos.
 */
class EstadoPermisoInstalacionTest {

    @Test
    @DisplayName("solicitado admite las transiciones a aprobado y a rechazado (Req 17.2)")
    void transicionesValidasDesdeSolicitado() {
        assertThat(EstadoPermisoInstalacion.SOLICITADO
                .puedeTransicionarA(EstadoPermisoInstalacion.APROBADO)).isTrue();
        assertThat(EstadoPermisoInstalacion.SOLICITADO
                .puedeTransicionarA(EstadoPermisoInstalacion.RECHAZADO)).isTrue();
    }

    @Test
    @DisplayName("aprobado y rechazado son finales y no admiten transiciones (Req 17.2, 17.3)")
    void estadosFinales() {
        assertThat(EstadoPermisoInstalacion.APROBADO.esFinal()).isTrue();
        assertThat(EstadoPermisoInstalacion.RECHAZADO.esFinal()).isTrue();
        assertThat(EstadoPermisoInstalacion.SOLICITADO.esFinal()).isFalse();

        assertThat(EstadoPermisoInstalacion.APROBADO
                .puedeTransicionarA(EstadoPermisoInstalacion.RECHAZADO)).isFalse();
        assertThat(EstadoPermisoInstalacion.RECHAZADO
                .puedeTransicionarA(EstadoPermisoInstalacion.APROBADO)).isFalse();
        assertThat(EstadoPermisoInstalacion.APROBADO
                .puedeTransicionarA(EstadoPermisoInstalacion.APROBADO)).isFalse();
    }

    @Test
    @DisplayName("solicitado no puede transitar a si mismo (no declarado) (Req 17.2)")
    void sinAutotransicion() {
        assertThat(EstadoPermisoInstalacion.SOLICITADO
                .puedeTransicionarA(EstadoPermisoInstalacion.SOLICITADO)).isFalse();
    }

    @Test
    @DisplayName("valorBd/desdeValorBd son inversas y usan etiquetas ASCII del CHECK de V20")
    void etiquetasEstado() {
        assertThat(EstadoPermisoInstalacion.SOLICITADO.valorBd()).isEqualTo("solicitado");
        assertThat(EstadoPermisoInstalacion.APROBADO.valorBd()).isEqualTo("aprobado");
        assertThat(EstadoPermisoInstalacion.RECHAZADO.valorBd()).isEqualTo("rechazado");

        assertThat(EstadoPermisoInstalacion.desdeValorBd("APROBADO"))
                .isEqualTo(EstadoPermisoInstalacion.APROBADO);
        assertThat(EstadoPermisoInstalacion.desdeValorBd(" solicitado "))
                .isEqualTo(EstadoPermisoInstalacion.SOLICITADO);
    }

    @Test
    @DisplayName("desdeValorBd rechaza una etiqueta de estado desconocida")
    void estadoDesconocido() {
        assertThatThrownBy(() -> EstadoPermisoInstalacion.desdeValorBd("cancelado"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("valorBd/desdeValorBd del tipo usan etiquetas ASCII del CHECK de V20 (Req 17.1)")
    void etiquetasTipo() {
        assertThat(TipoPermisoInstalacion.MUNICIPAL.valorBd()).isEqualTo("municipal");
        assertThat(TipoPermisoInstalacion.ARRENDADOR.valorBd()).isEqualTo("arrendador");

        assertThat(TipoPermisoInstalacion.desdeValorBd("MUNICIPAL"))
                .isEqualTo(TipoPermisoInstalacion.MUNICIPAL);
        assertThatThrownBy(() -> TipoPermisoInstalacion.desdeValorBd("comercial"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
