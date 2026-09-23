package com.dessti.crm.rhnomina.nomina.domain;

import java.math.BigDecimal;
import java.util.regex.Pattern;
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
 * Entidad JPA y raiz del agregado {@code nomina}: el proceso de nomina de un
 * Periodo_Nomina de la Empresa, mapeada sobre la tabla {@code nomina} de la
 * migracion V34 (Req 41, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignada desde el {@code TenantContext} al persistir,
 * nunca desde la peticion, Req 23.4), {@code version} (concurrencia optimista,
 * Req 49) y las marcas de auditoria. El mapeo de columnas coincide
 * <em>exactamente</em> con V34.</p>
 *
 * <h2>Reglas de dominio (Req 41)</h2>
 * <ul>
 *   <li>{@link #crear(String, String)} crea la Nomina en {@link EstadoNomina#BORRADOR}
 *       para un Periodo_Nomina en formato {@code AAAA-MM} (coherente con V32),
 *       con totales en cero.</li>
 *   <li>{@link #registrarCalculo(BigDecimal, BigDecimal, BigDecimal, String)} fija
 *       los totales agregados de los Recibo_Nomina y transita
 *       {@code borrador -> calculada} (Req 41.1, 41.5).</li>
 *   <li>{@link #autorizar(String)} transita {@code calculada -> autorizada}
 *       (Req 41.4, 41.5); {@link #marcarTimbrada(String)} transita
 *       {@code autorizada -> timbrada} (Req 41.4); {@link #marcarPagada(String)}
 *       transita {@code timbrada -> pagada} (Req 41.5).</li>
 *   <li>Toda transicion no permitida se rechaza con
 *       {@link TransicionInvalidaException} (409) conservando el estado (Req 41.6).</li>
 * </ul>
 */
@Entity
@Table(name = "nomina")
public class Nomina extends TenantScopedEntity {

    /** Formato del codigo de Periodo_Nomina: {@code AAAA-MM} (coherente con V32). */
    private static final Pattern PATRON_PERIODO = Pattern.compile("^[0-9]{4}-(0[1-9]|1[0-2])$");

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Codigo del Periodo_Nomina en formato {@code AAAA-MM} (Req 41.1). */
    @Column(name = "periodo_nomina", nullable = false, updatable = false, length = 7)
    private String periodoNomina;

    /** Estado; se persiste como etiqueta ASCII (Req 41.5). */
    @Convert(converter = EstadoNominaConverter.class)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoNomina estado;

    /** Total de percepciones agregado de los Recibo_Nomina, escala 2 (Req 41.1). */
    @Column(name = "total_percepciones", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalPercepciones;

    /** Total de deducciones agregado de los Recibo_Nomina, escala 2 (Req 41.1). */
    @Column(name = "total_deducciones", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalDeducciones;

    /** Total neto agregado de los Recibo_Nomina, escala 2 (Req 41.1). */
    @Column(name = "total_neto", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalNeto;

    protected Nomina() {
        // Requerido por JPA.
    }

    /**
     * Crea una Nomina nueva en {@link EstadoNomina#BORRADOR} para un Periodo_Nomina
     * (Req 41.1, 41.5), con totales en cero. El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param periodoNomina codigo del periodo {@code AAAA-MM}; obligatorio y valido.
     * @param actor         identificador de quien crea, para {@code created_by}/{@code updated_by}.
     * @return la Nomina lista para persistir, en {@code borrador}.
     * @throws ReglaNegocioException si el periodo es nulo o no cumple el formato (422).
     */
    public static Nomina crear(String periodoNomina, String actor) {
        Nomina nomina = new Nomina();
        nomina.id = UUID.randomUUID();
        nomina.periodoNomina = normalizarPeriodo(periodoNomina);
        nomina.estado = EstadoNomina.BORRADOR;
        nomina.totalPercepciones = BigDecimal.ZERO.setScale(2);
        nomina.totalDeducciones = BigDecimal.ZERO.setScale(2);
        nomina.totalNeto = BigDecimal.ZERO.setScale(2);
        nomina.setCreatedBy(actor);
        nomina.setUpdatedBy(actor);
        return nomina;
    }

    /**
     * Registra los totales agregados del calculo y transita
     * {@code borrador -> calculada} (Req 41.1, 41.5). Los totales deben ser no
     * negativos.
     *
     * @param totalPercepciones total de percepciones agregado; obligatorio y no negativo.
     * @param totalDeducciones  total de deducciones agregado; obligatorio y no negativo.
     * @param totalNeto         total neto agregado; obligatorio y no negativo.
     * @param actor             identificador de quien calcula (auditoria).
     * @throws ReglaNegocioException       si algun total es nulo o negativo (422).
     * @throws TransicionInvalidaException si la Nomina no esta en {@code borrador} (409).
     */
    public void registrarCalculo(BigDecimal totalPercepciones, BigDecimal totalDeducciones,
                                 BigDecimal totalNeto, String actor) {
        exigirNoNegativo(totalPercepciones, "El total de percepciones");
        exigirNoNegativo(totalDeducciones, "El total de deducciones");
        exigirNoNegativo(totalNeto, "El total neto");
        transitar(EstadoNomina.CALCULADA);
        this.totalPercepciones = totalPercepciones.setScale(2);
        this.totalDeducciones = totalDeducciones.setScale(2);
        this.totalNeto = totalNeto.setScale(2);
        this.setUpdatedBy(actor);
    }

    /**
     * Autoriza la Nomina y transita {@code calculada -> autorizada} (Req 41.4, 41.5).
     *
     * @param actor identificador de quien autoriza (auditoria).
     * @throws TransicionInvalidaException si la Nomina no esta {@code calculada} (409).
     */
    public void autorizar(String actor) {
        transitar(EstadoNomina.AUTORIZADA);
        this.setUpdatedBy(actor);
    }

    /**
     * Marca la Nomina como timbrada y transita {@code autorizada -> timbrada} tras
     * el Timbrado exitoso de sus Recibo_Nomina (Req 41.4, 41.5).
     *
     * @param actor identificador de quien timbra (auditoria).
     * @throws TransicionInvalidaException si la Nomina no esta {@code autorizada} (409).
     */
    public void marcarTimbrada(String actor) {
        transitar(EstadoNomina.TIMBRADA);
        this.setUpdatedBy(actor);
    }

    /**
     * Marca la Nomina como pagada y transita {@code timbrada -> pagada} (Req 41.5).
     *
     * @param actor identificador de quien registra el pago (auditoria).
     * @throws TransicionInvalidaException si la Nomina no esta {@code timbrada} (409).
     */
    public void marcarPagada(String actor) {
        transitar(EstadoNomina.PAGADA);
        this.setUpdatedBy(actor);
    }

    /**
     * Aplica una transicion de estado por la maquina de estados pura (Req 41.5).
     * Toda transicion no permitida —incluida cualquiera que parta de un estado
     * final— se rechaza con {@link TransicionInvalidaException} (409, Req 41.6)
     * conservando el estado actual.
     */
    private void transitar(EstadoNomina destino) {
        if (destino == null) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        if (!this.estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + this.estado.valorBd()
                            + "' a '" + destino.valorBd() + "'.");
        }
        this.estado = destino;
    }

    private static String normalizarPeriodo(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El Periodo_Nomina es obligatorio.");
        }
        String normalizado = valor.strip();
        if (!PATRON_PERIODO.matcher(normalizado).matches()) {
            throw new ReglaNegocioException(
                    "El Periodo_Nomina debe tener el formato AAAA-MM (por ejemplo 2026-01).");
        }
        return normalizado;
    }

    private static void exigirNoNegativo(BigDecimal valor, String etiqueta) {
        if (valor == null) {
            throw new ReglaNegocioException(etiqueta + " es obligatorio.");
        }
        if (valor.signum() < 0) {
            throw new ReglaNegocioException(etiqueta + " no puede ser negativo.");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getPeriodoNomina() {
        return periodoNomina;
    }

    public EstadoNomina getEstado() {
        return estado;
    }

    public BigDecimal getTotalPercepciones() {
        return totalPercepciones;
    }

    public BigDecimal getTotalDeducciones() {
        return totalDeducciones;
    }

    public BigDecimal getTotalNeto() {
        return totalNeto;
    }
}
