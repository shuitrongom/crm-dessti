package com.dessti.crm.compras.factura.application;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades configurables de la Conciliacion_Tres_Vias de las Facturas de
 * Proveedor (Req 33.3).
 *
 * <p>Expone la <strong>tolerancia de precio configurable</strong> (Req 33.3): la
 * fraccion decimal maxima de desviacion admitida entre el precio facturado y el
 * precio de la Orden_Compra por partida. Se interpreta como una fraccion RELATIVA
 * al precio de la Orden_Compra: una tolerancia {@code 0.02} equivale a un 2% de
 * margen; una desviacion mayor marca la Factura como {@code discrepancia} y no
 * autoriza el pago (Req 33.4).</p>
 *
 * <p>Se enlaza a {@code crm.compras.conciliacion.*} en {@code application.yml}. El
 * valor por defecto es {@code 0.02} (2%) si no se configura externamente. Se
 * mantiene inyectable para que las pruebas puedan construirla con un valor
 * explicito. Sigue el mismo patron que {@code OffboardingProperties} /
 * {@code AuditoriaProperties} de la plataforma.</p>
 *
 * @param toleranciaPrecio fraccion decimal de tolerancia relativa de precio; por
 *                         defecto {@code 0.02} (2%). Debe ser no negativa.
 */
@ConfigurationProperties(prefix = "crm.compras.conciliacion")
public record ConciliacionProperties(BigDecimal toleranciaPrecio) {

    /** Tolerancia de precio por defecto (2%) si no se configura externamente. */
    private static final BigDecimal TOLERANCIA_POR_DEFECTO = new BigDecimal("0.02");

    /**
     * Aplica el valor por defecto cuando no se provee y valida que la tolerancia no
     * sea negativa.
     *
     * @throws IllegalArgumentException si la tolerancia es negativa.
     */
    public ConciliacionProperties {
        if (toleranciaPrecio == null) {
            toleranciaPrecio = TOLERANCIA_POR_DEFECTO;
        }
        if (toleranciaPrecio.signum() < 0) {
            throw new IllegalArgumentException(
                    "crm.compras.conciliacion.tolerancia-precio no puede ser negativa");
        }
    }
}
