package com.dessti.crm.social.analitica.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.dessti.crm.social.domain.CanalSocial;

/**
 * Funcion <strong>pura</strong> de agregacion de la analitica social (Req 66.1,
 * 66.6; Property 40). Dado un conjunto de {@link FilaMetricaSocial} de solo lectura,
 * calcula por {@link CanalSocial} las {@link MetricasSociales}: alcance,
 * interacciones, mensajes recibidos/enviados, tiempo de respuesta promedio y
 * conversiones/leads.
 *
 * <h2>Propiedades garantizadas (Property 40)</h2>
 * <ul>
 *   <li><strong>Solo lectura / no mutacion (Req 66.1):</strong> no modifica la lista
 *       de entrada ni sus elementos; itera sobre una copia defensiva. Es
 *       <em>deterministica</em>: la misma entrada produce siempre el mismo
 *       resultado.</li>
 *   <li><strong>Aislamiento por tenant (Req 66.6):</strong>
 *       {@link #porCanalParaTenant(List, UUID)} considera <em>unicamente</em> las
 *       filas cuyo {@code tenantId} coincide con el tenant objetivo; ninguna fila de
 *       otra Empresa contribuye a su resultado.</li>
 * </ul>
 *
 * <h2>Tiempo de respuesta</h2>
 * <p>Por cada Conversacion se toma el instante del primer entrante y el de la primera
 * respuesta saliente <em>posterior o igual</em> a ese entrante; su diferencia en
 * segundos aporta al promedio del canal. Las Conversaciones sin entrante o sin
 * respuesta saliente posterior no aportan al promedio. Si el canal no tiene ninguna
 * respuesta medible, su tiempo promedio es {@code 0}.</p>
 *
 * <p>Clase de utilidad sin estado: no se instancia.</p>
 */
public final class CalculoMetricasSociales {

    private CalculoMetricasSociales() {
        // Clase de utilidad: sin instancias.
    }

    /**
     * Agrega las metricas por Canal_Social sobre <em>todas</em> las filas recibidas,
     * sin filtrar por tenant. Util cuando el llamador ya acoto las filas al tenant
     * vigente (por ejemplo, via RLS/consulta de repositorio, Req 66.6).
     *
     * @param filas filas fuente de solo lectura; {@code null} se trata como vacio.
     * @return una lista inmutable de metricas, una por canal presente, ordenada por
     *         el nombre del canal; nunca {@code null}.
     */
    public static List<MetricasSociales> porCanal(List<FilaMetricaSocial> filas) {
        return agregar(filas, null, false);
    }

    /**
     * Agrega las metricas por Canal_Social considerando <strong>solo</strong> las
     * filas cuyo {@code tenantId} coincide con {@code tenantObjetivo} (Req 66.6,
     * Property 40). Las filas de otras Empresas se ignoran por completo.
     *
     * @param filas          filas fuente de solo lectura (posiblemente multi-tenant);
     *                       {@code null} se trata como vacio.
     * @param tenantObjetivo Empresa cuyas metricas se calculan; obligatorio.
     * @return una lista inmutable de metricas del tenant objetivo, una por canal,
     *         ordenada por el nombre del canal; nunca {@code null}.
     * @throws IllegalArgumentException si {@code tenantObjetivo} es nulo.
     */
    public static List<MetricasSociales> porCanalParaTenant(List<FilaMetricaSocial> filas,
                                                            UUID tenantObjetivo) {
        if (tenantObjetivo == null) {
            throw new IllegalArgumentException("El calculo por tenant exige el tenant objetivo.");
        }
        return agregar(filas, tenantObjetivo, true);
    }

    // ------------------------------------------------------------------
    // Nucleo puro
    // ------------------------------------------------------------------

    private static List<MetricasSociales> agregar(List<FilaMetricaSocial> filas,
                                                  UUID tenantObjetivo, boolean filtrarPorTenant) {
        // Copia defensiva: la funcion nunca muta la lista ni el orden del llamador.
        List<FilaMetricaSocial> fuente = (filas == null) ? List.of() : new ArrayList<>(filas);

        Map<CanalSocial, Acumulador> porCanal = new EnumMap<>(CanalSocial.class);
        for (FilaMetricaSocial fila : fuente) {
            if (fila == null) {
                continue;
            }
            if (filtrarPorTenant && !tenantObjetivo.equals(fila.tenantId())) {
                continue; // Aislamiento por tenant (Req 66.6).
            }
            porCanal.computeIfAbsent(fila.canal(), c -> new Acumulador()).acumular(fila);
        }

        List<MetricasSociales> resultado = new ArrayList<>(porCanal.size());
        for (Map.Entry<CanalSocial, Acumulador> entrada : porCanal.entrySet()) {
            resultado.add(entrada.getValue().aMetricas(entrada.getKey()));
        }
        // Orden determinista por etiqueta de canal (independiente del orden de entrada).
        resultado.sort(Comparator.comparing(m -> m.canal().valorBd()));
        return List.copyOf(resultado);
    }

    /**
     * Acumulador mutable interno de un canal; nunca se expone fuera de esta clase, de
     * modo que la funcion permanece pura hacia el exterior.
     */
    private static final class Acumulador {

        private long mensajesRecibidos;
        private long mensajesEnviados;
        private final Set<UUID> conversaciones = new HashSet<>();
        private final Set<UUID> leads = new HashSet<>();
        /** Primer entrante por Conversacion. */
        private final Map<UUID, Instant> primerEntrante = new HashMap<>();
        /** Primera respuesta saliente (cualquiera) por Conversacion. */
        private final Map<UUID, Instant> salientesPorConversacion = new HashMap<>();

        void acumular(FilaMetricaSocial fila) {
            conversaciones.add(fila.conversacionId());
            if (fila.esLead()) {
                leads.add(fila.conversacionId());
            }
            if (fila.esEntrante()) {
                mensajesRecibidos++;
                if (fila.instante() != null) {
                    primerEntrante.merge(fila.conversacionId(), fila.instante(),
                            CalculoMetricasSociales::menor);
                }
            } else if (fila.esSaliente()) {
                mensajesEnviados++;
                if (fila.instante() != null) {
                    salientesPorConversacion.merge(fila.conversacionId(), fila.instante(),
                            CalculoMetricasSociales::menor);
                }
            }
        }

        MetricasSociales aMetricas(CanalSocial canal) {
            long totalSegundos = 0L;
            long conRespuesta = 0L;
            for (Map.Entry<UUID, Instant> entrada : primerEntrante.entrySet()) {
                Instant entrante = entrada.getValue();
                Instant respuesta = primeraRespuestaPosterior(entrada.getKey(), entrante);
                if (respuesta != null) {
                    totalSegundos += Duration.between(entrante, respuesta).getSeconds();
                    conRespuesta++;
                }
            }
            long promedio = (conRespuesta == 0L) ? 0L : totalSegundos / conRespuesta;
            long interacciones = mensajesRecibidos + mensajesEnviados;
            return new MetricasSociales(
                    canal,
                    conversaciones.size(),
                    interacciones,
                    mensajesRecibidos,
                    mensajesEnviados,
                    promedio,
                    leads.size());
        }

        /**
         * Primera respuesta saliente cuyo instante no es anterior al {@code entrante}
         * de la Conversacion; {@code null} si no hubo respuesta posterior. Como solo
         * se guarda la saliente mas temprana, se compara esa contra el entrante.
         */
        private Instant primeraRespuestaPosterior(UUID conversacionId, Instant entrante) {
            Instant saliente = salientesPorConversacion.get(conversacionId);
            if (saliente == null || saliente.isBefore(entrante)) {
                return null;
            }
            return saliente;
        }
    }

    /** Devuelve el menor de dos instantes (para el {@code merge} determinista). */
    private static Instant menor(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
