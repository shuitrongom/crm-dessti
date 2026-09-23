package com.dessti.crm.social.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link CanalSocial} y su etiqueta persistida en las
 * columnas {@code canal} (VARCHAR con CHECK {@code IN ('whatsapp','messenger',
 * 'instagram')} de la migracion V41).
 *
 * <p>Persiste siempre {@link CanalSocial#valorBd()} (minusculas ASCII), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK de
 * V41 y un arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue el
 * mismo patron que {@code EstadoActivoFijoConverter}.</p>
 */
@Converter(autoApply = false)
public class CanalSocialConverter implements AttributeConverter<CanalSocial, String> {

    @Override
    public String convertToDatabaseColumn(CanalSocial atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public CanalSocial convertToEntityAttribute(String columna) {
        return (columna == null) ? null : CanalSocial.desdeValorBd(columna);
    }
}
