package com.dessti.crm.platform.empresas;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dessti.crm.platform.error.ReglaNegocioException;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA de {@code suscripcion} (Req 25), mapeada sobre la tabla
 * {@code suscripcion} definida en la migracion V1.
 *
 * <p>Una Suscripcion vincula una Empresa (tenant) con un {@link Plan} y registra
 * su estado ({@link EstadoSuscripcion}: activa, suspendida o cancelada) y su
 * periodo de vigencia (Req 25.2).</p>
 *
 * <h2>Alcance historico</h2>
 * <p>La tarea 14.1 solo necesitaba {@link #crearBasica(UUID, UUID, LocalDate,
 * String)} para dejar la Empresa utilizable con una Suscripcion {@code activa}
 * al Plan inicial (Req 24.2). Ese metodo se conserva intacto. La tarea 14.2
 * extiende el modelo <strong>sin romperlo</strong>: introduce el mapeo del
 * estado como {@link EstadoSuscripcion} (via {@link EstadoSuscripcionConverter},
 * coherente con {@link EstadoEmpresa}), las transiciones de estado
 * ({@link #activar(String)}, {@link #suspender(String)},
 * {@link #cancelar(String)}) y la actualizacion de la vigencia
 * ({@link #actualizarVigencia(LocalDate, LocalDate, String)}).</p>
 *
 * <p>El mapeo de columnas coincide con V1 (incluida {@code tenant_id}, que
 * referencia a {@code empresa.id}, y el CHECK de estado/vigencia). Como esta
 * entidad es de plataforma y su gestion la orquesta el {@code super_admin}, no
 * se apoya en el filtro de tenant de Hibernate; el {@code tenant_id} se fija
 * explicitamente.</p>
 *
 * <h2>El "Contrato": Plan XOR Paquete de suscripcion (V64)</h2>
 * <p>La migracion V64 convierte esta entidad en el <strong>Contrato</strong>
 * que referencia <em>o</em> un {@link Plan} <em>o</em> un paquete de suscripcion,
 * pero nunca ambos: una <strong>invariante XOR</strong> exige que exactamente
 * uno de {@code plan_id}/{@code paquete_suscripcion_id} sea no nulo, en funcion
 * del {@link TipoInstrumento} ({@code tipo_instrumento}). Por eso {@code plan_id}
 * pasa a ser <em>opcional</em> a nivel de columna (la nulabilidad efectiva la
 * garantiza el CHECK XOR {@code ck_suscripcion_instrumento} y el dominio via
 * {@link #validarInstrumentoXor(TipoInstrumento, UUID, UUID)}).</p>
 *
 * <p>El ciclo de vida se amplia con los estados {@link EstadoSuscripcion#EN_PRUEBA
 * EN_PRUEBA} (periodo de prueba de un contrato de suscripcion, que otorga acceso)
 * y {@link EstadoSuscripcion#VENCIDA VENCIDA} (contrato vencido; el vencimiento
 * tambien se calcula de forma derivada con {@link #estaVencida(LocalDate)} sin
 * persistir nada). La facturacion de la prueba se registra con
 * {@code facturacion_activada}/{@code inicio_facturacion} y se activa con la
 * transicion {@link #activarFacturacion(LocalDate, LocalDate, String)}
 * (EN_PRUEBA &rarr; ACTIVA). Las factorias {@link #crearDePlan}
 * ({@link TipoInstrumento#PLAN}), {@link #crearDeSuscripcion} y
 * {@link #crearEnPrueba} ({@link TipoInstrumento#SUSCRIPCION}) construyen cada
 * variante; {@link #crear}/{@link #crearBasica} se conservan y producen un
 * contrato de tipo {@link TipoInstrumento#PLAN} por compatibilidad.</p>
 */
@Entity
@Table(name = "suscripcion")
public class Suscripcion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Empresa (tenant) titular de la Suscripcion; referencia a {@code empresa.id}. */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /**
     * Tipo de instrumento que respalda el Contrato ({@code plan}/{@code suscripcion});
     * CHECK en V64. Se persiste como la etiqueta en minusculas via
     * {@link TipoInstrumentoConverter} y determina cual de
     * {@code plan_id}/{@code paquete_suscripcion_id} debe estar presente
     * (invariante XOR).
     */
    @Convert(converter = TipoInstrumentoConverter.class)
    @Column(name = "tipo_instrumento", nullable = false)
    private TipoInstrumento tipoInstrumento;

    /**
     * Plan contratado; referencia a {@code plan.id}. Opcional a nivel de columna
     * desde V64: solo esta presente cuando {@code tipo_instrumento = 'plan'}
     * (invariante XOR con {@code paquete_suscripcion_id}).
     */
    @Column(name = "plan_id")
    private UUID planId;

    /**
     * Paquete de suscripcion contratado; referencia a {@code paquete_suscripcion.id}
     * (V64). Opcional: solo esta presente cuando {@code tipo_instrumento =
     * 'suscripcion'} (invariante XOR con {@code plan_id}).
     */
    @Column(name = "paquete_suscripcion_id")
    private UUID paqueteSuscripcionId;

    /**
     * Estado de la Suscripcion (activa/en_prueba/suspendida/cancelada/vencida);
     * CHECK ampliado en V64. Se persiste como la etiqueta en minusculas via
     * {@link EstadoSuscripcionConverter}.
     */
    @Convert(converter = EstadoSuscripcionConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoSuscripcion estado;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fin")
    private LocalDate vigenciaFin;

    /**
     * Indica si la facturacion del Contrato ya fue activada (V64). Por defecto
     * {@code false}; un contrato de suscripcion en periodo de prueba comienza sin
     * facturar y se marca {@code true} al invocar
     * {@link #activarFacturacion(LocalDate, LocalDate, String)}.
     */
    @Column(name = "facturacion_activada", nullable = false)
    private boolean facturacionActivada;

    /**
     * Fecha en que arranca la facturacion del Contrato (V64). {@code null}
     * mientras la facturacion no se haya activado; se fija al invocar
     * {@link #activarFacturacion(LocalDate, LocalDate, String)}.
     */
    @Column(name = "inicio_facturacion")
    private LocalDate inicioFacturacion;

    /**
     * Override por Empresa del subconjunto de modulos habilitados (Req 25.4),
     * persistido como JSONB (array de cadenas) en la columna
     * {@code modulos_habilitados} (V21).
     *
     * <p><strong>Semantica null vs vacio (critica):</strong></p>
     * <ul>
     *   <li>{@code null} = <em>no hay override</em>: la Empresa HEREDA todos los
     *       modulos de su {@link Plan} (comportamiento historico). Es el valor
     *       por defecto: {@link #crearBasica} y {@link #crear} lo dejan
     *       {@code null}.</li>
     *   <li>una lista (incluida la <em>vacia</em>) = override presente: la
     *       Empresa recibe EXACTAMENTE esos modulos. La lista vacia significa
     *       CERO modulos habilitados (todos denegados -&gt; 403).</li>
     * </ul>
     *
     * <p>Los nombres se almacenan normalizados (recortados y en minusculas), sin
     * duplicados, igual que en {@link Plan}. El mapeo respeta exactamente el
     * nombre y la nulabilidad de la columna V21 para que un arranque con
     * {@code ddl-auto=validate} valide sin conflictos.</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modulos_habilitados")
    private List<String> modulosHabilitados;

    /**
     * Moneda (ISO 4217) en la que se factura la renta de modulos de esta Empresa
     * (monetizacion, V23). {@code null} = sin moneda de facturacion fijada; la
     * factura de renta no puede emitirse hasta definirla. Se normaliza a
     * mayusculas al asignarse.
     */
    @Column(name = "moneda_facturacion", length = 3)
    private String monedaFacturacion;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    protected Suscripcion() {
        // Requerido por JPA.
    }

    /**
     * Crea una Suscripcion basica en estado {@code activa} que vincula una
     * Empresa con su Plan inicial (Req 24.2), con vigencia desde el dia indicado
     * y sin fin de vigencia.
     *
     * @param tenantId       Empresa (tenant) titular; obligatorio.
     * @param planId         Plan contratado; obligatorio.
     * @param vigenciaInicio fecha de inicio de vigencia (normalmente hoy).
     * @param actor          identificador de quien crea la Suscripcion (super_admin).
     * @return la Suscripcion lista para persistir.
     */
    public static Suscripcion crearBasica(UUID tenantId, UUID planId,
                                          LocalDate vigenciaInicio, String actor) {
        return crear(tenantId, planId, vigenciaInicio, null, actor);
    }

    /**
     * Crea una Suscripcion en estado {@code activa} que vincula una Empresa con
     * un Plan, con un periodo de vigencia (Req 25.2). Es la factoria completa
     * usada por la gestion de Suscripciones (tarea 14.2).
     *
     * @param tenantId       Empresa (tenant) titular; obligatorio.
     * @param planId         Plan contratado; obligatorio.
     * @param vigenciaInicio fecha de inicio de vigencia; si es {@code null} se
     *                       toma la fecha actual.
     * @param vigenciaFin    fecha de fin de vigencia; opcional ({@code null} =
     *                       sin fin). Si se indica, no puede ser anterior al
     *                       inicio (CHECK {@code ck_suscripcion_vigencia}, V1).
     * @param actor          identificador de quien crea la Suscripcion (super_admin).
     * @return la Suscripcion lista para persistir.
     * @throws IllegalArgumentException si falta {@code tenantId} o {@code planId}.
     * @throws ReglaNegocioException    si {@code vigenciaFin} es anterior al inicio.
     */
    public static Suscripcion crear(UUID tenantId, UUID planId, LocalDate vigenciaInicio,
                                    LocalDate vigenciaFin, String actor) {
        if (tenantId == null) {
            throw new IllegalArgumentException("La Suscripcion requiere tenant_id");
        }
        if (planId == null) {
            throw new IllegalArgumentException("La Suscripcion requiere plan_id");
        }
        validarInstrumentoXor(TipoInstrumento.PLAN, planId, null);
        LocalDate inicio = (vigenciaInicio != null) ? vigenciaInicio : LocalDate.now();
        validarRangoVigencia(inicio, vigenciaFin);

        Suscripcion suscripcion = new Suscripcion();
        suscripcion.id = UUID.randomUUID();
        suscripcion.tenantId = tenantId;
        suscripcion.tipoInstrumento = TipoInstrumento.PLAN;
        suscripcion.planId = planId;
        suscripcion.paqueteSuscripcionId = null;
        suscripcion.estado = EstadoSuscripcion.ACTIVA;
        suscripcion.facturacionActivada = false;
        suscripcion.vigenciaInicio = inicio;
        suscripcion.vigenciaFin = vigenciaFin;
        suscripcion.createdBy = actor;
        suscripcion.updatedBy = actor;
        return suscripcion;
    }

    /**
     * Crea un Contrato de tipo {@link TipoInstrumento#PLAN} en estado
     * {@link EstadoSuscripcion#ACTIVA activa} (V64). Es un alias semantico de
     * {@link #crear(UUID, UUID, LocalDate, LocalDate, String)} que deja explicito
     * que el instrumento es un {@link Plan}.
     *
     * @param tenantId       Empresa (tenant) titular; obligatorio.
     * @param planId         Plan contratado; obligatorio.
     * @param vigenciaInicio fecha de inicio de vigencia; {@code null} = hoy.
     * @param vigenciaFin    fecha de fin de vigencia; opcional ({@code null} = sin fin).
     * @param actor          identificador de quien crea el Contrato (super_admin).
     * @return el Contrato de tipo PLAN listo para persistir.
     * @throws IllegalArgumentException si falta {@code tenantId} o {@code planId}.
     * @throws ReglaNegocioException    si se viola la invariante XOR o el rango de vigencia.
     */
    public static Suscripcion crearDePlan(UUID tenantId, UUID planId, LocalDate vigenciaInicio,
                                          LocalDate vigenciaFin, String actor) {
        return crear(tenantId, planId, vigenciaInicio, vigenciaFin, actor);
    }

    /**
     * Crea un Contrato de tipo {@link TipoInstrumento#SUSCRIPCION} en estado
     * {@link EstadoSuscripcion#ACTIVA activa} que referencia un paquete de
     * suscripcion (V64).
     *
     * @param tenantId             Empresa (tenant) titular; obligatorio.
     * @param paqueteSuscripcionId paquete de suscripcion contratado; obligatorio.
     * @param vigenciaInicio       fecha de inicio de vigencia; {@code null} = hoy.
     * @param vigenciaFin          fecha de fin de vigencia; opcional ({@code null} = sin fin).
     * @param actor                identificador de quien crea el Contrato (super_admin).
     * @return el Contrato de tipo SUSCRIPCION listo para persistir.
     * @throws IllegalArgumentException si falta {@code tenantId} o {@code paqueteSuscripcionId}.
     * @throws ReglaNegocioException    si se viola la invariante XOR o el rango de vigencia.
     */
    public static Suscripcion crearDeSuscripcion(UUID tenantId, UUID paqueteSuscripcionId,
                                                 LocalDate vigenciaInicio, LocalDate vigenciaFin,
                                                 String actor) {
        if (tenantId == null) {
            throw new IllegalArgumentException("La Suscripcion requiere tenant_id");
        }
        if (paqueteSuscripcionId == null) {
            throw new IllegalArgumentException("La Suscripcion requiere paquete_suscripcion_id");
        }
        validarInstrumentoXor(TipoInstrumento.SUSCRIPCION, null, paqueteSuscripcionId);
        LocalDate inicio = (vigenciaInicio != null) ? vigenciaInicio : LocalDate.now();
        validarRangoVigencia(inicio, vigenciaFin);

        Suscripcion suscripcion = new Suscripcion();
        suscripcion.id = UUID.randomUUID();
        suscripcion.tenantId = tenantId;
        suscripcion.tipoInstrumento = TipoInstrumento.SUSCRIPCION;
        suscripcion.planId = null;
        suscripcion.paqueteSuscripcionId = paqueteSuscripcionId;
        suscripcion.estado = EstadoSuscripcion.ACTIVA;
        suscripcion.facturacionActivada = false;
        suscripcion.vigenciaInicio = inicio;
        suscripcion.vigenciaFin = vigenciaFin;
        suscripcion.createdBy = actor;
        suscripcion.updatedBy = actor;
        return suscripcion;
    }

    /**
     * Crea un Contrato de tipo {@link TipoInstrumento#SUSCRIPCION} en estado
     * {@link EstadoSuscripcion#EN_PRUEBA en prueba} (V64), otorgando un periodo de
     * prueba de {@code duracionPruebaMeses} meses a partir de {@code vigenciaInicio}.
     * El fin de vigencia se calcula como {@code vigenciaInicio.plusMonths(duracionPruebaMeses)}
     * y la facturacion arranca desactivada.
     *
     * @param tenantId             Empresa (tenant) titular; obligatorio.
     * @param paqueteSuscripcionId paquete de suscripcion contratado; obligatorio.
     * @param vigenciaInicio       fecha de inicio de la prueba; {@code null} = hoy.
     * @param duracionPruebaMeses  duracion de la prueba en meses; debe ser mayor que cero.
     * @param actor                identificador de quien crea el Contrato (super_admin).
     * @return el Contrato de tipo SUSCRIPCION en periodo de prueba listo para persistir.
     * @throws IllegalArgumentException si falta {@code tenantId} o {@code paqueteSuscripcionId}.
     * @throws ReglaNegocioException    si {@code duracionPruebaMeses} no es positiva o si se
     *                                  viola la invariante XOR o el rango de vigencia.
     */
    public static Suscripcion crearEnPrueba(UUID tenantId, UUID paqueteSuscripcionId,
                                            LocalDate vigenciaInicio, int duracionPruebaMeses,
                                            String actor) {
        if (tenantId == null) {
            throw new IllegalArgumentException("La Suscripcion requiere tenant_id");
        }
        if (paqueteSuscripcionId == null) {
            throw new IllegalArgumentException("La Suscripcion requiere paquete_suscripcion_id");
        }
        if (duracionPruebaMeses <= 0) {
            throw new ReglaNegocioException(
                    "La duracion de la prueba debe ser mayor que cero meses.");
        }
        validarInstrumentoXor(TipoInstrumento.SUSCRIPCION, null, paqueteSuscripcionId);
        LocalDate inicio = (vigenciaInicio != null) ? vigenciaInicio : LocalDate.now();
        LocalDate fin = inicio.plusMonths(duracionPruebaMeses);
        validarRangoVigencia(inicio, fin);

        Suscripcion suscripcion = new Suscripcion();
        suscripcion.id = UUID.randomUUID();
        suscripcion.tenantId = tenantId;
        suscripcion.tipoInstrumento = TipoInstrumento.SUSCRIPCION;
        suscripcion.planId = null;
        suscripcion.paqueteSuscripcionId = paqueteSuscripcionId;
        suscripcion.estado = EstadoSuscripcion.EN_PRUEBA;
        suscripcion.facturacionActivada = false;
        suscripcion.vigenciaInicio = inicio;
        suscripcion.vigenciaFin = fin;
        suscripcion.createdBy = actor;
        suscripcion.updatedBy = actor;
        return suscripcion;
    }

    /**
     * Valida la invariante XOR del Contrato (V64): en funcion del
     * {@link TipoInstrumento}, exige que exactamente una de las referencias este
     * presente y la otra sea nula.
     *
     * <ul>
     *   <li>{@link TipoInstrumento#PLAN}: {@code planId} no nulo y
     *       {@code paqueteId} nulo.</li>
     *   <li>{@link TipoInstrumento#SUSCRIPCION}: {@code paqueteId} no nulo y
     *       {@code planId} nulo.</li>
     * </ul>
     *
     * @param tipo     tipo de instrumento del Contrato; obligatorio.
     * @param planId   referencia al Plan (puede ser {@code null}).
     * @param paqueteId referencia al paquete de suscripcion (puede ser {@code null}).
     * @throws ReglaNegocioException si {@code tipo} es nulo o si la combinacion de
     *                               referencias no respeta la invariante XOR.
     */
    private static void validarInstrumentoXor(TipoInstrumento tipo, UUID planId, UUID paqueteId) {
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo de instrumento del Contrato es obligatorio.");
        }
        switch (tipo) {
            case PLAN -> {
                if (planId == null || paqueteId != null) {
                    throw new ReglaNegocioException(
                            "Un Contrato de tipo plan requiere plan_id y no admite "
                                    + "paquete_suscripcion_id.");
                }
            }
            case SUSCRIPCION -> {
                if (paqueteId == null || planId != null) {
                    throw new ReglaNegocioException(
                            "Un Contrato de tipo suscripcion requiere paquete_suscripcion_id y no "
                                    + "admite plan_id.");
                }
            }
        }
    }

    /**
     * Activa la Suscripcion (Req 25.2). No se permite reactivar una Suscripcion
     * {@link EstadoSuscripcion#CANCELADA cancelada} (estado final).
     *
     * @param actor identificador de quien realiza el cambio.
     * @throws ReglaNegocioException si la Suscripcion esta cancelada.
     */
    public void activar(String actor) {
        if (estado == EstadoSuscripcion.CANCELADA) {
            throw new ReglaNegocioException(
                    "No se puede activar una Suscripcion cancelada.");
        }
        this.estado = EstadoSuscripcion.ACTIVA;
        this.updatedBy = actor;
    }

    /**
     * Suspende la Suscripcion (Req 25.2). No se permite suspender una
     * Suscripcion {@link EstadoSuscripcion#CANCELADA cancelada} (estado final).
     *
     * @param actor identificador de quien realiza el cambio.
     * @throws ReglaNegocioException si la Suscripcion esta cancelada.
     */
    public void suspender(String actor) {
        if (estado == EstadoSuscripcion.CANCELADA) {
            throw new ReglaNegocioException(
                    "No se puede suspender una Suscripcion cancelada.");
        }
        this.estado = EstadoSuscripcion.SUSPENDIDA;
        this.updatedBy = actor;
    }

    /**
     * Cancela la Suscripcion (Req 25.2). Es un estado final: una vez cancelada
     * no admite mas transiciones.
     *
     * @param actor identificador de quien realiza el cambio.
     */
    public void cancelar(String actor) {
        this.estado = EstadoSuscripcion.CANCELADA;
        this.updatedBy = actor;
    }

    /**
     * Actualiza el periodo de vigencia de la Suscripcion (Req 25.2).
     *
     * @param vigenciaInicio nuevo inicio; obligatorio.
     * @param vigenciaFin    nuevo fin; opcional ({@code null} = sin fin). Si se
     *                       indica, no puede ser anterior al inicio.
     * @param actor          identificador de quien realiza el cambio.
     * @throws ReglaNegocioException si {@code vigenciaInicio} es {@code null} o
     *                               si {@code vigenciaFin} es anterior al inicio.
     */
    public void actualizarVigencia(LocalDate vigenciaInicio, LocalDate vigenciaFin, String actor) {
        if (vigenciaInicio == null) {
            throw new ReglaNegocioException("La vigencia de inicio es obligatoria.");
        }
        validarRangoVigencia(vigenciaInicio, vigenciaFin);
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFin = vigenciaFin;
        this.updatedBy = actor;
    }

    /**
     * Activa la facturacion de un Contrato en periodo de prueba (V64, Req 8.1/8.4):
     * transiciona de {@link EstadoSuscripcion#EN_PRUEBA EN_PRUEBA} a
     * {@link EstadoSuscripcion#ACTIVA ACTIVA}, marca la facturacion como activada,
     * registra su fecha de inicio y actualiza el fin de vigencia al del contrato ya
     * facturado.
     *
     * <p>Solo se permite cuando el estado actual es {@link EstadoSuscripcion#EN_PRUEBA};
     * desde cualquier otro estado se rechaza con {@link ReglaNegocioException}
     * (traducida a HTTP 422).</p>
     *
     * @param inicioFacturacion fecha en que arranca la facturacion; obligatoria.
     * @param nuevaVigenciaFin  nuevo fin de vigencia del contrato facturado;
     *                          opcional ({@code null} = sin fin). Si se indica, no
     *                          puede ser anterior al inicio de vigencia.
     * @param actor             identificador de quien realiza el cambio.
     * @throws ReglaNegocioException si el estado no es EN_PRUEBA, si falta
     *                               {@code inicioFacturacion} o si el rango de
     *                               vigencia resultante es invalido.
     */
    public void activarFacturacion(LocalDate inicioFacturacion, LocalDate nuevaVigenciaFin,
                                   String actor) {
        if (estado != EstadoSuscripcion.EN_PRUEBA) {
            throw new ReglaNegocioException(
                    "Solo se puede activar la facturacion de un contrato en periodo de prueba.");
        }
        if (inicioFacturacion == null) {
            throw new ReglaNegocioException("La fecha de inicio de facturacion es obligatoria.");
        }
        validarRangoVigencia(this.vigenciaInicio, nuevaVigenciaFin);
        this.estado = EstadoSuscripcion.ACTIVA;
        this.facturacionActivada = true;
        this.inicioFacturacion = inicioFacturacion;
        this.vigenciaFin = nuevaVigenciaFin;
        this.updatedBy = actor;
    }

    /**
     * Indica, de forma <strong>derivada</strong> (sin persistir nada), si el
     * Contrato esta vencido a la fecha indicada (V64). Se considera vencido cuando
     * su estado otorga acceso ({@link EstadoSuscripcion#ACTIVA ACTIVA} o
     * {@link EstadoSuscripcion#EN_PRUEBA EN_PRUEBA}), tiene un fin de vigencia
     * definido y este es anterior a {@code hoy}.
     *
     * <p>Pensado para el enriquecimiento del DTO/UI del super_admin; no modifica el
     * estado persistido de la entidad.</p>
     *
     * @param hoy fecha de referencia (normalmente el dia actual).
     * @return {@code true} si el Contrato esta vencido a esa fecha; {@code false}
     *         en caso contrario.
     */
    public boolean estaVencida(LocalDate hoy) {
        return (estado == EstadoSuscripcion.ACTIVA || estado == EstadoSuscripcion.EN_PRUEBA)
                && vigenciaFin != null
                && hoy != null
                && vigenciaFin.isBefore(hoy);
    }

    /**
     * Asigna (o limpia) el override del subconjunto de modulos habilitados para
     * esta Empresa (Req 25.4).
     *
     * <p><strong>Semantica:</strong></p>
     * <ul>
     *   <li>{@code null} =&gt; limpia el override: la Empresa vuelve a HEREDAR
     *       todos los modulos del Plan.</li>
     *   <li>una coleccion (posiblemente vacia) =&gt; fija el override a ese
     *       subconjunto EXACTO. Se normaliza (recorte + minusculas) y deduplica,
     *       igual que en {@link Plan}. La coleccion vacia deja el override
     *       presente pero sin modulos (cero modulos habilitados).</li>
     * </ul>
     *
     * <p>La validacion de que los modulos sean un subconjunto de los del Plan es
     * responsabilidad del servicio de aplicacion (que conoce el Plan); esta
     * entidad solo almacena el override ya normalizado.</p>
     *
     * @param modulos subconjunto de modulos, o {@code null} para heredar del Plan.
     * @param actor   identificador de quien realiza el cambio.
     */
    public void asignarModulos(Collection<String> modulos, String actor) {
        this.modulosHabilitados = (modulos == null) ? null : normalizarModulos(modulos);
        this.updatedBy = actor;
    }

    /**
     * Indica si esta Suscripcion tiene un override de modulos definido (Req 25.4).
     *
     * @return {@code true} si hay un override (aunque sea vacio); {@code false}
     *         si la Empresa hereda todos los modulos del Plan ({@code null}).
     */
    /**
     * Fija la moneda de facturacion de la renta de modulos de esta Empresa
     * (monetizacion, V23). El codigo se normaliza a mayusculas (ISO 4217).
     *
     * @param monedaCodigo codigo ISO 4217 de la moneda de facturacion; no nulo.
     * @param actor        identificador de quien realiza el cambio.
     */
    public void fijarMonedaFacturacion(String monedaCodigo, String actor) {
        if (monedaCodigo == null || monedaCodigo.isBlank()) {
            throw new ReglaNegocioException("La moneda de facturacion es obligatoria.");
        }
        this.monedaFacturacion = monedaCodigo.strip().toUpperCase(java.util.Locale.ROOT);
        this.updatedBy = actor;
    }

    /**
     * @return el codigo ISO 4217 de la moneda de facturacion de la Empresa, o
     *         {@code null} si aun no se ha fijado (monetizacion, V23).
     */
    public String getMonedaFacturacion() {
        return monedaFacturacion;
    }

    public boolean tieneOverrideModulos() {
        return modulosHabilitados != null;
    }

    /**
     * Indica si un modulo esta habilitado <strong>por el override</strong> de
     * esta Suscripcion (Req 25.4). Solo tiene sentido cuando
     * {@link #tieneOverrideModulos()} es {@code true}; el adaptador de gating usa
     * este metodo unicamente en ese caso y, si no hay override, recurre al Plan.
     *
     * <p>La comparacion es insensible a mayusculas/minusculas y a espacios
     * sobrantes. Si el override es una lista vacia, siempre devuelve
     * {@code false} (cero modulos habilitados).</p>
     *
     * @param modulo nombre del modulo a comprobar.
     * @return {@code true} si el override incluye el modulo; {@code false} si no
     *         lo incluye, si no hay override, o si {@code modulo} es
     *         {@code null}/vacio.
     */
    public boolean tieneModulo(String modulo) {
        if (modulosHabilitados == null || modulo == null) {
            return false;
        }
        String normalizado = modulo.strip().toLowerCase(Locale.ROOT);
        if (normalizado.isEmpty()) {
            return false;
        }
        return modulosHabilitados.contains(normalizado);
    }

    /**
     * Normaliza el conjunto de modulos: descarta nulos/vacios, recorta y pasa a
     * minusculas, y deduplica preservando el orden de insercion. Devuelve una
     * lista mutable propia (nunca {@code null}).
     */
    private static List<String> normalizarModulos(Collection<String> modulos) {
        Set<String> normalizados = new LinkedHashSet<>();
        for (String modulo : modulos) {
            if (modulo == null) {
                continue;
            }
            String limpio = modulo.strip().toLowerCase(Locale.ROOT);
            if (!limpio.isEmpty()) {
                normalizados.add(limpio);
            }
        }
        return new ArrayList<>(normalizados);
    }

    private static void validarRangoVigencia(LocalDate inicio, LocalDate fin) {
        if (fin != null && inicio != null && fin.isBefore(inicio)) {
            throw new ReglaNegocioException(
                    "La vigencia de fin no puede ser anterior a la de inicio.");
        }
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPlanId() {
        return planId;
    }

    /**
     * @return el tipo de instrumento que respalda el Contrato
     *         ({@link TipoInstrumento#PLAN}/{@link TipoInstrumento#SUSCRIPCION}), V64.
     */
    public TipoInstrumento getTipoInstrumento() {
        return tipoInstrumento;
    }

    /**
     * @return el identificador del paquete de suscripcion contratado, o
     *         {@code null} si el Contrato es de tipo {@link TipoInstrumento#PLAN} (V64).
     */
    public UUID getPaqueteSuscripcionId() {
        return paqueteSuscripcionId;
    }

    /**
     * @return {@code true} si la facturacion del Contrato ya fue activada (V64).
     */
    public boolean isFacturacionActivada() {
        return facturacionActivada;
    }

    /**
     * @return la fecha en que arranca la facturacion del Contrato, o {@code null}
     *         si aun no se ha activado (V64).
     */
    public LocalDate getInicioFacturacion() {
        return inicioFacturacion;
    }

    public EstadoSuscripcion getEstado() {
        return estado;
    }

    public LocalDate getVigenciaInicio() {
        return vigenciaInicio;
    }

    public LocalDate getVigenciaFin() {
        return vigenciaFin;
    }

    /**
     * Devuelve el override de modulos habilitados de esta Suscripcion (Req 25.4).
     *
     * <p>Coherente con la semantica null-vs-vacio: devuelve {@code null} cuando
     * NO hay override (la Empresa hereda todos los modulos del Plan), o una
     * <em>copia inmutable</em> del subconjunto (posiblemente vacia) cuando el
     * override esta presente. Quien consuma este metodo (adaptador de gating,
     * DTOs) debe tratar {@code null} como "heredar del Plan".</p>
     *
     * @return copia inmutable del subconjunto de modulos, o {@code null} si se
     *         hereda del Plan.
     */
    public List<String> getModulosHabilitados() {
        return (modulosHabilitados == null) ? null : List.copyOf(modulosHabilitados);
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
