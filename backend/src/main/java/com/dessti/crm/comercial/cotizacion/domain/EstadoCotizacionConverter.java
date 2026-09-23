package com.dessti.crm.comercial.cotizacion.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoCotizacion} y su etiqueta persistida en la
 * columna {@code cotizacion.estado} (VARCHAR con CHECK {@code IN ('borrador',
 * 'enviada', 'aprobada', 'rechazada')} de la migracion V14).
 *
 * <p>Persiste siempre {@link EstadoCotizacion#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete
 * el CHECK de V14 y un arranque con {@code ddl-auto=validate} valide sin
 * conflictos. Sigue el mismo patron que {@code EtapaOportunidadConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoCotizacionConverter implements AttributeConverter<EstadoCotizacion, String> {

    @Override
    public String convertToDatabaseColumn(EstadoCotizacion atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoCotizacion convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoCotizacion.desdeValorBd(columna);
    }
}
