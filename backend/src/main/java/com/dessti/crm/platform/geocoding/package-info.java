/**
 * Utilidad transversal de <strong>geocoding via backend propio</strong> (revision
 * R2). El backend consulta al proveedor OSM (Photon con respaldo Nominatim) desde
 * el servidor y expone {@code GET /api/v1/geocoding/direcciones}, de modo que el
 * navegador ya no llama al tercero directamente (evita los fallos CORS/red del
 * navegador que quedaban ocultos por el {@code catchError -> []}).
 */
package com.dessti.crm.platform.geocoding;
