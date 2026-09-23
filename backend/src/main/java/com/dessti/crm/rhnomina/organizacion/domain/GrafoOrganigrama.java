package com.dessti.crm.rhnomina.organizacion.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Componente <strong>puro</strong> del dominio que gobierna la jerarquia de
 * Puestos del organigrama (Req 61.1, 61.7). No depende de Spring ni de JPA:
 * opera sobre vistas {@link PuestoJerarquia} (arista {@code puesto -> superior})
 * y sobre identificadores, por lo que es completamente determinista y
 * unitariamente comprobable (Property 35, tarea 36.2).
 *
 * <h2>Responsabilidades</h2>
 * <ul>
 *   <li>{@link #introduciriaCiclo(Collection, UUID, UUID)}: dado el conjunto de
 *       Puestos actuales y una arista propuesta {@code (hijoId -> nuevoSuperiorId)},
 *       indica si esa arista crearia un ciclo en la jerarquia (Req 61.7). Es la
 *       funcion que la capa de aplicacion consulta antes de crear o mover un
 *       Puesto, y la que ejercita directamente la Property 35.</li>
 *   <li>{@link #derivar(Collection)}: reconstruye el organigrama como un bosque de
 *       {@link OrganigramaNodo} de solo lectura a partir de los Puestos del tenant
 *       (Req 61.1), sin modificar los datos de origen.</li>
 * </ul>
 *
 * <h2>Deteccion de ciclos (Req 61.7)</h2>
 * <p>La jerarquia sin la arista propuesta es siempre aciclica (invariante que la
 * capa de aplicacion mantiene rechazando toda arista ciclica). Para decidir si
 * {@code (hijoId -> nuevoSuperiorId)} introduce un ciclo basta comprobar dos
 * cosas: (1) que no sea un auto-superior ({@code hijoId == nuevoSuperiorId}), y
 * (2) que {@code hijoId} no sea ya un ancestro de {@code nuevoSuperiorId}. Para
 * ello se recorren los ancestros desde {@code nuevoSuperiorId} hacia arriba; si en
 * ese ascenso se alcanza {@code hijoId}, colgar {@code hijoId} de
 * {@code nuevoSuperiorId} cerraria el ciclo. El recorrido tambien se detiene con
 * seguridad si el estado base contuviera un ciclo (proteccion contra bucles
 * infinitos mediante un conjunto de visitados).</p>
 *
 * <p>Clase de utilidad no instanciable.</p>
 */
public final class GrafoOrganigrama {

    private GrafoOrganigrama() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Indica si anadir la arista {@code (hijoId -> nuevoSuperiorId)} a la jerarquia
     * de Puestos introduciria un ciclo (Req 61.7). Funcion pura: sin efectos
     * secundarios y determinista.
     *
     * <p>Se considera ciclo si {@code nuevoSuperiorId == hijoId} (auto-superior) o
     * si {@code hijoId} ya es ancestro (directo o indirecto) de
     * {@code nuevoSuperiorId} en el estado actual. Un {@code nuevoSuperiorId}
     * {@code null} representa "sin superior" (raiz) y nunca crea un ciclo.</p>
     *
     * @param puestosActuales  Puestos del tenant con su superior actual; se usa
     *                         para reconstruir la cadena de ancestros. Nunca
     *                         {@code null}.
     * @param hijoId           Puesto al que se le fijaria el superior; obligatorio.
     * @param nuevoSuperiorId  superior propuesto; {@code null} si se deja sin
     *                         superior (raiz), en cuyo caso no hay ciclo.
     * @return {@code true} si la arista propuesta introduciria un ciclo.
     * @throws NullPointerException si {@code puestosActuales} es {@code null} o
     *         {@code hijoId} es {@code null}.
     */
    public static boolean introduciriaCiclo(Collection<PuestoJerarquia> puestosActuales,
                                            UUID hijoId, UUID nuevoSuperiorId) {
        if (puestosActuales == null) {
            throw new NullPointerException("El conjunto de Puestos actuales es obligatorio.");
        }
        if (hijoId == null) {
            throw new NullPointerException("El Puesto al que se asigna el superior es obligatorio.");
        }
        // Sin superior (raiz): jamas introduce un ciclo.
        if (nuevoSuperiorId == null) {
            return false;
        }
        // Auto-superior directo: es un ciclo (Req 61.7).
        if (hijoId.equals(nuevoSuperiorId)) {
            return true;
        }

        // Mapa arista hijo -> superior del estado actual (sin la arista propuesta).
        Map<UUID, UUID> superiorDe = new HashMap<>();
        for (PuestoJerarquia puesto : puestosActuales) {
            if (puesto == null) {
                throw new NullPointerException("Un Puesto de la jerarquia no puede ser nulo.");
            }
            superiorDe.put(puesto.id(), puesto.superiorId());
        }

        // Se asciende por los ancestros del nuevo superior. Si se alcanza el hijo,
        // colgar el hijo del nuevo superior cerraria un ciclo. El conjunto de
        // visitados protege contra un eventual ciclo preexistente en los datos.
        Set<UUID> visitados = new HashSet<>();
        UUID actual = nuevoSuperiorId;
        while (actual != null) {
            if (actual.equals(hijoId)) {
                return true;
            }
            if (!visitados.add(actual)) {
                // Ciclo preexistente detectado durante el ascenso: se detiene con
                // seguridad. Como el hijo no aparecio, la arista propuesta no lo
                // agrava sobre esta rama.
                return false;
            }
            actual = superiorDe.get(actual);
        }
        return false;
    }

    /**
     * Deriva el organigrama de la Empresa como un bosque de {@link OrganigramaNodo}
     * de solo lectura a partir de los Puestos del tenant (Req 61.1). Funcion pura:
     * no modifica los datos de origen.
     *
     * <p>Cada Puesto sin superior (o cuyo superior no este presente en la coleccion,
     * por ejemplo por filtrado) se trata como una raiz. Las raices se devuelven en
     * el orden de aparicion de la coleccion de entrada, y los subordinados de cada
     * nodo en el mismo orden relativo, para que la salida sea determinista.</p>
     *
     * @param puestos Puestos del tenant con su superior; nunca {@code null}.
     * @return la lista de nodos raiz del organigrama (bosque); vacia si no hay
     *         Puestos.
     * @throws NullPointerException si {@code puestos} es {@code null} o contiene un
     *         elemento {@code null}.
     */
    public static List<OrganigramaNodo> derivar(Collection<PuestoJerarquia> puestos) {
        if (puestos == null) {
            throw new NullPointerException("El conjunto de Puestos es obligatorio.");
        }

        // Preserva el orden de entrada para una salida determinista.
        Map<UUID, PuestoJerarquia> porId = new LinkedHashMap<>();
        for (PuestoJerarquia puesto : puestos) {
            if (puesto == null) {
                throw new NullPointerException("Un Puesto de la jerarquia no puede ser nulo.");
            }
            porId.put(puesto.id(), puesto);
        }

        // Indice de subordinados directos por superior, en orden de aparicion.
        Map<UUID, List<PuestoJerarquia>> hijosDe = new LinkedHashMap<>();
        List<PuestoJerarquia> raices = new ArrayList<>();
        for (PuestoJerarquia puesto : porId.values()) {
            UUID superiorId = puesto.superiorId();
            if (superiorId != null && porId.containsKey(superiorId)) {
                hijosDe.computeIfAbsent(superiorId, k -> new ArrayList<>()).add(puesto);
            } else {
                raices.add(puesto);
            }
        }

        List<OrganigramaNodo> bosque = new ArrayList<>(raices.size());
        for (PuestoJerarquia raiz : raices) {
            bosque.add(construirNodo(raiz, hijosDe, new HashSet<>()));
        }
        return bosque;
    }

    /**
     * Construye recursivamente el nodo derivado de un Puesto y sus subordinados.
     * El conjunto de visitados evita recursion infinita ante datos con ciclos
     * (que la capa de aplicacion no deberia permitir, pero que aqui se toleran de
     * forma segura cortando la rama repetida).
     */
    private static OrganigramaNodo construirNodo(PuestoJerarquia puesto,
                                                 Map<UUID, List<PuestoJerarquia>> hijosDe,
                                                 Set<UUID> visitados) {
        if (!visitados.add(puesto.id())) {
            // Rama ya visitada (ciclo defensivo): se corta sin subordinados.
            return new OrganigramaNodo(puesto.id(), puesto.nombre(), puesto.superiorId(), List.of());
        }
        List<PuestoJerarquia> hijos = hijosDe.getOrDefault(puesto.id(), List.of());
        List<OrganigramaNodo> subordinados = new ArrayList<>(hijos.size());
        for (PuestoJerarquia hijo : hijos) {
            subordinados.add(construirNodo(hijo, hijosDe, visitados));
        }
        return new OrganigramaNodo(puesto.id(), puesto.nombre(), puesto.superiorId(), subordinados);
    }
}
