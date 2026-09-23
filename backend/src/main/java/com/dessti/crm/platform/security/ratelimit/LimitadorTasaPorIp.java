package com.dessti.crm.platform.security.ratelimit;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limitador de tasa por direccion IP mediante <b>ventana fija</b> (fixed
 * window) en memoria (Req 2.4).
 *
 * <p>Cada IP dispone de un contador y de la marca de inicio de su ventana
 * actual. Cuando llega una peticion:</p>
 * <ul>
 *   <li>si la ventana vigente expiro (transcurrio mas de la duracion
 *       configurada desde su inicio), el contador se reinicia y comienza una
 *       ventana nueva anclada al instante actual;</li>
 *   <li>se incrementa el contador; si supera el maximo permitido, la peticion
 *       se rechaza (return {@code false}).</li>
 * </ul>
 *
 * <p><strong>Alcance (single-node):</strong> el estado vive en memoria del
 * proceso, por lo que el limite es <b>por instancia</b> de la aplicacion. En el
 * despliegue on-premise inicial hay un unico nodo, por lo que es suficiente. Si
 * en el futuro se escala horizontalmente, este limitador debera sustituirse por
 * uno distribuido (por ejemplo respaldado en Redis) para que el limite sea
 * global; el resto del codigo (filtro y manejo de errores) permanece igual.</p>
 *
 * <p><strong>Determinismo:</strong> toda la logica temporal usa el
 * {@link Clock} inyectado, nunca {@code System.currentTimeMillis()} ni
 * {@code Instant.now()} directos, de modo que las pruebas pueden avanzar el
 * tiempo de forma controlada.</p>
 *
 * <p>La clase es thread-safe: el mapa es un {@link ConcurrentHashMap} y la
 * actualizacion por IP se realiza dentro de {@link ConcurrentHashMap#compute}
 * (bloqueo por bucket), suficiente para el volumen esperado del endpoint de
 * autenticacion.</p>
 */
public class LimitadorTasaPorIp {

    /** Contador y marca de inicio de la ventana de una IP. */
    private static final class Ventana {
        private long inicioMillis;
        private final AtomicInteger conteo;

        private Ventana(long inicioMillis) {
            this.inicioMillis = inicioMillis;
            this.conteo = new AtomicInteger(0);
        }
    }

    private final int maxPeticiones;
    private final long ventanaMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Ventana> ventanasPorIp = new ConcurrentHashMap<>();

    /**
     * @param maxPeticiones numero maximo de peticiones permitidas por ventana
     *                      (Req 2.4: 100).
     * @param ventanaMillis duracion de la ventana en milisegundos (Req 2.4:
     *                      60000 = 1 min).
     * @param clock         reloj (UTC) para medir el tiempo; obligatorio.
     */
    public LimitadorTasaPorIp(int maxPeticiones, long ventanaMillis, Clock clock) {
        if (maxPeticiones < 1) {
            throw new IllegalArgumentException("maxPeticiones debe ser >= 1");
        }
        if (ventanaMillis < 1) {
            throw new IllegalArgumentException("ventanaMillis debe ser >= 1");
        }
        this.maxPeticiones = maxPeticiones;
        this.ventanaMillis = ventanaMillis;
        this.clock = clock;
    }

    /**
     * Registra una peticion de la IP indicada e informa si se permite.
     *
     * @param ip direccion de origen; obligatorio.
     * @return {@code true} si la peticion esta dentro del limite; {@code false}
     *         si lo excede y debe rechazarse con 429 (Req 2.4).
     */
    public boolean permitir(String ip) {
        long ahora = clock.millis();
        Ventana ventana = ventanasPorIp.compute(ip, (clave, actual) -> {
            if (actual == null || ahora - actual.inicioMillis >= ventanaMillis) {
                return new Ventana(ahora);
            }
            return actual;
        });
        int conteo = ventana.conteo.incrementAndGet();
        return conteo <= maxPeticiones;
    }

    /**
     * @return el maximo de peticiones permitidas por ventana.
     */
    public int maxPeticiones() {
        return maxPeticiones;
    }

    /**
     * Elimina el estado de las IP cuya ventana ya expiro. Metodo utilitario para
     * una eventual limpieza periodica; no es necesario para la correccion (una
     * ventana expirada se reinicia perezosamente en {@link #permitir(String)}),
     * pero acota el crecimiento del mapa ante muchas IP distintas.
     */
    public void purgarExpiradas() {
        long ahora = clock.millis();
        ventanasPorIp.values().removeIf(v -> ahora - v.inicioMillis >= ventanaMillis);
    }
}
