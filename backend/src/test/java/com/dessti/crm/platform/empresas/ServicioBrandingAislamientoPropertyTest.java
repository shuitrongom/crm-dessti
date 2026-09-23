package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.tenant.TenantContext;

import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterTry;

/**
 * Prueba de propiedad (jqwik) del aislamiento multi-tenant del color de marca en
 * {@link ServicioBranding}.
 *
 * <p>Feature: tematizacion-empresa-enterprise, Property 7: para todo par de
 * tenants distintos con colores distintos, el color resuelto/aplicado bajo un
 * tenant es siempre el de ese tenant; se resuelve por TenantContext, no por la
 * peticion.</p>
 *
 * <p><strong>Validates: Requirements 4.5, 5.2, 6.6</strong></p>
 *
 * <p>Monta el servicio con un {@link EmpresaRepository} simulado que resuelve
 * {@code findById(id)} a la Empresa cuyo PK coincide (cada Empresa ES su propio
 * tenant, {@code PK == tenantId}). El {@link ActualizarBrandingCommand} NO lleva
 * tenant: el servicio deriva el tenant SIEMPRE de {@link TenantContext}. Se
 * verifica que consultar/actualizar bajo {@code T_A} opera sobre la Empresa de
 * {@code T_A} (su color) y bajo {@code T_B} sobre la de {@code T_B}, y que
 * cambiar el color bajo {@code T_A} no afecta a la Empresa de {@code T_B}. El
 * {@link TenantContext} se limpia en {@link AfterTry}.</p>
 */
class ServicioBrandingAislamientoPropertyTest {

    private static final UUID T_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID T_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @AfterTry
    void limpiarContexto() {
        TenantContext.clear();
    }

    /**
     * Genera colores hexadecimales validos {@code #RRGGBB} en minusculas (la
     * forma en que el dominio los normaliza al persistir).
     */
    @Provide
    Arbitrary<String> colorHex() {
        return Arbitraries.integers().between(0, 0xFFFFFF)
                .map(n -> String.format(Locale.ROOT, "#%06x", n));
    }

    /**
     * Crea una Empresa cuya PK es {@code tenantId}, para que
     * {@code findById(tenantId)} la localice como su propio tenant, con un color
     * inicial dado (o nulo).
     */
    private Empresa empresaConColor(UUID tenantId, String colorInicial) {
        Empresa empresa = Empresa.crear("Empresa " + tenantId, "ANO120101AB1",
                UUID.randomUUID(), "creador");
        try {
            java.lang.reflect.Field id = Empresa.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(empresa, tenantId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        if (colorInicial != null) {
            empresa.actualizarBranding(null, null, colorInicial, "seed");
        }
        return empresa;
    }

    @Property(tries = 100, afterFailure = AfterFailureMode.PREVIOUS_SEED)
    void colorSeResuelvePorTenantContextNoPorPeticion(
            @ForAll("colorHex") String colorA,
            @ForAll("colorHex") String colorB,
            @ForAll("colorHex") String nuevoColorA,
            @ForAll @IntRange(min = 0, max = Integer.MAX_VALUE) int distinguidor) {

        // Fuerza colores distintos entre T_A y T_B: si coinciden, deriva el de B.
        String colorInicialB = colorB.equals(colorA)
                ? String.format(Locale.ROOT, "#%06x", (distinguidor ^ 0x0f0f0f) & 0xFFFFFF)
                : colorB;
        // Asegura que aun tras derivarlo, A != B (evita colision degenerada).
        if (colorInicialB.equals(colorA)) {
            colorInicialB = colorA.equals("#000000") ? "#ffffff" : "#000000";
        }

        Empresa empresaA = empresaConColor(T_A, colorA);
        Empresa empresaB = empresaConColor(T_B, colorInicialB);

        EmpresaRepository empresaRepository = mock(EmpresaRepository.class);
        when(empresaRepository.findById(T_A)).thenReturn(Optional.of(empresaA));
        when(empresaRepository.findById(T_B)).thenReturn(Optional.of(empresaB));
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        ServicioBranding servicio = new ServicioBranding(empresaRepository, auditoria);

        // --- Bajo T_A, se resuelve la Empresa de A y su color ---
        TenantContext.set(T_A);
        assertThat(servicio.consultarBranding().colorPrimario()).isEqualTo(colorA);

        // --- Bajo T_B, se resuelve la Empresa de B y su color ---
        TenantContext.set(T_B);
        assertThat(servicio.consultarBranding().colorPrimario()).isEqualTo(colorInicialB);

        // --- Actualizar bajo T_A (el comando NO lleva tenant) afecta solo a A ---
        TenantContext.set(T_A);
        BrandingDto resultadoA = servicio.actualizarBranding(
                new ActualizarBrandingCommand(null, null, nuevoColorA));
        assertThat(resultadoA.colorPrimario()).isEqualTo(nuevoColorA);
        assertThat(empresaA.getBrandingColorPrimario()).isEqualTo(nuevoColorA);

        // La Empresa de T_B queda intacta: el cambio bajo T_A no la contamina.
        assertThat(empresaB.getBrandingColorPrimario()).isEqualTo(colorInicialB);
        TenantContext.set(T_B);
        assertThat(servicio.consultarBranding().colorPrimario()).isEqualTo(colorInicialB);
    }
}
