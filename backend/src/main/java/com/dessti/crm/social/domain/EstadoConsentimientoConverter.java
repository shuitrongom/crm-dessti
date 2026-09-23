package com.dessti.crm.social.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoConsentimiento} y su etiqueta persistida en
 * la columna {@code consentimiento_canal.estado} (VARCHAR con CHECK
 * {@code IN ('opt_in','opt_out')} de la migracion V41). Persiste siempre
 * {@link EstadoConsentimiento#valorBd()} (minusculas ASCII).
 */
@Converter(autoApply = false)
public class EstadoConsentimientoConverter
        implements AttributeConverter<EstadoConsentimiento, String> {

    @Override
    public String convertToDatabaseColumn(EstadoConsentimiento atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoConsentimiento convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoConsentimiento.desdeValorBd(columna);
    }
}
