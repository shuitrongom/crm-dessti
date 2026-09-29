package com.dessti.crm.operacion.proyecto.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dessti.crm.operacion.proyecto.application.evidencia.EvidenciaStorageException;
import com.dessti.crm.operacion.proyecto.application.evidencia.EvidenciaStorageProperties;

/**
 * Pruebas del adaptador de almacenamiento de evidencias en sistema de archivos.
 * Verifican, sin Spring:
 *   - guardar+leer devuelve el mismo contenido y aisla por tenant (subdirectorio).
 *   - la clave lleva la extension derivada del MIME.
 *   - una clave con path traversal se rechaza (seguridad).
 *   - eliminar es idempotente.
 */
class EvidenciaStorageFilesystemAdapterTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private EvidenciaStorageFilesystemAdapter adaptador(Path base) {
        EvidenciaStorageProperties props = new EvidenciaStorageProperties(
                base.toString(), 10, List.of("image/jpeg", "application/pdf"));
        return new EvidenciaStorageFilesystemAdapter(props);
    }

    @Test
    void guardarYLeerDevuelveElMismoContenido(@TempDir Path base) {
        var adaptador = adaptador(base);
        byte[] contenido = "foto-de-instalacion".getBytes();

        String clave = adaptador.guardar(TENANT_A, contenido, "obra.jpg", "image/jpeg");

        assertThat(clave).endsWith(".jpg");
        assertThat(adaptador.leer(TENANT_A, clave)).isEqualTo(contenido);
        // El archivo vive bajo el subdirectorio del tenant.
        assertThat(base.resolve(TENANT_A.toString()).resolve(clave)).exists();
    }

    @Test
    void aislaElContenidoPorTenant(@TempDir Path base) {
        var adaptador = adaptador(base);
        String clave = adaptador.guardar(TENANT_A, "x".getBytes(), "a.pdf", "application/pdf");

        // Otro tenant no puede leer la clave de A (su archivo no existe en su dir).
        assertThatThrownBy(() -> adaptador.leer(TENANT_B, clave))
                .isInstanceOf(EvidenciaStorageException.class);
    }

    @Test
    void rechazaClaveConPathTraversal(@TempDir Path base) {
        var adaptador = adaptador(base);
        assertThatThrownBy(() -> adaptador.leer(TENANT_A, "../../etc/passwd"))
                .isInstanceOf(EvidenciaStorageException.class);
    }

    @Test
    void eliminarEsIdempotente(@TempDir Path base) {
        var adaptador = adaptador(base);
        String clave = adaptador.guardar(TENANT_A, "y".getBytes(), "b.jpg", "image/jpeg");

        adaptador.eliminar(TENANT_A, clave);
        // Segunda eliminacion no falla.
        adaptador.eliminar(TENANT_A, clave);
        assertThatThrownBy(() -> adaptador.leer(TENANT_A, clave))
                .isInstanceOf(EvidenciaStorageException.class);
    }
}
