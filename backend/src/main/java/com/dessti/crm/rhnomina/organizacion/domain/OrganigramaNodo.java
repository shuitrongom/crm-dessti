package com.dessti.crm.rhnomina.organizacion.domain;

import java.util.List;
import java.util.UUID;

/**
 * Nodo del organigrama derivado (Req 61.1), de solo lectura. Representa un
 * {@link Puesto} junto con la lista de sus Puestos subordinados directos, formando
 * recursivamente el arbol (o bosque) del organigrama de la Empresa.
 *
 * <p>Es un modelo derivado PURO producido por
 * {@link GrafoOrganigrama#derivar(java.util.Collection)}: no se persiste ni
 * modifica los datos de origen; se reconstruye bajo demanda a partir de las filas
 * de {@code puesto} del tenant. Un organigrama puede tener varias raices (bosque):
 * cada Puesto sin superior es la raiz de un arbol.</p>
 *
 * @param puestoId    identificador del Puesto de este nodo.
 * @param nombre      nombre del Puesto.
 * @param superiorId  identificador del Puesto superior directo; {@code null} si el
 *                    nodo es una raiz del organigrama.
 * @param subordinados nodos de los Puestos subordinados directos (nunca
 *                    {@code null}; vacio si el Puesto es una hoja).
 */
public record OrganigramaNodo(
        UUID puestoId,
        String nombre,
        UUID superiorId,
        List<OrganigramaNodo> subordinados) {

    /**
     * Construye el nodo garantizando que la lista de subordinados sea inmutable y
     * nunca {@code null}.
     *
     * @param puestoId     identificador del Puesto; obligatorio.
     * @param nombre       nombre del Puesto.
     * @param superiorId   identificador del superior directo; {@code null} si es raiz.
     * @param subordinados subordinados directos; se copia de forma inmutable.
     */
    public OrganigramaNodo {
        subordinados = (subordinados == null) ? List.of() : List.copyOf(subordinados);
    }
}
