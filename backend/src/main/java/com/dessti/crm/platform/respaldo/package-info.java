/**
 * Capacidad transversal de <strong>respaldo y recuperacion de datos</strong>
 * (Req 50), implementada con arquitectura hexagonal (Puertos y Adaptadores).
 *
 * <p>Modela respaldos periodicos <em>cifrados</em> de los datos de negocio,
 * fiscales y contables, con RPO/RTO configurables, restauracion <em>auditada</em>
 * y acceso restringido al {@code super_admin} (operacion de nivel plataforma).</p>
 *
 * <p>Organizacion:</p>
 * <ul>
 *   <li>{@code domain}: la entidad {@code Respaldo} (bitacora de ejecuciones,
 *       solo metadatos) y sus tipos/estados; libre de dependencias de
 *       infraestructura de respaldo.</li>
 *   <li>{@code application}: el servicio de orquestacion {@code ServicioRespaldo},
 *       el programador {@code ProgramadorRespaldos}, la configuracion tipada
 *       {@code RespaldoProperties} y los <strong>puertos</strong>
 *       {@code MotorRespaldoPort} (volcado/restauracion fisica) y
 *       {@code CifradorRespaldoPort} (cifrado/descifrado del artefacto).</li>
 *   <li>{@code adapter.out}: adaptadores por defecto que invocan {@code pg_dump}/
 *       {@code pg_restore} a traves de un {@code EjecutorProceso} (frontera de
 *       invocacion de procesos, sustituible en pruebas) y que cifran el
 *       artefacto con la criptografia existente (AES-256-GCM, Req 67).</li>
 *   <li>{@code adapter.in.rest}: adaptador REST para disparar respaldo y
 *       restauracion bajo demanda, restringido por RBAC a {@code super_admin}.</li>
 * </ul>
 *
 * <p><strong>Seguridad en pruebas:</strong> los adaptadores que ejecutan
 * procesos del sistema operativo y el programador periodico se activan
 * unicamente cuando {@code crm.respaldo.habilitado=true}. Por defecto estan
 * desactivados, de modo que el arranque del contexto en pruebas nunca lanza
 * {@code pg_dump} ni dispara respaldos programados.</p>
 */
package com.dessti.crm.platform.respaldo;
