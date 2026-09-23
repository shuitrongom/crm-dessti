package com.dessti.crm.notificaciones.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link CanalNotificacion} y su etiqueta persistida en la
 * columna {@code notificacion.canal} (VARCHAR con CHECK {@code IN ('correo',
 * 'whatsapp','messenger','instagram')} de la migracion V42).
 *
 * <p>Persiste siempre {@link CanalNotificacion#valorBd()} (minusculas ASCII), nunca
 * el nombre de la constante Java, de modo que el valor almacenado respete el CHECK
 * de V42 y un arranque con {@code ddl-auto=validate} valide sin conflictos. Sigue el
 * mismo patron que {@code EstadoOrdenFabricacionConverter}.</p>
 */
@Converter(autoApply = false)
public class CanalNotificacionConverter implements AttributeConverter<CanalNotificacion, String> {

    @Override
    public String convertToDatabaseColumn(CanalNotificacion atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public CanalNotificacion convertToEntityAttribute(String columna) {
        return (columna == null) ? null : CanalNotificacion.desdeValorBd(columna);
    }
}
