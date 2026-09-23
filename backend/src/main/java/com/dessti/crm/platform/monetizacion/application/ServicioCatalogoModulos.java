package com.dessti.crm.platform.monetizacion.application;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.CatalogoModuloRepository;
import com.dessti.crm.platform.monetizacion.domain.CatalogoModulo;
import com.dessti.crm.platform.monetizacion.domain.MonetizacionValidaciones;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;

/**
 * Servicio de plataforma para el catalogo de modulos facturables (Req 24.3).
 * Administrado por el {@code super_admin}. Audita como evento de plataforma.
 */
@Service
public class ServicioCatalogoModulos {

    static final String RECURSO = "modulo_catalogo";

    private final CatalogoModuloRepository catalogoRepository;
    private final AuditoriaPort auditoria;

    public ServicioCatalogoModulos(CatalogoModuloRepository catalogoRepository, AuditoriaPort auditoria) {
        this.catalogoRepository = catalogoRepository;
        this.auditoria = auditoria;
    }

    /** Crea un modulo del catalogo. 409 si la clave ya existe. */
    @Transactional
    public CatalogoModuloDto crear(String clave, String nombre, String descripcion) {
        String actor = actorActual();
        String claveNorm = MonetizacionValidaciones.normalizarClaveModulo(clave);
        if (catalogoRepository.existsByClave(claveNorm)) {
            throw new ConflictoUnicidadException("Ya existe un modulo con la clave '" + claveNorm + "'.");
        }
        CatalogoModulo modulo = CatalogoModulo.crear(claveNorm, nombre, descripcion, actor);
        try {
            catalogoRepository.saveAndFlush(modulo);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoUnicidadException("Ya existe un modulo con la clave '" + claveNorm + "'.");
        }
        auditar(actor, "crear", "creado modulo de catalogo '" + claveNorm + "'");
        return CatalogoModuloDto.de(modulo);
    }

    /** Actualiza nombre/descripcion de un modulo (la clave es inmutable). 404 si no existe. */
    @Transactional
    public CatalogoModuloDto actualizar(UUID moduloId, String nombre, String descripcion) {
        String actor = actorActual();
        CatalogoModulo modulo = cargar(moduloId);
        modulo.actualizar(nombre, descripcion, actor);
        catalogoRepository.save(modulo);
        auditar(actor, "actualizar", "actualizado modulo de catalogo '" + modulo.getClave() + "'");
        return CatalogoModuloDto.de(modulo);
    }

    /** Baja logica de un modulo del catalogo. 404 si no existe. */
    @Transactional
    public CatalogoModuloDto desactivar(UUID moduloId) {
        String actor = actorActual();
        CatalogoModulo modulo = cargar(moduloId);
        modulo.desactivar(actor);
        catalogoRepository.save(modulo);
        auditar(actor, "actualizar", "desactivado modulo de catalogo '" + modulo.getClave() + "'");
        return CatalogoModuloDto.de(modulo);
    }

    /** Consulta puntual de un modulo. 404 si no existe. */
    @Transactional(readOnly = true)
    public CatalogoModuloDto consultar(UUID moduloId) {
        return CatalogoModuloDto.de(cargar(moduloId));
    }

    /** Listado paginado de modulos activos. */
    @Transactional(readOnly = true)
    public Page<CatalogoModuloDto> listar(Pageable pageable) {
        return catalogoRepository.findByActivoTrue(pageable).map(CatalogoModuloDto::de);
    }

    private CatalogoModulo cargar(UUID moduloId) {
        if (moduloId == null) {
            throw new RecursoNoEncontradoException("No se encontro el modulo solicitado.");
        }
        return catalogoRepository.findById(moduloId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el modulo solicitado."));
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