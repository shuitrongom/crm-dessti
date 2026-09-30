package com.dessti.crm.compras.proveedor.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dessti.crm.compras.proveedor.adapter.out.persistence.ProveedorRepository;
import com.dessti.crm.compras.proveedor.domain.DatosProveedor;
import com.dessti.crm.compras.proveedor.domain.Proveedor;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias (Mockito) del {@link ServicioProveedores} (Req 29): reglas de
 * unicidad de RFC entre activos (409), validaciones de dominio (422), baja/reactivacion
 * logica y persistencia de los datos fiscales/comerciales de V89. No arranca Spring ni
 * base de datos: dobla el repositorio y la auditoria; el tenant se fija en el contexto.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServicioProveedores (Req 29)")
class ServicioProveedoresTest {

    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final String RFC = "ABC010101AB1";

    @Mock private ProveedorRepository proveedorRepository;
    @Mock private AuditoriaPort auditoria;

    private ServicioProveedores servicio;

    @BeforeEach
    void setUp() {
        servicio = new ServicioProveedores(proveedorRepository, auditoria);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static CrearProveedorCommand comandoCrear() {
        return new CrearProveedorCommand("Aceros del Norte", RFC, "compras@aceros.mx", null,
                "Juan Perez", "601", 30, "Av. Reforma 100", "Monterrey", "Nuevo Leon", "64000");
    }

    @Test
    @DisplayName("crear persiste el proveedor con sus datos fiscales cuando el RFC es unico")
    void crearPersiste() {
        when(proveedorRepository.existsByRfcAndActivoTrue(RFC)).thenReturn(false);
        when(proveedorRepository.saveAndFlush(any(Proveedor.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProveedorDto dto = servicio.crearProveedor(comandoCrear());

        assertThat(dto.rfc()).isEqualTo(RFC);
        assertThat(dto.diasCredito()).isEqualTo(30);
        assertThat(dto.regimenFiscal()).isEqualTo("601");
        assertThat(dto.codigoPostal()).isEqualTo("64000");
        assertThat(dto.personaContacto()).isEqualTo("Juan Perez");
        assertThat(dto.activo()).isTrue();
        verify(proveedorRepository).saveAndFlush(any(Proveedor.class));
    }

    @Test
    @DisplayName("crear rechaza (409) si ya existe un proveedor activo con el mismo RFC")
    void crearRfcDuplicado() {
        when(proveedorRepository.existsByRfcAndActivoTrue(RFC)).thenReturn(true);

        assertThatThrownBy(() -> servicio.crearProveedor(comandoCrear()))
                .isInstanceOf(ConflictoUnicidadException.class);

        verify(proveedorRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("crear rechaza (422) un RFC con formato invalido antes de tocar el repositorio")
    void crearRfcInvalido() {
        CrearProveedorCommand invalido = new CrearProveedorCommand(
                "Aceros del Norte", "RFC-INVALIDO", "compras@aceros.mx", null,
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> servicio.crearProveedor(invalido))
                .isInstanceOf(ReglaNegocioException.class);

        verify(proveedorRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("crear rechaza (422) cuando no hay ningun dato de contacto")
    void crearSinContacto() {
        CrearProveedorCommand sinContacto = new CrearProveedorCommand(
                "Aceros del Norte", RFC, null, null,
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> servicio.crearProveedor(sinContacto))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("contacto");
    }

    @Test
    @DisplayName("desactivar marca inactivo el proveedor activo y lo guarda")
    void desactivar() {
        Proveedor proveedor = Proveedor.crear(new DatosProveedor(
                "Aceros", RFC, "a@b.mx", null, null, null, null, null, null, null, null), "sistema");
        UUID id = proveedor.getId();
        when(proveedorRepository.findByIdAndActivoTrue(id)).thenReturn(Optional.of(proveedor));
        when(proveedorRepository.save(any(Proveedor.class))).thenAnswer(inv -> inv.getArgument(0));

        ProveedorDto dto = servicio.desactivarProveedor(id);

        assertThat(dto.activo()).isFalse();
    }

    @Test
    @DisplayName("reactivar un proveedor inactivo lo marca activo si el RFC esta libre")
    void reactivar() {
        Proveedor proveedor = Proveedor.crear(new DatosProveedor(
                "Aceros", RFC, "a@b.mx", null, null, null, null, null, null, null, null), "sistema");
        proveedor.desactivar("sistema");
        UUID id = proveedor.getId();
        when(proveedorRepository.findById(id)).thenReturn(Optional.of(proveedor));
        when(proveedorRepository.existsByRfcAndActivoTrue(RFC)).thenReturn(false);
        when(proveedorRepository.saveAndFlush(any(Proveedor.class))).thenAnswer(inv -> inv.getArgument(0));

        ProveedorDto dto = servicio.reactivarProveedor(id);

        assertThat(dto.activo()).isTrue();
    }

    @Test
    @DisplayName("reactivar rechaza (409) si otro proveedor activo ya usa el RFC")
    void reactivarRfcEnUso() {
        Proveedor proveedor = Proveedor.crear(new DatosProveedor(
                "Aceros", RFC, "a@b.mx", null, null, null, null, null, null, null, null), "sistema");
        proveedor.desactivar("sistema");
        UUID id = proveedor.getId();
        when(proveedorRepository.findById(id)).thenReturn(Optional.of(proveedor));
        when(proveedorRepository.existsByRfcAndActivoTrue(RFC)).thenReturn(true);

        assertThatThrownBy(() -> servicio.reactivarProveedor(id))
                .isInstanceOf(ConflictoUnicidadException.class);

        verify(proveedorRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("reactivar rechaza (422) si el proveedor ya esta activo")
    void reactivarYaActivo() {
        Proveedor proveedor = Proveedor.crear(new DatosProveedor(
                "Aceros", RFC, "a@b.mx", null, null, null, null, null, null, null, null), "sistema");
        UUID id = proveedor.getId();
        when(proveedorRepository.findById(id)).thenReturn(Optional.of(proveedor));

        assertThatThrownBy(() -> servicio.reactivarProveedor(id))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
