package com.dessti.crm.platform.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

/**
 * Pruebas unitarias acotadas de {@link PageRequestFactory} (Req 12, 7.8).
 *
 * <p>Ejercen la resolucion de parametros de paginacion y la politica de tamano
 * (rechazo vs. acotamiento) sin arrancar el contexto de Spring.</p>
 */
class PageRequestFactoryTest {

    @Test
    @DisplayName("size nulo aplica el tamano por defecto (20) y page nula aplica la pagina 0")
    void of_sinParametros_usaValoresPorDefecto() {
        Pageable pageable = PageRequestFactory.of(null, null);

        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("size = 100 (maximo) se acepta")
    void of_sizeEnElMaximo_seAcepta() {
        Pageable pageable = PageRequestFactory.of(2, 100);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("size = 101 (mayor al maximo) se rechaza con excepcion mapeada a 400")
    void of_sizeSuperiorAlMaximo_seRechaza() {
        assertThatThrownBy(() -> PageRequestFactory.of(0, 101))
                .isInstanceOf(ParametrosPaginacionInvalidosException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("size = 0 (menor a 1) se rechaza")
    void of_sizeMenorAUno_seRechaza() {
        assertThatThrownBy(() -> PageRequestFactory.of(0, 0))
                .isInstanceOf(ParametrosPaginacionInvalidosException.class)
                .satisfies(ex -> assertThat(((ParametrosPaginacionInvalidosException) ex).campo()).isEqualTo("size"));
    }

    @Test
    @DisplayName("page negativa se rechaza")
    void of_pageNegativa_seRechaza() {
        assertThatThrownBy(() -> PageRequestFactory.of(-1, 20))
                .isInstanceOf(ParametrosPaginacionInvalidosException.class)
                .satisfies(ex -> assertThat(((ParametrosPaginacionInvalidosException) ex).campo()).isEqualTo("page"));
    }

    @Test
    @DisplayName("modo acotar: size > 100 se limita a 100 en lugar de rechazarse")
    void acotando_sizeSuperior_seAcotaAlMaximo() {
        Pageable pageable = PageRequestFactory.acotando(1, 500);

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("modo acotar: size valido se respeta y page negativa sigue rechazandose")
    void acotando_respetaValidosYRechazaPageNegativa() {
        assertThatCode(() -> PageRequestFactory.acotando(0, 50)).doesNotThrowAnyException();
        assertThatThrownBy(() -> PageRequestFactory.acotando(-1, 50))
                .isInstanceOf(ParametrosPaginacionInvalidosException.class);
    }
}
