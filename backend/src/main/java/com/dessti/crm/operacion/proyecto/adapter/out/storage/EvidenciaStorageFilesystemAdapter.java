package com.dessti.crm.operacion.proyecto.adapter.out.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.proyecto.application.evidencia.EvidenciaStorageException;
import com.dessti.crm.operacion.proyecto.application.evidencia.EvidenciaStoragePort;
import com.dessti.crm.operacion.proyecto.application.evidencia.EvidenciaStorageProperties;

/**
 * Adaptador de {@link EvidenciaStoragePort} sobre el <strong>sistema de
 * archivos</strong>. Guarda cada evidencia como un archivo real bajo
 * {@code <directorioBase>/<tenantId>/<uuid>.<ext>}, de modo que:
 * <ul>
 *   <li>El binario nunca vive en la base de datos (no la infla ni degrada).</li>
 *   <li>El aislamiento multi-tenant se refleja en la ruta (un subdirectorio por
 *       tenant), y toda lectura exige el tenant propietario.</li>
 *   <li>La clave que se persiste en la entidad es el nombre del archivo
 *       ({@code <uuid>.<ext>}), opaca y sin datos del usuario.</li>
 * </ul>
 *
 * <p>Seguridad: la clave se valida para impedir <em>path traversal</em> (no
 * admite separadores ni {@code ..}); el nombre original del usuario NO forma
 * parte de la ruta (solo se usa para derivar la extension como respaldo del
 * MIME). Migrar a S3/MinIO seria otro adaptador que implemente el mismo puerto.</p>
 */
@Component
public class EvidenciaStorageFilesystemAdapter implements EvidenciaStoragePort {

    private static final Logger log = LoggerFactory.getLogger(EvidenciaStorageFilesystemAdapter.class);

    private final Path directorioBase;

    public EvidenciaStorageFilesystemAdapter(EvidenciaStorageProperties propiedades) {
        this.directorioBase = Path.of(propiedades.directorioBase()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directorioBase);
        } catch (IOException e) {
            throw new EvidenciaStorageException(
                    "No se pudo preparar el directorio base de evidencias.", e);
        }
        log.info("Almacen de evidencias en {}", this.directorioBase);
    }

    @Override
    public String guardar(UUID tenantId, byte[] contenido, String nombreOriginal, String tipoMime) {
        if (tenantId == null) {
            throw new EvidenciaStorageException("El tenant es obligatorio para guardar la evidencia.");
        }
        if (contenido == null || contenido.length == 0) {
            throw new EvidenciaStorageException("El contenido de la evidencia esta vacio.");
        }
        String extension = extensionDe(nombreOriginal, tipoMime);
        String clave = UUID.randomUUID() + extension;
        Path dirTenant = directorioTenant(tenantId);
        try {
            Files.createDirectories(dirTenant);
            Path destino = resolverSeguro(dirTenant, clave);
            Files.write(destino, contenido);
            return clave;
        } catch (IOException e) {
            throw new EvidenciaStorageException("No se pudo guardar el archivo de evidencia.", e);
        }
    }

    @Override
    public byte[] leer(UUID tenantId, String clave) {
        Path archivo = resolverSeguro(directorioTenant(tenantId), clave);
        if (!Files.isRegularFile(archivo)) {
            throw new EvidenciaStorageException("El archivo de evidencia no existe.");
        }
        try {
            return Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new EvidenciaStorageException("No se pudo leer el archivo de evidencia.", e);
        }
    }

    @Override
    public void eliminar(UUID tenantId, String clave) {
        try {
            Path archivo = resolverSeguro(directorioTenant(tenantId), clave);
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            // La eliminacion es best-effort: se registra sin romper el flujo.
            log.warn("No se pudo eliminar la evidencia {} del tenant {}", clave, tenantId);
        }
    }

    private Path directorioTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new EvidenciaStorageException("El tenant es obligatorio.");
        }
        return directorioBase.resolve(tenantId.toString());
    }

    /**
     * Resuelve la ruta del archivo dentro del directorio del tenant validando que
     * la clave no contenga separadores ni escape del directorio (path traversal).
     */
    private Path resolverSeguro(Path dirTenant, String clave) {
        if (clave == null || clave.isBlank()
                || clave.contains("/") || clave.contains("\\") || clave.contains("..")) {
            throw new EvidenciaStorageException("Clave de evidencia invalida.");
        }
        Path resuelto = dirTenant.resolve(clave).normalize();
        if (!resuelto.startsWith(dirTenant.normalize())) {
            throw new EvidenciaStorageException("Ruta de evidencia fuera del ambito del tenant.");
        }
        return resuelto;
    }

    /** Deriva la extension del archivo a partir del MIME (o del nombre original). */
    private static String extensionDe(String nombreOriginal, String tipoMime) {
        String mime = tipoMime == null ? "" : tipoMime.strip().toLowerCase(Locale.ROOT);
        return switch (mime) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> extensionDeNombre(nombreOriginal);
        };
    }

    private static String extensionDeNombre(String nombreOriginal) {
        if (nombreOriginal == null) {
            return "";
        }
        int punto = nombreOriginal.lastIndexOf('.');
        if (punto < 0 || punto == nombreOriginal.length() - 1) {
            return "";
        }
        String ext = nombreOriginal.substring(punto).toLowerCase(Locale.ROOT);
        // Solo se aceptan extensiones alfanumericas cortas para no meter basura en la ruta.
        return ext.matches("\\.[a-z0-9]{1,8}") ? ext : "";
    }
}
