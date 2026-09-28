package com.dessti.crm.contabilidad.polizas.domain;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Periodo contable mensual de la Empresa y su estado de cierre (candado contable).
 *
 * <p>Un periodo se identifica por {@code (tenant_id, anio, mes)} y puede estar
 * {@link EstadoPeriodo#ABIERTO} o {@link EstadoPeriodo#CERRADO}. La AUSENCIA de una
 * fila para un {@code (anio, mes)} se interpreta como periodo ABIERTO: solo se
 * materializa una fila al cerrar (o reabrir) un periodo, evitando sembrar registros
 * por adelantado.</p>
 *
 * <p>Cuando un periodo esta {@code CERRADO}, ninguna Poliza_Contable nueva ni reverso
 * puede afectarlo (la validacion la aplica el servicio de contabilidad en el unico
 * punto de entrada de polizas). La reapertura exige un motivo y queda auditada.</p>
 *
 * <p>Tenant-scoped (Req 23): extiende {@link TenantScopedEntity}, que asigna el
 * {@code tenant_id} desde el contexto autenticado y aporta {@code version} y marcas
 * de auditoria. La tabla tiene RLS {@code tenant_isolation} (V72).</p>
 */
@Entity
@Table(name = "periodo_contable")
public class PeriodoContable extends TenantScopedEntity {

    /** Anio minimo admitido (coincide con el CHECK de V72). */
    public static final int ANIO_MINIMO = 2000;

    /** Anio maximo admitido (coincide con el CHECK de V72). */
    public static final int ANIO_MAXIMO = 2100;

    /** Longitud maxima del motivo de reapertura (coincide con V72). */
    public static final int MOTIVO_MAX = 500;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Anio del periodo (2000..2100). Inmutable. */
    @Column(name = "anio", nullable = false, updatable = false)
    private short anio;

    /** Mes del periodo (1..12). Inmutable. */
    @Column(name = "mes", nullable = false, updatable = false)
    private short mes;

    /** Estado del periodo (abierto/cerrado). */
    @Convert(converter = EstadoPeriodoConverter.class)
    @Column(name = "estado", nullable = false, length = 8)
    private EstadoPeriodo estado;

    /** Momento del ultimo cierre; {@code null} si nunca se ha cerrado. */
    @Column(name = "fecha_cierre")
    private Instant fechaCierre;

    /** Actor que realizo el ultimo cierre; {@code null} si nunca se ha cerrado. */
    @Column(name = "cerrado_por", length = 255)
    private String cerradoPor;

    /** Momento de la ultima reapertura; {@code null} si nunca se ha reabierto. */
    @Column(name = "fecha_reapertura")
    private Instant fechaReapertura;

    /** Actor que realizo la ultima reapertura; {@code null} si nunca se ha reabierto. */
    @Column(name = "reabierto_por", length = 255)
    private String reabiertoPor;

    /** Motivo de la ultima reapertura; {@code null} si nunca se ha reabierto. */
    @Column(name = "motivo_reapertura", length = MOTIVO_MAX)
    private String motivoReapertura;

    protected PeriodoContable() {
        // Requerido por JPA.
    }

    /**
     * Crea un Periodo_Contable directamente en estado {@link EstadoPeriodo#CERRADO}.
     * Es el caso comun del primer cierre: un periodo abierto de forma implicita (sin
     * fila) se materializa ya cerrado.
     *
     * @param anio  anio del periodo (2000..2100).
     * @param mes   mes del periodo (1..12).
     * @param actor identificador de quien cierra (auditoria).
     * @return el periodo cerrado listo para persistir.
     * @throws ReglaNegocioException si el anio o el mes estan fuera de rango (422).
     */
    public static PeriodoContable crearCerrado(int anio, int mes, String actor) {
        validarAnioMes(anio, mes);
        PeriodoContable periodo = new PeriodoContable();
        periodo.id = UUID.randomUUID();
        periodo.anio = (short) anio;
        periodo.mes = (short) mes;
        periodo.estado = EstadoPeriodo.CERRADO;
        periodo.fechaCierre = Instant.now();
        periodo.cerradoPor = actor;
        periodo.setCreatedBy(actor);
        periodo.setUpdatedBy(actor);
        return periodo;
    }

    /**
     * Cierra este periodo (transicion {@link EstadoPeriodo#ABIERTO} -&gt;
     * {@link EstadoPeriodo#CERRADO}), validada por la maquina de estados pura.
     *
     * @param actor identificador de quien cierra (auditoria).
     * @throws TransicionInvalidaException si el periodo no esta abierto (409).
     */
    public void cerrar(String actor) {
        if (!estado.puedeTransicionarA(EstadoPeriodo.CERRADO)) {
            throw new TransicionInvalidaException(
                    "El periodo " + etiqueta() + " ya esta cerrado; no puede volver a cerrarse.");
        }
        this.estado = EstadoPeriodo.CERRADO;
        this.fechaCierre = Instant.now();
        this.cerradoPor = actor;
        setUpdatedBy(actor);
    }

    /**
     * Reabre este periodo (transicion {@link EstadoPeriodo#CERRADO} -&gt;
     * {@link EstadoPeriodo#ABIERTO}) indicando un motivo obligatorio. La reapertura
     * queda registrada para auditoria.
     *
     * @param motivo motivo de la reapertura; obligatorio y no vacio.
     * @param actor  identificador de quien reabre (auditoria).
     * @throws ReglaNegocioException      si el motivo es vacio (422).
     * @throws TransicionInvalidaException si el periodo no esta cerrado (409).
     */
    public void reabrir(String motivo, String actor) {
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException(
                    "La reapertura de un periodo requiere indicar un motivo.");
        }
        if (!estado.puedeTransicionarA(EstadoPeriodo.ABIERTO)) {
            throw new TransicionInvalidaException(
                    "El periodo " + etiqueta() + " no esta cerrado; no puede reabrirse.");
        }
        String motivoNormalizado = motivo.strip();
        if (motivoNormalizado.length() > MOTIVO_MAX) {
            motivoNormalizado = motivoNormalizado.substring(0, MOTIVO_MAX);
        }
        this.estado = EstadoPeriodo.ABIERTO;
        this.fechaReapertura = Instant.now();
        this.reabiertoPor = actor;
        this.motivoReapertura = motivoNormalizado;
        setUpdatedBy(actor);
    }

    /**
     * @return {@code true} si el periodo esta cerrado (candado activo).
     */
    public boolean estaCerrado() {
        return estado == EstadoPeriodo.CERRADO;
    }

    /**
     * Valida el rango de anio y mes segun los CHECK de V72.
     *
     * @param anio anio a validar.
     * @param mes  mes a validar.
     * @throws ReglaNegocioException si estan fuera de rango (422).
     */
    public static void validarAnioMes(int anio, int mes) {
        if (anio < ANIO_MINIMO || anio > ANIO_MAXIMO) {
            throw new ReglaNegocioException(
                    "El anio del periodo debe estar entre " + ANIO_MINIMO + " y " + ANIO_MAXIMO + ".");
        }
        if (mes < 1 || mes > 12) {
            throw new ReglaNegocioException("El mes del periodo debe estar entre 1 y 12.");
        }
    }

    private String etiqueta() {
        return String.format("%04d-%02d", (int) anio, (int) mes);
    }

    public UUID getId() {
        return id;
    }

    public int getAnio() {
        return anio;
    }

    public int getMes() {
        return mes;
    }

    public EstadoPeriodo getEstado() {
        return estado;
    }

    public Instant getFechaCierre() {
        return fechaCierre;
    }

    public String getCerradoPor() {
        return cerradoPor;
    }

    public Instant getFechaReapertura() {
        return fechaReapertura;
    }

    public String getReabiertoPor() {
        return reabiertoPor;
    }

    public String getMotivoReapertura() {
        return motivoReapertura;
    }
}
