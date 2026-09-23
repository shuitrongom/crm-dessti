package com.dessti.crm.rhnomina.organizacion.domain;

import java.util.UUID;

/**
 * Vista minima e inmutable de un {@link Puesto} para el calculo PURO del
 * organigrama y de la validacion aciclica (Req 61.1, 61.7). Desacopla el
 * componente {@link GrafoOrganigrama} de la entidad JPA y de Spring: solo aporta
 * el identificador del Puesto, su nombre y el identificador de su superior
 * directo (o {@code null} si es una raiz del organigrama).
 *
 * @param id         identificador del Puesto; obligatorio.
 * @param nombre     nombre del Puesto (para etiquetar el nodo derivado).
 * @param superiorId identificador del Puesto superior directo; {@code null} si el
 *                   Puesto es una raiz del organigrama.
 */
public record PuestoJerarquia(UUID id, String nombre, UUID superiorId) {

    /**
     * Construye la vista de jerarquia exigiendo el identificador del Puesto.
     *
     * @param id         identificador del Puesto; obligatorio.
     * @param nombre     nombre del Puesto.
     * @param superiorId identificador del superior directo; {@code null} si es raiz.
     * @throws NullPointerException si {@code id} es {@code null}.
     */
    public PuestoJerarquia {
        if (id == null) {
            throw new NullPointerException("El identificador del Puesto es obligatorio.");
        }
    }
}
