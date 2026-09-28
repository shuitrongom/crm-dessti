package com.dessti.crm.operacion.proyecto.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la funcion pura {@link DerivacionEstadoMultisitio}, que
 * consolida el estado de un Proyecto multi-sitio de giro generico a partir de la
 * {@link FaseSitioGenerica} de cada Sitio (Req 3.2). Sin Spring ni JPA.
 */
class DerivacionEstadoMultisitioTest {

    @Test
    void sinSitiosEsSinSitios() {
        assertThat(DerivacionEstadoMultisitio.derivar(List.of()))
                .isEqualTo(EstadoConsolidadoMultisitio.SIN_SITIOS);
    }

    @Test
    void listaNulaLanzaNpe() {
        assertThatNullPointerException()
                .isThrownBy(() -> DerivacionEstadoMultisitio.derivar(null));
    }

    @Test
    void algunPendienteEsEnPreparacion() {
        List<FaseSitioGenerica> fases = List.of(
                FaseSitioGenerica.ENTREGADO,
                FaseSitioGenerica.PENDIENTE,
                FaseSitioGenerica.EN_INSTALACION);
        assertThat(DerivacionEstadoMultisitio.derivar(fases))
                .isEqualTo(EstadoConsolidadoMultisitio.EN_PREPARACION);
    }

    @Test
    void sinPendientesPeroAlgunEnPreparacionEsEnInstalacion() {
        List<FaseSitioGenerica> fases = List.of(
                FaseSitioGenerica.EN_PREPARACION,
                FaseSitioGenerica.ENTREGADO,
                FaseSitioGenerica.EN_INSTALACION);
        assertThat(DerivacionEstadoMultisitio.derivar(fases))
                .isEqualTo(EstadoConsolidadoMultisitio.EN_INSTALACION);
    }

    @Test
    void sinPreparacionPeroAlgunEnInstalacionEsEnEntrega() {
        List<FaseSitioGenerica> fases = List.of(
                FaseSitioGenerica.EN_INSTALACION,
                FaseSitioGenerica.ENTREGADO);
        assertThat(DerivacionEstadoMultisitio.derivar(fases))
                .isEqualTo(EstadoConsolidadoMultisitio.EN_ENTREGA);
    }

    @Test
    void todosEntregadosEsCompletado() {
        List<FaseSitioGenerica> fases = List.of(
                FaseSitioGenerica.ENTREGADO,
                FaseSitioGenerica.ENTREGADO);
        assertThat(DerivacionEstadoMultisitio.derivar(fases))
                .isEqualTo(EstadoConsolidadoMultisitio.COMPLETADO);
    }

    @Test
    void faseNulaSeTrataComoPendiente() {
        // Un Sitio sin fila de avance materializado se interpreta como PENDIENTE.
        List<FaseSitioGenerica> fases = Arrays.asList(FaseSitioGenerica.ENTREGADO, null);
        assertThat(DerivacionEstadoMultisitio.derivar(fases))
                .isEqualTo(EstadoConsolidadoMultisitio.EN_PREPARACION);
    }

    @Test
    void esDeterministaConLaMismaEntrada() {
        List<FaseSitioGenerica> fases = List.of(
                FaseSitioGenerica.EN_INSTALACION,
                FaseSitioGenerica.EN_PREPARACION);
        EstadoConsolidadoMultisitio primera = DerivacionEstadoMultisitio.derivar(fases);
        assertThat(DerivacionEstadoMultisitio.derivar(fases)).isEqualTo(primera);
    }
}
