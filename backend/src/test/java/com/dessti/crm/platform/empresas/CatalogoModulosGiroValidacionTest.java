package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.giros.domain.Giro;
import com.dessti.crm.platform.modulos.CatalogoModulosService;
import com.dessti.crm.platform.modulos.ModuloCatalogoDto;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.MonedaRepository;
import com.dessti.crm.platform.monetizacion.domain.Moneda;

/**
 * Pruebas unitarias del helper compartido {@link CatalogoModulosGiroValidacion}
 * (Req 2.5, 11.1, 11.2), extraido de {@code ServicioPlanes}. Cubre las tres
 * responsabilidades del helper de forma aislada:
 * <ul>
 *   <li>{@code validarModulosDelGiro} acepta modulos validos del Giro (y de
 *       Nucleo) sin lanzar excepcion.</li>
 *   <li>{@code validarModulosDelGiro} rechaza con {@link ReglaNegocioException}
 *       (HTTP 422) modulos que pertenecen a otro Giro o que no existen.</li>
 *   <li>{@code validarMonedaActiva} rechaza con {@link ReglaNegocioException}
 *       (HTTP 422) una moneda inactiva o inexistente.</li>
 * </ul>
 *
 * <p>No usa {@code @SpringBootTest}: los colaboradores ({@link CatalogoModulosService}
 * y {@link MonedaRepository}) se sustituyen por dobles de Mockito, igual que en
 * {@link ServicioPlanesTest}.</p>
 */
class CatalogoModulosGiroValidacionTest {

    private static final String GIRO_CLAVE = "anuncios-luminosos";

    private CatalogoModulosService catalogoModulosService;
    private MonedaRepository monedaRepository;
    private CatalogoModulosGiroValidacion validacion;
    private Giro giro;

    @BeforeEach
    void preparar() {
        catalogoModulosService = mock(CatalogoModulosService.class);
        monedaRepository = mock(MonedaRepository.class);
        validacion = new CatalogoModulosGiroValidacion(catalogoModulosService, monedaRepository);

        giro = Giro.crear(GIRO_CLAVE, "Anuncios luminosos", null, "super");

        // Moneda MXN activa por defecto.
        Moneda mxn = Moneda.crear("MXN", "Peso mexicano", "super");
        lenient().when(monedaRepository.findByCodigo("MXN")).thenReturn(Optional.of(mxn));

        // Catalogo: 'comercial'/'facturacion' son Nucleo (giro null);
        // 'produccion-industrial' pertenece al Giro anuncios;
        // 'modulo-de-otro-giro' pertenece a 'manufactura'.
        lenient().when(catalogoModulosService.listar()).thenReturn(List.of(
                new ModuloCatalogoDto("comercial", "Comercial (CRM)", null, UUID.randomUUID(),
                        new BigDecimal("100.00"), "MXN"),
                new ModuloCatalogoDto("facturacion", "Facturacion (CFDI)", null, UUID.randomUUID(),
                        new BigDecimal("50.00"), "MXN"),
                new ModuloCatalogoDto("produccion-industrial", "Produccion industrial", GIRO_CLAVE, null,
                        null, "MXN"),
                new ModuloCatalogoDto("modulo-de-otro-giro", "Otro", "manufactura", null, null, "MXN")));
    }

    // -----------------------------------------------------------------
    // validarModulosDelGiro: aceptacion de modulos validos (Req 2.5, 11.1)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("validarModulosDelGiro acepta modulos de Nucleo y del propio Giro sin lanzar (Req 2.5, 11.1)")
    void aceptaModulosValidosDelGiroYNucleo() {
        Map<String, BigDecimal> precios = Map.of(
                "comercial", new BigDecimal("100.00"),
                "produccion-industrial", new BigDecimal("200.00"));

        assertThatCode(() -> validacion.validarModulosDelGiro(precios, giro))
                .doesNotThrowAnyException();

        Map<String, BigDecimal> normalizado = validacion.validarModulosDelGiro(precios, giro);
        assertThat(normalizado).containsKeys("comercial", "produccion-industrial");
    }

    // -----------------------------------------------------------------
    // validarModulosDelGiro: rechazo -> 422 (Req 11.2)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("validarModulosDelGiro rechaza un modulo de OTRO Giro con 422 (Req 11.2)")
    void rechazaModuloDeOtroGiro() {
        Map<String, BigDecimal> precios = Map.of("modulo-de-otro-giro", new BigDecimal("10.00"));

        assertThatThrownBy(() -> validacion.validarModulosDelGiro(precios, giro))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Giro distinto");
    }

    @Test
    @DisplayName("validarModulosDelGiro rechaza una clave de modulo inexistente con 422 (Req 11.2)")
    void rechazaModuloDesconocido() {
        Map<String, BigDecimal> precios = Map.of("no-existe", new BigDecimal("10.00"));

        assertThatThrownBy(() -> validacion.validarModulosDelGiro(precios, giro))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no existen en el catalogo");
    }

    // -----------------------------------------------------------------
    // validarMonedaActiva: rechazo -> 422 (Req 11.2)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("validarMonedaActiva rechaza una moneda inactiva con 422 (Req 11.2)")
    void rechazaMonedaInactiva() {
        Moneda inactiva = Moneda.crear("EUR", "Euro", "super");
        inactiva.desactivar("super");
        when(monedaRepository.findByCodigo("EUR")).thenReturn(Optional.of(inactiva));

        assertThatThrownBy(() -> validacion.validarMonedaActiva("EUR"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("inactiva");
    }

    @Test
    @DisplayName("validarMonedaActiva rechaza una moneda inexistente con 422 (Req 11.2)")
    void rechazaMonedaInexistente() {
        when(monedaRepository.findByCodigo("JPY")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validacion.validarMonedaActiva("JPY"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no existe en el catalogo");
    }
}
