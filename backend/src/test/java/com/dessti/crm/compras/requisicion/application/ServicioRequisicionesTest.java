package com.dessti.crm.compras.requisicion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dessti.crm.compras.ordencompra.application.CrearPartidaOrdenCompraCommand;
import com.dessti.crm.compras.ordencompra.application.ServicioOrdenesCompra;
import com.dessti.crm.compras.requisicion.adapter.out.persistence.RequisicionCompraRepository;
import com.dessti.crm.compras.requisicion.domain.EstadoRequisicionCompra;
import com.dessti.crm.compras.requisicion.domain.PartidaRequisicion;
import com.dessti.crm.compras.requisicion.domain.RequisicionCompra;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias (Mockito) del {@link ServicioRequisiciones} (Req 30): alta con
 * validación de material, máquina de estados (incluida la cancelación desde borrador/
 * enviada), y generación de orden de compra solo desde una requisición aprobada. No
 * arranca Spring ni base de datos: dobla el repositorio, el puerto de material, el
 * servicio de órdenes y la auditoría; el tenant se fija en el contexto.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServicioRequisiciones (Req 30)")
class ServicioRequisicionesTest {

    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID MATERIAL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROVEEDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private RequisicionCompraRepository requisicionRepository;
    @Mock private MaterialExistentePort materialExistente;
    @Mock private ServicioOrdenesCompra servicioOrdenesCompra;
    @Mock private AuditoriaPort auditoria;

    private ServicioRequisiciones servicio;

    @BeforeEach
    void setUp() {
        servicio = new ServicioRequisiciones(
                requisicionRepository, materialExistente, servicioOrdenesCompra, auditoria);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static CrearRequisicionCompraCommand comandoCrear() {
        return new CrearRequisicionCompraCommand(
                List.of(new CrearPartidaRequisicionCommand(MATERIAL_ID, 5)));
    }

    /** Construye una requisición de dominio en el estado indicado. */
    private static RequisicionCompra requisicionEn(EstadoRequisicionCompra estado) {
        RequisicionCompra req = RequisicionCompra.crear(
                List.of(PartidaRequisicion.crear(MATERIAL_ID, 5, "sistema")), "sistema");
        // Avanza por la máquina de estados hasta el estado deseado.
        if (estado == EstadoRequisicionCompra.ENVIADA || estado == EstadoRequisicionCompra.APROBADA
                || estado == EstadoRequisicionCompra.RECHAZADA) {
            req.cambiarEstado(EstadoRequisicionCompra.ENVIADA, "sistema");
        }
        if (estado == EstadoRequisicionCompra.APROBADA) {
            req.cambiarEstado(EstadoRequisicionCompra.APROBADA, "sistema");
        }
        if (estado == EstadoRequisicionCompra.RECHAZADA) {
            req.cambiarEstado(EstadoRequisicionCompra.RECHAZADA, "sistema");
        }
        return req;
    }

    @Test
    @DisplayName("crear valida el material y persiste la requisición en borrador")
    void crearPersiste() {
        when(materialExistente.existeMaterialActivo(MATERIAL_ID)).thenReturn(true);
        when(requisicionRepository.save(any(RequisicionCompra.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RequisicionCompraDto dto = servicio.crearRequisicion(comandoCrear());

        assertThat(dto.estado()).isEqualTo("borrador");
        assertThat(dto.partidas()).hasSize(1);
        verify(requisicionRepository).save(any(RequisicionCompra.class));
    }

    @Test
    @DisplayName("crear rechaza (404) si el material no existe o no está activo")
    void crearMaterialInexistente() {
        when(materialExistente.existeMaterialActivo(MATERIAL_ID)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crearRequisicion(comandoCrear()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(requisicionRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear rechaza (422) si no hay ninguna partida")
    void crearSinPartidas() {
        assertThatThrownBy(() -> servicio.crearRequisicion(new CrearRequisicionCompraCommand(List.of())))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("cambiar estado a enviada desde borrador funciona")
    void enviarBorrador() {
        RequisicionCompra req = requisicionEn(EstadoRequisicionCompra.BORRADOR);
        when(requisicionRepository.findById(req.getId())).thenReturn(Optional.of(req));
        when(requisicionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RequisicionCompraDto dto = servicio.cambiarEstado(req.getId(), "enviada");

        assertThat(dto.estado()).isEqualTo("enviada");
    }

    @Test
    @DisplayName("cancelar desde borrador es una transición válida (fix de negocio)")
    void cancelarBorrador() {
        RequisicionCompra req = requisicionEn(EstadoRequisicionCompra.BORRADOR);
        when(requisicionRepository.findById(req.getId())).thenReturn(Optional.of(req));
        when(requisicionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RequisicionCompraDto dto = servicio.cambiarEstado(req.getId(), "cancelada");

        assertThat(dto.estado()).isEqualTo("cancelada");
    }

    @Test
    @DisplayName("cancelar desde enviada es una transición válida")
    void cancelarEnviada() {
        RequisicionCompra req = requisicionEn(EstadoRequisicionCompra.ENVIADA);
        when(requisicionRepository.findById(req.getId())).thenReturn(Optional.of(req));
        when(requisicionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RequisicionCompraDto dto = servicio.cambiarEstado(req.getId(), "cancelada");

        assertThat(dto.estado()).isEqualTo("cancelada");
    }

    @Test
    @DisplayName("una transición inválida (aprobar desde borrador) se rechaza con 409")
    void transicionInvalida() {
        RequisicionCompra req = requisicionEn(EstadoRequisicionCompra.BORRADOR);
        when(requisicionRepository.findById(req.getId())).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> servicio.cambiarEstado(req.getId(), "aprobada"))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    @DisplayName("generar orden desde una requisición aprobada crea la OC y la vincula")
    void generarOrdenAprobada() {
        RequisicionCompra req = requisicionEn(EstadoRequisicionCompra.APROBADA);
        UUID ordenId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(requisicionRepository.findById(req.getId())).thenReturn(Optional.of(req));
        when(servicioOrdenesCompra.crearDesdeRequisicion(eq(req.getId()), any(), any()))
                .thenReturn(ordenId);
        when(requisicionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerarOrdenCompraCommand comando = new GenerarOrdenCompraCommand(
                PROVEEDOR_ID,
                List.of(new CrearPartidaOrdenCompraCommand(MATERIAL_ID, 5, new BigDecimal("100.00"))));
        RequisicionCompraDto dto = servicio.generarOrdenCompra(req.getId(), comando);

        assertThat(dto.ordenCompraId()).isEqualTo(ordenId);
    }

    @Test
    @DisplayName("generar orden desde una requisición NO aprobada se rechaza con 422")
    void generarOrdenNoAprobada() {
        RequisicionCompra req = requisicionEn(EstadoRequisicionCompra.BORRADOR);
        when(requisicionRepository.findById(req.getId())).thenReturn(Optional.of(req));

        GenerarOrdenCompraCommand comando = new GenerarOrdenCompraCommand(
                PROVEEDOR_ID,
                List.of(new CrearPartidaOrdenCompraCommand(MATERIAL_ID, 5, new BigDecimal("100.00"))));

        assertThatThrownBy(() -> servicio.generarOrdenCompra(req.getId(), comando))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("aprobada");

        verify(servicioOrdenesCompra, never()).crearDesdeRequisicion(any(), any(), any());
    }
}
