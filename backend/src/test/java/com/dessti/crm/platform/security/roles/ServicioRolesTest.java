package com.dessti.crm.platform.security.roles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.GiroEmpresaPort;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioRoles} (Req 27, 28) centradas en las
 * reglas de negocio criticas del modelo de roles:
 *
 * <ul>
 *   <li>Un {@code Rol_Personalizado} rechaza permisos de nivel plataforma
 *       (Req 28.5).</li>
 *   <li>Un {@code Rol_Personalizado} rechaza permisos inexistentes (Req 28.5).</li>
 *   <li>Los roles predefinidos son inmutables: modificar/eliminar lanza
 *       excepcion (Req 28.6).</li>
 *   <li>El nombre de rol es unico por Empresa: nombre duplicado da conflicto
 *       (Req 28.2).</li>
 *   <li>La creacion valida audita la operacion (Req 28.7).</li>
 * </ul>
 *
 * <p>No usa {@code @SpringBootTest} ni Testcontainers: los colaboradores
 * (repositorios y puerto de auditoria) se sustituyen por dobles de Mockito.</p>
 */
class ServicioRolesTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Clave del Giro de la Empresa del contexto en las pruebas (Req 7.2). */
    private static final String GIRO_EMPRESA = "anuncios-luminosos";

    private RolRepository rolRepository;
    private PermisoRepository permisoRepository;
    private AuditoriaPort auditoria;
    private ClasificadorRecursosVertical clasificadorVertical;
    private GiroEmpresaPort giroEmpresaPort;
    private com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort modulosHabilitados;
    private ServicioRoles servicio;

    @BeforeEach
    void preparar() {
        rolRepository = mock(RolRepository.class);
        permisoRepository = mock(PermisoRepository.class);
        auditoria = mock(AuditoriaPort.class);
        clasificadorVertical = mock(ClasificadorRecursosVertical.class);
        giroEmpresaPort = mock(GiroEmpresaPort.class);
        // Por defecto la Empresa del contexto pertenece al Giro 'anuncios-luminosos'
        // y todo recurso se considera aplicable a su Giro (recurso de Nucleo o del
        // propio vertical); cada prueba de Giro ajeno restringe este comportamiento.
        when(giroEmpresaPort.giroDeTenant(TENANT)).thenReturn(Optional.of(GIRO_EMPRESA));
        when(clasificadorVertical.esRecursoAplicableAGiro(any(), any())).thenReturn(true);
        modulosHabilitados = mock(com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort.class);
        servicio = new ServicioRoles(rolRepository, permisoRepository, auditoria,
                clasificadorVertical, giroEmpresaPort, modulosHabilitados);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    /** Construye un doble de {@link PermisoEntity} con id, recurso y operacion. */
    private static PermisoEntity permiso(UUID id, String recurso, String operacion) {
        PermisoEntity p = mock(PermisoEntity.class);
        when(p.getId()).thenReturn(id);
        when(p.getRecurso()).thenReturn(recurso);
        when(p.getOperacion()).thenReturn(operacion);
        when(p.authority()).thenReturn(recurso + ":" + operacion);
        return p;
    }

    // -----------------------------------------------------------------
    // Req 28.5: rechazo de permisos de nivel plataforma
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Rechaza crear un rol que incluya un permiso de nivel plataforma (Req 28.5)")
    void rechazaPermisoDePlataforma() {
        UUID idEmpresa = UUID.randomUUID();
        // 'empresa' es un recurso de plataforma reservado a super_admin.
        PermisoEntity permisoPlataforma = permiso(idEmpresa, "empresa", "crear");
        when(permisoRepository.findByIdIn(any())).thenReturn(List.of(permisoPlataforma));

        var comando = new CrearRolPersonalizadoCommand("mi-rol", Set.of(idEmpresa));

        assertThatThrownBy(() -> servicio.crearRolPersonalizado(comando))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("plataforma");

        verify(rolRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    // -----------------------------------------------------------------
    // Req 28.5: rechazo de permisos inexistentes
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Rechaza crear un rol que referencie un permiso inexistente (Req 28.5)")
    void rechazaPermisoInexistente() {
        UUID idExistente = UUID.randomUUID();
        UUID idInexistente = UUID.randomUUID();
        PermisoEntity existente = permiso(idExistente, "cliente", "crear");
        // El repositorio solo devuelve el existente: falta uno de los solicitados.
        when(permisoRepository.findByIdIn(any())).thenReturn(List.of(existente));

        var comando = new CrearRolPersonalizadoCommand("mi-rol", Set.of(idExistente, idInexistente));

        assertThatThrownBy(() -> servicio.crearRolPersonalizado(comando))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("inexistentes");

        verify(rolRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rechaza crear un rol sin permisos (debe combinar permisos existentes)")
    void rechazaSinPermisos() {
        var comando = new CrearRolPersonalizadoCommand("mi-rol", Set.of());

        assertThatThrownBy(() -> servicio.crearRolPersonalizado(comando))
                .isInstanceOf(ReglaNegocioException.class);

        verify(permisoRepository, never()).findByIdIn(any());
        verify(rolRepository, never()).save(any());
    }

    // -----------------------------------------------------------------
    // Req 28.6: inmutabilidad de roles predefinidos
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Rechaza modificar los permisos de un rol predefinido (Req 28.6)")
    void rechazaModificarRolPredefinido() {
        UUID rolId = UUID.randomUUID();
        Rol predefinido = mock(Rol.class);
        when(predefinido.isPredefinido()).thenReturn(true);
        when(rolRepository.findById(rolId)).thenReturn(java.util.Optional.of(predefinido));

        assertThatThrownBy(() -> servicio.actualizarPermisos(rolId, Set.of(UUID.randomUUID())))
                .isInstanceOf(RolPredefinidoInmutableException.class);

        verify(rolRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("Rechaza eliminar un rol predefinido (Req 28.6)")
    void rechazaEliminarRolPredefinido() {
        UUID rolId = UUID.randomUUID();
        Rol predefinido = mock(Rol.class);
        when(predefinido.isPredefinido()).thenReturn(true);
        when(rolRepository.findById(rolId)).thenReturn(java.util.Optional.of(predefinido));

        assertThatThrownBy(() -> servicio.eliminarRolPersonalizado(rolId))
                .isInstanceOf(RolPredefinidoInmutableException.class);

        verify(rolRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Devuelve 404 al gestionar un rol inexistente en la Empresa (Req 23)")
    void rolInexistenteEsNoEncontrado() {
        UUID rolId = UUID.randomUUID();
        when(rolRepository.findById(rolId)).thenReturn(java.util.Optional.empty());
        when(rolRepository.findByIdAndTenantId(rolId, TENANT)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> servicio.eliminarRolPersonalizado(rolId))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // -----------------------------------------------------------------
    // Req 28.2: unicidad del nombre por Empresa
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Rechaza crear un rol con nombre ya existente en la Empresa (Req 28.2)")
    void rechazaNombreDuplicadoPorTenant() {
        UUID idPermiso = UUID.randomUUID();
        PermisoEntity valido = permiso(idPermiso, "cliente", "leer");
        when(permisoRepository.findByIdIn(any())).thenReturn(List.of(valido));
        when(rolRepository.existsByTenantIdAndNombre(TENANT, "ventas-jr")).thenReturn(true);

        var comando = new CrearRolPersonalizadoCommand("ventas-jr", Set.of(idPermiso));

        assertThatThrownBy(() -> servicio.crearRolPersonalizado(comando))
                .isInstanceOf(ConflictoUnicidadException.class);

        verify(rolRepository, never()).save(any());
    }

    @Test
    @DisplayName("Traduce la violacion del indice de unicidad de la BD a 409 (Req 28.2)")
    void traduceViolacionUnicidadDeLaBaseDeDatos() {
        UUID idPermiso = UUID.randomUUID();
        PermisoEntity valido = permiso(idPermiso, "cliente", "leer");
        when(permisoRepository.findByIdIn(any())).thenReturn(List.of(valido));
        when(rolRepository.existsByTenantIdAndNombre(TENANT, "ventas-jr")).thenReturn(false);
        when(rolRepository.save(any())).thenThrow(new DataIntegrityViolationException("uq_rol_nombre_por_tenant"));

        var comando = new CrearRolPersonalizadoCommand("ventas-jr", Set.of(idPermiso));

        assertThatThrownBy(() -> servicio.crearRolPersonalizado(comando))
                .isInstanceOf(ConflictoUnicidadException.class);
    }

    // -----------------------------------------------------------------
    // Req 28.2 + 28.7: creacion valida persiste y audita
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Crea un Rol_Personalizado valido, lo persiste y audita la operacion (Req 28.2, 28.7)")
    void creaRolValidoYAudita() {
        UUID idPermiso = UUID.randomUUID();
        PermisoEntity valido = permiso(idPermiso, "cliente", "crear");
        when(permisoRepository.findByIdIn(any())).thenReturn(List.of(valido));
        when(rolRepository.existsByTenantIdAndNombre(TENANT, "comercial-junior")).thenReturn(false);
        when(rolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new CrearRolPersonalizadoCommand("comercial-junior", Set.of(idPermiso));

        UUID id = servicio.crearRolPersonalizado(comando);

        assertThat(id).isNotNull();

        ArgumentCaptor<Rol> rolCaptor = ArgumentCaptor.forClass(Rol.class);
        verify(rolRepository).save(rolCaptor.capture());
        Rol persistido = rolCaptor.getValue();
        assertThat(persistido.getTenantId()).isEqualTo(TENANT);
        assertThat(persistido.isPredefinido()).isFalse();
        assertThat(persistido.getNombre()).isEqualTo("comercial-junior");
        assertThat(persistido.getPermisos()).containsExactly(valido);

        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventoCaptor.capture());
        EventoAuditoria evento = eventoCaptor.getValue();
        assertThat(evento.tenantId()).contains(TENANT);
        assertThat(evento.accion()).isEqualTo("crear");
        assertThat(evento.recurso()).isEqualTo(ServicioRoles.RECURSO_ROL);
    }

    // -----------------------------------------------------------------
    // Req 7.4: rechazo de permisos de un vertical ajeno al Giro
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Rechaza crear un rol con un permiso de un vertical ajeno al Giro de la Empresa (Req 7.4)")
    void rechazaPermisoDeVerticalAjeno() {
        UUID idPermiso = UUID.randomUUID();
        // 'bom' es un recurso del vertical 'manufactura', ajeno al Giro de la Empresa.
        PermisoEntity permisoAjeno = permiso(idPermiso, "bom", "crear");
        when(permisoRepository.findByIdIn(any())).thenReturn(List.of(permisoAjeno));
        // El recurso NO es aplicable al Giro de la Empresa.
        when(clasificadorVertical.esRecursoAplicableAGiro("bom", GIRO_EMPRESA)).thenReturn(false);

        var comando = new CrearRolPersonalizadoCommand("mi-rol", Set.of(idPermiso));

        assertThatThrownBy(() -> servicio.crearRolPersonalizado(comando))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("vertical ajeno")
                .hasMessageContaining("bom:crear");

        verify(rolRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("Rechaza actualizar los permisos de un rol con un permiso de vertical ajeno (Req 7.4)")
    void rechazaActualizarConPermisoDeVerticalAjeno() {
        UUID rolId = UUID.randomUUID();
        Rol personalizado = mock(Rol.class);
        when(rolRepository.findById(rolId)).thenReturn(Optional.of(personalizado));
        when(rolRepository.findByIdAndTenantId(rolId, TENANT)).thenReturn(Optional.of(personalizado));

        UUID idPermiso = UUID.randomUUID();
        PermisoEntity permisoAjeno = permiso(idPermiso, "orden_produccion", "crear");
        when(permisoRepository.findByIdIn(any())).thenReturn(List.of(permisoAjeno));
        when(clasificadorVertical.esRecursoAplicableAGiro("orden_produccion", GIRO_EMPRESA))
                .thenReturn(false);

        assertThatThrownBy(() -> servicio.actualizarPermisos(rolId, Set.of(idPermiso)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("vertical ajeno");

        verify(rolRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    // -----------------------------------------------------------------
    // Req 7.2: permisos de Nucleo o del vertical del propio Giro -> OK
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Acepta crear un rol con permisos de Nucleo y del vertical del propio Giro (Req 7.2)")
    void aceptaPermisosDeNucleoYDelVerticalPropio() {
        UUID idNucleo = UUID.randomUUID();
        UUID idVerticalPropio = UUID.randomUUID();
        // 'cliente' es de Nucleo; 'orden_fabricacion' es del vertical del propio Giro.
        PermisoEntity permisoNucleo = permiso(idNucleo, "cliente", "leer");
        PermisoEntity permisoVerticalPropio = permiso(idVerticalPropio, "orden_fabricacion", "crear");
        when(permisoRepository.findByIdIn(any()))
                .thenReturn(List.of(permisoNucleo, permisoVerticalPropio));
        when(clasificadorVertical.esRecursoAplicableAGiro("cliente", GIRO_EMPRESA)).thenReturn(true);
        when(clasificadorVertical.esRecursoAplicableAGiro("orden_fabricacion", GIRO_EMPRESA))
                .thenReturn(true);
        when(rolRepository.existsByTenantIdAndNombre(TENANT, "operacion-anuncios")).thenReturn(false);
        when(rolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new CrearRolPersonalizadoCommand(
                "operacion-anuncios", Set.of(idNucleo, idVerticalPropio));

        UUID id = servicio.crearRolPersonalizado(comando);

        assertThat(id).isNotNull();
        verify(rolRepository).save(any());
        verify(auditoria).registrar(any());
    }

    // -----------------------------------------------------------------
    // Roles asignables filtrados por modulos contratados (plataforma-multigiro)
    // -----------------------------------------------------------------

    /**
     * Doble de {@link Rol} predefinido de empresa (tenant NULL) con id y nombre.
     * Se construye y estubea COMPLETAMENTE antes de devolverse, para no anidar el
     * estubado de este mock dentro del {@code when(...).thenReturn(...)} del
     * repositorio (lo que Mockito detecta como "unfinished stubbing").
     */
    private static Rol rolPredefinido(String nombre) {
        Rol r = mock(Rol.class);
        when(r.getId()).thenReturn(UUID.randomUUID());
        when(r.getNombre()).thenReturn(nombre);
        when(r.getTenantId()).thenReturn(null);
        return r;
    }

    /** Stub laxo: cualquier nombre de rol predefinido resuelve a un doble. */
    private void stubTodosLosRolesPredefinidos() {
        for (var entrada : com.dessti.crm.platform.security.roles.RolModuloCatalogo.todos()) {
            // Primero se crea el doble (con su propio estubado) y LUEGO se estubea
            // el repositorio, evitando anidar when() dentro de otro when().
            Rol doble = rolPredefinido(entrada.nombreRol());
            when(rolRepository.findByNombreAndTenantIdIsNull(entrada.nombreRol()))
                    .thenReturn(Optional.of(doble));
        }
    }

    @Test
    @DisplayName("GET roles/asignables con solo 'estrategia' contratado devuelve unicamente los transversales")
    void asignablesSoloTransversalesCuandoSoloEstrategia() {
        stubTodosLosRolesPredefinidos();
        when(modulosHabilitados.modulosHabilitadosDe(TENANT)).thenReturn(List.of("estrategia"));

        List<com.dessti.crm.platform.security.roles.RolAsignableDto> asignables =
                servicio.listarRolesAsignables();

        assertThat(asignables).extracting(
                com.dessti.crm.platform.security.roles.RolAsignableDto::nombre)
                .containsExactly("admin_empresa", "director", "gerente", "supervisor");
        // Los transversales exponen modulo null.
        assertThat(asignables).allSatisfy(dto -> assertThat(dto.modulo()).isNull());
    }

    @Test
    @DisplayName("GET roles/asignables con 'comercial' contratado incluye 'ventas' (rol de modulo)")
    void asignablesIncluyeVentasConComercial() {
        stubTodosLosRolesPredefinidos();
        when(modulosHabilitados.modulosHabilitadosDe(TENANT)).thenReturn(List.of("comercial"));

        List<com.dessti.crm.platform.security.roles.RolAsignableDto> asignables =
                servicio.listarRolesAsignables();

        assertThat(asignables).extracting(
                com.dessti.crm.platform.security.roles.RolAsignableDto::nombre)
                // Transversales + los roles de 'comercial': ventas (operativo) y
                // gerente_comercial (aprobador de cotizaciones/oportunidades).
                .contains("admin_empresa", "director", "gerente", "supervisor",
                        "ventas", "gerente_comercial")
                // Roles de otros modulos NO contratados no aparecen; tampoco
                // super_admin (plataforma).
                .doesNotContain("contabilidad", "rh", "marketing", "super_admin",
                        "gerente_compras", "contador_general", "tesorero", "gerente_rh",
                        "gerente_operaciones", "gerente_calidad", "calidad");
        // 'ventas' expone su modulo representativo 'comercial'.
        assertThat(asignables)
                .filteredOn(dto -> dto.nombre().equals("ventas"))
                .singleElement()
                .satisfies(dto -> assertThat(dto.modulo()).isEqualTo("comercial"));
    }

    @Test
    @DisplayName("GET roles/asignables nunca incluye super_admin (rol de plataforma)")
    void asignablesNuncaIncluyeSuperAdmin() {
        stubTodosLosRolesPredefinidos();
        when(modulosHabilitados.modulosHabilitadosDe(TENANT)).thenReturn(List.of(
                "comercial", "operacion", "compras", "inventario-avanzado", "mantenimiento",
                "facturacion", "contabilidad", "tesoreria", "activos-fijos", "rh-nomina",
                "redes-sociales"));

        List<com.dessti.crm.platform.security.roles.RolAsignableDto> asignables =
                servicio.listarRolesAsignables();

        assertThat(asignables).extracting(
                com.dessti.crm.platform.security.roles.RolAsignableDto::nombre)
                .doesNotContain("super_admin")
                // Con todos los modulos de negocio, aparecen todos los roles de
                // empresa: transversales, operativos y los gerentes/aprobadores
                // (segregacion de funciones, V62).
                .contains("admin_empresa", "director", "gerente", "supervisor",
                        "ventas", "diseno", "produccion", "almacen", "instalacion",
                        "mantenimiento", "contabilidad", "rh", "marketing", "calidad",
                        "gerente_comercial", "gerente_compras", "contador_general",
                        "tesorero", "gerente_rh", "gerente_operaciones", "gerente_calidad");
    }
}
