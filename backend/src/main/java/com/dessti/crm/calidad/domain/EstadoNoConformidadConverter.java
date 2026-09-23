package com.dessti.crm.calidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoNoConformidad} y su etiqueta persistida en la
 * columna {@code no_conformidad.estado} (VARCHAR con CHECK
 * {@code IN ('abierta','en_tratamiento','cerrada')} de la migracion V47). Persiste
 * siempre {@link EstadoNoConformidad#valorBd()} (minusculas ASCII).
 */
@Converter(autoApply = false)
public class EstadoNoConformidadConverter
        implements AttributeConverter<EstadoNoConformidad, String> {

    @Override
    public String convertToDatabaseColumn(EstadoNoConformidad atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoNoConformidad convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoNoConformidad.desdeValorBd(columna);
    }
}
