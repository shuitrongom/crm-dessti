package com.dessti.crm.vertical.anuncios.levantamiento.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoLevantamiento} y su etiqueta persistida en la
 * columna {@code levantamiento_sitio.estado} (VARCHAR con CHECK {@code IN
 * ('en_proceso','completado')} de la migracion V19).
 *
 * <p>Persiste siempre {@link EstadoLevantamiento#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete el
 * CHECK de V19 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@code EstadoOrdenFabricacionConverter} y
 * {@code EstadoPruebaDisenoConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoLevantamientoConverter
        implements AttributeConverter<EstadoLevantamiento, String> {

    @Override
    public String convertToDatabaseColumn(EstadoLevantamiento atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoLevantamiento convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoLevantamiento.desdeValorBd(columna);
    }
}
