package com.dessti.crm.platform.empresas;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.modulos.CatalogoDependenciasModulos;
import com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort;
import com.dessti.crm.platform.security.rbac.PlanModulosPort;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Adaptador REAL de {@link PlanModulosPort} y {@link ModulosHabilitadosPort}
 * respaldado por la Suscripcion vigente de la Empresa y su Plan (Req 25.4).
 *
 * <p>Al existir este bean, el placeholder permisivo
 * {@code PlanModulosPermisivoPorDefecto} (anotado con
 * {@code @ConditionalOnMissingBean(PlanModulosPort.class)}) cede su lugar
 * automaticamente y este adaptador pasa a gobernar el gating de modulos.</p>
 *
 * <h2>Fuente unica de verdad (single-sourced)</h2>
 * <p>La resolucion del conjunto de modulos habilitados vive en UN solo metodo,
 * {@link #modulosHabilitadosDe(UUID)}. De ahi se derivan tanto:</p>
 * <ul>
 *   <li>la comprobacion binaria por-modulo de {@link PlanModulosPort}
 *       ({@link #moduloHabilitado(UUID, String)}), que consume el
 *       {@code Autorizador} en las expresiones {@code @PreAuthorize}; como</li>
 *   <li>la lista completa de {@link ModulosHabilitadosPort}, que se expone al
 *       cliente en el claim {@code modulos} del JWT (para que el frontend pinte
 *       el menu solo con los modulos contratados).</li>
 * </ul>
 * <p>Asi el gating y el claim nunca divergen: ambos leen la misma fuente.</p>
 *
 * <h2>Decision: denegacion por defecto (Req 6.4, 25.4)</h2>
 * <p>El acceso a un modulo se concede unicamente cuando la Empresa tiene un
 * Contrato <strong>vigente</strong> que habilita el modulo. Se consideran
 * vigentes los estados que <em>otorgan acceso</em>: {@link EstadoSuscripcion#ACTIVA
 * ACTIVA} y {@link EstadoSuscripcion#EN_PRUEBA EN_PRUEBA} (Req 5.6, 6.1). En
 * cualquier otro caso se deniega (lista vacia / {@code false}), lo que el
 * {@code Autorizador} traduce a 403:</p>
 * <ul>
 *   <li>sin Contrato en un estado que otorgue acceso (incluye SUSPENDIDA/CANCELADA,
 *       que no se seleccionan),</li>
 *   <li>Contrato vencido en tiempo real: {@code vigenciaFin} definida y anterior a
 *       hoy (Req 6.4; ver mas abajo),</li>
 *   <li>instrumento del Contrato (Plan o Paquete de Suscripcion) inexistente
 *       (situacion anomala, fail-safe),</li>
 *   <li>modulo no habilitado por la resolucion de modulos (ver abajo).</li>
 * </ul>
 *
 * <h2>Corte por vencimiento en tiempo real (Req 6.4, D2)</h2>
 * <p>El vencimiento NO se persiste por un job: se evalua en cada consulta. Tras
 * seleccionar el Contrato vigente, si {@link Suscripcion#estaVencida(LocalDate)}
 * (estado ACTIVA/EN_PRUEBA con {@code vigenciaFin} definida y anterior a la fecha
 * actual, {@code LocalDate.now(clock)}) se devuelve <strong>lista vacia</strong>,
 * el mismo resultado que la ausencia de Contrato. Un Contrato con
 * {@code vigenciaFin == null} concede siempre; con {@code vigenciaFin == hoy}
 * concede (no esta vencido).</p>
 *
 * <h2>Resolucion del catalogo de modulos: override, Plan o Paquete (Req 6.2, 6.3, 12.6)</h2>
 * <p>Cada Empresa puede tener un subconjunto propio de modulos guardado en su
 * Contrato ({@link Suscripcion#getModulosHabilitados()}). La resolucion es:</p>
 * <ul>
 *   <li><strong>override presente</strong>
 *       ({@link Suscripcion#tieneOverrideModulos()} = {@code true}): manda el
 *       override; la lista efectiva es exactamente la del override. Un override
 *       <em>vacio</em> deniega todos los modulos (lista vacia).</li>
 *   <li><strong>sin override</strong> ({@code null}): se hereda el catalogo
 *       completo del instrumento del Contrato segun su
 *       {@link TipoInstrumento}: si es {@link TipoInstrumento#PLAN PLAN} del
 *       {@link Plan} ({@link Plan#getModulosHabilitados()}); si es
 *       {@link TipoInstrumento#SUSCRIPCION SUSCRIPCION} del Paquete de
 *       Suscripcion ({@link PaqueteSuscripcion#getModulosHabilitados()}). El
 *       instrumento solo se carga en este caso. Un instrumento inexistente
 *       (anomalo) deniega todos los modulos (fail-safe).</li>
 * </ul>
 *
 * <p>La comparacion del nombre de modulo es insensible a mayusculas/minusculas y
 * a espacios sobrantes.</p>
 *
 * <h2>Aislamiento multi-tenant (RLS) al leer la Suscripcion</h2>
 * <p>La tabla {@code suscripcion} tiene RLS (politica {@code tenant_isolation},
 * V2/V53). Este adaptador se invoca tanto durante el LOGIN (contexto de
 * PLATAFORMA, sin {@code app.current_tenant} fijado) como en peticiones normales
 * (con el tenant ya resuelto por el {@code Autorizador} desde el contexto de
 * seguridad). Si no se fija el tenant, la RLS oculta la fila de la Suscripcion y
 * la resolucion devolveria cero modulos (falso vacio) &rarr; el claim
 * {@code modulos} del JWT saldria {@code []}. Por eso, antes de leer la
 * Suscripcion, se fija explicitamente el tenant destino en la transaccion en
 * curso ({@code SET LOCAL}, se revierte al terminar) mediante
 * {@link TenantSessionInitializer#applyTenant(UUID)}, mismo patron probado en
 * {@code ServicioFacturacionRenta}/{@code ServicioSuscripciones}. Solo se aplica
 * cuando hay tenant (nunca para el super_admin, cuyo {@code tenantId} es
 * {@code null} y provoca un retorno temprano). Fijar el tenant siempre coincide
 * con el propio del llamante, por lo que es idempotente y seguro.</p>
 */
@Component
public class PlanModulosPlanAdapter implements PlanModulosPort, ModulosHabilitadosPort {

    private final SuscripcionRepository suscripcionRepository;
    private final PlanRepository planRepository;
    private final PaqueteSuscripcionRepository paqueteSuscripcionRepository;
    private final TenantSessionInitializer tenantSession;
    private final Clock clock;

    public PlanModulosPlanAdapter(SuscripcionRepository suscripcionRepository,
                                  PlanRepository planRepository,
                                  PaqueteSuscripcionRepository paqueteSuscripcionRepository,
                                  TenantSessionInitializer tenantSession,
                                  Clock clock) {
        this.suscripcionRepository = suscripcionRepository;
        this.planRepository = planRepository;
        this.paqueteSuscripcionRepository = paqueteSuscripcionRepository;
        this.tenantSession = tenantSession;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean moduloHabilitado(UUID tenantId, String modulo) {
        if (tenantId == null || modulo == null || modulo.isBlank()) {
            return false;
        }
        // Fuente unica de verdad: la comprobacion por-modulo se deriva de la
        // misma lista efectiva que consume el claim del JWT, evitando cualquier
        // divergencia entre el gating y lo que ve el cliente.
        String normalizado = modulo.strip().toLowerCase(java.util.Locale.ROOT);
        return modulosHabilitadosDe(tenantId).contains(normalizado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> modulosHabilitadosDe(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        // Fija app.current_tenant en ESTA transaccion ANTES de leer la
        // Suscripcion (tabla con RLS tenant_isolation, V2/V53). Sin esto, en el
        // contexto de plataforma del login (sin tenant fijado) la RLS ocultaria
        // la fila y la lista efectiva saldria vacia (falso []). SET LOCAL se
        // revierte al terminar la transaccion; el tenant coincide siempre con el
        // propio del llamante (idempotente). Para el super_admin (tenantId null)
        // ya se retorno arriba, de modo que applyTenant NO se invoca.
        tenantSession.applyTenant(tenantId);

        // Seleccion del Contrato vigente (Req 5.6, 6.1): entre los estados que
        // otorgan acceso (ACTIVA y EN_PRUEBA), se toma el primero de forma
        // estable por id. SUSPENDIDA/CANCELADA no entran en el filtro, por lo
        // que producen cero modulos (deny-by-default). Sin Contrato vigente: cero.
        List<Suscripcion> vigentes = suscripcionRepository
                .findByTenantIdAndEstadoInOrderByIdAsc(
                        tenantId,
                        List.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.EN_PRUEBA));
        if (vigentes.isEmpty()) {
            return List.of();
        }
        Suscripcion contrato = vigentes.get(0);

        // Corte por vencimiento en tiempo real (Req 6.4, D2): un Contrato en
        // estado que otorga acceso pero con vigenciaFin anterior a hoy se trata
        // como sin acceso (cero modulos), sin depender de ningun scheduler. La
        // fecha se obtiene del Clock inyectado (mismo patron que ServicioEmpresas).
        if (contrato.estaVencida(LocalDate.now(clock))) {
            return List.of();
        }

        // Override por Empresa (Req 6.2, 25.4): si el Contrato define un
        // subconjunto propio de modulos, este manda sobre el instrumento. Un
        // override presente pero vacio deja cero modulos habilitados.
        if (contrato.tieneOverrideModulos()) {
            List<String> override = contrato.getModulosHabilitados();
            return (override == null) ? List.of() : normalizarDefensivo(override);
        }

        // Sin override: se hereda el catalogo completo del instrumento del
        // Contrato segun su tipo (Req 6.3, 12.6). El instrumento solo se carga
        // cuando hace falta. Un instrumento inexistente (anomalo) deja cero
        // modulos (fail-safe deny-by-default).
        return switch (contrato.getTipoInstrumento()) {
            case PLAN -> planRepository.findById(contrato.getPlanId())
                    .map(Plan::getModulosHabilitados)
                    .map(this::normalizarDefensivo)
                    .orElseGet(List::of);
            case SUSCRIPCION -> paqueteSuscripcionRepository.findById(contrato.getPaqueteSuscripcionId())
                    .map(PaqueteSuscripcion::getModulosHabilitados)
                    .map(this::normalizarDefensivo)
                    .orElseGet(List::of);
        };
    }

    /**
     * Red de seguridad <strong>defensiva</strong> (D7-b) aplicada a la lista
     * efectiva de modulos (override, herencia de Plan o herencia de Paquete)
     * <em>antes</em> de devolverla en el claim. Aplica el cierre transitivo de
     * {@link CatalogoDependenciasModulos#normalizar(java.util.Collection)} para
     * cubrir datos legados que no hayan pasado por el backfill (V65) ni por la
     * normalizacion al persistir (Plan/Paquete/Suscripcion). Asi, aunque la
     * fuente traiga {@code inventario-avanzado} sin {@code operacion}, el claim
     * efectivo incluira {@code operacion} (Req 8.2, 8.4, 11.1, 11.2).
     *
     * <p><strong>Idempotente:</strong> en el caso normal (tras 3.6/3.9 la fuente
     * ya esta normalizada) no altera nada; {@code normalizar} preserva el orden
     * de insercion y solo agrega los requeridos ausentes, sin duplicar. Sobre una
     * lista vacia devuelve vacio, de modo que los retornos de "cero modulos" (sin
     * contrato vigente, vencido, instrumento inexistente, tenant null) NO se ven
     * afectados: nunca se fuerza {@code operacion} si no habia
     * {@code inventario-avanzado}.</p>
     *
     * <p>{@code normalizar} devuelve un {@link java.util.LinkedHashSet} que
     * preserva el orden; se convierte a {@link List} con {@link List#copyOf}
     * conservando dicho orden para respetar la firma del puerto.</p>
     *
     * @param modulos lista efectiva de modulos de la fuente (no nula).
     * @return lista inmutable normalizada, con el orden preservado.
     */
    private List<String> normalizarDefensivo(List<String> modulos) {
        return List.copyOf(CatalogoDependenciasModulos.normalizar(modulos));
    }
}
