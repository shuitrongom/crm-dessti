package com.dessti.crm.rhnomina.organizacion.application;

import java.util.List;
import java.util.UUID;

import com.dessti.crm.rhnomina.organizacion.domain.OrganigramaNodo;

/**
 * DTO de salida de un nodo del organigrama derivado (Req 12.2, 61.1), distinto
 * del modelo de dominio {@link OrganigramaNodo}. Representa un Puesto con sus
 * Puestos subordinados directos, formando recursivamente el arbol (o bosque) del
 * organigrama de solo lectura.
 *
 * @param puestoId     identificador del Puesto.
 * @param nombre       nombre del Puesto.
 * @param superiorId   superior directo; {@code null} si es raiz del organigrama.
 * @param subordinados nodos de los Puestos subordinados directos (nunca {@code null}).
 */
public record OrganigramaNodoDto(
        UUID puestoId,
        String nombre,
        UUID superiorId,
        List<OrganigramaNodoDto> subordinados) {

    /**
     * Proyecta recursivamente un {@link OrganigramaNodo} del dominio a su DTO de
     * salida, conservando la estructura del arbol.
     *
     * @param nodo nodo del organigrama a proyectar.
     * @return el DTO correspondiente con sus subordinados proyectados.
     */
    public static OrganigramaNodoDto de(OrganigramaNodo nodo) {
        return new OrganigramaNodoDto(
                nodo.puestoId(),
                nodo.nombre(),
                nodo.superiorId(),
                nodo.subordinados().stream().map(OrganigramaNodoDto::de).toList());
    }
}
