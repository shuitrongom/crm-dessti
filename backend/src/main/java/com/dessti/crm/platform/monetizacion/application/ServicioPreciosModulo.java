package com.dessti.crm.platform.monetizacion.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.empresas.EmpresaRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.CatalogoModuloRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.EmpresaModuloPrecioRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.MonedaRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.PrecioModuloRepository;
import com.dessti.crm.platform.monetizacion.domain.CatalogoModulo;
import com.dessti.crm.platform.monetizacion.domain.EmpresaModuloPrecio;
import com.dessti.crm.platform.monetizacion.domain.MonetizacionValidaciones;
import com.dessti.crm.platform.monetizacion.domain.PrecioModulo;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;

/**
 * Servicio de plataforma para los precios de los modulos (Req 24.3):
 * <ul>
 *   <li>precio de LISTA de un modulo en una moneda ({@code precio_modulo});</li>
 *   <li>precio ESPECIAL negociado de una Empresa para un modulo en una moneda
 *       ({@code empresa_modulo_precio});</li>
 *   <li>resolucion del <strong>precio aplicable</strong> (especial &gt; lista) a
 *       traves de {@link PrecioModuloAplicablePort}, que consume la factura de
 *       renta (PARTE 2).</li>
 * </ul>
 * Administrado por el {@code super_admin}. Audita como evento de plataforma.
 */
@Service
public class ServicioPreciosModulo implements PrecioModuloAplicablePort {

    static final String RECURSO = "precio_modulo";

    private final PrecioModuloRepository precioRepository;
    private final EmpresaModuloPrecioRepository empresaPrecioRepository;
    private final CatalogoModuloRepository catalogoRepository;
    private final MonedaRepository monedaRepository;
    private final EmpresaRepository empresaRepository;
    private final AuditoriaPort auditoria;

    public ServicioPreciosModulo(PrecioModuloRepository precioRepository,
                                 EmpresaModuloPrecioRepository empresaPrecioRepository,
                                 CatalogoModuloRepository catalogoRepository,
                                 MonedaRepository monedaRepository,
                                 EmpresaRepository empresaRepository,
                                 AuditoriaPort auditoria) {
        this.precioRepository = precioRepository;
        this.empresaPrecioRepository = empresaPrecioRepository;
        this.catalogoRepository = catalogoRepository;
        this.monedaRepository = monedaRepository;
        this.empresaRepository = empresaRepository;
        this.auditoria = auditoria;
    }

    /**
     * Define o actualiza el precio de LISTA de un modulo en una moneda (upsert por
     * (modulo, moneda)). Valida que el modulo y la moneda existan (404) y el rango
     * del precio (422).
     */
    @Transactional
    public PrecioModuloDto definirPrecioLista(UUID catalogoModuloId, String monedaCodigo, BigDecimal precio) {
        String actor = actorActual();
        exigirModuloExiste(catalogoModuloId);
        String monedaNorm = exigirMonedaExiste(monedaCodigo);

        PrecioModulo p = precioRepository
                .findByCatalogoModuloIdAndMonedaCodigo(catalogoModuloId, monedaNorm)
                .map(existente -> {
                    existente.actualizarPrecio(precio, actor);
                    return existente;
                })
                .orElseGet(() -> PrecioModulo.crear(catalogoModuloId, monedaNorm, precio, actor));
        PrecioModulo guardado = precioRepository.save(p);
        auditar(actor, "definir_precio_lista",
                "precio de lista modulo=" + catalogoModuloId + " moneda=" + monedaNorm
                        + " precio=" + guardado.getPrecio().toPlainString());
        return PrecioModuloDto.de(guardado);
    }

    /**
     * Define o actualiza el precio ESPECIAL negociado de una Empresa para un
     * modulo en una moneda (upsert por (empresa, modulo, moneda)). Valida que la
     * Empresa, el modulo y la moneda existan (404) y el rango del precio (422).
     */
    @Transactional
    public EmpresaModuloPrecioDto definirPrecioEspecial(UUID tenantId, UUID catalogoModuloId,
                                                        String monedaCodigo, BigDecimal precio) {
        String actor = actorActual();
        if (tenantId == null || !empresaRepository.existsById(tenantId)) {
            throw new RecursoNoEncontradoException("No se encontro la Empresa indicada.");
        }
        exigirModuloExiste(catalogoModuloId);
        String monedaNorm = exigirMonedaExiste(monedaCodigo);

        EmpresaModuloPrecio p = empresaPrecioRepository
                .findByTenantIdAndCatalogoModuloIdAndMonedaCodigo(tenantId, catalogoModuloId, monedaNorm)
                .map(existente -> {
                    existente.actualizarPrecio(precio, actor);
                    return existente;
                })
                .orElseGet(() -> EmpresaModuloPrecio.crear(tenantId, catalogoModuloId, monedaNorm, precio, actor));
        EmpresaModuloPrecio guardado = empresaPrecioRepository.save(p);
        auditar(actor, "definir_precio_especial",
                "precio especial empresa=" + tenantId + " modulo=" + catalogoModuloId
                        + " moneda=" + monedaNorm + " precio=" + guardado.getPrecio().toPlainString());
        return EmpresaModuloPrecioDto.de(guardado);
    }

    /** Lista los precios de lista definidos para un modulo (en sus monedas). */
    @Transactional(readOnly = true)
    public List<PrecioModuloDto> listarPreciosLista(UUID catalogoModuloId) {
        return precioRepository.findByCatalogoModuloId(catalogoModuloId).stream()
                .map(PrecioModuloDto::de).toList();
    }

    /** Lista los precios especiales negociados por una Empresa. */
    @Transactional(readOnly = true)
    public List<EmpresaModuloPrecioDto> listarPreciosEspeciales(UUID tenantId) {
        return empresaPrecioRepository.findByTenantId(tenantId).stream()
                .map(EmpresaModuloPrecioDto::de).toList();
    }

    // ------------------------------------------------------------------
    // PrecioModuloAplicablePort (regla: especial > lista > vacio)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> precioAplicable(UUID tenantId, String claveModulo, String monedaCodigo) {
        if (tenantId == null || claveModulo == null || claveModulo.isBlank()
                || monedaCodigo == null || monedaCodigo.isBlank()) {
            return Optional.empty();
        }
        String claveNorm = MonetizacionValidaciones.normalizarClaveModulo(claveModulo);
        String monedaNorm = MonetizacionValidaciones.normalizarCodigoMoneda(monedaCodigo);

        Optional<CatalogoModulo> modulo = catalogoRepository.findByClave(claveNorm);
        if (modulo.isEmpty()) {
            return Optional.empty();
        }
        UUID moduloId = modulo.get().getId();

        // 1) Precio especial negociado por la Empresa.
        Optional<BigDecimal> especial = empresaPrecioRepository
                .findByTenantIdAndCatalogoModuloIdAndMonedaCodigo(tenantId, moduloId, monedaNorm)
                .map(EmpresaModuloPrecio::getPrecio);
        if (especial.isPresent()) {
            return especial;
        }
        // 2) Precio de lista del modulo en esa moneda.
        return precioRepository.findByCatalogoModuloIdAndMonedaCodigo(moduloId, monedaNorm)
                .map(PrecioModulo::getPrecio);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private void exigirModuloExiste(UUID catalogoModuloId) {
        if (catalogoModuloId == null || !catalogoRepository.existsById(catalogoModuloId)) {
            throw new RecursoNoEncontradoException("No se encontro el modulo del catalogo indicado.");
        }
    }

    private String exigirMonedaExiste(String monedaCodigo) {
        String norm = MonetizacionValidaciones.normalizarCodigoMoneda(monedaCodigo);
        if (!monedaRepository.existsById(norm)) {
            throw new RecursoNoEncontradoException("No se encontro la moneda '" + norm + "'.");
        }
        return norm;
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