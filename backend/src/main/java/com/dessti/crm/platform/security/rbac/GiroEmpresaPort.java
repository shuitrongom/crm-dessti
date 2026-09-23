package com.dessti.crm.platform.security.rbac;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de consulta para el <em>gating por Giro</em>: resuelve la
 * <strong>clave del Giro</strong> al que pertenece un tenant (Empresa), de modo
 * que la capa de autorizacion pueda decidir si un modulo de un vertical
 * corresponde o no al Giro de la Empresa autenticada (Req 6.1, 8.1, 8.2).
 *
 * <p>Es el hermano de {@link PlanModulosPort}: ambos viven en
 * {@code platform.security.rbac} porque los consume el {@code Autorizador} para
 * componer el <strong>doble gating</strong> (Plan Y Giro, tarea 6.1). Este
 * puerto aporta la mitad del Giro: dado el tenant actual, indica a que vertical
 * pertenece.</p>
 *
 * <h2>Decision de diseno: la clave (String) y no el UUID (Req 2.4)</h2>
 * <p>El puerto devuelve la <strong>clave canonica</strong> del Giro (p. ej.
 * {@code anuncios-luminosos}), no su {@code giro_id} (UUID). El motivo es que el
 * gating por Giro (tarea 6.1) compara el Giro del tenant contra la clave que
 * declara {@code ContratoVertical.giro()} —una clave, no un identificador de
 * fila—. Devolver directamente la clave evita que el {@code Autorizador} tenga
 * que traducir UUID→clave en cada comprobacion y mantiene el {@code Autorizador}
 * desacoplado del catalogo {@code giro}. La traduccion desde el
 * {@code empresa.giro_id} a la clave la realiza el adaptador
 * ({@code platform.empresas.GiroEmpresaAdapter}), que consulta el catalogo de
 * Giros.</p>
 *
 * <h2>Decision de diseno: sin placeholder (a diferencia de {@link PlanModulosPort})</h2>
 * <p>{@link PlanModulosPort} necesitaba una implementacion permisiva por defecto
 * ({@code PlanModulosPermisivoPorDefecto}, {@code @ConditionalOnMissingBean})
 * porque su adaptador real dependia de Planes/Suscripciones que llegaban en una
 * tarea posterior; el placeholder mantenia arrancable la autorizacion mientras
 * tanto. Aqui NO hace falta: el adaptador real
 * ({@code platform.empresas.GiroEmpresaAdapter}) se entrega junto con este puerto
 * y sus dependencias ({@code EmpresaRepository}, {@code GiroRepository}) ya
 * existen, de modo que siempre habra una implementacion respaldada por
 * persistencia en el contexto. Anadir un placeholder solo introduciria un camino
 * permisivo innecesario que debilitaria el deny-safe del gating por Giro.</p>
 */
public interface GiroEmpresaPort {

    /**
     * Resuelve la clave canonica del Giro al que pertenece el tenant indicado.
     *
     * @param tenantId identificador de la Empresa (tenant); nunca se toma de la
     *                 peticion sino del contexto autenticado (Req 8.1, 8.2).
     * @return la clave del Giro de la Empresa (p. ej. {@code anuncios-luminosos})
     *         envuelta en {@link Optional}; {@link Optional#empty()} si el tenant
     *         es nulo, la Empresa no existe o su Giro no se resuelve
     *         (comportamiento <em>deny-safe</em>: la ausencia de clave conduce a
     *         denegacion en el gating por Giro).
     */
    Optional<String> giroDeTenant(UUID tenantId);
}
