package com.dessti.crm.vertical.anuncios.permiso.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * Entidad JPA y raiz del agregado {@code permiso_instalacion}: la autorizacion
 * ({@code municipal} o {@code arrendador}) requerida para instalar un anuncio en un
 * Sitio, mapeada sobre la tabla {@code permiso_instalacion} de la migracion V20
 * (Req 17, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V20.</p>
 *
 * <h2>Reglas de dominio (Req 17)</h2>
 * <ul>
 *   <li>{@link #crear(TipoPermisoInstalacion, LocalDate, UUID, String)} valida los
 *       datos obligatorios (tipo y fecha de vencimiento) y fija el estado inicial
 *       {@link EstadoPermisoInstalacion#SOLICITADO} (Req 17.1). El vinculo al Sitio
 *       es obligatorio segun el Req 17.1; su existencia la verifica la capa de
 *       aplicacion cuando el bloque 22.2 exponga el repositorio de Sitio.</li>
 *   <li>{@link #aprobar(String, Clock)} / {@link #rechazar(String, Clock)} aplican
 *       la maquina de estados {@code solicitado -> {aprobado|rechazado}},
 *       registrando el actor y el instante UTC (Clock inyectado) de la decision
 *       (Req 17.2). Una transicion invalida —incluida cualquier salida desde un
 *       estado final— se rechaza con {@link TransicionInvalidaException} (409),
 *       conservando el estado (Req 17.3).</li>
 *   <li>{@link #venceEnProximosDias(int, Clock)} indica si el permiso vence dentro
 *       de la ventana indicada; da soporte a la notificacion de vencimiento proximo
 *       (Req 17.5).</li>
 * </ul>
 */
@Entity
@Table(name = "permiso_instalacion")
public class PermisoInstalacion extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Sitio al que se vincula el permiso (Req 17.1). Referencia debil (sin FK, ver V20). */
    @Column(name = "sitio_id")
    private UUID sitioId;

    /** Tipo del permiso; dato obligatorio (Req 17.1). Se persiste como etiqueta ASCII. */
    @Convert(converter = TipoPermisoInstalacionConverter.class)
    @Column(name = "tipo", nullable = false)
    private TipoPermisoInstalacion tipo;

    /** Fecha de vencimiento; dato obligatorio (Req 17.1). */
    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    /** Estado; se persiste como etiqueta ASCII (Req 17.1, 17.2). */
    @Convert(converter = EstadoPermisoInstalacionConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoPermisoInstalacion estado;

    /** Actor que decidio (aprobo/rechazo); {@code null} mientras esta solicitado (Req 17.2). */
    @Column(name = "decidido_por")
    private String decididoPor;

    /** Instante UTC de la decision; {@code null} mientras esta solicitado (Req 17.2). */
    @Column(name = "decidido_en")
    private Instant decididoEn;

    protected PermisoInstalacion() {
        // Requerido por JPA.
    }

    /**
     * Crea un Permiso_Instalacion nuevo validando los datos obligatorios (tipo y
     * fecha de vencimiento) y fijando el estado inicial
     * {@link EstadoPermisoInstalacion#SOLICITADO} (Req 17.1). El {@code tenant_id}
     * lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param tipo             tipo del permiso ({@code municipal}/{@code arrendador});
     *                         obligatorio (Req 17.1).
     * @param fechaVencimiento fecha de vencimiento; obligatoria (Req 17.1).
     * @param sitioId          Sitio vinculado (Req 17.1); su existencia la verifica
     *                         la capa de aplicacion.
     * @param actor            identificador de quien crea, para {@code created_by}/
     *                         {@code updated_by}.
     * @return el Permiso_Instalacion listo para persistir, en {@code solicitado}.
     * @throws ReglaNegocioException si algun dato obligatorio falta (422, Req 17.1).
     */
    public static PermisoInstalacion crear(TipoPermisoInstalacion tipo, LocalDate fechaVencimiento,
                                           UUID sitioId, String actor) {
        PermisoInstalacion permiso = new PermisoInstalacion();
        permiso.id = UUID.randomUUID();
        permiso.tipo = exigirTipo(tipo);
        permiso.fechaVencimiento = exigirFecha(fechaVencimiento);
        permiso.sitioId = sitioId;
        permiso.estado = EstadoPermisoInstalacion.SOLICITADO;
        permiso.setCreatedBy(actor);
        permiso.setUpdatedBy(actor);
        return permiso;
    }

    /**
     * Aprueba el Permiso_Instalacion, aplicando la transicion
     * {@code solicitado -> aprobado} y registrando el actor y el instante UTC de la
     * decision (Req 17.2). Aprobar un permiso que no esta {@code solicitado} se
     * rechaza con {@link TransicionInvalidaException} (409, Req 17.3), conservando
     * el estado actual sin modificarlo.
     *
     * @param actor identificador de quien aprueba; obligatorio.
     * @param clock reloj (UTC) para fijar {@code decidido_en}; obligatorio.
     * @throws ReglaNegocioException       si el reloj es nulo (422).
     * @throws TransicionInvalidaException si el permiso no esta {@code solicitado}
     *                                     (409, Req 17.3).
     */
    public void aprobar(String actor, Clock clock) {
        transicionarA(EstadoPermisoInstalacion.APROBADO, actor, clock);
    }

    /**
     * Rechaza el Permiso_Instalacion, aplicando la transicion
     * {@code solicitado -> rechazado} y registrando el actor y el instante UTC de la
     * decision (Req 17.2). Rechazar un permiso que no esta {@code solicitado} se
     * rechaza con {@link TransicionInvalidaException} (409, Req 17.3), conservando
     * el estado actual sin modificarlo.
     *
     * @param actor identificador de quien rechaza; obligatorio.
     * @param clock reloj (UTC) para fijar {@code decidido_en}; obligatorio.
     * @throws ReglaNegocioException       si el reloj es nulo (422).
     * @throws TransicionInvalidaException si el permiso no esta {@code solicitado}
     *                                     (409, Req 17.3).
     */
    public void rechazar(String actor, Clock clock) {
        transicionarA(EstadoPermisoInstalacion.RECHAZADO, actor, clock);
    }

    private void transicionarA(EstadoPermisoInstalacion destino, String actor, Clock clock) {
        if (clock == null) {
            throw new ReglaNegocioException("El reloj para fijar la decision es obligatorio.");
        }
        if (!this.estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + this.estado.valorBd()
                            + "' a '" + destino.valorBd() + "' para el Permiso_Instalacion.");
        }
        this.estado = destino;
        this.decididoPor = actor;
        this.decididoEn = clock.instant();
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si el Permiso_Instalacion esta {@code aprobado} (Req 17.4). Es la
     * condicion de la guarda de programacion de instalacion.
     *
     * @return {@code true} si el estado es {@link EstadoPermisoInstalacion#APROBADO}.
     */
    public boolean estaAprobado() {
        return this.estado == EstadoPermisoInstalacion.APROBADO;
    }

    /**
     * Indica si el permiso vence dentro de los proximos {@code dias} contados desde
     * la fecha actual (segun el {@link Clock} inyectado), es decir, si su
     * {@code fecha_vencimiento} esta en el rango {@code [hoy, hoy + dias]}. Da
     * soporte a la notificacion de vencimiento proximo del Req 17.5 (que aplica solo
     * a permisos {@code aprobado}). Un permiso ya vencido (fecha anterior a hoy) no
     * cuenta como "por vencer".
     *
     * @param dias  ventana de dias hacia adelante; debe ser &gt;= 0.
     * @param clock reloj (UTC) para determinar la fecha actual; obligatorio.
     * @return {@code true} si el permiso vence dentro de la ventana indicada.
     * @throws ReglaNegocioException si el reloj es nulo o {@code dias} es negativo (422).
     */
    public boolean venceEnProximosDias(int dias, Clock clock) {
        if (clock == null) {
            throw new ReglaNegocioException("El reloj para evaluar el vencimiento es obligatorio.");
        }
        if (dias < 0) {
            throw new ReglaNegocioException("La ventana de dias no puede ser negativa.");
        }
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate limite = hoy.plusDays(dias);
        return !this.fechaVencimiento.isBefore(hoy) && !this.fechaVencimiento.isAfter(limite);
    }

    private static TipoPermisoInstalacion exigirTipo(TipoPermisoInstalacion tipo) {
        if (tipo == null) {
            throw new ReglaNegocioException(
                    "El tipo del Permiso_Instalacion es obligatorio (municipal o arrendador).");
        }
        return tipo;
    }

    private static LocalDate exigirFecha(LocalDate fechaVencimiento) {
        if (fechaVencimiento == null) {
            throw new ReglaNegocioException(
                    "La fecha de vencimiento del Permiso_Instalacion es obligatoria.");
        }
        return fechaVencimiento;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSitioId() {
        return sitioId;
    }

    public TipoPermisoInstalacion getTipo() {
        return tipo;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public EstadoPermisoInstalacion getEstado() {
        return estado;
    }

    public String getDecididoPor() {
        return decididoPor;
    }

    public Instant getDecididoEn() {
        return decididoEn;
    }
}
