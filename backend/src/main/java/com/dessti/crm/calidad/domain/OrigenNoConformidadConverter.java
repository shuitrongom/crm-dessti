package com.dessti.crm.calidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link OrigenNoConformidad} y su etiqueta persistida en la
 * columna {@code no_conformidad.origen} (VARCHAR con CHECK
 * {@code IN ('queja','auditoria_interna','proceso','proveedor','otro')} de la
 * migracion V47). Persiste siempre {@link OrigenNoConformidad#valorBd()}.
 */
@Converter(autoApply = false)
public class OrigenNoConformidadConverter
        implements AttributeConverter<OrigenNoConformidad, String> {

    @Override
    public String convertToDatabaseColumn(OrigenNoConformidad atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public OrigenNoConformidad convertToEntityAttribute(String columna) {
        return (columna == null) ? null : OrigenNoConformidad.desdeValorBd(columna);
    }
}
