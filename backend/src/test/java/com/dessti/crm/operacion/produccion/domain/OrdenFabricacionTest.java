package com.dessti.crm.operacion.produccion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;

/**
 * Pruebas unitarias del dominio {@link OrdenFabricacion} (Req 7.4, 7.5, 7.6). No
 * arrancan Spring ni base de datos. Cubren la fabrica {@code generar} (estado
 * inicial {@code pendiente}, vinculacion a Cotizacion y Cliente) y la maquina de
 * estados de {@code cambiarEstado} (transiciones validas e invalidas).
 */
class OrdenFabricacionTest {

    private static final UUID COTIZACION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CLIENTE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    @DisplayName("generar crea la OF en estado pendiente vinculada a Cotizacion y Cliente (Req 7.4)")
    void generarEstablecePendiente() {
        OrdenFabricacion orden = OrdenFabricacion.generar(COTIZACION, CLIENTE, "produccion");

        assertThat(orden.getId()).isNotNull();
        assertThat(orden.getCotizacionId()).isEqualTo(COTIZACION);
        assertThat(orden.getClienteId()).isEqualTo(CLIENTE);
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenFabricacion.PENDIENTE);
        assertThat(orden.getCreatedBy()).isEqualTo("produccion");
        assertThat(orden.getUpdatedBy()).isEqualTo("produccion");
    }

    @Test
    @DisplayName("generar exige Cotizacion y Cliente (422)")
    void generarExigeDatos() {
        assertThatThrownBy(() -> OrdenFabricacion.generar(null, CLIENTE, "p"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> OrdenFabricacion.generar(COTIZACION, null, "p"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("cambiarEstado aplica pendiente -> en_produccion -> terminada (Req 7.5)")
    void cambioEstadoValido() {
        OrdenFabricacion orden = OrdenFabricacion.generar(COTIZACION, CLIENTE, "produccion");

        orden.cambiarEstado(EstadoOrdenFabricacion.EN_PRODUCCION, "produccion");
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenFabricacion.EN_PRODUCCION);

        orden.cambiarEstado(EstadoOrdenFabricacion.TERMINADA, "produccion");
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenFabricacion.TERMINADA);
    }

    @Test
    @DisplayName("cambiarEstado permite cancelar desde pendiente y desde en_produccion (Req 7.5)")
    void cancelarPermitido() {
        OrdenFabricacion desdePendiente = OrdenFabricacion.generar(COTIZACION, CLIENTE, "p");
        desdePendiente.cambiarEstado(EstadoOrdenFabricacion.CANCELADA, "p");
        assertThat(desdePendiente.getEstado()).isEqualTo(EstadoOrdenFabricacion.CANCELADA);

        OrdenFabricacion desdeProduccion = OrdenFabricacion.generar(COTIZACION, CLIENTE, "p");
        desdeProduccion.cambiarEstado(EstadoOrdenFabricacion.EN_PRODUCCION, "p");
        desdeProduccion.cambiarEstado(EstadoOrdenFabricacion.CANCELADA, "p");
        assertThat(desdeProduccion.getEstado()).isEqualTo(EstadoOrdenFabricacion.CANCELADA);
    }

    @Test
    @DisplayName("cambiarEstado rechaza una transicion invalida y conserva el estado (Req 7.6)")
    void cambioEstadoInvalidoConservaEstado() {
        OrdenFabricacion orden = OrdenFabricacion.generar(COTIZACION, CLIENTE, "p");

        assertThatThrownBy(() -> orden.cambiarEstado(EstadoOrdenFabricacion.TERMINADA, "p"))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenFabricacion.PENDIENTE);
    }

    @Test
    @DisplayName("cambiarEstado desde un estado final se rechaza con 409 (Req 7.6)")
    void cambioDesdeFinalRechazado() {
        OrdenFabricacion orden = OrdenFabricacion.generar(COTIZACION, CLIENTE, "p");
        orden.cambiarEstado(EstadoOrdenFabricacion.CANCELADA, "p");

        assertThatThrownBy(() -> orden.cambiarEstado(EstadoOrdenFabricacion.EN_PRODUCCION, "p"))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenFabricacion.CANCELADA);
    }

    @Test
    @DisplayName("cambiarEstado exige el estado destino (422)")
    void cambioEstadoNuloRechazado() {
        OrdenFabricacion orden = OrdenFabricacion.generar(COTIZACION, CLIENTE, "p");
        assertThatThrownBy(() -> orden.cambiarEstado(null, "p"))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
