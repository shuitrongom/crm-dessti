package com.dessti.crm.calidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoAccionCorrectiva} y su etiqueta persistida en
 * la columna {@code accion_correctiva.estado} (VARCHAR con CHECK
 * {@code IN ('abierta','en_analisis','en_ejecucion','verificacion','cerrada')} de la
 * migracion V47). Persiste siempre {@link EstadoAccionCorrectiva#valorBd()}
 * (minusculas ASCII).
 */
@Converter(autoApply = false)
public class EstadoAccionCorrectivaConverter
        implements AttributeConverter<EstadoAccionCorrectiva, String> {

    @Override
    public String convertToDatabaseColumn(EstadoAccionCorrectiva atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoAccionCorrectiva convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoAccionCorrectiva.desdeValorBd(columna);
    }
}
