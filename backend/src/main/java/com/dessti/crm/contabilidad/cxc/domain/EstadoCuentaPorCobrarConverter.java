package com.dessti.crm.contabilidad.cxc.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoCuentaPorCobrar} y su etiqueta persistida en
 * la columna {@code cuenta_por_cobrar.estado} (VARCHAR con CHECK {@code IN
 * ('pendiente', 'parcial', 'pagada', 'cancelada')} de la migracion V31).
 *
 * <p>Persiste siempre {@link EstadoCuentaPorCobrar#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete el
 * CHECK de V31 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@code EstadoFacturaConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoCuentaPorCobrarConverter
        implements AttributeConverter<EstadoCuentaPorCobrar, String> {

    @Override
    public String convertToDatabaseColumn(EstadoCuentaPorCobrar atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoCuentaPorCobrar convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoCuentaPorCobrar.desdeValorBd(columna);
    }
}
