package com.dessti.crm.platform.giros.application;

import java.util.UUID;

/**
 * Puerto de consulta que resuelve <strong>cuantas Empresas usan un Giro</strong>
 * (Req 1.5). Es el contrato que el {@code ServicioGiros} (tarea 2.5) consume para
 * aplicar la regla de negocio que <em>impide desactivar un Giro en uso</em>: si
 * el conteo es mayor que cero, la desactivacion se rechaza con 422 enumerando el
 * numero de Empresas afectadas.
 *
 * <h2>Por que un puerto y no una consulta directa</h2>
 * <p>El conteo depende de la relacion {@code empresa.giro_id}, que
 * <strong>todavia no existe</strong>: la crea la migracion V51 en la tarea 4.1 y
 * el modelo {@code Empresa} la expone en la tarea 4.2. Consultarla ahora seria
 * imposible (la columna no esta) y, ademas, hacer que {@code platform.giros}
 * accediera a la persistencia de {@code platform.empresas} romperia la frontera
 * hexagonal del Nucleo.</p>
 *
 * <p>Por eso el conteo se define como este puerto de aplicacion, propiedad del
 * paquete {@code platform.giros}, de modo que el {@code ServicioGiros} pueda
 * depender de la <em>abstraccion</em> sin acoplarse a {@code platform.empresas}.
 * Mientras la relacion no exista, el sistema usa la implementacion por defecto
 * {@link ConteoEmpresasPorGiroPorDefecto}, que devuelve {@code 0} (ningun Giro se
 * considera en uso). Se replica asi el patron ya establecido en el proyecto por
 * {@code platform.security.rbac.PlanModulosPort} y su placeholder
 * {@code PlanModulosPermisivoPorDefecto}.</p>
 *
 * <p><strong>TODO (tarea 4.4/4.5 - Empresa ligada a un Giro):</strong> aportar un
 * adaptador real (en {@code platform.empresas}, respaldado por
 * {@code EmpresaRepository.countByGiroId(...)} una vez exista {@code empresa.giro_id})
 * que implemente este puerto. Al registrarse ese bean, el placeholder por defecto
 * cede su lugar automaticamente (via {@code @ConditionalOnMissingBean}) y la regla
 * "no desactivar Giro en uso" (Req 1.5) queda operativa extremo a extremo.</p>
 *
 * <p>Req 1.5.</p>
 */
public interface ConteoEmpresasPorGiroPort {

    /**
     * Cuenta las Empresas que actualmente pertenecen al Giro indicado.
     *
     * @param giroId identificador del Giro cuyo uso se consulta; nunca
     *               {@code null}.
     * @return numero de Empresas asociadas al Giro; {@code 0} si ninguna. Nunca
     *         negativo.
     */
    long contarEmpresasPorGiro(UUID giroId);
}
