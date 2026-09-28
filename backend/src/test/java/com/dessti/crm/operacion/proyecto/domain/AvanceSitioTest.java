package com.dessti.crm.operacion.proyecto.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;

/**
 * Pruebas unitarias de la entidad de dominio {@link AvanceSitio}: avance lineal,
 * correccion administrativa (retroceso/salto), evidencia y validaciones (Req 3.2).
 * Sin Spring ni JPA.
 */
class AvanceSitioTest {

    private static final String ACTOR = "tester";

    private AvanceSitio nuevo() {
        return AvanceSitio.inicial(UUID.randomUUID(), ACTOR);
    }

    @Test
    void avanceInicialEsPendiente() {
        assertThat(nuevo().getFase()).isEqualTo(FaseSitioGenerica.PENDIENTE);
    }

    @Test
    void avanzarSigueLaSecuenciaLineal() {
        AvanceSitio a = nuevo();
        a.avanzarFase(FaseSitioGenerica.EN_PREPARACION, null, null, ACTOR);
        assertThat(a.getFase()).isEqualTo(FaseSitioGenerica.EN_PREPARACION);
        a.avanzarFase(FaseSitioGenerica.EN_INSTALACION, null, null, ACTOR);
        a.avanzarFase(FaseSitioGenerica.ENTREGADO, null, null, ACTOR);
        assertThat(a.getFase()).isEqualTo(FaseSitioGenerica.ENTREGADO);
    }

    @Test
    void avanzarSaltandoFasesEsInvalido() {
        AvanceSitio a = nuevo();
        assertThatExceptionOfType(TransicionInvalidaException.class)
                .isThrownBy(() -> a.avanzarFase(FaseSitioGenerica.ENTREGADO, null, null, ACTOR));
    }

    @Test
    void avanzarHaciaAtrasEsInvalido() {
        AvanceSitio a = nuevo();
        a.avanzarFase(FaseSitioGenerica.EN_PREPARACION, null, null, ACTOR);
        assertThatExceptionOfType(TransicionInvalidaException.class)
                .isThrownBy(() -> a.avanzarFase(FaseSitioGenerica.PENDIENTE, null, null, ACTOR));
    }

    @Test
    void corregirPermiteRetroceder() {
        AvanceSitio a = nuevo();
        a.avanzarFase(FaseSitioGenerica.EN_PREPARACION, null, null, ACTOR);
        a.avanzarFase(FaseSitioGenerica.EN_INSTALACION, null, null, ACTOR);
        // Correccion administrativa: retrocede a pendiente sin restriccion lineal.
        a.corregirFase(FaseSitioGenerica.PENDIENTE, "marcado por error", null, ACTOR);
        assertThat(a.getFase()).isEqualTo(FaseSitioGenerica.PENDIENTE);
    }

    @Test
    void corregirPermiteSaltarFases() {
        AvanceSitio a = nuevo();
        a.corregirFase(FaseSitioGenerica.ENTREGADO, null, null, ACTOR);
        assertThat(a.getFase()).isEqualTo(FaseSitioGenerica.ENTREGADO);
    }

    @Test
    void guardaEvidenciaYNota() {
        AvanceSitio a = nuevo();
        a.avanzarFase(FaseSitioGenerica.EN_PREPARACION, "acopio listo",
                "https://docs.example.com/acta-123", ACTOR);
        assertThat(a.getNota()).isEqualTo("acopio listo");
        assertThat(a.getEvidenciaUrl()).isEqualTo("https://docs.example.com/acta-123");
    }

    @Test
    void evidenciaEnBlancoQuedaNula() {
        AvanceSitio a = nuevo();
        a.avanzarFase(FaseSitioGenerica.EN_PREPARACION, "   ", "   ", ACTOR);
        assertThat(a.getNota()).isNull();
        assertThat(a.getEvidenciaUrl()).isNull();
    }

    @Test
    void notaDemasiadoLargaEsInvalida() {
        AvanceSitio a = nuevo();
        String notaLarga = "x".repeat(AvanceSitio.LONGITUD_MAXIMA_NOTA + 1);
        assertThatThrownBy(
                () -> a.avanzarFase(FaseSitioGenerica.EN_PREPARACION, notaLarga, null, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void evidenciaDemasiadoLargaEsInvalida() {
        AvanceSitio a = nuevo();
        String evidenciaLarga = "y".repeat(AvanceSitio.LONGITUD_MAXIMA_EVIDENCIA + 1);
        assertThatThrownBy(
                () -> a.avanzarFase(FaseSitioGenerica.EN_PREPARACION, null, evidenciaLarga, ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
