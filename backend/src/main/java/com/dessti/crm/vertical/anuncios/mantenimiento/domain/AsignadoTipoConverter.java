package com.dessti.crm.vertical.anuncios.mantenimiento.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link AsignadoTipo} y su etiqueta persistida en la
 * columna {@code ticket_servicio.asignado_tipo} (VARCHAR NULL con CHECK {@code IN
 * ('tecnico','cuadrilla')} de la migracion V27).
 *
 * <p>Persiste siempre {@link AsignadoTipo#valorBd()} (minusculas ASCII), nunca el
 * nombre de la constante Java. Un ticket sin asignar tiene {@code asignado_tipo}
 * NULL, que este convertidor mapea a {@code null}. Sigue el mismo patron que
 * {@code EstadoOrdenFabricacionConverter}.</p>
 */
@Converter(autoApply = false)
public class AsignadoTipoConverter implements AttributeConverter<AsignadoTipo, String> {

    @Override
    public String convertToDatabaseColumn(AsignadoTipo atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public AsignadoTipo convertToEntityAttribute(String columna) {
        return (columna == null) ? null : AsignadoTipo.desdeValorBd(columna);
    }
}
