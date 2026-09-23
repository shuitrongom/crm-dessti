package com.dessti.crm.platform.monetizacion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.empresas.EmpresaRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.CatalogoModuloRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.EmpresaModuloPrecioRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.MonedaRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.PrecioModuloRepository;
import com.dessti.crm.platform.monetizacion.domain.CatalogoModulo;
import com.dessti.crm.platform.monetizacion.domain.EmpresaModuloPrecio;
import com.dessti.crm.platform.monetizacion.domain.PrecioModulo;

/**
 * Pruebas de la resolucion del precio aplicable en {@link ServicioPreciosModulo}:
 * precio ESPECIAL de la Empresa &gt; precio de LISTA &gt; vacio.
 */
class ServicioPreciosModuloTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MODULO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private PrecioModuloRepository precioRepository;
    private EmpresaModuloPrecioRepository empresaPrecioRepository;
    private CatalogoModuloRepository catalogoRepository;
    private MonedaRepository monedaRepository;
    private EmpresaRepository empresaRepository;
    private ServicioPreciosModulo servicio;

    @BeforeEach
    void preparar() {
        precioRepository = mock(PrecioModuloRepository.class);
        empresaPrecioRepository = mock(EmpresaModuloPrecioRepository.class);
        catalogoRepository = mock(CatalogoModuloRepository.class);
        monedaRepository = mock(MonedaRepository.class);
        empresaRepository = mock(EmpresaRepository.class);
        servicio = new ServicioPreciosModulo(precioRepository, empresaPrecioRepository,
                catalogoRepository, monedaRepository, empresaRepository, mock(AuditoriaPort.class));

        CatalogoModulo modulo = CatalogoModulo.crear("facturacion", "Facturacion", null, "super");
        // fijar id conocido por reflexion (el catalogo usa UUID aleatorio en crear)
        try {
            var f = CatalogoModulo.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(modulo, MODULO_ID);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        when(catalogoRepository.findByClave("facturacion")).thenReturn(Optional.of(modulo));
    }

    @Test
    @DisplayName("precio aplicable: gana el ESPECIAL de la Empresa sobre el de lista")
    void especialGanaSobreLista() {
        when(empresaPrecioRepository.findByTenantIdAndCatalogoModuloIdAndMonedaCodigo(
                eq(TENANT), eq(MODULO_ID), eq("USD")))
                .thenReturn(Optional.of(EmpresaModuloPrecio.crear(
                        TENANT, MODULO_ID, "USD", new BigDecimal("20.00"), "super")));

        Optional<BigDecimal> precio = servicio.precioAplicable(TENANT, "facturacion", "USD");

        assertThat(precio).contains(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("precio aplicable: sin especial, usa el precio de LISTA")
    void sinEspecialUsaLista() {
        when(empresaPrecioRepository.findByTenantIdAndCatalogoModuloIdAndMonedaCodigo(
                eq(TENANT), eq(MODULO_ID), eq("USD"))).thenReturn(Optional.empty());
        when(precioRepository.findByCatalogoModuloIdAndMonedaCodigo(eq(MODULO_ID), eq("USD")))
                .thenReturn(Optional.of(PrecioModulo.crear(MODULO_ID, "USD", new BigDecimal("30.00"), "super")));

        Optional<BigDecimal> precio = servicio.precioAplicable(TENANT, "facturacion", "USD");

        assertThat(precio).contains(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("precio aplicable: sin especial ni lista en esa moneda, vacio")
    void sinPrecioVacio() {
        when(empresaPrecioRepository.findByTenantIdAndCatalogoModuloIdAndMonedaCodigo(
                eq(TENANT), eq(MODULO_ID), eq("EUR"))).thenReturn(Optional.empty());
        when(precioRepository.findByCatalogoModuloIdAndMonedaCodigo(eq(MODULO_ID), eq("EUR")))
                .thenReturn(Optional.empty());

        assertThat(servicio.precioAplicable(TENANT, "facturacion", "EUR")).isEmpty();
    }

    @Test
    @DisplayName("precio aplicable: modulo inexistente en catalogo, vacio")
    void moduloInexistenteVacio() {
        when(catalogoRepository.findByClave("inexistente")).thenReturn(Optional.empty());
        assertThat(servicio.precioAplicable(TENANT, "inexistente", "USD")).isEmpty();
    }
}