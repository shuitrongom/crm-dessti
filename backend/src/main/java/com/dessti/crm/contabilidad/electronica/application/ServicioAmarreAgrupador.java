package com.dessti.crm.contabilidad.electronica.application;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.CuentaContableRepository;
import com.dessti.crm.contabilidad.polizas.application.CuentaContableDto;
import com.dessti.crm.contabilidad.polizas.domain.CuentaContable;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el <strong>amarre</strong> de una
 * Cuenta_Contable a un codigo agrupador del SAT (Anexo 24) para la Contabilidad
 * Electronica (Req 1).
 *
 * <p>Vive en el submodulo {@code contabilidad.electronica} (no en
 * {@code contabilidad.polizas}) para evitar una dependencia circular entre
 * submodulos: {@code electronica} depende de {@code polizas} (usa
 * {@link CuentaContable} y su repositorio), nunca al reves. Al ser modulo de
 * Nucleo (no vertical), puede acceder al {@link CuentaContableRepository}.</p>
 *
 * <p>Multi-tenant (Req 23): el repositorio esta acotado al tenant vigente por el
 * filtro de Hibernate y la RLS de V33; el {@code tenant_id} proviene del
 * {@link TenantContext}, nunca de la peticion (Req 23.4). Audita el amarre
 * (Req 1.6).</p>
 */
@Service
public class ServicioAmarreAgrupador {

    private static final String RECURSO_CUENTA = "cuenta_contable";

    private final CuentaContableRepository cuentaContableRepository;
    private final CatalogoAgrupadorSatPort catalogoAgrupador;
    private final AuditoriaPort auditoria;

    public ServicioAmarreAgrupador(CuentaContableRepository cuentaContableRepository,
                                   CatalogoAgrupadorSatPort catalogoAgrupador,
                                   AuditoriaPort auditoria) {
        this.cuentaContableRepository = cuentaContableRepository;
        this.catalogoAgrupador = catalogoAgrupador;
        this.auditoria = auditoria;
    }

    /**
     * Amarra una Cuenta_Contable del tenant a un codigo agrupador del SAT (Req 1.3,
     * 1.4, 1.6). Valida que el codigo exista en el catalogo oficial (422 si no) y
     * que la cuenta sea accesible (404 si no). Audita el cambio.
     *
     * @param cuentaId identificador de la Cuenta_Contable a amarrar.
     * @param codigo   codigo agrupador del SAT a amarrar; obligatorio.
     * @return el DTO actualizado de la cuenta (incluye el codigo amarrado).
     * @throws ReglaNegocioException        si el codigo es vacio o no existe en el
     *                                      catalogo del SAT (422).
     * @throws RecursoNoEncontradoException si la cuenta no es accesible (404).
     */
    @Transactional
    public CuentaContableDto amarrarCodigoAgrupador(UUID cuentaId, String codigo) {
        String actor = actorActual();
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaNegocioException(
                    "Debe indicar el codigo agrupador del SAT a amarrar.");
        }
        String codigoNorm = codigo.strip();
        if (!catalogoAgrupador.existe(codigoNorm)) {
            throw new ReglaNegocioException(
                    "El codigo agrupador del SAT '" + codigoNorm
                            + "' no existe en el catalogo del Anexo 24.");
        }
        CuentaContable cuenta = cargarCuenta(cuentaId, actor);
        String anterior = cuenta.getCodigoAgrupadorSat();
        cuenta.amarrarCodigoAgrupador(codigoNorm, actor);
        CuentaContable guardada = cuentaContableRepository.save(cuenta);
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "amarrar_codigo_agrupador", RECURSO_CUENTA,
                "amarre de Cuenta_Contable [codigo=" + guardada.getCodigo()
                        + "] al agrupador SAT '" + codigoNorm + "' [id=" + guardada.getId() + "]",
                anterior, codigoNorm));
        return CuentaContableDto.de(guardada);
    }

    private CuentaContable cargarCuenta(UUID cuentaId, String actor) {
        if (cuentaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Cuenta_Contable solicitada.");
        }
        return cuentaContableRepository.findById(cuentaId)
                .orElseGet(() -> {
                    auditoria.registrar(EventoAuditoria.deTenant(
                            TenantContext.require(), actor, "acceso_denegado", RECURSO_CUENTA,
                            "intento de amarre a Cuenta_Contable no disponible en el tenant [id="
                                    + cuentaId + "]", null, null));
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Cuenta_Contable solicitada.");
                });
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
