package com.dessti.crm.comercial.canalventa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias del dominio de {@link CanalVenta} (Req 63.1). No requieren
 * contexto de Spring ni base de datos: validan las reglas de creacion,
 * actualizacion y borrado logico directamente sobre la entidad.
 */
class CanalVentaTest {

    private static final String ACTOR = "ventas@acme.com";

    @Test
    @DisplayName("crear normaliza el nombre, acepta descripcion opcional y deja el canal activo (Req 63.1)")
    void crearNormalizaYActiva() {
        CanalVenta canal = CanalVenta.crear("  Directo  ", "  Venta directa  ", ACTOR);

        assertThat(canal.getNombre()).isEqualTo("Directo");
        assertThat(canal.getDescripcion()).isEqualTo("Venta directa");
        assertThat(canal.estaActivo()).isTrue();
        assertThat(canal.getId()).isNotNull();
        assertThat(canal.getCreatedBy()).isEqualTo(ACTOR);
    }

    @Test
    @DisplayName("crear acepta un canal sin descripcion (opcional -> null) (Req 63.1)")
    void crearSinDescripcion() {
        CanalVenta canal = CanalVenta.crear("En linea", "   ", ACTOR);

        assertThat(canal.getNombre()).isEqualTo("En linea");
        assertThat(canal.getDescripcion()).isNull();
    }

    @Test
    @DisplayName("crear rechaza el nombre vacio (Req 63.1)")
    void crearRechazaNombreVacio() {
        assertThatThrownBy(() -> CanalVenta.crear("   ", "desc", ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear rechaza el nombre que excede 100 caracteres (Req 63.1)")
    void crearRechazaNombreLargo() {
        String largo = "x".repeat(101);
        assertThatThrownBy(() -> CanalVenta.crear(largo, null, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear rechaza la descripcion que excede 500 caracteres (Req 63.1)")
    void crearRechazaDescripcionLarga() {
        String largo = "x".repeat(501);
        assertThatThrownBy(() -> CanalVenta.crear("Referido", largo, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("actualizar revalida y aplica los nuevos datos (Req 63.1)")
    void actualizaDatos() {
        CanalVenta canal = CanalVenta.crear("Directo", "desc", ACTOR);

        canal.actualizar("Referido", "Por recomendacion", "otro@acme.com");

        assertThat(canal.getNombre()).isEqualTo("Referido");
        assertThat(canal.getDescripcion()).isEqualTo("Por recomendacion");
        assertThat(canal.getUpdatedBy()).isEqualTo("otro@acme.com");
    }

    @Test
    @DisplayName("desactivar realiza el borrado logico conservando los datos (Req 63.1)")
    void desactivaLogicamente() {
        CanalVenta canal = CanalVenta.crear("Directo", "desc", ACTOR);

        canal.desactivar("admin@acme.com");

        assertThat(canal.estaActivo()).isFalse();
        assertThat(canal.getNombre()).isEqualTo("Directo");
        assertThat(canal.getUpdatedBy()).isEqualTo("admin@acme.com");
    }
}
