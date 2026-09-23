package com.dessti.crm.vertical.anuncios.instalacion.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoOrdenTrabajoInstalacion} y su etiqueta
 * persistida en la columna {@code orden_trabajo_instalacion.estado} (VARCHAR con
 * CHECK {@code IN ('programada','en_curso','completada','cancelada')} de la
 * migracion V24).
 *
 * <p>Persiste siempre {@link EstadoOrdenTrabajoInstalacion#valorBd()} (minusculas
 * ASCII), nunca el nombre de la constante Java, de modo que el valor almacenado
 * respete el CHECK de V24 y un arranque con {@code ddl-auto=validate} valide sin
 * conflictos. Sigue el mismo patron que {@code EstadoOrdenFabricacionConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoOrdenTrabajoInstalacionConverter
        implements AttributeConverter<EstadoOrdenTrabajoInstalacion, String> {

    @Override
    public String convertToDatabaseColumn(EstadoOrdenTrabajoInstalacion atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoOrdenTrabajoInstalacion convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoOrdenTrabajoInstalacion.desdeValorBd(columna);
    }
}
