package com.dessti.crm.tesoreria.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas del dominio puro de {@link TransferenciaBancaria}: las invariantes de la
 * fabrica {@code registrar} (cuentas distintas, monto estrictamente positivo, fecha
 * obligatoria) y la normalizacion de importes/concepto.
 */
class TransferenciaBancariaTest {

    private static final String ACTOR = "tesorero@empresa";
    private static final LocalDate FECHA = LocalDate.of(2026, 3, 15);

    @Test
    @DisplayName("registrar crea la transferencia con importe a escala 2 y concepto normalizado")
    void registrarValida() {
        TenantContext.set(UUID.randomUUID());
        try {
            UUID origen = UUID.randomUUID();
            UUID destino = UUID.randomUUID();
            TransferenciaBancaria t = TransferenciaBancaria.registrar(
                    origen, destino, new BigDecimal("1500.5"), FECHA, "  Traspaso nomina  ", ACTOR);

            assertThat(t.getCuentaOrigenId()).isEqualTo(origen);
            assertThat(t.getCuentaDestinoId()).isEqualTo(destino);
            assertThat(t.getMonto()).isEqualByComparingTo("1500.50");
            assertThat(t.getMonto().scale()).isEqualTo(2);
            assertThat(t.getFecha()).isEqualTo(FECHA);
            assertThat(t.getConcepto()).isEqualTo("Traspaso nomina");
            assertThat(t.getFechaRegistro()).isNotNull();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("registrar con la misma cuenta de origen y destino lanza ReglaNegocioException (422)")
    void mismaCuentaRechaza() {
        TenantContext.set(UUID.randomUUID());
        try {
            UUID cuenta = UUID.randomUUID();
            assertThatThrownBy(() -> TransferenciaBancaria.registrar(
                    cuenta, cuenta, BigDecimal.TEN, FECHA, null, ACTOR))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("distintas");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("registrar con monto cero o negativo lanza ReglaNegocioException (422)")
    void montoNoPositivoRechaza() {
        TenantContext.set(UUID.randomUUID());
        try {
            UUID origen = UUID.randomUUID();
            UUID destino = UUID.randomUUID();
            assertThatThrownBy(() -> TransferenciaBancaria.registrar(
                    origen, destino, BigDecimal.ZERO, FECHA, null, ACTOR))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("positivo");
            assertThatThrownBy(() -> TransferenciaBancaria.registrar(
                    origen, destino, new BigDecimal("-1.00"), FECHA, null, ACTOR))
                    .isInstanceOf(ReglaNegocioException.class);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("registrar sin cuentas o sin fecha lanza ReglaNegocioException (422)")
    void datosObligatorios() {
        TenantContext.set(UUID.randomUUID());
        try {
            UUID origen = UUID.randomUUID();
            UUID destino = UUID.randomUUID();
            assertThatThrownBy(() -> TransferenciaBancaria.registrar(
                    null, destino, BigDecimal.TEN, FECHA, null, ACTOR))
                    .isInstanceOf(ReglaNegocioException.class);
            assertThatThrownBy(() -> TransferenciaBancaria.registrar(
                    origen, null, BigDecimal.TEN, FECHA, null, ACTOR))
                    .isInstanceOf(ReglaNegocioException.class);
            assertThatThrownBy(() -> TransferenciaBancaria.registrar(
                    origen, destino, BigDecimal.TEN, null, null, ACTOR))
                    .isInstanceOf(ReglaNegocioException.class);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("registrar con concepto en blanco lo normaliza a null")
    void conceptoEnBlancoEsNull() {
        TenantContext.set(UUID.randomUUID());
        try {
            TransferenciaBancaria t = TransferenciaBancaria.registrar(
                    UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, FECHA, "   ", ACTOR);
            assertThat(t.getConcepto()).isNull();
        } finally {
            TenantContext.clear();
        }
    }
}
