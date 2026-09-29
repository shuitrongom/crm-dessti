package com.dessti.crm.operacion.inventario.avanzado.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

import com.dessti.crm.operacion.inventario.adapter.out.persistence.MaterialRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.AlmacenRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.CapaCostoRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.ConfigInventarioMaterialRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.ExistenciaAlmacenRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.LoteRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.MovimientoAlmacenRepository;
import com.dessti.crm.operacion.inventario.avanzado.domain.Almacen;
import com.dessti.crm.operacion.inventario.avanzado.domain.ConfigInventarioMaterial;
import com.dessti.crm.operacion.inventario.avanzado.domain.ExistenciaAlmacen;
import com.dessti.crm.operacion.inventario.avanzado.domain.Lote;
import com.dessti.crm.operacion.inventario.avanzado.domain.MetodoCosteo;
import com.dessti.crm.operacion.inventario.avanzado.domain.MovimientoAlmacen;
import com.dessti.crm.operacion.inventario.domain.Material;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias (Mockito) de la regla de negocio de CADUCIDAD DE LOTE en la SALIDA de
 * inventario avanzado (Req 60): un Material con control de lote no puede despachar un Lote
 * ya vencido. Se verifica el bloqueo (422 sin registrar movimiento) y que un Lote vigente
 * SI permite la salida. El {@link Clock} se fija para ser determinista.
 *
 * <p>No arranca Spring ni base de datos: dobla los repositorios y comprueba que la guarda
 * actua ANTES de tocar el saldo/las capas (no se registra {@link MovimientoAlmacen}).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServicioInventarioAvanzado - caducidad de lote en la salida (Req 60)")
class ServicioInventarioAvanzadoSalidaLoteTest {

    private static final String ACTOR = "sistema";
    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDate HOY = LocalDate.of(2026, 3, 1);
    private static final UUID ALMACEN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MATERIAL_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String LOTE = "L-001";

    @Mock private AlmacenRepository almacenRepository;
    @Mock private ConfigInventarioMaterialRepository configRepository;
    @Mock private ExistenciaAlmacenRepository existenciaRepository;
    @Mock private MovimientoAlmacenRepository movimientoRepository;
    @Mock private CapaCostoRepository capaCostoRepository;
    @Mock private LoteRepository loteRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private NotificadorInventarioAvanzadoPort notificador;
    @Mock private AuditoriaPort auditoria;

    private ServicioInventarioAvanzado servicio;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(HOY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        servicio = new ServicioInventarioAvanzado(almacenRepository, configRepository,
                existenciaRepository, movimientoRepository, capaCostoRepository, loteRepository,
                materialRepository, notificador, auditoria, reloj);
        // El servicio deriva el tenant del contexto (Req 23.4) para auditar/notificar.
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Config con control de lote habilitado y metodo promedio (sin capas PEPS). */
    private static ConfigInventarioMaterial configConControlLote() {
        ConfigInventarioMaterial config = ConfigInventarioMaterial.predeterminada(MATERIAL_ID, ACTOR);
        config.actualizar(MetodoCosteo.PROMEDIO, null, true,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, ACTOR);
        return config;
    }

    /**
     * Prepara los dobles del camino de salida hasta resolver el lote, con un saldo de
     * existencias suficiente ({@code 10}) para que, si la guarda no bloquea, el movimiento
     * pueda registrarse.
     */
    private void prepararSalida(Lote lote) {
        Almacen almacen = Almacen.crear("Central", "bodega", ACTOR);
        Material material = Material.crear("Perfil", "metro", BigDecimal.ZERO, ACTOR);
        ExistenciaAlmacen existencia = ExistenciaAlmacen.paraAlmacenMaterial(ALMACEN_ID, MATERIAL_ID, ACTOR);
        existencia.aplicarSaldo(new BigDecimal("10"), BigDecimal.ZERO, ACTOR);

        when(almacenRepository.findById(ALMACEN_ID)).thenReturn(Optional.of(almacen));
        when(materialRepository.findByIdAndActivoTrue(MATERIAL_ID)).thenReturn(Optional.of(material));
        when(configRepository.findByMaterialId(MATERIAL_ID))
                .thenReturn(Optional.of(configConControlLote()));
        when(existenciaRepository.findByAlmacenIdAndMaterialId(ALMACEN_ID, MATERIAL_ID))
                .thenReturn(Optional.of(existencia));
        when(loteRepository.findByMaterialIdAndCodigo(eq(MATERIAL_ID), any()))
                .thenReturn(Optional.of(lote));
        lenient().when(capaCostoRepository
                        .findByAlmacenIdAndMaterialIdOrderBySecuenciaAsc(ALMACEN_ID, MATERIAL_ID))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("rechaza la salida (422) cuando el Lote esta caducado y no registra movimiento")
    void salidaConLoteCaducado() {
        Lote caducado = Lote.crear(MATERIAL_ID, LOTE, HOY.minusDays(1), ACTOR);
        prepararSalida(caducado);

        RegistrarMovimientoAlmacenCommand comando = new RegistrarMovimientoAlmacenCommand(
                ALMACEN_ID, MATERIAL_ID, LOTE, new BigDecimal("1"), null, "consumo");

        assertThatThrownBy(() -> servicio.registrarSalida(comando))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("caducado");

        verify(movimientoRepository, never()).save(any());
        verify(existenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("permite la salida cuando el Lote sigue vigente (caducidad futura)")
    void salidaConLoteVigente() {
        Lote vigente = Lote.crear(MATERIAL_ID, LOTE, HOY.plusDays(30), ACTOR);
        prepararSalida(vigente);
        when(movimientoRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        RegistrarMovimientoAlmacenCommand comando = new RegistrarMovimientoAlmacenCommand(
                ALMACEN_ID, MATERIAL_ID, LOTE, new BigDecimal("1"), null, "consumo");

        MovimientoAlmacenDto dto = servicio.registrarSalida(comando);

        assertThat(dto).isNotNull();
        verify(movimientoRepository).save(any());
    }
}
