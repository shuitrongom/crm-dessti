package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioBranding} (Req 26). Usan dobles de
 * Mockito y establecen el {@link TenantContext} del hilo; no arrancan contexto
 * de Spring ni base de datos. Verifican que la actualizacion aplica el nombre
 * visible y el logotipo y audita (Req 26.1, 26.3), que el vaciado
 * ({@code null}/blanco) limpia los campos, que un logotipo desmesurado se
 * rechaza con 422, que la consulta devuelve los valores vigentes (Req 26.2) y
 * que una Empresa inexistente produce 404.
 */
class ServicioBrandingTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private EmpresaRepository empresaRepository;
    private AuditoriaPort auditoria;
    private ServicioBranding servicio;

    @BeforeEach
    void setUp() {
        empresaRepository = mock(EmpresaRepository.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioBranding(empresaRepository, auditoria);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * Crea una Empresa cuya PK es el tenant del contexto, para que
     * {@code findById(tenantId)} la localice como su propio tenant.
     */
    private Empresa empresaDelTenant() {
        Empresa empresa = Empresa.crear("Anuncios del Norte", "ANO120101AB1",
                java.util.UUID.randomUUID(), "creador");
        try {
            java.lang.reflect.Field id = Empresa.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(empresa, TENANT);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return empresa;
    }

    @Test
    @DisplayName("actualizarBranding fija nombre visible y logotipo, persiste y audita (Req 26.1, 26.3)")
    void actualizaNombreVisibleYLogo() {
        Empresa empresa = empresaDelTenant();
        when(empresaRepository.findById(TENANT)).thenReturn(Optional.of(empresa));
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));

        BrandingDto dto = servicio.actualizarBranding(
                new ActualizarBrandingCommand("  Luminosos MX  ", "https://cdn.example.com/logo.png", null));

        // Nombre visible normalizado (recortado) y logotipo aplicados.
        assertThat(dto.nombreVisible()).isEqualTo("Luminosos MX");
        assertThat(dto.logo()).isEqualTo("https://cdn.example.com/logo.png");
        assertThat(empresa.getBrandingNombreVisible()).isEqualTo("Luminosos MX");
        assertThat(empresa.getBrandingLogo()).isEqualTo("https://cdn.example.com/logo.png");
        verify(empresaRepository).save(empresa);

        // Auditoria de empresa (Req 26.3): recurso branding, accion actualizar,
        // tenant presente y sin volcar el logotipo completo en el detalle.
        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        EventoAuditoria evento = captor.getValue();
        assertThat(evento.tenantId()).contains(TENANT);
        assertThat(evento.accion()).isEqualTo("actualizar");
        assertThat(evento.recurso()).isEqualTo("branding");
        assertThat(evento.detalle()).contains("logo establecido");
        assertThat(evento.detalle()).doesNotContain("https://cdn.example.com/logo.png");
        // Los campos JSONB (valor_anterior/valor_nuevo) DEBEN ir nulos: el
        // resumen legible vive en 'detalle' (TEXT). Volcar texto plano en el
        // JSONB producia el error 22P02 de PostgreSQL -> HTTP 500 (bug corregido).
        assertThat(evento.valorAnterior()).isNull();
        assertThat(evento.valorNuevo()).isNull();
    }

    @Test
    @DisplayName("actualizarBranding NO lanza y audita con valor_anterior/valor_nuevo nulos (JSONB valido, bug 22P02 corregido)")
    void actualizarNoLanzaYAuditaConJsonValido() {
        Empresa empresa = empresaDelTenant();
        empresa.actualizarBranding("Anuncios Luminosos", null, null, "actor");
        when(empresaRepository.findById(TENANT)).thenReturn(Optional.of(empresa));
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));

        // Con texto plano (no-JSON) en el nombre visible; antes esto derivaba en
        // un intento de escribir texto plano en columnas JSONB (500). Ahora no lanza.
        org.assertj.core.api.Assertions.assertThatCode(() -> servicio.actualizarBranding(
                new ActualizarBrandingCommand("Nuevo Nombre (sin JSON)", null, null)))
                .doesNotThrowAnyException();

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        EventoAuditoria evento = captor.getValue();
        // Los valores JSONB son null (validos); el texto legible va en 'detalle'.
        assertThat(evento.valorAnterior()).isNull();
        assertThat(evento.valorNuevo()).isNull();
        assertThat(evento.detalle()).contains("Anuncios Luminosos");
        assertThat(evento.detalle()).contains("Nuevo Nombre (sin JSON)");
    }

    @Test
    @DisplayName("actualizarBranding con valores en blanco limpia el branding (almacena null)")
    void limpiaBrandingConValoresEnBlanco() {
        Empresa empresa = empresaDelTenant();
        empresa.actualizarBranding("Marca Previa", "https://cdn.example.com/prev.png", null, "actor");
        when(empresaRepository.findById(TENANT)).thenReturn(Optional.of(empresa));
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));

        BrandingDto dto = servicio.actualizarBranding(new ActualizarBrandingCommand("   ", null, null));

        assertThat(dto.nombreVisible()).isNull();
        assertThat(dto.logo()).isNull();
        assertThat(empresa.getBrandingNombreVisible()).isNull();
        assertThat(empresa.getBrandingLogo()).isNull();

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        assertThat(captor.getValue().detalle()).contains("logo eliminado");
    }

    @Test
    @DisplayName("actualizarBranding rechaza un logotipo desmesurado con 422 (ReglaNegocioException)")
    void rechazaLogoDesmesurado() {
        Empresa empresa = empresaDelTenant();
        when(empresaRepository.findById(TENANT)).thenReturn(Optional.of(empresa));
        String logoEnorme = "a".repeat(Empresa.LONGITUD_MAXIMA_LOGO + 1);

        assertThatThrownBy(() -> servicio.actualizarBranding(
                new ActualizarBrandingCommand("Marca", logoEnorme, null)))
                .isInstanceOf(ReglaNegocioException.class);

        // No se persiste ni se audita si el dominio rechaza la entrada.
        verify(empresaRepository, never()).save(any(Empresa.class));
        verify(auditoria, never()).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("consultarBranding devuelve el nombre visible y el logotipo vigentes (Req 26.2)")
    void consultaDevuelveValoresVigentes() {
        Empresa empresa = empresaDelTenant();
        empresa.actualizarBranding("Luminosos MX", "https://cdn.example.com/logo.png", null, "actor");
        when(empresaRepository.findById(TENANT)).thenReturn(Optional.of(empresa));

        BrandingDto dto = servicio.consultarBranding();

        assertThat(dto.nombreVisible()).isEqualTo("Luminosos MX");
        assertThat(dto.logo()).isEqualTo("https://cdn.example.com/logo.png");
        verify(auditoria, never()).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("consultarBranding sin personalizar devuelve campos nulos")
    void consultaSinPersonalizarDevuelveNulos() {
        Empresa empresa = empresaDelTenant();
        when(empresaRepository.findById(TENANT)).thenReturn(Optional.of(empresa));

        BrandingDto dto = servicio.consultarBranding();

        assertThat(dto.nombreVisible()).isNull();
        assertThat(dto.logo()).isNull();
    }

    @Test
    @DisplayName("actualizarBranding con Empresa inexistente produce 404 (RecursoNoEncontradoException)")
    void empresaInexistenteProduce404EnActualizar() {
        when(empresaRepository.findById(TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizarBranding(
                new ActualizarBrandingCommand("Marca", null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(empresaRepository, never()).save(any(Empresa.class));
        verify(auditoria, never()).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("consultarBranding con Empresa inexistente produce 404 (RecursoNoEncontradoException)")
    void empresaInexistenteProduce404EnConsultar() {
        when(empresaRepository.findById(TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultarBranding())
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
