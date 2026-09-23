package com.dessti.crm.vertical.anuncios.permiso.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoPermisoInstalacion} y su etiqueta persistida
 * en la columna {@code permiso_instalacion.estado} (VARCHAR con CHECK {@code IN
 * ('solicitado','aprobado','rechazado')} de la migracion V20).
 *
 * <p>Persiste siempre {@link EstadoPermisoInstalacion#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete el
 * CHECK de V20 y un arranque con {@code ddl-auto=validate} valide sin conflictos.
 * Sigue el mismo patron que {@code EstadoLevantamientoConverter}.</p>
 */
@Converter(autoApply = false)
public class EstadoPermisoInstalacionConverter
        implements AttributeConverter<EstadoPermisoInstalacion, String> {

    @Override
    public String convertToDatabaseColumn(EstadoPermisoInstalacion atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoPermisoInstalacion convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoPermisoInstalacion.desdeValorBd(columna);
    }
}
