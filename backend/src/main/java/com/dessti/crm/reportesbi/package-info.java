/**
 * Modulo <strong>reportes-bi</strong>: Tablero de indicadores por area (Req 22) e
 * Inteligencia de Negocio consolidada (Req 48).
 *
 * <p>Es un <strong>agregador de solo lectura</strong> (Req 22.2, 48.2): reune
 * indicadores de todas las areas del sistema (comercial, produccion, instalacion,
 * mantenimiento, inventario, compras, finanzas, RH/nomina, tesoreria, CxP, estrategia,
 * inventario avanzado, presupuesto, redes sociales) sin modificar los datos de origen y
 * sin acoplarse a los demas modulos.</p>
 *
 * <h2>Decisiones de diseno</h2>
 * <ul>
 *   <li><strong>Indicadores via puertos desacoplados con default en cero:</strong> el
 *       modulo declara un puerto por area en
 *       {@code com.dessti.crm.reportesbi.application.indicadores} y consume solo esas
 *       abstracciones. {@code ReportesBiConfig} registra un adaptador por defecto por
 *       puerto ({@code @ConditionalOnMissingBean}) que devuelve indicadores vacios, de
 *       modo que el modulo compila y funciona de forma independiente; cada modulo de
 *       area puede aportar su adaptador concreto de solo lectura mas adelante. Esto
 *       evita una telarana de dependencias y colisiones con el desarrollo en paralelo
 *       (en particular, este modulo NO referencia {@code com.dessti.crm.social}).</li>
 *   <li><strong>Tableros personalizados persistidos (Req 48.3):</strong> la unica
 *       informacion propia y mutable del modulo son las definiciones de tableros
 *       analiticos ({@code TableroPersonalizado} + {@code WidgetTablero}), persistidas
 *       por la migracion V44.</li>
 *   <li><strong>Solo lectura (Req 22.2, 48.2):</strong> las agregaciones no modifican
 *       el origen; las tendencias/comparativos se derivan en memoria del periodo
 *       anterior.</li>
 *   <li><strong>403 sin permiso (Req 22.5, 48.6):</strong> el Tablero usa
 *       {@code tablero:leer}/{@code reporte:exportar} (sembrados en V5); la Inteligencia
 *       de Negocio usa el recurso {@code inteligencia_negocio:{leer,gestionar,exportar}}
 *       sembrado en V44.</li>
 *   <li><strong>Aislamiento por tenant (Req 23, 48.5):</strong> el {@code tenant_id} se
 *       deriva del {@code TenantContext}; los tableros personalizados quedan acotados por
 *       el filtro global de Hibernate y la RLS (V44).</li>
 * </ul>
 */
package com.dessti.crm.reportesbi;
