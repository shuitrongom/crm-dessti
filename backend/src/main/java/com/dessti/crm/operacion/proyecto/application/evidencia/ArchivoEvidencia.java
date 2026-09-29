package com.dessti.crm.operacion.proyecto.application.evidencia;

/**
 * Contenido de un archivo de evidencia listo para servir por HTTP: los bytes mas
 * los metadatos que el controlador necesita para los encabezados
 * ({@code Content-Type} y {@code Content-Disposition}).
 *
 * @param contenido      bytes del archivo.
 * @param tipoMime       tipo MIME para el {@code Content-Type}.
 * @param nombreOriginal nombre original para el {@code Content-Disposition}.
 */
public record ArchivoEvidencia(byte[] contenido, String tipoMime, String nombreOriginal) {
}
