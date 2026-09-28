package com.dessti.crm.contabilidad.cxc.adapter.out.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.contabilidad.cxc.application.PolizaCobroPort;
import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.CuentaContableRepository;
import com.dessti.crm.contabilidad.polizas.application.PolizaContablePort;
import com.dessti.crm.contabilidad.polizas.application.RenglonPolizaCommand;
import com.dessti.crm.contabilidad.polizas.domain.CuentaContable;
import com.dessti.crm.contabilidad.polizas.domain.TipoPoliza;
import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Adaptador de salida que implementa {@link PolizaCobroPort} generando la
 * Poliza_Contable de cobro de un Pago_Cliente (Req 38.2), a partir de las cuentas
 * estandar de Bancos y Clientes del catalogo contable del tenant, y delegando la
 * generacion balanceada al {@link PolizaContablePort}. Replica el patron de
 * {@code PolizaDepreciacionAdapter}.
 *
 * <h2>Asiento contable (poliza balanceada, Property 16)</h2>
 * <ul>
 *   <li><strong>Cargo</strong> a Bancos ({@value #CODIGO_BANCOS}) por el monto.</li>
 *   <li><strong>Abono</strong> a Clientes ({@value #CODIGO_CLIENTES}) por el monto.</li>
 * </ul>
 *
 * <h2>Cuentas estandar y degradacion gracil</h2>
 * <p>Resuelve ambas cuentas por su codigo estable. Si falta alguna, o si el periodo
 * contable de la fecha esta cerrado, se omite la poliza devolviendo
 * {@link Optional#empty()} (sin revertir el pago ya registrado). Mismo criterio que
 * {@code PolizaDepreciacionAdapter} y {@code PolizaVentaAdapter}.</p>
 */
@Component
public class PolizaCobroAdapter implements PolizaCobroPort {

    /** Codigo de la cuenta de Bancos (cargo por el monto cobrado). */
    static final String CODIGO_BANCOS = "102-01";

    /** Codigo de la cuenta de Clientes / cuentas por cobrar (abono por el monto). */
    static final String CODIGO_CLIENTES = "105-01";

    private final CuentaContableRepository cuentaContableRepository;
    private final PolizaContablePort polizaContablePort;

    public PolizaCobroAdapter(CuentaContableRepository cuentaContableRepository,
                              PolizaContablePort polizaContablePort) {
        this.cuentaContableRepository = cuentaContableRepository;
        this.polizaContablePort = polizaContablePort;
    }

    @Override
    public Optional<UUID> generarPolizaCobro(LocalDate fecha, UUID pagoId, BigDecimal monto) {
        if (pagoId == null || monto == null || monto.signum() <= 0) {
            return Optional.empty();
        }
        Optional<CuentaContable> bancos = cuentaContableRepository.findByCodigo(CODIGO_BANCOS);
        Optional<CuentaContable> clientes = cuentaContableRepository.findByCodigo(CODIGO_CLIENTES);
        if (bancos.isEmpty() || clientes.isEmpty()) {
            return Optional.empty(); // Catalogo incompleto: se omite la poliza (manejo gracil).
        }

        // Asiento balanceado (Property 16): cargo a bancos, abono a clientes (== monto).
        List<RenglonPolizaCommand> renglones = List.of(
                new RenglonPolizaCommand(bancos.get().getId(), monto, null),
                new RenglonPolizaCommand(clientes.get().getId(), null, monto));

        String concepto = "Cobro de Pago_Cliente " + pagoId;
        try {
            UUID polizaId = polizaContablePort.generarPolizaDeEvento(
                    fecha, TipoPoliza.INGRESO, concepto, "pago_cliente", pagoId, renglones);
            return Optional.of(polizaId);
        } catch (ReglaNegocioException ex) {
            // Periodo cerrado (u otra regla contable): se omite la poliza para no
            // revertir el pago ya registrado (degradacion gracil).
            return Optional.empty();
        }
    }
}
