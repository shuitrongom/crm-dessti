package com.dessti.crm.platform.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.vertical.ContratoVertical;
import com.dessti.crm.platform.vertical.ItemNavegacionVertical;
import com.dessti.crm.platform.vertical.RegistroVerticales;

/**
 * Pruebas unitarias acotadas del evaluador RBAC {@link Autorizador} (Req 3, 25.4,
 * 6): verifican la denegacion por defecto, la concesion cuando existe el permiso
 * atomico, el gating por Plan y el <strong>gating por Giro</strong>
 * (doble gating, Req 6.1-6.5).
 */
class AutorizadorTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String GIRO_ANUNCIOS = "anuncios-luminosos";
    private static final String MODULO_VERTICAL = "anuncios";
    private static final String MODULO_NUCLEO = "facturacion";

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /**
     * Registro con un unico vertical que aporta el Giro {@code anuncios-luminosos}
     * y el modulo {@code anuncios}; cualquier otro modulo (p. ej. {@code facturacion})
     * queda sin Giro asociado y por tanto se considera de Nucleo.
     */
    private RegistroVerticales registroConAnuncios() {
        ContratoVertical anuncios = new ContratoVertical() {
            @Override
            public String giro() {
                return GIRO_ANUNCIOS;
            }

            @Override
            public Set<String> modulos() {
                return Set.of(MODULO_VERTICAL);
            }

            @Override
            public Set<String> recursos() {
                return Set.of();
            }

            @Override
            public List<ItemNavegacionVertical> navegacion() {
                return List.of();
            }
        };
        return new RegistroVerticales(List.of(anuncios));
    }

    private Autorizador autorizadorConGating(boolean moduloHabilitado) {
        return new Autorizador(
                (tenantId, modulo) -> moduloHabilitado,
                registroConAnuncios(),
                tenantId -> Optional.empty(),
                mock(AuditoriaPort.class));
    }

    private Autorizador autorizador(GiroEmpresaPort giroEmpresa, AuditoriaPort auditoria) {
        return new Autorizador(
                (tenantId, modulo) -> true,
                registroConAnuncios(),
                giroEmpresa,
                auditoria);
    }

    private void autenticarCon(String... authorities) {
        var auth = new UsernamePasswordAuthenticationToken(
                "usuario@empresa", "n/a",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ------------------------------------------------------------------
    // RBAC atomico y gating por Plan (existentes)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Concede cuando el Usuario posee el permiso atomico requerido")
    void permiteCuandoTienePermiso() {
        autenticarCon("cliente:crear", "cliente:leer");
        Autorizador autorizador = autorizadorConGating(true);

        assertThat(autorizador.tiene("cliente", "crear")).isTrue();
    }

    @Test
    @DisplayName("Deniega cuando el Usuario carece del permiso (-> 403)")
    void deniegaCuandoNoTienePermiso() {
        autenticarCon("cliente:leer");
        Autorizador autorizador = autorizadorConGating(true);

        assertThat(autorizador.tiene("cliente", "crear")).isFalse();
    }

    @Test
    @DisplayName("Deniega por defecto cuando no hay autenticacion (-> 403)")
    void deniegaSinAutenticacion() {
        Autorizador autorizador = autorizadorConGating(true);

        assertThat(autorizador.tiene("cliente", "crear")).isFalse();
    }

    @Test
    @DisplayName("Deniega cuando la autenticacion es anonima (-> 403)")
    void deniegaAnonimo() {
        var anon = new AnonymousAuthenticationToken(
                "clave", "anonimo", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anon);
        Autorizador autorizador = autorizadorConGating(true);

        assertThat(autorizador.tiene("cliente", "crear")).isFalse();
    }

    @Test
    @DisplayName("La comparacion de permisos es insensible a mayusculas")
    void permisoInsensibleAMayusculas() {
        autenticarCon("Factura:Timbrar");
        Autorizador autorizador = autorizadorConGating(true);

        assertThat(autorizador.tiene("factura", "timbrar")).isTrue();
    }

    @Test
    @DisplayName("Gating por Plan: modulo habilitado permite")
    void gatingModuloHabilitado() {
        autenticarCon("factura:timbrar");
        TenantContext.set(TENANT);
        Autorizador autorizador = autorizadorConGating(true);

        assertThat(autorizador.moduloHabilitado("facturacion")).isTrue();
    }

    @Test
    @DisplayName("Gating por Plan: modulo no habilitado deniega (-> 403)")
    void gatingModuloNoHabilitado() {
        autenticarCon("factura:timbrar");
        TenantContext.set(TENANT);
        Autorizador autorizador = autorizadorConGating(false);

        assertThat(autorizador.moduloHabilitado("facturacion")).isFalse();
    }

    @Test
    @DisplayName("Gating por Plan: sin tenant en contexto deniega")
    void gatingSinTenantDeniega() {
        autenticarCon("factura:timbrar");
        Autorizador autorizador = autorizadorConGating(true);

        assertThat(autorizador.moduloHabilitado("facturacion")).isFalse();
    }

    @Test
    @DisplayName("mismoTenant refuerza la evaluacion intra-tenant (Req 23.5)")
    void mismoTenant() {
        TenantContext.set(TENANT);
        Autorizador autorizador = autorizadorConGating(true);

        assertThat(autorizador.mismoTenant(TENANT)).isTrue();
        assertThat(autorizador.mismoTenant(UUID.randomUUID())).isFalse();
    }

    // ------------------------------------------------------------------
    // Gating por Giro (Req 6.1-6.5)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Gating por Giro: un modulo de Nucleo siempre corresponde y no audita (Req 6.4)")
    void giroCorrespondeNucleoSiempreTrueSinAuditar() {
        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        GiroEmpresaPort giroEmpresa = mock(GiroEmpresaPort.class);
        autenticarCon("factura:timbrar");
        TenantContext.set(TENANT);
        Autorizador autorizador = autorizador(giroEmpresa, auditoria);

        assertThat(autorizador.giroCorresponde(MODULO_NUCLEO)).isTrue();
        verify(auditoria, never()).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("Gating por Giro: modulo de vertical cuyo Giro coincide con el de la Empresa permite (Req 6.1, 6.2)")
    void giroCorrespondeVerticalGiroCoincidente() {
        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        GiroEmpresaPort giroEmpresa = mock(GiroEmpresaPort.class);
        when(giroEmpresa.giroDeTenant(TENANT)).thenReturn(Optional.of(GIRO_ANUNCIOS));
        autenticarCon("prueba_diseno:crear");
        TenantContext.set(TENANT);
        Autorizador autorizador = autorizador(giroEmpresa, auditoria);

        assertThat(autorizador.giroCorresponde(MODULO_VERTICAL)).isTrue();
        verify(auditoria, never()).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("Gating por Giro: la comparacion de Giro es insensible a mayusculas/espacios")
    void giroCorrespondeVerticalNormalizaClaves() {
        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        GiroEmpresaPort giroEmpresa = mock(GiroEmpresaPort.class);
        when(giroEmpresa.giroDeTenant(TENANT)).thenReturn(Optional.of("  Anuncios-Luminosos  "));
        autenticarCon("prueba_diseno:crear");
        TenantContext.set(TENANT);
        Autorizador autorizador = autorizador(giroEmpresa, auditoria);

        assertThat(autorizador.giroCorresponde(MODULO_VERTICAL)).isTrue();
    }

    @Test
    @DisplayName("Gating por Giro: modulo de vertical ajeno al Giro deniega y audita la denegacion (Req 6.3, 6.5)")
    void giroCorrespondeVerticalGiroAjenoDeniegaYAudita() {
        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        GiroEmpresaPort giroEmpresa = mock(GiroEmpresaPort.class);
        when(giroEmpresa.giroDeTenant(TENANT)).thenReturn(Optional.of("manufactura"));
        autenticarCon("prueba_diseno:crear");
        TenantContext.set(TENANT);
        Autorizador autorizador = autorizador(giroEmpresa, auditoria);

        assertThat(autorizador.giroCorresponde(MODULO_VERTICAL)).isFalse();
        verify(auditoria, times(1)).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("Gating por Giro: sin autenticacion deniega y no audita (deny-by-default)")
    void giroCorrespondeSinAutenticacionDeniega() {
        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        GiroEmpresaPort giroEmpresa = mock(GiroEmpresaPort.class);
        TenantContext.set(TENANT);
        Autorizador autorizador = autorizador(giroEmpresa, auditoria);

        assertThat(autorizador.giroCorresponde(MODULO_VERTICAL)).isFalse();
        verify(auditoria, never()).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("Gating por Giro: sin tenant en contexto deniega y no audita (deny-by-default)")
    void giroCorrespondeSinTenantDeniega() {
        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        GiroEmpresaPort giroEmpresa = mock(GiroEmpresaPort.class);
        autenticarCon("prueba_diseno:crear");
        Autorizador autorizador = autorizador(giroEmpresa, auditoria);

        assertThat(autorizador.giroCorresponde(MODULO_VERTICAL)).isFalse();
        verify(auditoria, never()).registrar(any(EventoAuditoria.class));
    }
}
