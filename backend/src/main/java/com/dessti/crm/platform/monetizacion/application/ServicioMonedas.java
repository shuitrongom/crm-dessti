package com.dessti.crm.platform.monetizacion.application;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.config.ConfiguracionCache;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.MonedaRepository;
import com.dessti.crm.platform.monetizacion.domain.Moneda;
import com.dessti.crm.platform.monetizacion.domain.MonetizacionValidaciones;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;

/**
 * Servicio de plataforma para el catalogo de monedas (Req 24.3). Administrado
 * por el {@code super_admin}. Audita como evento de plataforma.
 *
 * <p><strong>Cache de catalogo de cambio lento (Req 12.3):</strong> el catalogo
 * de monedas es de <em>plataforma</em> (global, sin {@code tenant_id}) y cambia
 * muy raramente, por lo que {@link #listarActivas()} se sirve desde la cache
 * {@link ConfiguracionCache#CACHE_MONEDAS}. Al ser un catalogo global, la clave
 * de cache es constante y no requiere el tenant, sin riesgo de fuga entre
 * empresas (Req 23). Toda escritura (alta, activacion o desactivacion) evicta la
 * cache para que la siguiente lectura refleje el cambio.</p>
 */
@Service
public class ServicioMonedas {

    static final String RECURSO = "moneda";

    private final MonedaRepository monedaRepository;
    private final AuditoriaPort auditoria;

    public ServicioMonedas(MonedaRepository monedaRepository, AuditoriaPort auditoria) {
        this.monedaRepository = monedaRepository;
        this.auditoria = auditoria;
    }

    /** Da de alta una moneda ISO 4217. 409 si el codigo ya existe. Evicta la cache (Req 12.3). */
    @Transactional
    @CacheEvict(cacheNames = ConfiguracionCache.CACHE_MONEDAS, allEntries = true)
    public MonedaDto crear(String codigo, String nombre) {
        String actor = actorActual();
        String codigoNorm = MonetizacionValidaciones.normalizarCodigoMoneda(codigo);
        if (monedaRepository.existsById(codigoNorm)) {
            throw new ConflictoUnicidadException("Ya existe la moneda '" + codigoNorm + "'.");
        }
        Moneda moneda = Moneda.crear(codigoNorm, nombre, actor);
        try {
            monedaRepository.saveAndFlush(moneda);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoUnicidadException("Ya existe la moneda '" + codigoNorm + "'.");
        }
        auditar(actor, "crear", "creada moneda " + codigoNorm);
        return MonedaDto.de(moneda);
    }

    /** Activa una moneda existente. 404 si no existe. Evicta la cache (Req 12.3). */
    @Transactional
    @CacheEvict(cacheNames = ConfiguracionCache.CACHE_MONEDAS, allEntries = true)
    public MonedaDto activar(String codigo) {
        return cambiarActivo(codigo, true, "activar");
    }

    /** Desactiva una moneda existente. 404 si no existe. Evicta la cache (Req 12.3). */
    @Transactional
    @CacheEvict(cacheNames = ConfiguracionCache.CACHE_MONEDAS, allEntries = true)
    public MonedaDto desactivar(String codigo) {
        return cambiarActivo(codigo, false, "desactivar");
    }

    /**
     * Lista las monedas activas, servida desde la cache de catalogo de cambio
     * lento (Req 12.3). Catalogo de plataforma (global, sin tenant): clave de
     * cache constante, sin riesgo de fuga entre empresas (Req 23).
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ConfiguracionCache.CACHE_MONEDAS, key = "'activas'")
    public List<MonedaDto> listarActivas() {
        return monedaRepository.findByActivoTrueOrderByCodigoAsc().stream().map(MonedaDto::de).toList();
    }

    private MonedaDto cambiarActivo(String codigo, boolean activo, String accion) {
        String actor = actorActual();
        String codigoNorm = MonetizacionValidaciones.normalizarCodigoMoneda(codigo);
        Moneda moneda = monedaRepository.findByCodigo(codigoNorm)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la moneda '" + codigoNorm + "'."));
        if (activo) {
            moneda.activar(actor);
        } else {
            moneda.desactivar(actor);
        }
        monedaRepository.save(moneda);
        auditar(actor, accion, accion + " moneda " + codigoNorm);
        return MonedaDto.de(moneda);
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