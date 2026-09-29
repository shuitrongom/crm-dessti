package com.dessti.crm.operacion.inventario.avanzado.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias del dominio puro {@link Lote} (Req 60). No arrancan Spring ni base de
 * datos: verifican la fabrica de alta y la regla de caducidad {@link Lote#estaCaducado}
 * como funcion pura y determinista frente a una fecha de referencia fija.
 */
class LoteTest {

    private static final String ACTOR = "almacen";
    private static final UUID MATERIAL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate HOY = LocalDate.of(2026, 3, 1);

    @Test
    @DisplayName("crear normaliza el codigo y conserva la caducidad opcional (Req 60)")
    void crearConserva() {
        Lote lote = Lote.crear(MATERIAL_ID, "  L-001  ", LocalDate.of(2026, 6, 30), ACTOR);

        assertThat(lote.getMaterialId()).isEqualTo(MATERIAL_ID);
        assertThat(lote.getCodigo()).isEqualTo("L-001");
        assertThat(lote.getFechaCaducidad()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(lote.getId()).isNotNull();
    }

    @Test
    @DisplayName("un Lote SIN fecha de caducidad nunca esta caducado (Req 60)")
    void sinCaducidadNuncaCaduca() {
        Lote lote = Lote.crear(MATERIAL_ID, "L-001", null, ACTOR);

        assertThat(lote.estaCaducado(HOY)).isFalse();
    }

    @Test
    @DisplayName("un Lote con caducidad ANTERIOR a hoy esta caducado (Req 60)")
    void caducidadAnteriorCaduca() {
        Lote lote = Lote.crear(MATERIAL_ID, "L-001", HOY.minusDays(1), ACTOR);

        assertThat(lote.estaCaducado(HOY)).isTrue();
    }

    @Test
    @DisplayName("el dia de caducidad AUN es utilizable (frontera inclusiva, Req 60)")
    void diaDeCaducidadNoCaduca() {
        Lote lote = Lote.crear(MATERIAL_ID, "L-001", HOY, ACTOR);

        assertThat(lote.estaCaducado(HOY)).isFalse();
    }

    @Test
    @DisplayName("un Lote con caducidad FUTURA no esta caducado (Req 60)")
    void caducidadFuturaNoCaduca() {
        Lote lote = Lote.crear(MATERIAL_ID, "L-001", HOY.plusDays(30), ACTOR);

        assertThat(lote.estaCaducado(HOY)).isFalse();
    }

    @Test
    @DisplayName("estaCaducado rechaza una fecha de referencia nula (Req 60, 422)")
    void rechazaHoyNulo() {
        Lote lote = Lote.crear(MATERIAL_ID, "L-001", HOY, ACTOR);

        assertThatThrownBy(() -> lote.estaCaducado(null))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
