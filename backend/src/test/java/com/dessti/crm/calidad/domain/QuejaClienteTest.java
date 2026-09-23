package com.dessti.crm.calidad.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;

/**
 * Pruebas unitarias del agregado {@link QuejaCliente} (Req 70.1, 70.8): registro, vinculo
 * opcional a Accion_Correctiva y atencion. Son pruebas puras de dominio, sin Spring ni base
 * de datos.
 */
class QuejaClienteTest {

    private static QuejaCliente registrada() {
        return QuejaCliente.registrar(
                UUID.randomUUID(), OrigenQueja.PORTAL, null, "descripcion", Instant.now(), "actor");
    }

    @Test
    void registrarDejaLaQuejaRegistradaSinVinculo() {
        QuejaCliente queja = registrada();

        assertThat(queja.getEstado()).isEqualTo(EstadoQuejaCliente.REGISTRADA);
        assertThat(queja.getAccionCorrectivaId()).isNull();
    }

    @Test
    void registrarSocialConservaElCanalSocial() {
        UUID canal = UUID.randomUUID();
        QuejaCliente queja = QuejaCliente.registrar(
                UUID.randomUUID(), OrigenQueja.SOCIAL, canal, "queja social", Instant.now(), "actor");

        assertThat(queja.getOrigen()).isEqualTo(OrigenQueja.SOCIAL);
        assertThat(queja.getCanalSocialId()).isEqualTo(canal);
    }

    @Test
    void registrarRechazaClienteNulo() {
        assertThatThrownBy(() -> QuejaCliente.registrar(
                null, OrigenQueja.PORTAL, null, "d", Instant.now(), "actor"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void vincularFijaLaAccionYTransitaAVinculada() {
        QuejaCliente queja = registrada();
        UUID accion = UUID.randomUUID();

        queja.vincularAccionCorrectiva(accion, "actor");

        assertThat(queja.getEstado()).isEqualTo(EstadoQuejaCliente.VINCULADA);
        assertThat(queja.getAccionCorrectivaId()).isEqualTo(accion);
    }

    @Test
    void vincularRechazaAccionNula() {
        QuejaCliente queja = registrada();

        assertThatThrownBy(() -> queja.vincularAccionCorrectiva(null, "actor"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void atenderTransitaAAtendidaYEsFinal() {
        QuejaCliente queja = registrada();

        queja.atender("actor");
        assertThat(queja.getEstado()).isEqualTo(EstadoQuejaCliente.ATENDIDA);
        assertThatThrownBy(() -> queja.atender("actor"))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void vincularTrasAtenderSeRechaza() {
        QuejaCliente queja = registrada();
        queja.atender("actor");

        assertThatThrownBy(() -> queja.vincularAccionCorrectiva(UUID.randomUUID(), "actor"))
                .isInstanceOf(TransicionInvalidaException.class);
    }
}
