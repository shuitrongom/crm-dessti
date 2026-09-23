package com.dessti.crm.platform.empresas;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Suscripcion} (Req 25).
 *
 * <p>En la tarea 14.1 se uso para persistir la Suscripcion basica creada durante
 * el alta de una Empresa (Req 24.2). La tarea 14.2 anade las consultas de la
 * gestion completa: la Suscripcion vigente de una Empresa (para el gating de
 * modulos y el limite de usuarios, Req 25.3, 25.4) y el listado por tenant.</p>
 *
 * <p>La tabla {@code suscripcion} es tenant-scoped por RLS (V2), pero su gestion
 * la realiza el {@code super_admin} (plataforma). Estas consultas acotan
 * explicitamente por {@code tenant_id} en los parametros, sin depender del
 * filtro de tenant de Hibernate.</p>
 */
public interface SuscripcionRepository extends JpaRepository<Suscripcion, UUID> {

    /**
     * Devuelve la Suscripcion <strong>activa</strong> de una Empresa, si existe.
     * Es la Suscripcion relevante para el gating de modulos (Req 25.4) y para el
     * limite de usuarios (Req 25.3). Se asume a lo sumo una Suscripcion activa
     * por Empresa; ante mas de una (situacion anomala) se toma la primera de
     * forma estable por {@code id}.
     *
     * @param tenantId Empresa (tenant) titular.
     * @param estado   estado buscado (normalmente {@link EstadoSuscripcion#ACTIVA}).
     * @return la Suscripcion activa de la Empresa, o vacio si no hay ninguna.
     */
    Optional<Suscripcion> findFirstByTenantIdAndEstadoOrderByIdAsc(UUID tenantId, EstadoSuscripcion estado);

    /**
     * Devuelve los Contratos de una Empresa cuyo estado este dentro del conjunto
     * indicado, ordenados de forma estable por {@code id}. Es la consulta que
     * sustenta el <strong>gating de modulos</strong> del
     * {@code PlanModulosPlanAdapter}: selecciona el Contrato vigente entre los
     * estados que <em>otorgan acceso</em> ({@link EstadoSuscripcion#ACTIVA} y
     * {@link EstadoSuscripcion#EN_PRUEBA}), tomando el primero de la lista de
     * forma determinista (por {@code id}). El corte por vencimiento
     * ({@code vigenciaFin} vencida) lo aplica el adaptador en tiempo real; aqui
     * solo se filtra por estado.
     *
     * <p>La tabla {@code suscripcion} tiene RLS (politica {@code tenant_isolation},
     * V2/V53); el servicio que invoca esta consulta fija {@code app.current_tenant}
     * al tenant destino ANTES de leer (mismo patron documentado en
     * {@code PlanModulosPlanAdapter} / {@code ServicioSuscripciones}). El
     * {@code tenant_id} se acota ademas explicitamente en el parametro, sin
     * depender del filtro de tenant de Hibernate.</p>
     *
     * @param tenantId Empresa (tenant) titular.
     * @param estados  conjunto de estados que se consideran vigentes para el
     *                 gating (normalmente {@link EstadoSuscripcion#ACTIVA} y
     *                 {@link EstadoSuscripcion#EN_PRUEBA}).
     * @return los Contratos de la Empresa en esos estados, ordenados por
     *         {@code id}; lista vacia si no hay ninguno.
     */
    List<Suscripcion> findByTenantIdAndEstadoInOrderByIdAsc(UUID tenantId, Collection<EstadoSuscripcion> estados);

    /**
     * Lista todas las Suscripciones de una Empresa (activas o no), ordenadas de
     * forma estable por {@code id}. Util para la consulta de plataforma.
     *
     * <p>Es la consulta que sustenta el enriquecimiento del {@code EmpresaDto}
     * con el "plan vigente" (Req 4.3-4.8): {@code ServicioEmpresas} fija
     * {@code app.current_tenant} a la Empresa (RLS FORCE, rol {@code NOBYPASSRLS})
     * y lee sus Suscripciones para aplicar la regla de "suscripcion vigente"
     * (Req 3). Sin fijar el tenant no seria visible ninguna fila
     * (deny-by-default).</p>
     *
     * @param tenantId Empresa (tenant) titular.
     * @return las Suscripciones de la Empresa.
     */
    List<Suscripcion> findByTenantIdOrderByIdAsc(UUID tenantId);

    /**
     * Cuenta cuantas Suscripciones referencian el Plan indicado,
     * <strong>independientemente de su estado</strong> (activa, suspendida o
     * cancelada). Sustenta la regla que impide eliminar un Plan aun asignado a
     * alguna Empresa: si el conteo es mayor que cero, la eliminacion se rechaza
     * (422). Se cuentan todas las filas con ese {@code planId} porque un Plan
     * referenciado por cualquier Suscripcion no puede borrarse (la columna
     * {@code suscripcion.plan_id} es FK a {@code plan.id}).
     *
     * @param planId identificador del Plan cuyo uso se consulta.
     * @return numero de Suscripciones que referencian el Plan; {@code 0} si
     *         ninguna. Nunca negativo.
     */
    long countByPlanId(UUID planId);

    /**
     * Cuenta cuantas Suscripciones (Contratos) referencian el Paquete de
     * Suscripcion indicado, <strong>independientemente de su estado</strong>
     * (activa, en prueba, suspendida o cancelada). Espejo de
     * {@link #countByPlanId(UUID)}: sustenta la regla que impide eliminar un
     * Paquete aun asignado a alguna Empresa. Si el conteo es mayor que cero, la
     * eliminacion se rechaza (422). Se cuentan todas las filas con ese
     * {@code paqueteSuscripcionId} porque un Paquete referenciado por cualquier
     * Contrato no puede borrarse (la columna {@code suscripcion.paquete_suscripcion_id}
     * es FK a {@code paquete_suscripcion.id}).
     *
     * @param paqueteSuscripcionId identificador del Paquete cuyo uso se consulta.
     * @return numero de Suscripciones que referencian el Paquete; {@code 0} si
     *         ninguna. Nunca negativo.
     */
    long countByPaqueteSuscripcionId(UUID paqueteSuscripcionId);
}
