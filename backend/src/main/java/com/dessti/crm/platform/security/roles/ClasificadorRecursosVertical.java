package com.dessti.crm.platform.security.roles;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.dessti.crm.platform.vertical.RegistroVerticales;

/**
 * Regla de dominio, analoga a {@link ClasificadorRecursosPlataforma} pero
 * <strong>consciente del Giro</strong>, que resuelve a que Giro pertenece un
 * recurso RBAC y decide si un recurso es aplicable al Giro de una Empresa
 * (Req 7.2, 7.3, 7.4).
 *
 * <p>A diferencia de {@link ClasificadorRecursosPlataforma} —cuya frontera
 * plataforma/empresa es estatica y conocida en tiempo de compilacion— la
 * frontera Nucleo/Vertical depende de que verticales esten enchufados en el
 * arranque (los recursos de un vertical los declara su {@code ContratoVertical}
 * y los indexa el {@code RegistroVerticales}). Por eso este clasificador
 * <strong>no puede ser una utilidad estatica</strong>: es un {@link Component}
 * que delega en el {@link RegistroVerticales} (dependencia de runtime).</p>
 *
 * <h2>Frontera Nucleo vs Vertical</h2>
 * <p>Un recurso es <em>de Nucleo</em> (transversal a todo Giro) cuando ningun
 * vertical lo declara: {@code cliente}, {@code cotizacion}, {@code factura}, etc.
 * Un recurso es <em>de Vertical</em> cuando un {@code ContratoVertical} lo
 * introduce (p. ej. {@code orden_fabricacion} pertenece al Giro
 * {@code anuncios-luminosos}). Los recursos de plataforma
 * ({@code empresa}, {@code plan}, ...) los clasifica
 * {@link ClasificadorRecursosPlataforma} y quedan fuera del ambito de este
 * clasificador: no se duplica esa logica aqui.</p>
 *
 * <p>Base de los Req 7.2 y 7.3 (ofrecer solo permisos aplicables al Giro) y del
 * Req 7.4 (rechazar un {@code Rol_Personalizado} con permisos de un vertical
 * ajeno al Giro de la Empresa), que aplica {@link ServicioRoles}.</p>
 */
@Component
public class ClasificadorRecursosVertical {

    private final RegistroVerticales registroVerticales;

    /**
     * @param registroVerticales registro del Nucleo que indexa recurso&rarr;Giro
     *                            a partir de los verticales enchufados; nunca
     *                            nulo (puede no tener verticales registrados).
     */
    public ClasificadorRecursosVertical(RegistroVerticales registroVerticales) {
        this.registroVerticales = registroVerticales;
    }

    /**
     * Resuelve la clave de Giro cuyo vertical introduce el recurso indicado.
     *
     * @param recurso nombre del recurso RBAC; puede ser nulo o en blanco.
     * @return la clave de Giro que aporta el recurso, o vacio si es un recurso de
     *         Nucleo (transversal a todo Giro).
     */
    public Optional<String> giroDeRecurso(String recurso) {
        return registroVerticales.giroDeRecurso(recurso);
    }

    /**
     * Indica si el recurso pertenece a algun vertical (es decir, no es de Nucleo).
     *
     * @param recurso nombre del recurso RBAC; puede ser nulo o en blanco.
     * @return {@code true} si algun vertical declara el recurso.
     */
    public boolean esRecursoDeVertical(String recurso) {
        return giroDeRecurso(recurso).isPresent();
    }

    /**
     * Indica si un recurso es aplicable a una Empresa de la clave de Giro dada
     * (Req 7.2): lo es cuando el recurso es de Nucleo (no lo declara ningun
     * vertical) <em>o</em> pertenece al vertical de ese mismo Giro. Un recurso de
     * un vertical <strong>ajeno</strong> al Giro no es aplicable (base del rechazo
     * 422 del Req 7.4 y del filtrado de oferta del Req 7.3).
     *
     * <p>La comparacion de claves de Giro se realiza de forma normalizada
     * (minusculas, sin espacios extremos), coherente con el indexado del
     * {@link RegistroVerticales}.</p>
     *
     * @param recurso   nombre del recurso RBAC a evaluar; puede ser nulo o en
     *                  blanco.
     * @param giroClave clave del Giro de la Empresa; si es nula/en blanco, solo
     *                  se consideran aplicables los recursos de Nucleo
     *                  (comportamiento deny-safe).
     * @return {@code true} si el recurso es aplicable a una Empresa del Giro dado.
     */
    public boolean esRecursoAplicableAGiro(String recurso, String giroClave) {
        Optional<String> giroDelRecurso = giroDeRecurso(recurso);
        if (giroDelRecurso.isEmpty()) {
            // Recurso de Nucleo: aplicable a todo Giro (Req 7.2).
            return true;
        }
        String giroEmpresa = normalizar(giroClave);
        if (giroEmpresa == null) {
            // Sin Giro de Empresa resoluble, un recurso de vertical no es aplicable.
            return false;
        }
        return giroDelRecurso.get().equals(giroEmpresa);
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.strip().toLowerCase(java.util.Locale.ROOT);
        return normalizado.isEmpty() ? null : normalizado;
    }
}
