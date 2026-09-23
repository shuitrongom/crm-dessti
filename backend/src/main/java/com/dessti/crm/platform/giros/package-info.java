/**
 * Catalogo de Giros (verticales de negocio) de la plataforma multigiro
 * (Req 1.2, tarea 2.2). NUCLEO de plataforma.
 *
 * <p>Un <strong>Giro</strong> es el vertical de negocio al que pertenece una
 * Empresa (p. ej. {@code anuncios-luminosos}, {@code manufactura}). Este paquete
 * administra el {@code Catalogo_Giros}: alta, activacion, desactivacion y
 * consulta de los Giros disponibles, reservados al rol {@code super_admin}
 * (Req 1.1). Sigue el patron hexagonal del proyecto (dominio + application +
 * adapter, como {@code platform.monetizacion}).</p>
 *
 * <h2>Fronteras de responsabilidad</h2>
 * <ul>
 *   <li><strong>Dato de plataforma, sin RLS (Req 8.4):</strong> la entidad
 *       {@link com.dessti.crm.platform.giros.domain.Giro} se mapea sobre la tabla
 *       {@code giro} de la migracion V50, que <em>no</em> lleva {@code tenant_id}
 *       ni politicas Row-Level Security, coherente con {@code empresa}/{@code plan}
 *       de V1. Un Giro no pertenece a ninguna Empresa: es compartido por todas.</li>
 *   <li><strong>El Giro es atributo, no eje de aislamiento (Req 8.1):</strong> el
 *       aislamiento de datos de negocio sigue rigiendose por {@code tenant_id} +
 *       RLS; el Giro solo determina que Modulos-Vertical, permisos y navegacion
 *       hereda la Empresa. El enlace {@code empresa.giro_id} pertenece a
 *       {@code platform.empresas} (migracion V51, tarea 4.x) y no se modela aqui.</li>
 *   <li><strong>Clave canonica normalizada (Req 1.2):</strong> la {@code clave}
 *       del Giro es unica e inmutable, persistida en minusculas y formato kebab
 *       mediante {@link com.dessti.crm.platform.giros.domain.Giro#normalizarClave(String)},
 *       normalizacion idempotente reforzada en BD por {@code uq_giro_clave} y el
 *       CHECK de minusculas de V50.</li>
 *   <li><strong>Alcance de la tarea 2.2:</strong> solo la entidad de dominio y la
 *       normalizacion de la clave. El repositorio/adaptador JPA, el
 *       {@code ServicioGiros} y el {@code GiroController} REST corresponden a las
 *       tareas 2.4/2.5/2.6.</li>
 * </ul>
 */
package com.dessti.crm.platform.giros;
