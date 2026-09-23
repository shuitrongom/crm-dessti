package com.dessti.crm.platform.empresas;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidad de dominio que valida que el subconjunto de modulos elegido para una
 * Empresa sea SIEMPRE un subconjunto de los modulos habilitados por su Plan
 * (Req 25.4).
 *
 * <p>Se centraliza aqui porque la regla se reutiliza en dos flujos: el alta de
 * Empresa ({@link ServicioEmpresas#crearEmpresa}) y la edicion posterior del
 * subconjunto ({@link ServicioSuscripciones#actualizarModulosEmpresa}).</p>
 *
 * <h2>Semantica</h2>
 * <ul>
 *   <li>{@code null} = la Empresa hereda TODOS los modulos del Plan; no hay nada
 *       que validar (se considera valido).</li>
 *   <li>una coleccion (posiblemente vacia) = subconjunto especifico; todos sus
 *       elementos (normalizados: recorte + minusculas) deben estar contenidos en
 *       {@code plan.modulos_habilitados}. La coleccion vacia es valida (cero
 *       modulos habilitados para la Empresa).</li>
 * </ul>
 *
 * <p>Si algun modulo elegido NO pertenece al Plan, se lanza
 * {@link ReglaNegocioException} (que la capa web traduce a HTTP 422),
 * enumerando el/los modulo(s) infractor(es).</p>
 */
final class ModulosPlanValidacion {

    private ModulosPlanValidacion() {
    }

    /**
     * Valida que {@code modulosElegidos} sea un subconjunto de los modulos del
     * Plan (Req 25.4).
     *
     * @param modulosElegidos subconjunto elegido para la Empresa; {@code null}
     *                        significa "heredar del Plan" (no se valida nada).
     * @param plan            Plan cuya lista de modulos acota lo permitido.
     * @throws ReglaNegocioException si algun modulo elegido no pertenece al Plan.
     */
    static void exigirSubconjuntoDelPlan(Set<String> modulosElegidos, Plan plan) {
        if (modulosElegidos == null) {
            // null = heredar todos los modulos del Plan: nada que validar.
            return;
        }
        List<String> permitidos = plan.getModulosHabilitados();
        Set<String> infractores = new LinkedHashSet<>();
        for (String modulo : modulosElegidos) {
            if (modulo == null) {
                continue;
            }
            String normalizado = modulo.strip().toLowerCase(Locale.ROOT);
            if (normalizado.isEmpty()) {
                continue;
            }
            if (!permitidos.contains(normalizado)) {
                infractores.add(normalizado);
            }
        }
        if (!infractores.isEmpty()) {
            throw new ReglaNegocioException(
                    "Los siguientes modulos no estan habilitados por el Plan y no pueden "
                            + "seleccionarse para la Empresa: " + String.join(", ", infractores) + ".");
        }
    }

    /**
     * Valida que {@code modulosElegidos} sea un subconjunto de los modulos del
     * Paquete de Suscripcion (Req 4.5), con la misma semantica null-vs-vacio que
     * {@link #exigirSubconjuntoDelPlan(Set, Plan)}: se centraliza aqui la regla del
     * override para el alta con Suscripcion, evitando duplicar el patron.
     *
     * @param modulosElegidos subconjunto elegido para la Empresa; {@code null}
     *                        significa "heredar del Paquete" (no se valida nada).
     * @param paquete         Paquete de Suscripcion cuya lista de modulos acota lo permitido.
     * @throws ReglaNegocioException si algun modulo elegido no pertenece al Paquete.
     */
    static void exigirSubconjuntoDelPaquete(Set<String> modulosElegidos, PaqueteSuscripcion paquete) {
        if (modulosElegidos == null) {
            // null = heredar todos los modulos del Paquete: nada que validar.
            return;
        }
        List<String> permitidos = paquete.getModulosHabilitados();
        Set<String> infractores = new LinkedHashSet<>();
        for (String modulo : modulosElegidos) {
            if (modulo == null) {
                continue;
            }
            String normalizado = modulo.strip().toLowerCase(Locale.ROOT);
            if (normalizado.isEmpty()) {
                continue;
            }
            if (!permitidos.contains(normalizado)) {
                infractores.add(normalizado);
            }
        }
        if (!infractores.isEmpty()) {
            throw new ReglaNegocioException(
                    "Los siguientes modulos no estan habilitados por la Suscripcion y no pueden "
                            + "seleccionarse para la Empresa: " + String.join(", ", infractores) + ".");
        }
    }
}
