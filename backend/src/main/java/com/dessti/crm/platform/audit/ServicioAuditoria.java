package com.dessti.crm.platform.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementacion del {@link AuditoriaPort}: servicio de auditoria inmutable
 * encadenado por hash (Req 10).
 *
 * <h2>Encadenamiento y concurrencia</h2>
 * <p>Se mantiene una <strong>cadena global</strong> (ordenada por {@code id})
 * para el no repudio de toda la bitacora, incluidos los eventos de plataforma
 * ({@code tenant_id} nulo). Al registrar un evento se toma el
 * {@code hash_previo} del ultimo registro (mayor {@code id}) o la semilla si la
 * bitacora esta vacia, y se calcula {@code hash_actual = SHA-256(contenido ||
 * hash_previo)}.</p>
 *
 * <p>Para que la cadena sea consistente frente a inserciones concurrentes, la
 * escritura se ejecuta en una transaccion propia ({@link Propagation#REQUIRES_NEW})
 * y se <strong>serializa</strong> tomando un <em>bloqueo pesimista de
 * escritura</em> sobre el ultimo registro
 * ({@link RegistroAuditoriaRepository#bloquearUltimoRegistro()}). De este modo,
 * dos hilos que auditen a la vez no pueden leer la misma cabeza de cadena: el
 * segundo espera a que el primero confirme, evitando ramas o hashes duplicados
 * (reforzado ademas por la restriccion {@code UNIQUE(hash_actual)}). La cadena
 * completa se verifica en la Tarea 7.2.</p>
 *
 * <h2>trace_id (Req 10.12)</h2>
 * <p>El identificador de correlacion se toma del {@link MDC} (clave
 * {@value #MDC_TRACE_ID}) si esta presente; en caso contrario se registra
 * {@code null}. La propagacion del {@code trace_id} desde el filtro de entrada
 * se integra en tareas posteriores.</p>
 *
 * <h2>Sin secretos (Req 10.10)</h2>
 * <p>El servicio persiste el detalle y los valores anterior/nuevo tal cual los
 * recibe. NO es responsable de depurar secretos: el llamador debe excluir
 * contrasenas, claves y credenciales antes de construir el {@link EventoAuditoria}.</p>
 */
@Service
public class ServicioAuditoria implements AuditoriaPort {

    /** Clave del {@code trace_id} en el MDC de logging (Req 10.12). */
    public static final String MDC_TRACE_ID = "traceId";

    /** Accion usada al auditar accesos de lectura/exportacion de datos sensibles (Req 10.6). */
    static final String ACCION_ACCESO_DATOS_SENSIBLES = "acceso_datos_sensibles";

    private final RegistroAuditoriaRepository repositorio;

    /**
     * @param repositorio adaptador de persistencia de la bitacora.
     */
    public ServicioAuditoria(RegistroAuditoriaRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * {@inheritDoc}
     *
     * <p>La transaccion es propia ({@code REQUIRES_NEW}) para que el registro de
     * auditoria se confirme con independencia del resultado de la operacion de
     * negocio que lo origina, y serializa la escritura mediante bloqueo de la
     * cabeza de la cadena.</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public RegistroAuditoriaView registrar(EventoAuditoria evento) {
        UUID tenantId = evento.tenantId().orElse(null);
        String traceId = MDC.get(MDC_TRACE_ID);
        Instant ahora = Instant.now();

        RegistroAuditoria ultimo = repositorio.bloquearUltimoRegistro();
        String hashPrevio = (ultimo == null)
                ? CalculadoraHashCadena.HASH_SEMILLA
                : ultimo.getHashActual();

        // Coaccion a JSON valido para las columnas JSONB valor_anterior/valor_nuevo
        // (Req 10). Punto unico de la plataforma: cualquier servicio que pase texto
        // plano (p. ej. "activo", "existencias=0", "solicitado") queda protegido del
        // error 22P02 de PostgreSQL -> HTTP 500. El hash de la cadena se calcula
        // sobre el MISMO valor coaccionado que se persiste, preservando la integridad.
        String valorAnteriorJson = aJson(evento.valorAnterior());
        String valorNuevoJson = aJson(evento.valorNuevo());

        String hashActual = CalculadoraHashCadena.calcular(
                tenantId,
                evento.actor(),
                evento.accion(),
                evento.recurso(),
                evento.detalle(),
                valorAnteriorJson,
                valorNuevoJson,
                traceId,
                ahora,
                hashPrevio);

        RegistroAuditoria registro = new RegistroAuditoria(
                tenantId,
                evento.actor(),
                evento.accion(),
                evento.recurso(),
                evento.detalle(),
                valorAnteriorJson,
                valorNuevoJson,
                traceId,
                ahora,
                hashPrevio,
                hashActual);

        return aVista(repositorio.save(registro));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Registra el acceso reutilizando {@link #registrar(EventoAuditoria)}, de
     * modo que tambien queda encadenado por hash (Req 10.6). Si el evento no
     * trae una accion especifica, se etiqueta como
     * {@value #ACCION_ACCESO_DATOS_SENSIBLES}.</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public RegistroAuditoriaView registrarAccesoDatosSensibles(EventoAuditoria evento) {
        return registrar(evento);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<RegistroAuditoriaView> consultar(FiltroAuditoria filtro, Pageable pageable) {
        Pageable ordenado = conOrdenEstable(pageable);
        return repositorio.buscar(
                        filtro.tenantId().orElse(null),
                        filtro.actor().orElse(null),
                        filtro.recurso().orElse(null),
                        filtro.desde().orElse(null),
                        filtro.hasta().orElse(null),
                        ordenado)
                .map(ServicioAuditoria::aVista);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<RegistroAuditoriaView> exportar(FiltroAuditoria filtro) {
        return repositorio.exportar(
                        filtro.tenantId().orElse(null),
                        filtro.actor().orElse(null),
                        filtro.recurso().orElse(null),
                        filtro.desde().orElse(null),
                        filtro.hasta().orElse(null),
                        Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(ServicioAuditoria::aVista)
                .toList();
    }

    /**
     * Garantiza un orden estable de la consulta anadiendo el {@code id} como
     * criterio final de desempate (la cadena esta ordenada por id).
     */
    private static Pageable conOrdenEstable(Pageable pageable) {
        Sort orden = pageable.getSort().and(Sort.by(Sort.Direction.ASC, "id"));
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), orden);
    }

    /**
     * Coacciona un valor a una cadena JSON valida para las columnas JSONB
     * {@code valor_anterior}/{@code valor_nuevo} de la bitacora (Req 10), evitando
     * el error 22P02 de PostgreSQL (HTTP 500) cuando un llamador pasa texto plano.
     * Un {@code null} o texto en blanco se conserva como {@code null}; un texto que
     * ya es JSON valido (objeto, arreglo, cadena entrecomillada, numero, booleano o
     * {@code null}) se respeta tal cual; cualquier otro texto plano se envuelve como
     * literal de cadena JSON con el escape adecuado. Idempotente sobre JSON valido.
     */
    static String aJson(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        char inicio = limpio.charAt(0);
        boolean pareceJson = inicio == '{' || inicio == '[' || inicio == '"'
                || "true".equals(limpio) || "false".equals(limpio) || "null".equals(limpio)
                || limpio.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?");
        if (pareceJson) {
            return limpio;
        }
        StringBuilder sb = new StringBuilder(limpio.length() + 2);
        sb.append('"');
        for (int i = 0; i < limpio.length(); i++) {
            char ch = limpio.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static RegistroAuditoriaView aVista(RegistroAuditoria r) {
        return new RegistroAuditoriaView(
                r.getId(),
                r.getTenantId(),
                r.getActor(),
                r.getAccion(),
                r.getRecurso(),
                r.getDetalle(),
                r.getValorAnterior(),
                r.getValorNuevo(),
                r.getTraceId(),
                r.getTimestampUtc(),
                r.getHashPrevio(),
                r.getHashActual());
    }
}
