package com.dessti.crm.compras.factura.adapter.out.pagos;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.compras.factura.adapter.out.persistence.FacturaProveedorRepository;
import com.dessti.crm.compras.factura.domain.EstadoFacturaProveedor;
import com.dessti.crm.contabilidad.cxp.application.FacturaProveedorPagablePort;

/**
 * Adaptador de salida del modulo compras que <strong>implementa</strong> el puerto
 * {@link FacturaProveedorPagablePort} declarado por el submodulo Cuentas_Por_Pagar
 * (CxP). Permite que CxP marque una Factura_Proveedor como pagada cuando el saldo de
 * su CxP llega a 0 (Req 42.3, conforme al Req 33), sin que CxP dependa de las clases
 * de compras: la dependencia entre modulos queda <strong>aciclica a nivel de
 * interfaz</strong> (contabilidad.cxp declara el puerto; compras.factura lo
 * implementa).
 *
 * <p>Reutiliza la transicion {@code conciliada -> pagada} de la maquina de estados de
 * la Factura_Proveedor (bloque 27). Es <strong>idempotente</strong>: si la factura ya
 * esta {@code pagada} (o no es accesible en el tenant vigente), no hace nada.</p>
 */
@Component
public class FacturaProveedorPagableAdapter implements FacturaProveedorPagablePort {

    private final FacturaProveedorRepository facturaRepository;

    public FacturaProveedorPagableAdapter(FacturaProveedorRepository facturaRepository) {
        this.facturaRepository = facturaRepository;
    }

    /**
     * Marca la Factura_Proveedor como pagada transitando {@code conciliada -> pagada}
     * (Req 42.3, Req 33). Idempotente: no hace nada si la factura ya esta pagada o no
     * es accesible en el tenant vigente.
     */
    @Override
    @Transactional
    public void marcarPagada(UUID facturaProveedorId) {
        if (facturaProveedorId == null) {
            return;
        }
        facturaRepository.findById(facturaProveedorId).ifPresent(factura -> {
            if (factura.getEstado() == EstadoFacturaProveedor.PAGADA) {
                return; // Idempotencia (Req 42.3).
            }
            factura.cambiarEstado(EstadoFacturaProveedor.PAGADA, "sistema-cxp");
            facturaRepository.save(factura);
        });
    }
}
