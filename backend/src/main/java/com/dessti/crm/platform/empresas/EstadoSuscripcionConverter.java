package com.dessti.crm.platform.empresas;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoSuscripcion} y su etiqueta persistida en la
 * columna {@code suscripcion.estado} (VARCHAR con CHECK {@code IN ('activa',
 * 'suspendida', 'cancelada')} de la migracion V1, {@code ck_suscripcion_estado}).
 *
 * <p>Persiste siempre {@link EstadoSuscripcion#valorBd()} (minusculas), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK
 * de V1 y un arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue
 * el mismo patron que {@link EstadoEmpresaConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoSuscripcionConverter implements AttributeConverter<EstadoSuscripcion, String> {

    @Override
    public String convertToDatabaseColumn(EstadoSuscripcion atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoSuscripcion convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoSuscripcion.desdeValorBd(columna);
    }
}
