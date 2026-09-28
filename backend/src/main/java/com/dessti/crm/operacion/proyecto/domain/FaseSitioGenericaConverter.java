package com.dessti.crm.operacion.proyecto.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link FaseSitioGenerica} y su etiqueta persistida en la
 * columna {@code avance_sitio.fase} (VARCHAR con CHECK
 * {@code IN ('pendiente','en_preparacion','en_instalacion','entregado')} de la
 * migracion V78). Persiste siempre {@link FaseSitioGenerica#valorBd()} (minusculas
 * ASCII).
 */
@Converter(autoApply = false)
public class FaseSitioGenericaConverter
        implements AttributeConverter<FaseSitioGenerica, String> {

    @Override
    public String convertToDatabaseColumn(FaseSitioGenerica atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public FaseSitioGenerica convertToEntityAttribute(String columna) {
        return (columna == null) ? null : FaseSitioGenerica.desdeValorBd(columna);
    }
}
