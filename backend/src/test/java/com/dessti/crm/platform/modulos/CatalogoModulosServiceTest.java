package com.dessti.crm.platform.modulos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.dessti.crm.platform.monetizacion.adapter.out.persistence.CatalogoModuloRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.MonedaRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.PrecioModuloRepository;
import com.dessti.crm.platform.monetizacion.application.MonetizacionProperties;
import com.dessti.crm.platform.monetizacion.domain.CatalogoModulo;
import com.dessti.crm.platform.monetizacion.domain.Moneda;
import com.dessti.crm.platform.monetizacion.domain.PrecioModulo;
import com.dessti.crm.platform.vertical.RegistroVerticales;

/**
 * Pruebas unitarias de {@link CatalogoModulosService}: verifican la union de
 * modulos de Nucleo (catalogo_modulo) y de vertical (RegistroVerticales), la
 * deduplicacion con prioridad del vertical, la atribucion de Giro, la resolucion
 * de etiquetas, el orden determinista y el enriquecimiento con el id del catalogo
 * y el precio en la moneda principal. Sin BD: repositorios y registro se simulan.
 */
class CatalogoModulosServiceTest {

    private static final String MONEDA_PRINCIPAL = "MXN";

    private CatalogoModuloRepository catalogoRepository;
    private PrecioModuloRepository precioRepository;
    private MonedaRepository monedaRepository;
    private RegistroVerticales registroVerticales;
    private CatalogoModulosService servicio;

    @BeforeEach
    void setUp() {
        catalogoRepository = mock(CatalogoModuloRepository.class);
        precioRepository = mock(PrecioModuloRepository.class);
        monedaRepository = mock(MonedaRepository.class);
        registroVerticales = mock(RegistroVerticales.class);
        // Por defecto: sin precios definidos y moneda principal activa (MXN).
        lenient().when(precioRepository.findByMonedaCodigo(anyString())).thenReturn(List.of());
        lenient().when(monedaRepository.findByCodigo(MONEDA_PRINCIPAL))
                .thenReturn(Optional.of(moneda(MONEDA_PRINCIPAL, true)));
        servicio = new CatalogoModulosService(catalogoRepository, precioRepository, monedaRepository,
                new MonetizacionProperties(MONEDA_PRINCIPAL), registroVerticales);
    }

    private static CatalogoModulo modulo(String clave, String nombre) {
        return CatalogoModulo.crear(clave, nombre, null, "super");
    }

    private static Moneda moneda(String codigo, boolean activa) {
        Moneda m = Moneda.crear(codigo, codigo, "super");
        if (!activa) {
            m.desactivar("super");
        }
        return m;
    }

    private static PrecioModulo precio(UUID moduloId, String moneda, String monto) {
        return PrecioModulo.crear(moduloId, moneda, new BigDecimal(monto), "super");
    }

    @Test
    @DisplayName("Une modulos de Nucleo y de vertical; operacion (MONETIZACION_NUCLEO) se expone como Nucleo")
    void uneNucleoYVerticalConPrioridadVertical() {
        // catalogo_modulo trae comercial (Nucleo puro) y operacion (MONETIZACION_NUCLEO).
        when(catalogoRepository.findAll()).thenReturn(List.of(
                modulo("comercial", "Comercial (CRM)"),
                modulo("operacion", "Operacion y produccion")));
        // Verticales: operacion -> anuncios-luminosos, produccion-industrial -> manufactura.
        when(registroVerticales.modulosDeVerticalPorGiro()).thenReturn(Map.of(
                "operacion", "anuncios-luminosos",
                "produccion-industrial", "manufactura"));

        List<ModuloCatalogoDto> catalogo = servicio.listar();

        // Debe contener las 3 claves deduplicadas.
        assertThat(catalogo).extracting(ModuloCatalogoDto::clave)
                .containsExactlyInAnyOrder("comercial", "operacion", "produccion-industrial");

        // comercial es Nucleo (giro null).
        ModuloCatalogoDto comercial = buscar(catalogo, "comercial");
        assertThat(comercial.giro()).isNull();
        assertThat(comercial.nombreVisible()).isEqualTo("Comercial (CRM)");

        // operacion es una clave de MONETIZACION_NUCLEO: aunque el vertical de
        // anuncios tambien la declare, en el catalogo de monetizacion se expone
        // como Nucleo (giro null) para poder incluirla en Planes de cualquier Giro.
        // (El gating RBAC por Giro de los flujos de anuncios NO se ve afectado.)
        ModuloCatalogoDto operacion = buscar(catalogo, "operacion");
        assertThat(operacion.giro()).isNull();

        // produccion-industrial no esta en catalogo_modulo: viene del vertical.
        ModuloCatalogoDto produccion = buscar(catalogo, "produccion-industrial");
        assertThat(produccion.giro()).isEqualTo("manufactura");
        assertThat(produccion.nombreVisible()).isEqualTo("Produccion industrial");
    }

    @Test
    @DisplayName("Etiqueta: override documentado gana sobre el nombre del catalogo")
    void etiquetaOverrideGanaSobreNombreCatalogo() {
        when(catalogoRepository.findAll()).thenReturn(List.of(
                modulo("rh-nomina", "Nombre crudo distinto en BD")));
        when(registroVerticales.modulosDeVerticalPorGiro()).thenReturn(Map.of());

        List<ModuloCatalogoDto> catalogo = servicio.listar();

        assertThat(buscar(catalogo, "rh-nomina").nombreVisible()).isEqualTo("RH y nomina");
    }

    @Test
    @DisplayName("Etiqueta: sin override usa el nombre del catalogo_modulo")
    void etiquetaCaeAlNombreDelCatalogo() {
        when(catalogoRepository.findAll()).thenReturn(List.of(
                modulo("modulo-experimental", "Modulo Experimental Beta")));
        when(registroVerticales.modulosDeVerticalPorGiro()).thenReturn(Map.of());

        List<ModuloCatalogoDto> catalogo = servicio.listar();

        assertThat(buscar(catalogo, "modulo-experimental").nombreVisible())
                .isEqualTo("Modulo Experimental Beta");
    }

    @Test
    @DisplayName("Etiqueta: clave de vertical sin override ni catalogo se humaniza")
    void etiquetaSeHumanizaComoUltimoRecurso() {
        when(catalogoRepository.findAll()).thenReturn(List.of());
        when(registroVerticales.modulosDeVerticalPorGiro()).thenReturn(Map.of(
                "control-piso", "manufactura"));

        List<ModuloCatalogoDto> catalogo = servicio.listar();

        assertThat(buscar(catalogo, "control-piso").nombreVisible()).isEqualTo("Control piso");
    }

    @Test
    @DisplayName("Orden determinista: Nucleo primero por etiqueta, luego verticales por Giro")
    void ordenDeterministaNucleoLuegoVerticales() {
        when(catalogoRepository.findAll()).thenReturn(List.of(
                modulo("reportes-bi", "Reportes y BI"),
                modulo("comercial", "Comercial (CRM)")));
        when(registroVerticales.modulosDeVerticalPorGiro()).thenReturn(Map.of(
                "produccion-industrial", "manufactura",
                "operacion", "anuncios-luminosos"));

        List<ModuloCatalogoDto> catalogo = servicio.listar();

        // Nucleo primero (giro null), alfabetico por etiqueta:
        // Comercial < Operacion y produccion < Reportes (operacion es MONETIZACION_NUCLEO).
        // Luego el unico vertical restante: produccion-industrial (manufactura).
        assertThat(catalogo).extracting(ModuloCatalogoDto::clave)
                .containsExactly("comercial", "operacion", "reportes-bi", "produccion-industrial");
    }

    @Test
    @DisplayName("Catalogo vacio cuando no hay modulos de ninguna fuente")
    void catalogoVacioSinFuentes() {
        when(catalogoRepository.findAll()).thenReturn(List.of());
        when(registroVerticales.modulosDeVerticalPorGiro()).thenReturn(Map.of());

        assertThat(servicio.listar()).isEmpty();
    }

    @Test
    @DisplayName("Enriquecimiento: id del catalogo y precio en moneda principal; moneda en toda entrada")
    void enriquecePrecioEIdEnMonedaPrincipal() {
        CatalogoModulo comercial = modulo("comercial", "Comercial (CRM)");
        CatalogoModulo operacion = modulo("operacion", "Operacion y produccion");
        when(catalogoRepository.findAll()).thenReturn(List.of(comercial, operacion));
        // Solo comercial tiene precio de lista en la moneda principal (MXN).
        when(precioRepository.findByMonedaCodigo(MONEDA_PRINCIPAL)).thenReturn(List.of(
                precio(comercial.getId(), MONEDA_PRINCIPAL, "1500.00")));
        // produccion-industrial es solo de vertical (sin fila en catalogo_modulo).
        when(registroVerticales.modulosDeVerticalPorGiro()).thenReturn(Map.of(
                "produccion-industrial", "manufactura"));

        List<ModuloCatalogoDto> catalogo = servicio.listar();

        // Modulo del catalogo CON precio: id presente, precio de la moneda principal.
        ModuloCatalogoDto dtoComercial = buscar(catalogo, "comercial");
        assertThat(dtoComercial.catalogoModuloId()).isEqualTo(comercial.getId());
        assertThat(dtoComercial.precio()).isEqualByComparingTo("1500.00");
        assertThat(dtoComercial.monedaCodigo()).isEqualTo(MONEDA_PRINCIPAL);

        // Modulo del catalogo SIN precio definido: id presente, precio null.
        ModuloCatalogoDto dtoOperacion = buscar(catalogo, "operacion");
        assertThat(dtoOperacion.catalogoModuloId()).isEqualTo(operacion.getId());
        assertThat(dtoOperacion.precio()).isNull();
        assertThat(dtoOperacion.monedaCodigo()).isEqualTo(MONEDA_PRINCIPAL);

        // Clave solo de vertical: sin id ni precio, pero con la moneda principal.
        ModuloCatalogoDto dtoProduccion = buscar(catalogo, "produccion-industrial");
        assertThat(dtoProduccion.catalogoModuloId()).isNull();
        assertThat(dtoProduccion.precio()).isNull();
        assertThat(dtoProduccion.monedaCodigo()).isEqualTo(MONEDA_PRINCIPAL);
    }

    @Test
    @DisplayName("Degradacion controlada: moneda principal inexistente/inactiva cae a MXN")
    void monedaPrincipalInexistenteDegradaAMxn() {
        // Se configura EUR como principal, pero EUR no existe como moneda activa.
        when(monedaRepository.findByCodigo("EUR")).thenReturn(Optional.empty());
        CatalogoModulo comercial = modulo("comercial", "Comercial (CRM)");
        when(catalogoRepository.findAll()).thenReturn(List.of(comercial));
        when(registroVerticales.modulosDeVerticalPorGiro()).thenReturn(Map.of());
        // El precio resuelto debe buscarse en MXN (la moneda por defecto), no en EUR.
        when(precioRepository.findByMonedaCodigo(MONEDA_PRINCIPAL)).thenReturn(List.of(
                precio(comercial.getId(), MONEDA_PRINCIPAL, "999.99")));

        CatalogoModulosService servicioEur = new CatalogoModulosService(
                catalogoRepository, precioRepository, monedaRepository,
                new MonetizacionProperties("EUR"), registroVerticales);

        List<ModuloCatalogoDto> catalogo = servicioEur.listar();

        ModuloCatalogoDto dtoComercial = buscar(catalogo, "comercial");
        assertThat(dtoComercial.monedaCodigo()).isEqualTo(MONEDA_PRINCIPAL);
        assertThat(dtoComercial.precio()).isEqualByComparingTo("999.99");
    }

    private static ModuloCatalogoDto buscar(List<ModuloCatalogoDto> catalogo, String clave) {
        return catalogo.stream()
                .filter(m -> m.clave().equals(clave))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontro el modulo: " + clave));
    }
}
