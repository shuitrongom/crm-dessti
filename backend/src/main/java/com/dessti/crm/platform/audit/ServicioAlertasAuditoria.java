package com.dessti.crm.platform.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deteccion de patrones sensibles y emision de alertas de auditoria
 * (Alerta_Auditoria, Req 10.11).
 *
 * <p>Dado un evento recien registrado en la bitacora, resuelve el
 * {@link PatronAlerta} asociado a su accion, y por cada regla activa aplicable
 * (del tenant del evento o de plataforma) cuenta los eventos de ese patron
 * dentro de la <strong>ventana temporal</strong> configurada. Si el conteo
 * alcanza o supera el <strong>umbral</strong>, emite una Notificacion a los
 * destinatarios configurados a traves del {@link NotificadorAlertasPort}.</p>
 *
 * <h2>Enganche con el registro de auditoria (decision documentada)</h2>
 * <p>La deteccion se invoca <strong>despues</strong> de registrar el evento
 * (patron "evaluar tras registrar"). Para no perjudicar el rendimiento ni la
 * integridad de la cadena:</p>
 * <ul>
 *   <li>La evaluacion es <em>independiente</em> de la escritura de la cadena:
 *       hace una consulta de conteo acotada por indice (accion + ventana), no
 *       recorre la bitacora completa.</li>
 *   <li>Los fallos de la deteccion o de la notificacion se <em>aislan</em> (se
 *       registran y no se propagan), de modo que un problema al alertar nunca
 *       impide registrar la auditoria ni rompe la operacion de negocio.</li>
 *   <li>No abre ni exige una transaccion propia sobre la cadena; se apoya en
 *       consultas de solo lectura.</li>
 * </ul>
 * <p>La integracion concreta (llamar a {@link #evaluar(RegistroAuditoriaView)}
 * justo tras {@code registrar(...)}) puede realizarse desde el propio
 * {@link ServicioAuditoria} o mediante un evento de aplicacion en tareas
 * posteriores; aqui se deja el punto de deteccion listo y probado de forma
 * unitaria.</p>
 */
@Service
public class ServicioAlertasAuditoria {

    private static final Logger log = LoggerFactory.getLogger(ServicioAlertasAuditoria.class);

    private final AlertaAuditoriaRepository alertaRepositorio;
    private final RegistroAuditoriaRepository registroRepositorio;
    private final NotificadorAlertasPort notificador;

    /**
     * @param alertaRepositorio   repositorio de reglas de alerta.
     * @param registroRepositorio repositorio de la bitacora (conteo por ventana).
     * @param notificador         puerto de emision de la Notificacion.
     */
    public ServicioAlertasAuditoria(AlertaAuditoriaRepository alertaRepositorio,
                                    RegistroAuditoriaRepository registroRepositorio,
                                    NotificadorAlertasPort notificador) {
        this.alertaRepositorio = alertaRepositorio;
        this.registroRepositorio = registroRepositorio;
        this.notificador = notificador;
    }

    /**
     * Evalua los patrones de alerta ante un evento recien registrado y dispara
     * las Notificaciones cuyas reglas superen su umbral dentro de la ventana.
     *
     * <p>Es tolerante a fallos: cualquier excepcion al evaluar o notificar se
     * registra y se contiene, sin propagarse al llamador (Req 10.11 no debe
     * interferir con el registro de la auditoria ni con la operacion origen).</p>
     *
     * @param evento vista del registro recien insertado.
     * @return la lista de alertas efectivamente disparadas (puede estar vacia).
     */
    @Transactional(readOnly = true)
    public List<AlertaDisparada> evaluar(RegistroAuditoriaView evento) {
        try {
            Optional<PatronAlerta> patron = patronDe(evento.accion());
            if (patron.isEmpty()) {
                return List.of();
            }
            return evaluarPatron(evento.tenantId(), patron.get());
        } catch (RuntimeException e) {
            // Aislar: la deteccion de alertas nunca debe romper el registro de
            // auditoria ni la operacion de negocio que lo origino.
            log.error("Fallo al evaluar alertas de auditoria para accion '{}' (contenido no incluido)",
                    evento.accion(), e);
            return List.of();
        }
    }

    private List<AlertaDisparada> evaluarPatron(UUID tenantId, PatronAlerta patron) {
        List<AlertaAuditoria> reglas = alertaRepositorio.buscarActivasPorPatron(tenantId, patron);
        if (reglas.isEmpty()) {
            return List.of();
        }

        Instant ahora = Instant.now();
        return reglas.stream()
                .map(regla -> intentarDisparar(regla, patron, tenantId, ahora))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<AlertaDisparada> intentarDisparar(
            AlertaAuditoria regla, PatronAlerta patron, UUID tenantId, Instant ahora) {

        Instant desde = ahora.minus(regla.getVentana());
        long conteo = registroRepositorio.contarPorAccionEnVentana(
                tenantId, patron.accionAsociada(), desde);

        if (conteo < regla.getUmbral()) {
            return Optional.empty();
        }

        AlertaDisparada disparada = new AlertaDisparada(
                tenantId, patron, conteo, regla.getUmbral(), regla.getDestinatarios(), ahora);

        try {
            notificador.notificar(disparada);
        } catch (RuntimeException e) {
            // La notificacion la implementa la Tarea 43; aislar su fallo.
            log.error("Fallo al emitir la Notificacion de alerta {} (tenant={})", patron, tenantId, e);
        }
        return Optional.of(disparada);
    }

    /**
     * Resuelve el patron de alerta asociado a una accion de la bitacora, si lo
     * hay.
     *
     * @param accion accion del registro de auditoria.
     * @return el patron asociado, o vacio si la accion no dispara ninguna alerta.
     */
    static Optional<PatronAlerta> patronDe(String accion) {
        if (accion == null) {
            return Optional.empty();
        }
        for (PatronAlerta p : PatronAlerta.values()) {
            if (p.accionAsociada().equals(accion)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
