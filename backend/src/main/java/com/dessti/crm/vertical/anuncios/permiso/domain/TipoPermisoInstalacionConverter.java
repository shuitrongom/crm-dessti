package com.dessti.crm.vertical.anuncios.permiso.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link TipoPermisoInstalacion} y su etiqueta persistida en
 * la columna {@code permiso_instalacion.tipo} (VARCHAR con CHECK {@code IN
 * ('municipal','arrendador')} de la migracion V20).
 *
 * <p>Persiste siempre {@link TipoPermisoInstalacion#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete el
 * CHECK de V20 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@code TipoMovimientoInventarioConverter} y
 * {@code EstadoLevantamientoConverter}.</p>
 */
@Converter(autoApply = false)
public class TipoPermisoInstalacionConverter
        implements AttributeConverter<TipoPermisoInstalacion, String> {

    @Override
    public String convertToDatabaseColumn(TipoPermisoInstalacion atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public TipoPermisoInstalacion convertToEntityAttribute(String columna) {
        return (columna == null) ? null : TipoPermisoInstalacion.desdeValorBd(columna);
    }
}
