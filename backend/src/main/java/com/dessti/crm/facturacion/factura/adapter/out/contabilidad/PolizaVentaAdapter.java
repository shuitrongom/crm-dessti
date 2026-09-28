package com.dessti.crm.facturacion.factura.adapter.out.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.CuentaContableRepository;
import com.dessti.crm.contabilidad.polizas.application.PolizaContablePort;
import com.dessti.crm.contabilidad.polizas.application.RenglonPolizaCommand;
import com.dessti.crm.contabilidad.polizas.domain.CuentaContable;
import com.dessti.crm.contabilidad.polizas.domain.TipoPoliza;
import com.dessti.crm.facturacion.factura.application.PolizaVentaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Adaptador de salida que implementa {@link PolizaVentaPort} generando la
 * Poliza_Contable de ingreso de una Factura timbrada (Req 38.2), a partir de las
 * cuentas estandar de venta del catalogo contable del tenant, y delegando la
 * generacion balanceada al {@link PolizaContablePort} del submodulo de Polizas.
 * Replica el patron de {@code PolizaDepreciacionAdapter}.
 *
 * <h2>Asiento contable (poliza balanceada, Property 16)</h2>
 * <p>Con retenciones {@code = 0} el asiento es:</p>
 * <ul>
 *   <li><strong>Cargo</strong> a Clientes ({@value #CODIGO_CLIENTES}) por el total.</li>
 *   <li><strong>Abono</strong> a Ingresos ({@value #CODIGO_INGRESOS}) por el subtotal.</li>
 *   <li><strong>Abono</strong> a IVA trasladado ({@value #CODIGO_IVA_TRASLADADO}) por el IVA.</li>
 * </ul>
 * <p>Como {@code total == subtotal + iva - retenciones}, cuando hay retenciones se
 * agrega un <strong>cargo</strong> a Retenciones ({@value #CODIGO_RETENCIONES}) por
 * el monto retenido, de modo que el asiento siempre balancea
 * ({@code total + retenciones == subtotal + iva}).</p>
 *
 * <h2>Cuentas estandar (supuesto documentado)</h2>
 * <p>El adaptador resuelve las cuentas por su <strong>codigo</strong> estable en el
 * catalogo del tenant. Solo son obligatorias las cuentas de Clientes e Ingresos; las
 * de IVA y Retenciones se exigen unicamente cuando el importe correspondiente es
 * positivo. Si falta alguna cuenta requerida, el adaptador NO genera poliza y
 * devuelve {@link Optional#empty()} (la Factura queda timbrada; la capa de aplicacion
 * lo audita). Igualmente, si el periodo contable de la fecha esta cerrado, la
 * generacion de la poliza se omite con {@link Optional#empty()} para no revertir un
 * CFDI ya timbrado ante el SAT (degradacion gracil). Se elige este manejo, en lugar
 * de sembrar cuentas por defecto o abortar el timbrado, porque el catalogo contable
 * es responsabilidad del tenant y el CFDI timbrado no debe deshacerse por un asunto
 * contable interno.</p>
 */
@Component
public class PolizaVentaAdapter implements PolizaVentaPort {

    /** Codigo de la cuenta de Clientes / cuentas por cobrar (cargo por el total). */
    static final String CODIGO_CLIENTES = "105-01";

    /** Codigo de la cuenta de Ingresos por ventas (abono por el subtotal). */
    static final String CODIGO_INGRESOS = "401-01";

    /** Codigo de la cuenta de IVA trasladado (abono por el IVA). */
    static final String CODIGO_IVA_TRASLADADO = "208-01";

    /** Codigo de la cuenta de impuestos retenidos (cargo por las retenciones). */
    static final String CODIGO_RETENCIONES = "216-01";

    private final CuentaContableRepository cuentaContableRepository;
    private final PolizaContablePort polizaContablePort;

    public PolizaVentaAdapter(CuentaContableRepository cuentaContableRepository,
                              PolizaContablePort polizaContablePort) {
        this.cuentaContableRepository = cuentaContableRepository;
        this.polizaContablePort = polizaContablePort;
    }

    @Override
    public Optional<UUID> generarPolizaVenta(LocalDate fecha, UUID facturaId, BigDecimal subtotal,
                                             BigDecimal iva, BigDecimal retenciones,
                                             BigDecimal total) {
        if (facturaId == null || total == null || total.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal sub = normalizar(subtotal);
        BigDecimal ivaMonto = normalizar(iva);
        BigDecimal retMonto = normalizar(retenciones);

        // Cuentas siempre requeridas.
        Optional<CuentaContable> clientes = cuentaContableRepository.findByCodigo(CODIGO_CLIENTES);
        Optional<CuentaContable> ingresos = cuentaContableRepository.findByCodigo(CODIGO_INGRESOS);
        if (clientes.isEmpty() || ingresos.isEmpty()) {
            return Optional.empty(); // Catalogo incompleto: se omite la poliza (manejo gracil).
        }
        // Cuentas requeridas solo si su importe es positivo.
        Optional<CuentaContable> ivaCuenta = Optional.empty();
        if (ivaMonto.signum() > 0) {
            ivaCuenta = cuentaContableRepository.findByCodigo(CODIGO_IVA_TRASLADADO);
            if (ivaCuenta.isEmpty()) {
                return Optional.empty();
            }
        }
        Optional<CuentaContable> retCuenta = Optional.empty();
        if (retMonto.signum() > 0) {
            retCuenta = cuentaContableRepository.findByCodigo(CODIGO_RETENCIONES);
            if (retCuenta.isEmpty()) {
                return Optional.empty();
            }
        }

        // Asiento balanceado (Property 16): total + retenciones == subtotal + iva.
        List<RenglonPolizaCommand> renglones = new ArrayList<>();
        renglones.add(new RenglonPolizaCommand(clientes.get().getId(), total, null));
        if (retMonto.signum() > 0) {
            renglones.add(new RenglonPolizaCommand(retCuenta.get().getId(), retMonto, null));
        }
        renglones.add(new RenglonPolizaCommand(ingresos.get().getId(), null, sub));
        if (ivaMonto.signum() > 0) {
            renglones.add(new RenglonPolizaCommand(ivaCuenta.get().getId(), null, ivaMonto));
        }

        String concepto = "Ingreso por Factura timbrada " + facturaId;
        try {
            UUID polizaId = polizaContablePort.generarPolizaDeEvento(
                    fecha, TipoPoliza.INGRESO, concepto, "factura_timbrada", facturaId, renglones);
            return Optional.of(polizaId);
        } catch (ReglaNegocioException ex) {
            // Periodo cerrado (u otra regla contable): se omite la poliza para no
            // revertir el CFDI ya timbrado ante el SAT (degradacion gracil).
            return Optional.empty();
        }
    }

    private static BigDecimal normalizar(BigDecimal importe) {
        return importe == null ? BigDecimal.ZERO : importe;
    }
}
