package com.dessti.crm.platform.geocoding;

/**
 * Sugerencia de direccion ya mapeada a los campos que consumen los formularios
 * del frontend (Req 4, revision R2). Es el contrato de salida del endpoint
 * {@code GET /api/v1/geocoding/direcciones}: el backend hace el geocoding contra
 * el proveedor OSM y devuelve directamente esta forma, de modo que el navegador
 * ya no llama al tercero ni realiza mapeo alguno.
 *
 * <p>Salvo {@code etiqueta} (texto legible para la lista de sugerencias), el
 * resto de campos se vuelcan en los controles del formulario al elegir la
 * opcion. Cualquier campo puede quedar en cadena vacia cuando el proveedor no lo
 * aporta para ese resultado; nunca es {@code null}.</p>
 *
 * @param etiqueta texto legible de la sugerencia (para el {@code mat-option}).
 * @param calle    calle y numero (via + numero exterior si existe).
 * @param ciudad   ciudad (con respaldo a distrito/condado/estado/nombre).
 * @param estado   estado o provincia.
 * @param cp       codigo postal.
 * @param pais     pais.
 */
public record DireccionSugeridaDto(String etiqueta, String calle, String ciudad,
                                   String estado, String cp, String pais) {
}
