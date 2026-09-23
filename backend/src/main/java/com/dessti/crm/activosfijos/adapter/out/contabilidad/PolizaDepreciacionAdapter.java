package com.dessti.crm.activosfijos.adapter.out.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.activosfijos.application.PolizaDepreciacionPort;
import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.CuentaContableRepository;
import com.dessti.crm.contabilidad.polizas.application.PolizaContablePort;
import com.dessti.crm.contabilidad.polizas.application.RenglonPolizaCommand;
import com.dessti.crm.contabilidad.polizas.domain.CuentaContable;
import com.dessti.crm.contabilidad.polizas.domain.TipoPoliza;

/**
 * Adaptador de salida que implementa {@link PolizaDepreciacionPort} generando la
 * Poliza_Contable de una depreciacion de periodo (Req 44.3, 38.2) a partir de las
 * cuentas estandar de depreciacion del catalogo contable del tenant, y delegando la
 * generacion balanceada al {@link PolizaContablePort} del submodulo de Polizas
 * (bloque 30).
 *
 * <h2>Asiento contable (poliza balanceada, Property 16)</h2>
 * <p>La depreciacion del periodo produce una poliza de tipo {@code diario} con dos
 * renglones que balancean (cargo == abono):</p>
 * <ul>
 *   <li><strong>Cargo</strong> a la cuenta de <em>gasto por depreciacion</em>
 *       (codigo {@value #CODIGO_GASTO_DEPRECIACION}).</li>
 *   <li><strong>Abono</strong> a la cuenta de <em>depreciacion acumulada</em>
 *       (codigo {@value #CODIGO_DEPRECIACION_ACUMULADA}).</li>
 * </ul>
 *
 * <h2>Cuentas estandar (supuesto documentado)</h2>
 * <p>El adaptador resuelve ambas cuentas por su <strong>codigo</strong> estable en
 * el catalogo del tenant. Para que la depreciacion genere poliza, el catalogo de
 * Cuenta_Contable (Req 38.1) debe definir esas dos cuentas con dichos codigos. Si
 * falta alguna, el adaptador NO genera poliza y devuelve {@link Optional#empty()}
 * (la depreciacion se registra igualmente sin poliza; la capa de aplicacion lo
 * audita). Se elige este manejo gracil, en lugar de sembrar cuentas por defecto,
 * porque el catalogo contable es responsabilidad del tenant y no debe imponerse
 * desde este modulo; ademas evita romper otros flujos contables. Cuando el tenant
 * configure el catalogo, la generacion de poliza queda operativa sin cambios de
 * codigo.</p>
 */
@Component
public class PolizaDepreciacionAdapter implements PolizaDepreciacionPort {

    /** Codigo de la cuenta de gasto por depreciacion (cargo del asiento). */
    static final String CODIGO_GASTO_DEPRECIACION = "6250-DEP";

    /** Codigo de la cuenta de depreciacion acumulada (abono del asiento). */
    static final String CODIGO_DEPRECIACION_ACUMULADA = "1295-DEPAC";

    private final CuentaContableRepository cuentaContableRepository;
    private final PolizaContablePort polizaContablePort;

    public PolizaDepreciacionAdapter(CuentaContableRepository cuentaContableRepository,
                                     PolizaContablePort polizaContablePort) {
        this.cuentaContableRepository = cuentaContableRepository;
        this.polizaContablePort = polizaContablePort;
    }

    @Override
    public Optional<UUID> generarPolizaDepreciacion(LocalDate fecha, UUID activoFijoId,
                                                    String periodo, BigDecimal monto,
                                                    String actor) {
        if (monto == null || monto.signum() <= 0) {
            return Optional.empty();
        }
        Optional<CuentaContable> gasto =
                cuentaContableRepository.findByCodigo(CODIGO_GASTO_DEPRECIACION);
        Optional<CuentaContable> acumulada =
                cuentaContableRepository.findByCodigo(CODIGO_DEPRECIACION_ACUMULADA);
        if (gasto.isEmpty() || acumulada.isEmpty()) {
            // Catalogo contable incompleto: se omite la poliza (manejo gracil).
            return Optional.empty();
        }

        // Asiento balanceado: cargo al gasto por depreciacion, abono a la
        // depreciacion acumulada (Property 16: cargo == abono == monto).
        List<RenglonPolizaCommand> renglones = List.of(
                new RenglonPolizaCommand(gasto.get().getId(), monto, null),
                new RenglonPolizaCommand(acumulada.get().getId(), null, monto));

        String concepto = "Depreciacion del periodo " + periodo + " del Activo_Fijo " + activoFijoId;
        UUID polizaId = polizaContablePort.generarPolizaDeEvento(
                fecha, TipoPoliza.DIARIO, concepto, "depreciacion", activoFijoId, renglones);
        return Optional.of(polizaId);
    }
}
