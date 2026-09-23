package com.dessti.crm.comercial.oportunidad.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor JPA entre {@link EtapaOportunidad} y su etiqueta persistida en la
 * columna {@code oportunidad.etapa} (VARCHAR con CHECK {@code IN ('nuevo',
 * 'calificado', 'propuesta', 'negociacion', 'ganado', 'perdido')} de la
 * migracion V13).
 *
 * <p>Persiste siempre {@link EtapaOportunidad#valorBd()} (minusculas ASCII),
 * nunca el nombre de la constante Java, de modo que el valor almacenado respete
 * el CHECK de V13 y un arranque con {@code ddl-auto=validate} valide sin
 * conflictos. Sigue el mismo patron que
 * {@code com.dessti.crm.platform.empresas.EstadoEmpresaConverter}.</p>
 */
@Converter(autoApply = false)
public class EtapaOportunidadConverter implements AttributeConverter<EtapaOportunidad, String> {

    @Override
    public String convertToDatabaseColumn(EtapaOportunidad atributo) {
        return (atributo == null) ? null : atributo.valorBd();
    }

    @Override
    public EtapaOportunidad convertToEntityAttribute(String columna) {
        return (columna == null) ? null : EtapaOportunidad.desdeValorBd(columna);
    }
}
