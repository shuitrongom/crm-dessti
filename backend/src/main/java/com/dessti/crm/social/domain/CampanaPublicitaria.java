package com.dessti.crm.social.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code campana_publicitaria}: una
 * Campaña_Publicitaria de marketing con presupuesto y periodo, opcionalmente ligada
 * a una {@link CuentaCanalSocial}, mapeada sobre la tabla {@code campana_publicitaria}
 * de la migracion V43 (Req 65.7-65.9, 23, 49).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (Req 23.4), {@code version} (Req 49) y las marcas de
 * auditoria. El mapeo de columnas coincide <em>exactamente</em> con V43.</p>
 *
 * <h2>Validacion de presupuesto y periodo (Req 65.7, 65.8; Property 39)</h2>
 * <p>La fabrica {@link #crear} delega en {@link ValidacionCampana} la comprobacion
 * <strong>pura</strong> del presupuesto (rango {@code [0.01, 999,999,999.99]}) y del
 * periodo ({@code fecha_fin >= fecha_inicio}); un dato invalido se rechaza con
 * {@link ReglaNegocioException} (422) y no se persiste.</p>
 *
 * <h2>Estado externo de SOLO LECTURA (Req 65.9)</h2>
 * <p>El estado operativo de la campaña (activa/pausada/finalizada) es autoridad de
 * la Marketing API de Meta y se consulta via el adaptador; NO se persiste como
 * autoritativo. La columna opcional {@link #estadoExterno} guarda, a lo sumo, la
 * ultima instantanea conocida para diagnostico, nunca como fuente de verdad.</p>
 */
@Entity
@Table(name = "campana_publicitaria")
public class CampanaPublicitaria extends TenantScopedEntity {

    /** Longitud maxima del nombre (coincide con VARCHAR(200) de V43). */
    static final int MAX_NOMBRE = 200;

    /** Longitud maxima del identificador externo (coincide con VARCHAR(120) de V43). */
    static final int MAX_EXTERNO_ID = 120;

    /** Longitud maxima de la instantanea de estado externo (coincide con VARCHAR(20) de V43). */
    static final int MAX_ESTADO_EXTERNO = 20;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cuenta_Canal_Social asociada; opcional (Req 65.10, filtro por Canal_Social). */
    @Column(name = "cuenta_canal_social_id")
    private UUID cuentaCanalSocialId;

    /** Canal_Social de la campaña; opcional; inmutable. Apoya el filtro del listado. */
    @Convert(converter = CanalSocialConverter.class)
    @Column(name = "canal", length = 12, updatable = false)
    private CanalSocial canal;

    /** Nombre descriptivo de la campaña; no vacio (Req 65.7). */
    @Column(name = "nombre", nullable = false, length = MAX_NOMBRE)
    private String nombre;

    /** Presupuesto de la campaña, escala 2, en [0.01, 999,999,999.99] (Req 65.7). */
    @Column(name = "presupuesto", nullable = false, precision = 18, scale = 2)
    private BigDecimal presupuesto;

    /** Fecha de inicio del periodo; inmutable (Req 65.7). */
    @Column(name = "fecha_inicio", nullable = false, updatable = false)
    private LocalDate fechaInicio;

    /** Fecha de fin del periodo ({@code >= fecha_inicio}); inmutable (Req 65.7). */
    @Column(name = "fecha_fin", nullable = false, updatable = false)
    private LocalDate fechaFin;

    /** Identificador externo en la Marketing API de Meta; {@code null} si no aplica. */
    @Column(name = "externo_id", length = MAX_EXTERNO_ID)
    private String externoId;

    /**
     * Ultima instantanea conocida del estado externo (Req 65.9); NO autoritativa. La
     * fuente de verdad es la Marketing API consultada via el adaptador.
     */
    @Column(name = "estado_externo", length = MAX_ESTADO_EXTERNO)
    private String estadoExterno;

    protected CampanaPublicitaria() {
        // Requerido por JPA.
    }

    /**
     * Crea una Campaña_Publicitaria validando su presupuesto y periodo (Req 65.7,
     * 65.8; Property 39) mediante {@link ValidacionCampana}. El {@code tenant_id} lo
     * fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param cuentaCanalSocialId Cuenta_Canal_Social asociada; opcional.
     * @param canal               Canal_Social; opcional (apoya el filtro del listado).
     * @param nombre              nombre descriptivo; obligatorio (1..200).
     * @param presupuesto         presupuesto en [0.01, 999,999,999.99]; obligatorio.
     * @param fechaInicio         fecha de inicio del periodo; obligatoria.
     * @param fechaFin            fecha de fin del periodo ({@code >= inicio}); obligatoria.
     * @param externoId           id externo en Meta; opcional.
     * @param actor               identificador de quien crea (auditoria).
     * @return la Campaña_Publicitaria lista para persistir.
     * @throws ReglaNegocioException si el nombre falta, o el presupuesto/periodo son
     *         invalidos (422, Req 65.8).
     */
    public static CampanaPublicitaria crear(UUID cuentaCanalSocialId, CanalSocial canal, String nombre,
                                            BigDecimal presupuesto, LocalDate fechaInicio,
                                            LocalDate fechaFin, String externoId, String actor) {
        String nombreNorm = normalizarNombre(nombre);
        // Property 39: validacion pura de presupuesto (rango) y periodo (fin >= inicio).
        BigDecimal presupuestoNorm = ValidacionCampana.validar(presupuesto, fechaInicio, fechaFin);

        CampanaPublicitaria campana = new CampanaPublicitaria();
        campana.id = UUID.randomUUID();
        campana.cuentaCanalSocialId = cuentaCanalSocialId;
        campana.canal = canal;
        campana.nombre = nombreNorm;
        campana.presupuesto = presupuestoNorm;
        campana.fechaInicio = fechaInicio;
        campana.fechaFin = fechaFin;
        campana.externoId = normalizarExternoId(externoId);
        campana.setCreatedBy(actor);
        campana.setUpdatedBy(actor);
        return campana;
    }

    /**
     * Actualiza la ultima instantanea conocida del estado externo de la campaña
     * (Req 65.9). No es autoritativa; solo refleja lo consultado en la Marketing API.
     *
     * @param estadoExterno etiqueta del estado externo; opcional.
     * @param actor         identificador de quien actualiza, para {@code updated_by}.
     */
    public void registrarInstantaneaEstadoExterno(String estadoExterno, String actor) {
        this.estadoExterno = normalizarEstadoExterno(estadoExterno);
        this.setUpdatedBy(actor);
    }

    private static String normalizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre de la Campaña_Publicitaria es obligatorio.");
        }
        String limpio = nombre.strip();
        if (limpio.length() > MAX_NOMBRE) {
            throw new ReglaNegocioException(
                    "El nombre de la Campaña_Publicitaria no puede exceder " + MAX_NOMBRE + " caracteres.");
        }
        return limpio;
    }

    private static String normalizarExternoId(String externoId) {
        if (externoId == null || externoId.isBlank()) {
            return null;
        }
        String limpio = externoId.strip();
        return (limpio.length() > MAX_EXTERNO_ID) ? limpio.substring(0, MAX_EXTERNO_ID) : limpio;
    }

    private static String normalizarEstadoExterno(String estadoExterno) {
        if (estadoExterno == null || estadoExterno.isBlank()) {
            return null;
        }
        String limpio = estadoExterno.strip();
        return (limpio.length() > MAX_ESTADO_EXTERNO) ? limpio.substring(0, MAX_ESTADO_EXTERNO) : limpio;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCuentaCanalSocialId() {
        return cuentaCanalSocialId;
    }

    public CanalSocial getCanal() {
        return canal;
    }

    public String getNombre() {
        return nombre;
    }

    public BigDecimal getPresupuesto() {
        return presupuesto;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public String getExternoId() {
        return externoId;
    }

    public String getEstadoExterno() {
        return estadoExterno;
    }
}
