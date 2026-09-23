package com.dessti.crm.calidad.application;

/**
 * Entrada de la vista de solo lectura de trazabilidad de clausulas ISO 9001:2026
 * (Req 70.10). Mapea una clausula soportada al tema y al recurso/evidencia del Sistema
 * que la habilita, con el requisito de referencia. Es un catalogo fijo (no persistido).
 *
 * @param clausula          clausula de ISO 9001:2026 (por ejemplo {@code "10.2"}).
 * @param tema              tema breve de la clausula.
 * @param recursoEvidencia  recurso o evidencia del Sistema que la habilita.
 * @param requisito         requisito del Sistema de referencia (por ejemplo {@code "Req 70.2"}).
 */
public record TrazabilidadClausulaDto(
        String clausula,
        String tema,
        String recursoEvidencia,
        String requisito) {
}
