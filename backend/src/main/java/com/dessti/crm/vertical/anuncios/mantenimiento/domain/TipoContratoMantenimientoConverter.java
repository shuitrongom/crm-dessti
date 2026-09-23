package com.dessti.crm.vertical.anuncios.mantenimiento.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoContratoMantenimiento} y su etiqueta persistida
 * en la columna {@code contrato_mantenimiento.tipo} (VARCHAR con CHECK {@code IN
 * ('preventivo','correctivo')} de la migracion V27).
 *
 * <p>Persiste siempre {@link TipoContratoMantenimiento#valorBd()} (minusculas
 * ASCII), nunca el nombre de la constante Java, de modo que el valor almacenado
 * respete el CHECK de V27 y un arranque con {@code ddl-auto=validate} valide sin
 * conflictos. Sigue el mismo patron que {@code EstadoOrdenFabricacionConverter}.</p>
 */
@Converter(autoApply = false)
public class TipoContratoMantenimientoConverter
        implements AttributeConverter<TipoContratoMantenimiento, String> {

    @Override
    public String convertToDatabaseColumn(TipoContratoMantenimiento atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TipoContratoMantenimiento convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoContratoMantenimiento.desdeValorBd(columna);
    }
}
