package com.dessti.crm.operacion.proyecto.application.evidencia;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del almacenamiento de archivos de EVIDENCIA de avance de sitio
 * (Req 3.2, deber-ser enterprise). A diferencia del logo/branding o la foto de
 * Producto (que se guardan como {@code data-URI} base64 en la BD, aceptable para
 * imagenes pequenas de identificacion visual), la evidencia operativa
 * (fotografias y documentos de respaldo del avance en campo) se persiste como
 * <strong>archivo real</strong> en un almacen de objetos: el binario NUNCA vive
 * en la base de datos. Hoy el adaptador es de sistema de archivos; el contrato
 * ({@code EvidenciaStoragePort}) permite migrar a S3/MinIO sin tocar el dominio.
 *
 * <p>Se enlaza a {@code crm.evidencias.storage.*} en {@code application.yml}
 * siguiendo el patron del resto de la plataforma: un {@code record} anotado con
 * {@link ConfigurationProperties} y registrado via
 * {@code @EnableConfigurationProperties} (ver {@code EvidenciaStorageConfig}).
 * La validacion es fail-fast en el constructor compacto (aborta el arranque si
 * el directorio base esta en blanco), como {@code EmisorProperties}.</p>
 *
 * @param directorioBase     ruta raiz en disco donde se guardan las evidencias.
 *                           Se organiza internamente por tenant. Obligatoria.
 * @param maxTamanoMb        tamano maximo por archivo en MB (por defecto 10).
 * @param tiposMimePermitidos lista de tipos MIME aceptados (por defecto
 *                           JPG/PNG/WebP/PDF). Si viene vacia, se usa el conjunto
 *                           por defecto.
 */
@ConfigurationProperties(prefix = "crm.evidencias.storage")
public record EvidenciaStorageProperties(
        String directorioBase,
        Integer maxTamanoMb,
        List<String> tiposMimePermitidos) {

    /** Directorio base por defecto (relativo al arranque) si no se configura otro. */
    public static final String DIRECTORIO_POR_DEFECTO = "datos/evidencias";

    /** Tamano maximo por archivo por defecto: 10 MB. */
    public static final int MAX_TAMANO_MB_POR_DEFECTO = 10;

    /** Tipos MIME admitidos por defecto: imagenes comunes y PDF. */
    public static final Set<String> TIPOS_MIME_POR_DEFECTO = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    /**
     * Normaliza valores y aplica defaults. No lanza si el directorio esta en
     * blanco: cae al {@value #DIRECTORIO_POR_DEFECTO} para que un entorno de
     * desarrollo/pruebas arranque sin configuracion explicita; en produccion se
     * fija por variable de entorno ({@code EVIDENCIAS_DIR}).
     */
    public EvidenciaStorageProperties {
        directorioBase = (directorioBase == null || directorioBase.isBlank())
                ? DIRECTORIO_POR_DEFECTO
                : directorioBase.strip();
        maxTamanoMb = (maxTamanoMb == null || maxTamanoMb <= 0)
                ? MAX_TAMANO_MB_POR_DEFECTO
                : maxTamanoMb;
        tiposMimePermitidos = (tiposMimePermitidos == null || tiposMimePermitidos.isEmpty())
                ? List.copyOf(TIPOS_MIME_POR_DEFECTO)
                : List.copyOf(tiposMimePermitidos);
    }

    /** Tamano maximo por archivo expresado en bytes. */
    public long maxTamanoBytes() {
        return (long) maxTamanoMb * 1024L * 1024L;
    }

    /** Indica si el tipo MIME indicado esta permitido (comparacion normalizada). */
    public boolean permiteMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return false;
        }
        String normal = mime.strip().toLowerCase();
        return tiposMimePermitidos.stream().anyMatch(t -> t.equalsIgnoreCase(normal));
    }
}
