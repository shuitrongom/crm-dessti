package com.dessti.crm.social.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoPublicacion} y su etiqueta persistida en la
 * columna {@code publicacion_social.estado} (VARCHAR con CHECK
 * {@code IN ('borrador','programada','publicada','fallida')} de la migracion V43).
 * Persiste siempre {@link EstadoPublicacion#valorBd()} (minusculas ASCII), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK y
 * un arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue el mismo
 * patron que {@link EstadoConversacionConverter}.
 */
@Converter(autoApply = false)
public class EstadoPublicacionConverter implements AttributeConverter<EstadoPublicacion, String> {

    @Override
    public String convertToDatabaseColumn(EstadoPublicacion atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoPublicacion convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoPublicacion.desdeValorBd(columna);
    }
}
