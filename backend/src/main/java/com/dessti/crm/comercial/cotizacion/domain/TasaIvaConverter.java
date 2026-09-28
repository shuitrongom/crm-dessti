package com.dessti.crm.comercial.cotizacion.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TasaIva} y su etiqueta persistida en la columna
 * {@code partida_cotizacion.tasa_iva} (VARCHAR con CHECK {@code IN ('16','8','0',
 * 'exento')} de la migracion V80).
 *
 * <p>Persiste siempre {@link TasaIva#valorBd()}, nunca el nombre de la constante
 * Java, de modo que el valor almacenado respete el CHECK de V80 y un arranque con
 * {@code ddl-auto=validate} valide sin conflictos. Sigue el mismo patron que
 * {@code EstadoCotizacionConverter}.</p>
 */
@Converter(autoApply = false)
public class TasaIvaConverter implements AttributeConverter<TasaIva, String> {

    @Override
    public String convertToDatabaseColumn(TasaIva atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TasaIva convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TasaIva.desdeValorBd(columna);
    }
}
