package com.dessti.crm.platform.rendimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.dessti.crm.platform.config.ConfiguracionCache;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginacionConstantes;
import com.dessti.crm.platform.web.pagination.ParametrosPaginacionInvalidosException;

/**
 * Prueba de sanidad de rendimiento <strong>determinista</strong> (Tarea 49.3,
 * Req 51, 12). NO mide latencia ni realiza carga (eso queda fuera del alcance de
 * las pruebas automaticas, ver la clase {@code RendimientoCargaHarness}); en su
 * lugar comprueba, sin dependencia de tiempos, que las <em>salvaguardas</em> de
 * rendimiento estan efectivamente cableadas:
 *
 * <ul>
 *   <li><b>Acotacion de paginacion (Req 12.1):</b> la fabrica de {@code Pageable}
 *       rechaza tamanos por encima del maximo y acota cuando corresponde,
 *       evitando respuestas no acotadas que degraden el rendimiento.</li>
 *   <li><b>Cache de catalogos (Req 12.3):</b> el {@link CacheManager} de Caffeine
 *       declara la cache de catalogo de cambio lento, condicion necesaria para
 *       servir esos catalogos desde cache.</li>
 * </ul>
 */
@DisplayName("Tarea 49.3 - Sanidad de rendimiento determinista (Req 51, 12)")
class RendimientoSanidadTest {

    // ------------------------------------------------------------------------
    // Acotacion de paginacion (Req 12.1).
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("La paginacion rechaza un tamano por encima del maximo (modo estricto)")
    void paginacion_rechazaTamanoExcesivo() {
        assertThatThrownBy(() -> PageRequestFactory.of(0, PaginacionConstantes.MAX_SIZE + 1))
                .isInstanceOf(ParametrosPaginacionInvalidosException.class);
    }

    @Test
    @DisplayName("La paginacion acota el tamano al maximo en modo acotando")
    void paginacion_acotaTamanoAlMaximo() {
        int solicitado = PaginacionConstantes.MAX_SIZE + 500;

        var pageable = PageRequestFactory.acotando(0, solicitado);

        assertThat(pageable.getPageSize())
                .as("El tamano de pagina debe quedar acotado al maximo (Req 12.1)")
                .isEqualTo(PaginacionConstantes.MAX_SIZE);
    }

    @Test
    @DisplayName("La paginacion aplica el tamano por defecto cuando no se indica")
    void paginacion_aplicaTamanoPorDefecto() {
        var pageable = PageRequestFactory.acotando(null, null);

        assertThat(pageable.getPageSize()).isEqualTo(PaginacionConstantes.DEFAULT_SIZE);
        assertThat(pageable.getPageNumber()).isEqualTo(PaginacionConstantes.DEFAULT_PAGE);
    }

    // ------------------------------------------------------------------------
    // Cache de catalogos de cambio lento (Req 12.3).
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("El CacheManager declara la cache del catalogo de monedas (Req 12.3)")
    void cache_declaraCatalogoDeCambioLento() {
        CacheManager cacheManager = new ConfiguracionCache().cacheManager();

        Cache monedas = cacheManager.getCache(ConfiguracionCache.CACHE_MONEDAS);

        assertThat(monedas)
                .as("Debe existir la cache '%s' para servir el catalogo desde cache (Req 12.3)",
                        ConfiguracionCache.CACHE_MONEDAS)
                .isNotNull();
    }

    @Test
    @DisplayName("La cache de catalogo funciona: almacena y devuelve el valor cacheado")
    void cache_almacenaYRecupera() {
        CacheManager cacheManager = new ConfiguracionCache().cacheManager();
        Cache monedas = cacheManager.getCache(ConfiguracionCache.CACHE_MONEDAS);

        monedas.put("activas", "valor-cacheado");

        assertThat(monedas.get("activas", String.class)).isEqualTo("valor-cacheado");
    }
}
