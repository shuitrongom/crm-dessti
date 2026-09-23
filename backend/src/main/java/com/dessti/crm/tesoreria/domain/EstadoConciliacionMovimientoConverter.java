package com.dessti.crm.tesoreria.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoConciliacionMovimiento} y su etiqueta
 * persistida en la columna {@code movimiento_bancario.estado_conciliacion}
 * (VARCHAR con CHECK {@code IN ('pendiente', 'conciliado', 'excepcion')} de la
 * migracion V35).
 *
 * <p>Persiste siempre {@link EstadoConciliacionMovimiento#valorBd()} (minusculas
 * ASCII), nunca el nombre de la constante Java, de modo que el valor almacenado
 * respete el CHECK de V35 y un arranque con {@code ddl-auto=validate} valide sin
 * conflictos. Sigue el mismo patron que {@code EstadoCuentaPorPagarConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoConciliacionMovimientoConverter
        implements AttributeConverter<EstadoConciliacionMovimiento, String> {

    @Override
    public String convertToDatabaseColumn(EstadoConciliacionMovimiento atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoConciliacionMovimiento convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoConciliacionMovimiento.desdeValorBd(columna);
    }
}
