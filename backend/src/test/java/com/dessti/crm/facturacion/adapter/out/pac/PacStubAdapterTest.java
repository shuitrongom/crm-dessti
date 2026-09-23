package com.dessti.crm.facturacion.adapter.out.pac;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dessti.crm.facturacion.application.ResultadoCancelacion;
import com.dessti.crm.facturacion.application.ResultadoTimbrado;
import com.dessti.crm.facturacion.application.SolicitudCancelacion;
import com.dessti.crm.facturacion.application.SolicitudTimbrado;

/**
 * Prueba de integracion (a nivel de componente, sin contexto de Spring) del
 * {@link PacStubAdapter}: verifica el <strong>timbrado exitoso</strong>, el
 * <strong>rechazo</strong> simulado y la <strong>cancelacion</strong> con el mapeo
 * de estados que el adaptador expone hacia la aplicacion (Tarea 28.8; Req 35).
 *
 * <p>El stub es determinista, por lo que se ejercita como una clase corriente con
 * un {@link Clock} fijo (para una fecha de timbrado reproducible). No requiere red
 * ni credenciales.</p>
 */
class PacStubAdapterTest {

    private static final Instant AHORA = Instant.parse("2025-01-15T10:30:00Z");
    private final PacStubAdapter adaptador =
            new PacStubAdapter(Clock.fixed(AHORA, ZoneOffset.UTC));

    private static SolicitudTimbrado solicitudCon(String rfc) {
        return new SolicitudTimbrado(
                UUID.randomUUID(), UUID.randomUUID(),
                rfc, "Cliente de Prueba SA de CV", "01000", "601", "G03",
                new BigDecimal("1000.00"), new BigDecimal("160.00"),
                BigDecimal.ZERO, new BigDecimal("1160.00"));
    }

    @Test
    void timbrarDevuelveExitoConFolioSelloYFecha() {
        ResultadoTimbrado resultado = adaptador.timbrar(solicitudCon("AAA010101AAA"));

        assertThat(resultado.exito()).as("timbrado exitoso").isTrue();
        assertThat(resultado.folioFiscal()).as("se genera un Folio_Fiscal (UUID)").isNotNull();
        assertThat(resultado.selloSat()).as("se devuelve un sello").isNotBlank();
        assertThat(resultado.fechaTimbrado())
                .as("la fecha de timbrado proviene del Clock inyectado")
                .isEqualTo(AHORA);
        assertThat(resultado.mensajeError()).as("sin mensaje de error en exito").isNull();
    }

    @Test
    void timbrarGeneraFoliosDistintosEnCadaLlamada() {
        ResultadoTimbrado primero = adaptador.timbrar(solicitudCon("AAA010101AAA"));
        ResultadoTimbrado segundo = adaptador.timbrar(solicitudCon("BBB020202BB2"));

        assertThat(primero.folioFiscal())
                .as("cada timbrado exitoso produce un Folio_Fiscal distinto")
                .isNotEqualTo(segundo.folioFiscal());
    }

    @Test
    void timbrarConRfcCentinelaSimulaRechazoSinFolio() {
        ResultadoTimbrado resultado = adaptador.timbrar(
                solicitudCon(PacStubAdapter.RFC_SIMULA_RECHAZO));

        assertThat(resultado.exito()).as("el PAC rechaza el timbrado").isFalse();
        assertThat(resultado.folioFiscal()).as("un rechazo no asigna Folio_Fiscal").isNull();
        assertThat(resultado.selloSat()).isNull();
        assertThat(resultado.fechaTimbrado()).isNull();
        assertThat(resultado.mensajeError()).as("se informa el motivo del rechazo").isNotBlank();
    }

    @Test
    void timbrarConSolicitudNulaEsRechazo() {
        ResultadoTimbrado resultado = adaptador.timbrar(null);

        assertThat(resultado.exito()).isFalse();
        assertThat(resultado.mensajeError()).isNotBlank();
    }

    @Test
    void cancelarDevuelveAceptacionConAcuse() {
        ResultadoCancelacion resultado = adaptador.cancelar(
                new SolicitudCancelacion(UUID.randomUUID(), "02"));

        assertThat(resultado.exito()).as("cancelacion aceptada").isTrue();
        assertThat(resultado.acuse()).as("se devuelve un acuse de cancelacion").isNotBlank();
        assertThat(resultado.mensajeError()).isNull();
    }

    @Test
    void cancelarSinFolioFiscalEsRechazo() {
        ResultadoCancelacion resultado = adaptador.cancelar(new SolicitudCancelacion(null, "02"));

        assertThat(resultado.exito()).isFalse();
        assertThat(resultado.mensajeError()).isNotBlank();
    }

    @Test
    void cancelarSinMotivoSatEsRechazo() {
        ResultadoCancelacion resultado = adaptador.cancelar(
                new SolicitudCancelacion(UUID.randomUUID(), "  "));

        assertThat(resultado.exito()).as("se exige un motivo del SAT para cancelar").isFalse();
        assertThat(resultado.mensajeError()).isNotBlank();
    }
}
