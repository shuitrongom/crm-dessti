package com.dessti.crm.platform.respaldo.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.respaldo.adapter.out.persistence.RespaldoRepository;
import com.dessti.crm.platform.respaldo.domain.EstadoRespaldo;
import com.dessti.crm.platform.respaldo.domain.Respaldo;
import com.dessti.crm.platform.respaldo.domain.TipoRespaldo;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;

/**
 * Servicio de orquestacion del respaldo y la recuperacion de datos (Req 50).
 *
 * <p>Coordina los puertos {@link MotorRespaldoPort} (volcado/restauracion) y
 * {@link CifradorRespaldoPort} (cifrado/descifrado del artefacto), persiste la
 * bitacora de la ejecucion en {@link Respaldo} (metadatos, sin contenido ni
 * material de llaves) y <strong>audita</strong> cada operacion como evento de
 * plataforma (Req 50.4). El acceso queda restringido por RBAC en el adaptador
 * de entrada al {@code super_admin} (Req 50.3).</p>
 *
 * <p><strong>Respaldo cifrado (Req 50.3, 67):</strong> el flujo produce un
 * volcado en claro en un archivo temporal, lo cifra hacia el directorio de
 * almacenamiento restringido y elimina el temporal en claro, de modo que en
 * reposo el respaldo quede siempre cifrado. El alias de la version de llave se
 * conserva en la bitacora para poder descifrar en la restauracion tras una
 * rotacion (Req 67).</p>
 */
@Service
public class ServicioRespaldo {

    static final String RECURSO = "respaldo";

    private static final Logger log = LoggerFactory.getLogger(ServicioRespaldo.class);
    private static final DateTimeFormatter SELLO =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final RespaldoRepository respaldoRepository;
    private final MotorRespaldoPort motor;
    private final CifradorRespaldoPort cifrador;
    private final AuditoriaPort auditoria;
    private final RespaldoProperties propiedades;

    public ServicioRespaldo(RespaldoRepository respaldoRepository,
                            MotorRespaldoPort motor,
                            CifradorRespaldoPort cifrador,
                            AuditoriaPort auditoria,
                            RespaldoProperties propiedades) {
        this.respaldoRepository = respaldoRepository;
        this.motor = motor;
        this.cifrador = cifrador;
        this.auditoria = auditoria;
        this.propiedades = propiedades;
    }

    /**
     * Ejecuta un respaldo completo cifrado y registra su bitacora (Req 50.1,
     * 50.3, 50.4). Cualquier fallo se marca en la bitacora como {@code FALLIDO}
     * y se audita, propagando una {@link RespaldoException} controlada.
     *
     * @return el DTO de la ejecucion resultante.
     */
    @Transactional
    public RespaldoDto ejecutarRespaldo() {
        String actor = actorActual();
        Respaldo respaldo = respaldoRepository.saveAndFlush(
                Respaldo.iniciar(TipoRespaldo.RESPALDO, "completo", actor));

        Path directorio = Paths.get(propiedades.directorio());
        String sello = ZonedDateTime.now(ZoneOffset.UTC).format(SELLO);
        Path volcado = directorio.resolve("volcado-" + sello + "-" + respaldo.getId() + ".sql");
        Path artefacto = directorio.resolve("respaldo-" + sello + "-" + respaldo.getId() + ".enc");

        try {
            Files.createDirectories(directorio);
            motor.volcar(volcado);
            CifradorRespaldoPort.ArtefactoCifrado meta = cifrador.cifrar(volcado, artefacto);
            respaldo.completar(artefacto.toString(), meta.aliasLlave(), meta.checksum(), meta.tamanoBytes());
            respaldoRepository.save(respaldo);
            auditar(actor, "ejecutar",
                    "respaldo completado id=" + respaldo.getId() + " ubicacion=" + artefacto);
            log.info("Respaldo {} completado ({} bytes cifrados) (Req 50).",
                    respaldo.getId(), meta.tamanoBytes());
            return RespaldoDto.de(respaldo);
        } catch (IOException | RuntimeException e) {
            marcarFallido(respaldo, actor, "ejecutar", e);
            throw new RespaldoException("Fallo al ejecutar el respaldo (Req 50).", e);
        } finally {
            borrarSilencioso(volcado);
        }
    }

    /**
     * Restaura la base de datos a partir de un respaldo previo (Req 50.2),
     * descifrando su artefacto y registrando la operacion en la bitacora y en
     * auditoria (Req 50.4). Solo referencia respaldos en estado
     * {@code COMPLETADO}.
     *
     * @param respaldoId identificador del respaldo de origen; no nulo.
     * @return el DTO de la ejecucion de restauracion.
     */
    @Transactional
    public RespaldoDto restaurar(UUID respaldoId) {
        String actor = actorActual();
        Respaldo origen = cargarCompletado(respaldoId);

        Respaldo restauracion = respaldoRepository.saveAndFlush(
                Respaldo.iniciar(TipoRespaldo.RESTAURACION, "completo", actor));

        Path artefacto = Paths.get(origen.getUbicacion());
        String sello = ZonedDateTime.now(ZoneOffset.UTC).format(SELLO);
        Path volcado = Paths.get(propiedades.directorio())
                .resolve("restauracion-" + sello + "-" + restauracion.getId() + ".sql");

        try {
            Files.createDirectories(volcado.getParent());
            cifrador.descifrar(artefacto, volcado);
            motor.restaurar(volcado);
            restauracion.completar(origen.getUbicacion(), origen.getAliasLlave(), origen.getChecksum(), null);
            respaldoRepository.save(restauracion);
            auditar(actor, "restaurar",
                    "restauracion completada desde respaldo id=" + origen.getId());
            log.info("Restauracion {} completada desde respaldo {} (Req 50).",
                    restauracion.getId(), origen.getId());
            return RespaldoDto.de(restauracion);
        } catch (IOException | RuntimeException e) {
            marcarFallido(restauracion, actor, "restaurar", e);
            throw new RespaldoException("Fallo al restaurar desde el respaldo (Req 50).", e);
        } finally {
            borrarSilencioso(volcado);
        }
    }

    /** Historial paginado de la bitacora de respaldos (Req 50.4). */
    @Transactional(readOnly = true)
    public Page<RespaldoDto> listar(Pageable pageable) {
        return respaldoRepository.findAllByOrderByInstanteDesc(pageable).map(RespaldoDto::de);
    }

    private Respaldo cargarCompletado(UUID respaldoId) {
        if (respaldoId == null) {
            throw new RecursoNoEncontradoException("No se encontro el respaldo solicitado.");
        }
        Respaldo origen = respaldoRepository.findById(respaldoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el respaldo solicitado."));
        if (origen.getEstado() != EstadoRespaldo.COMPLETADO || origen.getTipo() != TipoRespaldo.RESPALDO) {
            throw new RespaldoException(
                    "El respaldo indicado no esta completado y no puede usarse para restaurar (Req 50.2).");
        }
        return origen;
    }

    private void marcarFallido(Respaldo respaldo, String actor, String accion, Exception e) {
        // Se registra el TIPO de excepcion y un mensaje generico; nunca material
        // de llave ni datos sensibles (Req 67.2).
        respaldo.fallar(e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        respaldoRepository.save(respaldo);
        auditar(actor, accion, "operacion de respaldo fallida id=" + respaldo.getId());
        log.error("Operacion de respaldo {} fallida (Req 50): {}", respaldo.getId(), e.getMessage());
    }

    private void borrarSilencioso(Path ruta) {
        if (ruta == null) {
            return;
        }
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException e) {
            // El volcado en claro es transitorio; si no se puede borrar, se
            // registra sin interrumpir el flujo (no se expone contenido).
            log.warn("No se pudo eliminar el archivo temporal de respaldo '{}': {}", ruta, e.getMessage());
        }
    }

    private void auditar(String actor, String accion, String detalle) {
        auditoria.registrar(EventoAuditoria.dePlataforma(actor, accion, RECURSO, detalle, null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(n -> n != null && !n.isBlank())
                .orElse("sistema");
    }
}
