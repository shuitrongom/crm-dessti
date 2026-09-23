/**
 * Capa de aplicacion del modulo reportes-bi (Req 22, 48).
 *
 * <p>Contiene los servicios de <strong>solo lectura</strong>
 * {@link com.dessti.crm.reportesbi.application.ServicioTablero} (Tablero de
 * indicadores por area, Req 22) y
 * {@link com.dessti.crm.reportesbi.application.ServicioInteligenciaNegocio} (analisis
 * consolidado con tendencias/comparativos y CRUD de tableros personalizados, Req 48),
 * junto con sus DTO de salida, el comando de guardado de tableros y la configuracion
 * {@link com.dessti.crm.reportesbi.application.ReportesBiConfig} que registra los
 * adaptadores por defecto de los puertos de indicadores. Los servicios componen sus
 * resultados a partir de los puertos de
 * {@code com.dessti.crm.reportesbi.application.indicadores}, sin acoplar el modulo a
 * los demas modulos del sistema.</p>
 */
package com.dessti.crm.reportesbi.application;
