package com.dessti.crm.operacion.proyecto.application.evidencia;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.operacion.proyecto.adapter.out.persistence.AvanceSitioRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.EvidenciaAvanceSitioRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.ProyectoRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.SitioRepository;
import com.dessti.crm.operacion.proyecto.domain.AvanceSitio;
import com.dessti.crm.operacion.proyecto.domain.FaseSitioGenerica;
import com.dessti.crm.operacion.proyecto.domain.Proyecto;
import com.dessti.crm.operacion.proyecto.domain.Sitio;
import com.dessti.crm.operacion.proyecto.domain.evidencia.EstadoEvidencia;
import com.dessti.crm.operacion.proyecto.domain.evidencia.EvidenciaAvanceSitio;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion de las EVIDENCIAS de avance de sitio con flujo de
 * aprobacion (Req 3.2, deber-ser enterprise). Coordina el dominio
 * ({@link EvidenciaAvanceSitio}), el almacen de archivos
 * ({@link EvidenciaStoragePort}) y la auditoria, y provee la guarda de negocio
 * al {@code ServicioProyectos} implementando {@link EvidenciaAvanceConsultaPort}.
 *
 * <h2>Operaciones</h2>
 * <ul>
 *   <li><strong>subir:</strong> valida MIME/tamano contra la configuracion,
 *       guarda el archivo real en el almacen, materializa el avance del Sitio si
 *       no existe (en PENDIENTE) y registra la evidencia PENDIENTE, congelando la
 *       fase actual del Sitio como la fase que documenta.</li>
 *   <li><strong>listar:</strong> devuelve las evidencias de un Sitio (mas reciente
 *       primero) para la galeria del admin/encargado.</li>
 *   <li><strong>leerArchivo:</strong> recupera los bytes del almacen para servir la
 *       visualizacion/descarga (aislado por tenant).</li>
 *   <li><strong>decidir (aprobar/rechazar):</strong> transiciona la evidencia y
 *       audita; el rechazo exige motivo. Gobernada por {@code evidencia_avance:aprobar}
 *       en el controlador.</li>
 * </ul>
 *
 * <p>El acceso siempre se valida verificando que el avance/Sitio pertenezcan a un
 * Proyecto accesible del tenant (RLS + filtro de Hibernate), traduciendo lo ajeno
 * a 404.</p>
 */
@Service
public class ServicioEvidenciasAvance implements EvidenciaAvanceConsultaPort {

    /** Recurso de auditoria de la evidencia de avance de sitio. */
    static final String RECURSO_EVIDENCIA = "evidencia_avance";

    private final EvidenciaAvanceSitioRepository evidenciaRepository;
    private final AvanceSitioRepository avanceSitioRepository;
    private final SitioRepository sitioRepository;
    private final ProyectoRepository proyectoRepository;
    private final EvidenciaStoragePort storage;
    private final EvidenciaStorageProperties propiedades;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioEvidenciasAvance(EvidenciaAvanceSitioRepository evidenciaRepository,
                                    AvanceSitioRepository avanceSitioRepository,
                                    SitioRepository sitioRepository,
                                    ProyectoRepository proyectoRepository,
                                    EvidenciaStoragePort storage,
                                    EvidenciaStorageProperties propiedades,
                                    AuditoriaPort auditoria,
                                    Clock clock) {
        this.evidenciaRepository = evidenciaRepository;
        this.avanceSitioRepository = avanceSitioRepository;
        this.sitioRepository = sitioRepository;
        this.proyectoRepository = proyectoRepository;
        this.storage = storage;
        this.propiedades = propiedades;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * Sube una evidencia (archivo real) que respalda la fase actual del Sitio y la
     * deja en estado PENDIENTE (Req 3.2). Valida el tipo MIME y el tamano contra la
     * configuracion; guarda el binario en el almacen de objetos; materializa el
     * avance del Sitio en PENDIENTE si aun no existe; persiste la evidencia y audita.
     *
     * @param proyectoId     Proyecto al que pertenece el Sitio (verifica acceso).
     * @param sitioId        Sitio cuya fase se documenta.
     * @param contenido      bytes del archivo; obligatorio y no vacio.
     * @param nombreOriginal nombre original del archivo (para mostrar/descargar).
     * @param tipoMime       tipo MIME del archivo.
     * @return el DTO de la evidencia creada (pendiente).
     * @throws RecursoNoEncontradoException si el Proyecto o el Sitio no son accesibles (404).
     * @throws ReglaNegocioException        si el tipo/tamano no son admitidos (422).
     */
    @Transactional
    public EvidenciaAvanceDto subir(UUID proyectoId, UUID sitioId, byte[] contenido,
                                    String nombreOriginal, String tipoMime) {
        String actor = actorActual();
        Sitio sitio = cargarSitioDelProyecto(proyectoId, sitioId);

        if (contenido == null || contenido.length == 0) {
            throw new ReglaNegocioException("El archivo de la evidencia esta vacio.");
        }
        if (!propiedades.permiteMime(tipoMime)) {
            throw new ReglaNegocioException(
                    "Tipo de archivo no permitido para la evidencia. Usa imagen (JPG, PNG, WebP) o PDF.");
        }
        if (contenido.length > propiedades.maxTamanoBytes()) {
            throw new ReglaNegocioException(
                    "El archivo supera el tamano maximo de " + propiedades.maxTamanoMb() + " MB.");
        }

        // Materializa el avance del Sitio si aun no existe (PENDIENTE): la evidencia
        // se ancla a ese avance y a la fase actual del Sitio.
        AvanceSitio avance = avanceSitioRepository.findBySitioId(sitio.getId())
                .orElseGet(() -> avanceSitioRepository.save(AvanceSitio.inicial(sitio.getId(), actor)));
        FaseSitioGenerica fase = avance.getFase();

        // Guarda el binario en el almacen de objetos (fuera de la BD).
        UUID tenantId = TenantContext.require();
        String clave = storage.guardar(tenantId, contenido, nombreOriginal, tipoMime);

        EvidenciaAvanceSitio evidencia = EvidenciaAvanceSitio.subir(
                avance.getId(), fase, clave, nombreOriginal, tipoMime, contenido.length, actor, clock);
        EvidenciaAvanceSitio guardada = evidenciaRepository.save(evidencia);

        auditar(actor, "subir_evidencia", guardada.getId(),
                "subida evidencia '" + guardada.getNombreOriginal() + "' (fase " + fase.valorBd()
                        + ") del Sitio [" + sitio.getId() + "] en el Proyecto [" + proyectoId + "]");
        return EvidenciaAvanceDto.de(guardada);
    }

    /**
     * Lista las evidencias del Sitio (mas reciente primero) para la galeria.
     *
     * @param proyectoId Proyecto al que pertenece el Sitio (verifica acceso).
     * @param sitioId    Sitio cuyas evidencias se listan.
     * @return las evidencias del avance del Sitio; lista vacia si no hay avance/evidencias.
     */
    @Transactional(readOnly = true)
    public List<EvidenciaAvanceDto> listar(UUID proyectoId, UUID sitioId) {
        Sitio sitio = cargarSitioDelProyecto(proyectoId, sitioId);
        return avanceSitioRepository.findBySitioId(sitio.getId())
                .map(avance -> evidenciaRepository
                        .findByAvanceSitioIdOrderBySubidaEnDesc(avance.getId())
                        .stream()
                        .map(EvidenciaAvanceDto::de)
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * Recupera el archivo de una evidencia para servir su visualizacion/descarga.
     *
     * @param evidenciaId identificador de la evidencia.
     * @return el contenido y metadatos del archivo.
     * @throws RecursoNoEncontradoException si la evidencia no es accesible (404).
     */
    @Transactional(readOnly = true)
    public ArchivoEvidencia leerArchivo(UUID evidenciaId) {
        EvidenciaAvanceSitio evidencia = cargarEvidencia(evidenciaId);
        byte[] contenido = storage.leer(TenantContext.require(), evidencia.getClaveAlmacen());
        return new ArchivoEvidencia(contenido, evidencia.getTipoMime(), evidencia.getNombreOriginal());
    }

    /**
     * Aprueba o rechaza una evidencia (Req 3.2). El rechazo exige motivo. Registra el
     * actor y el instante de la decision, y audita.
     *
     * @param evidenciaId identificador de la evidencia.
     * @param aprobar     {@code true} para aprobar, {@code false} para rechazar.
     * @param motivo      motivo del rechazo; obligatorio cuando {@code aprobar=false}.
     * @return el DTO de la evidencia tras la decision.
     * @throws RecursoNoEncontradoException si la evidencia no es accesible (404).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si no esta pendiente (409).
     * @throws ReglaNegocioException si se rechaza sin motivo (422).
     */
    @Transactional
    public EvidenciaAvanceDto decidir(UUID evidenciaId, boolean aprobar, String motivo) {
        String actor = actorActual();
        EvidenciaAvanceSitio evidencia = cargarEvidencia(evidenciaId);
        if (aprobar) {
            evidencia.aprobar(actor, clock);
        } else {
            evidencia.rechazar(motivo, actor, clock);
        }
        EvidenciaAvanceSitio guardada = evidenciaRepository.save(evidencia);
        auditar(actor, aprobar ? "aprobar_evidencia" : "rechazar_evidencia", guardada.getId(),
                (aprobar ? "aprobada" : "rechazada") + " evidencia '" + guardada.getNombreOriginal()
                        + "' (fase " + guardada.getFase().valorBd() + ")");
        return EvidenciaAvanceDto.de(guardada);
    }

    // ------------------------------------------------------------------
    // EvidenciaAvanceConsultaPort (guarda de negocio para ServicioProyectos)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public boolean tieneEvidenciaAprobada(UUID avanceSitioId, FaseSitioGenerica fase) {
        if (avanceSitioId == null || fase == null) {
            return false;
        }
        return evidenciaRepository.countByAvanceSitioIdAndFaseAndEstado(
                avanceSitioId, fase, EstadoEvidencia.APROBADA) > 0;
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /** Carga un Sitio verificando que pertenezca a un Proyecto accesible del tenant (404). */
    private Sitio cargarSitioDelProyecto(UUID proyectoId, UUID sitioId) {
        if (proyectoId == null || sitioId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Sitio solicitado en el Proyecto.");
        }
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el Proyecto solicitado."));
        return sitioRepository.findById(sitioId)
                .filter(s -> s.getProyectoId().equals(proyecto.getId()))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el Sitio solicitado en el Proyecto."));
    }

    /** Carga una evidencia accesible del tenant (404 si no existe/ajena). */
    private EvidenciaAvanceSitio cargarEvidencia(UUID evidenciaId) {
        if (evidenciaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la evidencia solicitada.");
        }
        return evidenciaRepository.findById(evidenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la evidencia solicitada."));
    }

    private void auditar(String actor, String accion, UUID recursoId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_EVIDENCIA,
                detalle + " [id=" + recursoId + "]", null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
