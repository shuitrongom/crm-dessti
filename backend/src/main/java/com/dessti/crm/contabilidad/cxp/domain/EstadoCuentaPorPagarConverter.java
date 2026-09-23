package com.dessti.crm.contabilidad.cxp.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoCuentaPorPagar} y su etiqueta persistida en la
 * columna {@code cuenta_por_pagar.estado} (VARCHAR con CHECK {@code IN
 * ('pendiente', 'parcial', 'pagada', 'cancelada')} de la migracion V33).
 *
 * <p>Persiste siempre {@link EstadoCuentaPorPagar#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete el
 * CHECK de V33 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@code EstadoCuentaPorCobrarConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoCuentaPorPagarConverter
        implements AttributeConverter<EstadoCuentaPorPagar, String> {

    @Override
    public String convertToDatabaseColumn(EstadoCuentaPorPagar atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoCuentaPorPagar convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoCuentaPorPagar.desdeValorBd(columna);
    }
}
