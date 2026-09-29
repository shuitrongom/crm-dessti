package com.dessti.crm.operacion.proyecto.application.evidencia;

import java.util.UUID;

/**
 * Puerto de salida para el almacenamiento de ARCHIVOS de evidencia de avance de
 * sitio (arquitectura hexagonal). Abstrae el almacen de objetos: el servicio de
 * aplicacion depende de esta interfaz, nunca del adaptador concreto. La
 * implementacion por defecto es de sistema de archivos
 * ({@code EvidenciaStorageFilesystemAdapter}); migrar a S3/MinIO en el futuro
 * solo requiere otro adaptador, sin tocar el dominio ni el servicio.
 *
 * <p>El almacen es <strong>multi-tenant</strong>: cada archivo se guarda bajo el
 * ambito de su {@code tenantId}, de modo que un tenant nunca pueda leer bytes de
 * otro. El servicio pasa el tenant del contexto autenticado (Req 23).</p>
 */
public interface EvidenciaStoragePort {

    /**
     * Persiste el contenido de un archivo de evidencia y devuelve su clave de
     * almacenamiento (opaca), que se guarda en la entidad {@code EvidenciaAvanceSitio}.
     *
     * @param tenantId       tenant propietario del archivo; obligatorio.
     * @param contenido      bytes del archivo; obligatorio y no vacio.
     * @param nombreOriginal nombre original del archivo (para derivar extension);
     *                       opcional.
     * @param tipoMime       tipo MIME del archivo; obligatorio.
     * @return la clave de almacenamiento con la que luego se recupera el archivo.
     * @throws EvidenciaStorageException si falla la escritura.
     */
    String guardar(UUID tenantId, byte[] contenido, String nombreOriginal, String tipoMime);

    /**
     * Recupera el contenido de un archivo de evidencia por su clave.
     *
     * @param tenantId tenant propietario; obligatorio (aisla el acceso).
     * @param clave    clave devuelta por {@link #guardar}.
     * @return los bytes del archivo.
     * @throws EvidenciaStorageException si no existe o falla la lectura.
     */
    byte[] leer(UUID tenantId, String clave);

    /**
     * Elimina el archivo asociado a la clave (limpieza tras un borrado logico o
     * compensacion). Es idempotente: si no existe, no falla.
     *
     * @param tenantId tenant propietario; obligatorio.
     * @param clave    clave del archivo a eliminar.
     */
    void eliminar(UUID tenantId, String clave);
}
