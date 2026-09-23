/**
 * Adaptadores de salida de persistencia del modulo reportes-bi (Req 48.3, 23).
 *
 * <p>Contiene el repositorio Spring Data JPA de los tableros analiticos personalizados
 * ({@link com.dessti.crm.reportesbi.adapter.out.persistence.TableroPersonalizadoRepository}),
 * tenant-scoped: sus consultas quedan acotadas al {@code tenant_id} vigente por el
 * filtro global de Hibernate y la RLS de PostgreSQL (V44), garantizando el aislamiento
 * por Empresa (Req 48.5).</p>
 */
package com.dessti.crm.reportesbi.adapter.out.persistence;
