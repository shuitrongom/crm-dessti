package com.dessti.crm.platform.security.rbac;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de consulta que resuelve el <strong>conjunto efectivo de modulos
 * habilitados</strong> para una Empresa (tenant) a partir de su Suscripcion
 * activa (Req 25.4).
 *
 * <p>Es el complemento de {@link PlanModulosPort}: mientras aquel responde una
 * pregunta binaria "¿esta habilitado este modulo?" (usada por el
 * {@code Autorizador} en {@code @PreAuthorize}), este devuelve la
 * <strong>lista completa</strong> de claves habilitadas, para que se expongan al
 * cliente en un claim del JWT ({@code modulos}) y el frontend pinte el menu solo
 * con los modulos contratados, sin un endpoint adicional.</p>
 *
 * <h2>Fuente unica de verdad (single-sourced)</h2>
 * <p>La resolucion —Suscripcion activa, override de Empresa si existe, o catalogo
 * completo del Plan en su defecto— vive en UN solo lugar: el adaptador REAL
 * {@code platform.empresas.PlanModulosPlanAdapter}, que implementa AMBOS puertos
 * y hace que la comprobacion por-modulo de {@link PlanModulosPort} se derive de
 * esta misma lista. Asi el gating (per-modulo) y el claim {@code modulos} (lista)
 * nunca divergen.</p>
 */
public interface ModulosHabilitadosPort {

    /**
     * Devuelve las claves canonicas de los modulos <strong>efectivamente</strong>
     * habilitados para el tenant indicado.
     *
     * @param tenantId identificador de la Empresa (tenant); nunca se toma de la
     *                 peticion sino del contexto autenticado (Req 23.4). Un valor
     *                 {@code null} devuelve la lista vacia.
     * @return lista inmutable de claves de modulo habilitadas (normalizadas en
     *         minusculas), o lista <em>vacia</em> si el tenant es {@code null}, no
     *         tiene Suscripcion activa, o su override/Plan no habilita ninguno
     *         (comportamiento coherente con la denegacion por defecto del gating).
     */
    List<String> modulosHabilitadosDe(UUID tenantId);
}
