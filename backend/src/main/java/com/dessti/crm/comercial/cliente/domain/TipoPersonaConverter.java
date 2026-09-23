package com.dessti.crm.comercial.cliente.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA que mapea {@link TipoPersona} a su {@link TipoPersona#clave()
 * clave} en minusculas para la columna {@code cliente.tipo_persona VARCHAR(20)}
 * (V59), y viceversa.
 *
 * <p>Se persiste la clave de negocio ({@code 'fisica'} / {@code 'moral'}) en
 * lugar del {@code name()} del enum, para respetar la restriccion
 * {@code ck_cliente_tipo_persona} y mantener el dato legible en base. Un valor
 * {@code null} (columna opcional) se conserva como {@code null} en ambos
 * sentidos.</p>
 */
@Converter
public class TipoPersonaConverter implements AttributeConverter<TipoPersona, String> {

    @Override
    public String convertToDatabaseColumn(TipoPersona atributo) {
        return (atributo == null) ? null : atributo.clave();
    }

    @Override
    public TipoPersona convertToEntityAttribute(String columna) {
        // Reutiliza la interpretacion tolerante del enum (mayusculas/espacios);
        // un valor nulo/en blanco devuelve null (dato ausente).
        return TipoPersona.desde(columna);
    }
}
