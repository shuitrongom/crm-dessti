package com.dessti.crm.operacion.inventario.avanzado.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dessti.crm.operacion.inventario.adapter.out.persistence.MaterialRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.AlertaInventarioRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.AlmacenRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.CapaCostoRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.ConfigInventarioMaterialRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.ExistenciaAlmacenRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.LoteRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.MovimientoAlmacenRepository;
import com.dessti.crm.operacion.inventario.avanzado.domain.Almacen;
import com.dessti.crm.operacion.inventario.avanzado.domain.ConfigInventarioMaterial;
import com.dessti.crm.operacion.inventario.avanzado.domain.ExistenciaAlmacen;
import com.dessti.crm.operacion.inventario.avanzado.domain.MovimientoAlmacen;
import com.dessti.crm.operacion.inventario.domain.Material;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias (Mockito) del AJUSTE de inventario por conteo fisico (Req 60). Verifican
 * las tres ramas de conciliacion (contada &gt; saldo = ENTRADA de ajuste; contada &lt; saldo =
 * SALIDA de ajuste; contada == saldo = sin movimiento) y el rechazo (422) de una cantidad
 * contada negativa. No arranca Spring ni base de datos: dobla los repositorios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServicioInventarioAvanzado - ajuste de inventario por conteo fisico (Req 60)")
class ServicioInventarioAvanzadoAjusteTest {

    private static final String ACTOR = "sistema";
    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDate HOY = LocalDate.of(2026, 3, 1);
    private static final UUID ALMACEN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MATERIAL_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock private AlmacenRepository almacenRepository;
    @Mock private ConfigInventarioMaterialRepository configRepository;
    @Mock private ExistenciaAlmacenRepository existenciaRepository;
    @Mock private MovimientoAlmacenRepository movimientoRepository;
    @Mock private CapaCostoRepository capaCostoRepository;
    @Mock private LoteRepository loteRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private AlertaInventarioRepository alertaRepository;
    @Mock private NotificadorInventarioAvanzadoPort notificador;
    @Mock private AuditoriaPort auditoria;

    private ServicioInventarioAvanzado servicio;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(HOY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        servicio = new ServicioInventarioAvanzado(almacenRepository, configRepository,
                existenciaRepository, movimientoRepository, capaCostoRepository, loteRepository,
                materialRepository, alertaRepository, notificador, auditoria, reloj);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Prepara Almacen y Material accesibles, y un saldo vigente de {@code saldoActual}. */
    private ExistenciaAlmacen prepararSaldo(BigDecimal saldoActual) {
        Almacen almacen = Almacen.crear("Central", "bodega", ACTOR);
        Material material = Material.crear("Perfil", "metro", BigDecimal.ZERO, ACTOR);
        ExistenciaAlmacen existencia = ExistenciaAlmacen.paraAlmacenMaterial(ALMACEN_ID, MATERIAL_ID, ACTOR);
        existencia.aplicarSaldo(saldoActual, new BigDecimal("5"), ACTOR);

        when(almacenRepository.findById(ALMACEN_ID)).thenReturn(Optional.of(almacen));
        when(materialRepository.findByIdAndActivoTrue(MATERIAL_ID)).thenReturn(Optional.of(material));
        when(existenciaRepository.findByAlmacenIdAndMaterialId(ALMACEN_ID, MATERIAL_ID))
                .thenReturn(Optional.of(existencia));
        // La rama de costeo del ajuste (entrada/salida) puede consultar config/capas.
        lenient().when(configRepository.findByMaterialId(MATERIAL_ID))
                .thenReturn(Optional.of(ConfigInventarioMaterial.predeterminada(MATERIAL_ID, ACTOR)));
        lenient().when(capaCostoRepository
                        .findByAlmacenIdAndMaterialIdOrderBySecuenciaAsc(ALMACEN_ID, MATERIAL_ID))
                .thenReturn(List.of());
        lenient().when(movimientoRepository.save(any()))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        return existencia;
    }

    @Test
    @DisplayName("contada MAYOR que el saldo registra una ENTRADA de ajuste por la diferencia")
    void contadaMayorGeneraEntrada() {
        prepararSaldo(new BigDecimal("10"));

        MovimientoAlmacenDto dto = servicio.ajustarInventario(new AjustarInventarioCommand(
                ALMACEN_ID, MATERIAL_ID, new BigDecimal("25"), "conteo mensual"));

        assertThat(dto).isNotNull();
        ArgumentCaptor<MovimientoAlmacen> captor = ArgumentCaptor.forClass(MovimientoAlmacen.class);
        verify(movimientoRepository).save(captor.capture());
        // Se ajusta hacia arriba: diferencia 25 - 10 = 15.
        assertThat(captor.getValue().getCantidad()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("contada MENOR que el saldo registra una SALIDA de ajuste por la diferencia")
    void contadaMenorGeneraSalida() {
        prepararSaldo(new BigDecimal("10"));

        MovimientoAlmacenDto dto = servicio.ajustarInventario(new AjustarInventarioCommand(
                ALMACEN_ID, MATERIAL_ID, new BigDecimal("4"), null));

        assertThat(dto).isNotNull();
        ArgumentCaptor<MovimientoAlmacen> captor = ArgumentCaptor.forClass(MovimientoAlmacen.class);
        verify(movimientoRepository).save(captor.capture());
        // Se ajusta hacia abajo: diferencia 10 - 4 = 6.
        assertThat(captor.getValue().getCantidad()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("contada IGUAL al saldo no registra movimiento y devuelve null")
    void contadaIgualNoGeneraMovimiento() {
        prepararSaldo(new BigDecimal("10"));

        MovimientoAlmacenDto dto = servicio.ajustarInventario(new AjustarInventarioCommand(
                ALMACEN_ID, MATERIAL_ID, new BigDecimal("10"), null));

        assertThat(dto).isNull();
        verify(movimientoRepository, never()).save(any());
        verify(existenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("rechaza (422) una cantidad contada negativa")
    void rechazaCantidadNegativa() {
        Almacen almacen = Almacen.crear("Central", "bodega", ACTOR);
        Material material = Material.crear("Perfil", "metro", BigDecimal.ZERO, ACTOR);
        when(almacenRepository.findById(ALMACEN_ID)).thenReturn(Optional.of(almacen));
        when(materialRepository.findByIdAndActivoTrue(MATERIAL_ID)).thenReturn(Optional.of(material));

        assertThatThrownBy(() -> servicio.ajustarInventario(new AjustarInventarioCommand(
                ALMACEN_ID, MATERIAL_ID, new BigDecimal("-1"), null)))
                .isInstanceOf(ReglaNegocioException.class);

        verify(movimientoRepository, never()).save(any());
    }
}
