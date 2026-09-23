/**
 * Nucleo de dominio del modulo reportes-bi (Req 48.3).
 *
 * <p>Contiene el agregado {@link com.dessti.crm.reportesbi.domain.TableroPersonalizado
 * TableroPersonalizado} y su composicion
 * {@link com.dessti.crm.reportesbi.domain.WidgetTablero WidgetTablero}: la unica
 * informacion propia y mutable del modulo, que persiste las definiciones de los tableros
 * analiticos personalizados que un Usuario guarda dentro de su Empresa. Ambas entidades
 * son tenant-scoped (Req 23, 48.5). Los indicadores del Tablero (Req 22) y del analisis
 * consolidado (Req 48.1) NO se modelan aqui: son agregaciones de solo lectura que se
 * componen via los puertos de {@code com.dessti.crm.reportesbi.application.indicadores}.</p>
 */
package com.dessti.crm.reportesbi.domain;
