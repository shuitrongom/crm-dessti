package com.dessti.crm.platform.respaldo.adapter.out;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementacion de {@link EjecutorProceso} basada en {@link ProcessBuilder}.
 *
 * <p>Lanza el comando pasando cada argumento por separado (nunca una cadena de
 * shell concatenada), evitando inyeccion de comandos. Las variables de entorno
 * sensibles (por ejemplo {@code PGPASSWORD}) se inyectan en el entorno del
 * proceso hijo y <strong>nunca</strong> se escriben en logs (Req 11.3). Si el
 * proceso excede el tiempo limite, se destruye a la fuerza.</p>
 *
 * <p><strong>Solo se instancia cuando la capacidad esta habilitada</strong>
 * ({@code crm.respaldo.habilitado=true}); ver la configuracion del modulo.</p>
 */
public class EjecutorProcesoSistema implements EjecutorProceso {

    private static final Logger log = LoggerFactory.getLogger(EjecutorProcesoSistema.class);

    @Override
    public int ejecutar(List<String> comando, Map<String, String> entorno, Duration tiempoLimite) {
        if (comando == null || comando.isEmpty()) {
            throw new ProcesoException("El comando del proceso de respaldo es obligatorio.");
        }
        ProcessBuilder pb = new ProcessBuilder(comando);
        // Redirige la salida de error a la estandar para poder drenarla.
        pb.redirectErrorStream(true);
        if (entorno != null) {
            // Las credenciales se colocan SOLO en el entorno del proceso hijo.
            pb.environment().putAll(entorno);
        }

        Process proceso = null;
        try {
            // Se registra unicamente el ejecutable (comando.get(0)), nunca los
            // argumentos ni el entorno, que podrian revelar rutas/credenciales.
            log.info("Ejecutando proceso de respaldo: {} (Req 50).", comando.get(0));
            proceso = pb.start();
            // Se drena la salida para evitar bloqueos por buffer lleno; no se
            // registra su contenido (podria incluir datos sensibles del volcado).
            drenar(proceso);
            boolean termino = proceso.waitFor(tiempoLimite.toMillis(), TimeUnit.MILLISECONDS);
            if (!termino) {
                proceso.destroyForcibly();
                throw new ProcesoException(
                        "El proceso de respaldo excedio el tiempo limite y fue abortado.");
            }
            return proceso.exitValue();
        } catch (IOException e) {
            throw new ProcesoException("No se pudo lanzar el proceso de respaldo.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (proceso != null) {
                proceso.destroyForcibly();
            }
            throw new ProcesoException("El proceso de respaldo fue interrumpido.", e);
        }
    }

    private void drenar(Process proceso) throws IOException {
        try (var is = proceso.getInputStream()) {
            byte[] buffer = new byte[8192];
            while (is.read(buffer) != -1) {
                // Se descarta el contenido: no se registra para no filtrar datos.
            }
        }
    }
}
