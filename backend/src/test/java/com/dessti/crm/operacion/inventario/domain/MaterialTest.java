package com.dessti.crm.operacion.inventario.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias del dominio puro {@link Material} (Req 18.1-18.3, 18.5; Property 9).
 * No arrancan Spring ni base de datos: verifican la fabrica de alta y el motor de
 * inventario {@link Material#aplicarMovimiento} como funcion pura y determinista.
 */
class MaterialTest {

    private static final String ACTOR = "almacen";

    private static Material materialConExistencias(BigDecimal existenciasIniciales,
                                                   BigDecimal stockMinimo) {
        Material material = Material.crear("Perfil de aluminio", "metro", stockMinimo, ACTOR);
        if (existenciasIniciales.signum() > 0) {
            material.aplicarMovimiento(TipoMovimientoInventario.ENTRADA, existenciasIniciales, ACTOR);
        }
        return material;
    }

    @Test
    @DisplayName("crear establece existencias iniciales en 0 y deja el Material activo (Req 18.1)")
    void crearExistenciasCero() {
        Material material = Material.crear("Tubo LED", "pieza", new BigDecimal("5"), ACTOR);

        assertThat(material.getExistencias()).isEqualByComparingTo("0");
        assertThat(material.getNombre()).isEqualTo("Tubo LED");
        assertThat(material.getUnidadMedida()).isEqualTo("pieza");
        assertThat(material.getStockMinimo()).isEqualByComparingTo("5");
        assertThat(material.isActivo()).isTrue();
        assertThat(material.getId()).isNotNull();
    }

    @Test
    @DisplayName("crear rechaza nombre vacio, unidad vacia y stock minimo negativo (Req 18.1, 422)")
    void crearRechazaDatosInvalidos() {
        assertThatThrownBy(() -> Material.crear("  ", "pieza", BigDecimal.ZERO, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Material.crear("Cable", "  ", BigDecimal.ZERO, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Material.crear("Cable", "metro", new BigDecimal("-1"), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("entrada incrementa las existencias por la cantidad (Req 18.2)")
    void entradaIncrementa() {
        Material material = Material.crear("Acrilico", "m2", BigDecimal.ZERO, ACTOR);

        ResultadoMovimiento resultado =
                material.aplicarMovimiento(TipoMovimientoInventario.ENTRADA, new BigDecimal("10.5"), ACTOR);

        assertThat(material.getExistencias()).isEqualByComparingTo("10.5");
        assertThat(resultado.existenciasResultantes()).isEqualByComparingTo("10.5");
    }

    @Test
    @DisplayName("salida decrementa las existencias por la cantidad (Req 18.2)")
    void salidaDecrementa() {
        Material material = materialConExistencias(new BigDecimal("10"), BigDecimal.ZERO);

        material.aplicarMovimiento(TipoMovimientoInventario.SALIDA, new BigDecimal("4"), ACTOR);

        assertThat(material.getExistencias()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("salida que dejaria existencias < 0 se rechaza (422 'existencias insuficientes') "
            + "y conserva las existencias (Req 18.3, Property 9)")
    void salidaInsuficienteRechazadaConservaExistencias() {
        Material material = materialConExistencias(new BigDecimal("3"), BigDecimal.ZERO);

        assertThatThrownBy(() ->
                material.aplicarMovimiento(TipoMovimientoInventario.SALIDA, new BigDecimal("5"), ACTOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("existencias insuficientes");

        // Property 9: las existencias no cambian tras el rechazo.
        assertThat(material.getExistencias()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("salida que deja las existencias exactamente en 0 se permite (borde, Property 9)")
    void salidaHastaCeroPermitida() {
        Material material = materialConExistencias(new BigDecimal("3"), BigDecimal.ZERO);

        ResultadoMovimiento resultado =
                material.aplicarMovimiento(TipoMovimientoInventario.SALIDA, new BigDecimal("3"), ACTOR);

        assertThat(resultado.existenciasResultantes()).isEqualByComparingTo("0");
        assertThat(material.getExistencias()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ajuste positivo suma y ajuste negativo resta las existencias (Req 18.2)")
    void ajustePositivoYNegativo() {
        Material material = materialConExistencias(new BigDecimal("10"), BigDecimal.ZERO);

        material.aplicarMovimiento(TipoMovimientoInventario.AJUSTE, new BigDecimal("2"), ACTOR);
        assertThat(material.getExistencias()).isEqualByComparingTo("12");

        material.aplicarMovimiento(TipoMovimientoInventario.AJUSTE, new BigDecimal("-5"), ACTOR);
        assertThat(material.getExistencias()).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("ajuste negativo que dejaria existencias < 0 se rechaza y conserva (Property 9)")
    void ajusteNegativoInsuficienteRechazado() {
        Material material = materialConExistencias(new BigDecimal("2"), BigDecimal.ZERO);

        assertThatThrownBy(() ->
                material.aplicarMovimiento(TipoMovimientoInventario.AJUSTE, new BigDecimal("-5"), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(material.getExistencias()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("el resultado marca stock bajo cuando las existencias quedan por debajo del minimo (Req 18.5)")
    void detectaStockBajo() {
        Material material = materialConExistencias(new BigDecimal("10"), new BigDecimal("8"));

        // 10 - 3 = 7 < 8 -> stock bajo.
        ResultadoMovimiento resultado =
                material.aplicarMovimiento(TipoMovimientoInventario.SALIDA, new BigDecimal("3"), ACTOR);

        assertThat(resultado.stockBajo()).isTrue();
        assertThat(material.estaEnStockBajo()).isTrue();
    }

    @Test
    @DisplayName("el resultado no marca stock bajo cuando las existencias quedan en/por encima del minimo (Req 18.5)")
    void noMarcaStockBajoCuandoSuficiente() {
        Material material = materialConExistencias(new BigDecimal("10"), new BigDecimal("5"));

        ResultadoMovimiento resultado =
                material.aplicarMovimiento(TipoMovimientoInventario.SALIDA, new BigDecimal("2"), ACTOR);

        assertThat(resultado.stockBajo()).isFalse();
        assertThat(material.estaEnStockBajo()).isFalse();
    }

    @Test
    @DisplayName("entrada/salida con cantidad no positiva se rechaza (422)")
    void cantidadNoPositivaRechazada() {
        Material material = Material.crear("Vinil", "m2", BigDecimal.ZERO, ACTOR);

        assertThatThrownBy(() ->
                material.aplicarMovimiento(TipoMovimientoInventario.ENTRADA, BigDecimal.ZERO, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() ->
                material.aplicarMovimiento(TipoMovimientoInventario.SALIDA, new BigDecimal("-1"), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("desactivar realiza la baja logica del Material (Req 18, 3.1)")
    void desactivarBajaLogica() {
        Material material = Material.crear("Soldadura", "kg", BigDecimal.ZERO, ACTOR);

        material.desactivar(ACTOR);

        assertThat(material.isActivo()).isFalse();
    }
}
