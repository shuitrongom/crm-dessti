package com.dessti.crm.platform.respaldo.adapter.out;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Frontera de <strong>invocacion de procesos del sistema operativo</strong>
 * para el motor de respaldo. Aislar la ejecucion de procesos detras de este
 * puerto permite que las pruebas sustituyan la invocacion por un doble, de modo
 * que NUNCA se lance {@code pg_dump}/{@code pg_restore} reales en el arranque
 * del contexto de pruebas.
 */
public interface EjecutorProceso {

    /**
     * Ejecuta un comando externo y espera su finalizacion dentro del tiempo
     * limite.
     *
     * @param comando       comando y argumentos (el primero es el ejecutable);
     *                      no nulo ni vacio.
     * @param entorno       variables de entorno adicionales para el proceso
     *                      (por ejemplo {@code PGPASSWORD}); nunca se registran
     *                      en logs (Req 11.3).
     * @param tiempoLimite  tiempo maximo de ejecucion antes de abortar.
     * @return el codigo de salida del proceso.
     * @throws ProcesoException si el proceso no puede lanzarse, se interrumpe o
     *                          excede el tiempo limite.
     */
    int ejecutar(List<String> comando, Map<String, String> entorno, Duration tiempoLimite);

    /**
     * Error de invocacion de un proceso externo. Su mensaje no expone
     * credenciales ni variables de entorno sensibles (Req 11.3).
     */
    class ProcesoException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ProcesoException(String mensaje) {
            super(mensaje);
        }

        public ProcesoException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
