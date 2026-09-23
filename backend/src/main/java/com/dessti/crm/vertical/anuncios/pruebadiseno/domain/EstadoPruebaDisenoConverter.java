package com.dessti.crm.vertical.anuncios.pruebadiseno.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoPruebaDiseno} y su etiqueta persistida en la
 * columna {@code prueba_diseno.estado} (VARCHAR con CHECK {@code IN ('pendiente',
 * 'aprobada', 'rechazada')} de la migracion V16).
 *
 * <p>Persiste siempre {@link EstadoPruebaDiseno#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete
 * el CHECK de V16 y un arranque con {@code ddl-auto=validate} valide sin
 * conflictos. Sigue el mismo patron que {@code EstadoCotizacionConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoPruebaDisenoConverter implements AttributeConverter<EstadoPruebaDiseno, String> {

    @Override
    public String convertToDatabaseColumn(EstadoPruebaDiseno atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoPruebaDiseno convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoPruebaDiseno.desdeValorBd(columna);
    }
}
