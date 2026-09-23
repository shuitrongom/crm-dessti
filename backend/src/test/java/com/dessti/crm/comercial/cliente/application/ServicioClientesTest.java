package com.dessti.crm.comercial.cliente.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.dessti.crm.comercial.cliente.adapter.out.persistence.ClienteRepository;
import com.dessti.crm.comercial.cliente.adapter.out.persistence.ContactoRepository;
import com.dessti.crm.comercial.cliente.domain.Cliente;
import com.dessti.crm.comercial.cliente.domain.Contacto;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioClientes} (Req 5, 4.3, 23). Usan dobles de
 * Mockito; no arrancan contexto de Spring ni base de datos. Cubren: alta exitosa
 * (5.1), rechazo de datos invalidos (5.2 -> 422), RFC duplicado entre activos
 * (5.3 -> 409, pre-check y carrera contra el indice), actualizacion con
 * auditoria (5.4), borrado logico (5.9), asociacion de Contacto a Cliente activo
 * e inactivo (5.5/5.6), listado con filtro (5.7/5.8) y acceso cruzado -> 404 +
 * auditoria (4.3/23.3).
 */
class ServicioClientesTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String RFC = "ABC120101AB1";

    private ClienteRepository clienteRepository;
    private ContactoRepository contactoRepository;
    private AuditoriaPort auditoria;
    private ServicioClientes servicio;

    @BeforeEach
    void setUp() {
        clienteRepository = mock(ClienteRepository.class);
        contactoRepository = mock(ContactoRepository.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioClientes(clienteRepository, contactoRepository, auditoria);
        // El servicio deriva el tenant del contexto (Req 23.4) para auditar.
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CrearClienteCommand comandoValido() {
        return new CrearClienteCommand("Acme S.A.", RFC, "contacto@acme.com", null,
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("crearCliente persiste el Cliente activo con RFC normalizado y audita (Req 5.1, 5.4)")
    void crearClienteExitoso() {
        when(clienteRepository.existsByRfcAndActivoTrue(RFC)).thenReturn(false);
        when(clienteRepository.saveAndFlush(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        ClienteDto dto = servicio.crearCliente(comandoValido());

        assertThat(dto.rfc()).isEqualTo(RFC);
        assertThat(dto.nombre()).isEqualTo("Acme S.A.");
        assertThat(dto.activo()).isTrue();

        verify(clienteRepository).saveAndFlush(any(Cliente.class));
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("crear");
        assertThat(ev.getValue().recurso()).isEqualTo("cliente");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
    }

    @Test
    @DisplayName("crearCliente persiste y devuelve los datos basicos de negocio (Req 5, V59)")
    void crearClienteConDatosBasicos() {
        when(clienteRepository.existsByRfcAndActivoTrue(RFC)).thenReturn(false);
        when(clienteRepository.saveAndFlush(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        CrearClienteCommand comando = new CrearClienteCommand(
                "Acme S.A.", RFC, "contacto@acme.com", null,
                "Acme Marca", "moral", "5598765432",
                "Av. Reforma 100", "Monterrey", "Nuevo Leon", "64000", "Mexico",
                "Cliente preferente");

        ClienteDto dto = servicio.crearCliente(comando);

        assertThat(dto.nombreComercial()).isEqualTo("Acme Marca");
        assertThat(dto.tipoPersona()).isEqualTo("moral");
        assertThat(dto.telefonoAdicional()).isEqualTo("5598765432");
        assertThat(dto.direccionCalle()).isEqualTo("Av. Reforma 100");
        assertThat(dto.direccionCiudad()).isEqualTo("Monterrey");
        assertThat(dto.direccionEstado()).isEqualTo("Nuevo Leon");
        assertThat(dto.direccionCp()).isEqualTo("64000");
        assertThat(dto.direccionPais()).isEqualTo("Mexico");
        assertThat(dto.notas()).isEqualTo("Cliente preferente");
    }

    @Test
    @DisplayName("crearCliente acepta telefono de 15 digitos (la cota de 10 la aplica el frontend, Req 5)")
    void crearClienteTelefono15DigitosAceptado() {
        when(clienteRepository.existsByRfcAndActivoTrue(RFC)).thenReturn(false);
        when(clienteRepository.saveAndFlush(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        CrearClienteCommand comando = new CrearClienteCommand(
                "Acme S.A.", RFC, null, "123456789012345",
                null, null, null, null, null, null, null, null, null);

        ClienteDto dto = servicio.crearCliente(comando);

        assertThat(dto.telefono()).isEqualTo("123456789012345");
        verify(clienteRepository).saveAndFlush(any(Cliente.class));
    }

    @Test
    @DisplayName("actualizarCliente modifica los datos basicos de negocio (Req 5.4, V59)")
    void actualizarClienteConDatosBasicos() {
        Cliente existente = Cliente.crear("Acme", RFC, "a@b.com", null, "ventas");
        when(clienteRepository.findByIdAndActivoTrue(existente.getId())).thenReturn(Optional.of(existente));
        when(clienteRepository.existsByRfcAndActivoTrue(any())).thenReturn(false);
        when(clienteRepository.saveAndFlush(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        ClienteDto dto = servicio.actualizarCliente(existente.getId(),
                new ActualizarClienteCommand("Acme", RFC, "a@b.com", null,
                        "Marca Nueva", "fisica", "5512345678",
                        "Calle 1", "CDMX", "CDMX", "01000", "Mexico", "notas nuevas"));

        assertThat(dto.nombreComercial()).isEqualTo("Marca Nueva");
        assertThat(dto.tipoPersona()).isEqualTo("fisica");
        assertThat(dto.telefonoAdicional()).isEqualTo("5512345678");
        assertThat(dto.direccionCiudad()).isEqualTo("CDMX");
        assertThat(dto.notas()).isEqualTo("notas nuevas");
    }

    @Test
    @DisplayName("crearCliente rechaza datos obligatorios invalidos con 422 y no persiste (Req 5.2)")
    void crearClienteDatosInvalidos() {
        CrearClienteCommand invalido = new CrearClienteCommand("Acme", "RFC-MALO", "a@b.com", null,
                null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> servicio.crearCliente(invalido))
                .isInstanceOf(ReglaNegocioException.class);

        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("crearCliente rechaza RFC duplicado entre activos con 409 (pre-check, Req 5.3)")
    void crearClienteRfcDuplicadoPrecheck() {
        when(clienteRepository.existsByRfcAndActivoTrue(RFC)).thenReturn(true);

        assertThatThrownBy(() -> servicio.crearCliente(comandoValido()))
                .isInstanceOf(ConflictoUnicidadException.class);

        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("crearCliente traduce la carrera contra el indice unico a 409 (Req 5.3)")
    void crearClienteRfcDuplicadoCarrera() {
        when(clienteRepository.existsByRfcAndActivoTrue(RFC)).thenReturn(false);
        when(clienteRepository.saveAndFlush(any(Cliente.class)))
                .thenThrow(new DataIntegrityViolationException("uq_cliente_rfc_activo_por_tenant"));

        assertThatThrownBy(() -> servicio.crearCliente(comandoValido()))
                .isInstanceOf(ConflictoUnicidadException.class);
    }

    @Test
    @DisplayName("actualizarCliente persiste los cambios y audita (Req 5.4)")
    void actualizarClienteAudita() {
        Cliente existente = Cliente.crear("Acme", RFC, "a@b.com", null, "ventas");
        when(clienteRepository.findByIdAndActivoTrue(existente.getId())).thenReturn(Optional.of(existente));
        when(clienteRepository.existsByRfcAndActivoTrue(any())).thenReturn(false);
        when(clienteRepository.saveAndFlush(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        ClienteDto dto = servicio.actualizarCliente(existente.getId(),
                new ActualizarClienteCommand("Acme Nueva", RFC, "a@b.com", null,
                        null, null, null, null, null, null, null, null, null));

        assertThat(dto.nombre()).isEqualTo("Acme Nueva");
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("actualizar");
    }

    @Test
    @DisplayName("desactivarCliente realiza el borrado logico y audita (Req 5.9)")
    void desactivarClienteAudita() {
        Cliente existente = Cliente.crear("Acme", RFC, "a@b.com", null, "ventas");
        when(clienteRepository.findByIdAndActivoTrue(existente.getId())).thenReturn(Optional.of(existente));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        ClienteDto dto = servicio.desactivarCliente(existente.getId());

        assertThat(dto.activo()).isFalse();
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("eliminar");
    }

    @Test
    @DisplayName("consultarCliente devuelve 404 y audita el acceso cruzado cuando no es accesible (Req 4.3, 23.3)")
    void consultarClienteAccesoCruzado() {
        UUID ajeno = UUID.randomUUID();
        when(clienteRepository.findByIdAndActivoTrue(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultarCliente(ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class);

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("cliente");
    }

    @Test
    @DisplayName("listarClientes normaliza el filtro a minusculas y proyecta a DTO (Req 5.7, 5.8)")
    void listarClientesFiltra() {
        Cliente c = Cliente.crear("Acme", RFC, "a@b.com", null, "ventas");
        Pageable pageable = PageRequest.of(0, 20);
        Page<Cliente> pagina = new PageImpl<>(List.of(c), pageable, 1);
        when(clienteRepository.buscarActivosPorNombreORfc(eq("acme"), any(Pageable.class)))
                .thenReturn(pagina);

        Page<ClienteDto> resultado = servicio.listarClientes("  ACME  ", pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).nombre()).isEqualTo("Acme");
        verify(clienteRepository).buscarActivosPorNombreORfc(eq("acme"), any(Pageable.class));
    }

    @Test
    @DisplayName("asociarContacto asocia a un Cliente activo y audita (Req 5.5)")
    void asociarContactoAClienteActivo() {
        Cliente cliente = Cliente.crear("Acme", RFC, "a@b.com", null, "ventas");
        when(clienteRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
        when(contactoRepository.save(any(Contacto.class))).thenAnswer(inv -> inv.getArgument(0));

        ContactoDto dto = servicio.asociarContacto(
                new CrearContactoCommand(cliente.getId(), "Juan Perez", "juan@acme.com", null));

        assertThat(dto.clienteId()).isEqualTo(cliente.getId());
        assertThat(dto.nombre()).isEqualTo("Juan Perez");
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().recurso()).isEqualTo("contacto");
        assertThat(ev.getValue().accion()).isEqualTo("crear");
    }

    @Test
    @DisplayName("asociarContacto a un Cliente inactivo se rechaza con 422 (Req 5.6)")
    void asociarContactoAClienteInactivo() {
        Cliente cliente = Cliente.crear("Acme", RFC, "a@b.com", null, "ventas");
        cliente.desactivar("ventas");
        when(clienteRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));

        assertThatThrownBy(() -> servicio.asociarContacto(
                new CrearContactoCommand(cliente.getId(), "Juan", "j@a.com", null)))
                .isInstanceOf(ReglaNegocioException.class);

        verify(contactoRepository, never()).save(any());
    }

    @Test
    @DisplayName("asociarContacto a un Cliente inexistente/otro tenant devuelve 404 y audita (Req 5.6, 23.3)")
    void asociarContactoAClienteInexistente() {
        UUID ajeno = UUID.randomUUID();
        when(clienteRepository.findById(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asociarContacto(
                new CrearContactoCommand(ajeno, "Juan", "j@a.com", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(contactoRepository, never()).save(any());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
    }
}
