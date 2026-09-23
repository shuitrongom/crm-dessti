package com.dessti.crm.platform.empresas;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoEmpresa} y su etiqueta persistida en la
 * columna {@code empresa.estado} (VARCHAR con CHECK {@code IN ('activa',
 * 'suspendida', 'cancelada')} de la migracion V1).
 *
 * <p>Persiste siempre {@link EstadoEmpresa#valorBd()} (minusculas), nunca el
 * nombre de la constante Java, de modo que el valor almacenado respete el CHECK
 * de V1 y un arranque con {@code ddl-auto=validate} valide sin conflictos.</p>
 */
@Converter(autoApply = false)
public class EstadoEmpresaConverter implements AttributeConverter<EstadoEmpresa, String> {

    @Override
    public String convertToDatabaseColumn(EstadoEmpresa atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoEmpresa convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoEmpresa.desdeValorBd(columna);
    }
}
