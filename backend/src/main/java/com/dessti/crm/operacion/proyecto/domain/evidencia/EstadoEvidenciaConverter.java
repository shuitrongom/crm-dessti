package com.dessti.crm.operacion.proyecto.domain.evidencia;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EstadoEvidencia} y su etiqueta persistida en la
 * columna {@code evidencia_avance_sitio.estado} (VARCHAR con CHECK
 * {@code IN ('pendiente','aprobada','rechazada')}). Persiste siempre
 * {@link EstadoEvidencia#valorBd()} (minusculas ASCII).
 */
@Converter(autoApply = false)
public class EstadoEvidenciaConverter implements AttributeConverter<EstadoEvidencia, String> {

    @Override
    public String convertToDatabaseColumn(EstadoEvidencia atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EstadoEvidencia convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EstadoEvidencia.desdeValorBd(columna);
    }
}
