package com.dessti.crm.rhnomina.nomina.domain;

import java.math.BigDecimal;
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
 * Entidad JPA del {@code recibo_nomina}: el Recibo de nomina de un {@link com.dessti.crm.rhnomina.empleado.domain.Empleado}
 * dentro de una {@link Nomina} (Req 41.1, 41.4), mapeada sobre la tabla
 * {@code recibo_nomina} de la migracion V34. Almacena los importes AGREGADOS ya
 * calculados (percepciones, deducciones, subsidio, neto) y el resultado del
 * Timbrado como CFDI de nomina (Folio_Fiscal, sello, fecha).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}; el
 * mapeo de columnas coincide <em>exactamente</em> con V34.</p>
 *
 * <h2>Reglas de dominio (Req 41)</h2>
 * <ul>
 *   <li>{@link #generar(UUID, UUID, ResultadoNomina, String)} crea el Recibo_Nomina
 *       en {@link EstadoReciboNomina#CALCULADO} con los importes del calculo
 *       (Req 41.1). Se verifica la identidad {@code neto = percepciones - deducciones
 *       + subsidio} y la no negatividad de los importes.</li>
 *   <li>{@link #timbrar(UUID, String, Instant, String)} registra el Folio_Fiscal y el
 *       sello del PAC y transita {@code calculado -> timbrado} (Req 41.4). A partir de
 *       ahi los importes y el Folio_Fiscal son <strong>inmutables</strong> (Req 41.7).</li>
 *   <li>{@link #cancelar(String)} transita {@code timbrado -> cancelado} conservando
 *       el Folio_Fiscal como historico (Req 41.7).</li>
 * </ul>
 */
@Entity
@Table(name = "recibo_nomina")
public class ReciboNomina extends TenantScopedEntity {

    /** Escala monetaria del sistema (coincide con NUMERIC(18,2) de V34). */
    public static final int ESCALA_MONETARIA = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Nomina a la que pertenece el Recibo_Nomina (Req 41.1). */
    @Column(name = "nomina_id", nullable = false, updatable = false)
    private UUID nominaId;

    /** Empleado al que corresponde el Recibo_Nomina (Req 41.1). */
    @Column(name = "empleado_id", nullable = false, updatable = false)
    private UUID empleadoId;

    /** Total de percepciones, escala 2 (Req 41.1). Inmutable una vez timbrado (Req 41.7). */
    @Column(name = "percepciones", nullable = false, precision = 18, scale = ESCALA_MONETARIA)
    private BigDecimal percepciones;

    /** Total de deducciones (ISR + IMSS + Infonavit), escala 2 (Req 41.1, 41.2). */
    @Column(name = "deducciones", nullable = false, precision = 18, scale = ESCALA_MONETARIA)
    private BigDecimal deducciones;

    /** Subsidio al empleo aplicable, escala 2 (Req 41.1). */
    @Column(name = "subsidio", nullable = false, precision = 18, scale = ESCALA_MONETARIA)
    private BigDecimal subsidio;

    /** Neto a pagar = percepciones - deducciones + subsidio, escala 2 (Req 41.1). */
    @Column(name = "neto", nullable = false, precision = 18, scale = ESCALA_MONETARIA)
    private BigDecimal neto;

    /** Estado; se persiste como etiqueta ASCII (Req 41.5). */
    @Convert(converter = EstadoReciboNominaConverter.class)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoReciboNomina estado;

    /** Folio_Fiscal (UUID del SAT) asignado al timbrar (Req 41.4); {@code null} si calculado. */
    @Column(name = "folio_fiscal")
    private UUID folioFiscal;

    /** Sello digital del SAT devuelto por el PAC al timbrar (Req 41.4). */
    @Column(name = "sello_sat")
    private String selloSat;

    /** Fecha/hora del Timbrado en UTC (Req 41.4); {@code null} si calculado. */
    @Column(name = "fecha_timbrado")
    private Instant fechaTimbrado;

    protected ReciboNomina() {
        // Requerido por JPA.
    }

    /**
     * Genera un Recibo_Nomina en {@link EstadoReciboNomina#CALCULADO} para un
     * Empleado dentro de una Nomina, con los importes del calculo (Req 41.1). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param nominaId    Nomina a la que pertenece; obligatorio.
     * @param empleadoId  Empleado al que corresponde; obligatorio.
     * @param resultado   resultado del calculo de nomina; obligatorio.
     * @param actor       identificador de quien calcula, para {@code created_by}/{@code updated_by}.
     * @return el Recibo_Nomina listo para persistir, en {@code calculado}.
     * @throws ReglaNegocioException si faltan datos, algun importe es negativo o la
     *         identidad {@code neto = percepciones - deducciones + subsidio} no se
     *         cumple (422).
     */
    public static ReciboNomina generar(UUID nominaId, UUID empleadoId,
                                       ResultadoNomina resultado, String actor) {
        if (nominaId == null) {
            throw new ReglaNegocioException("El Recibo_Nomina debe pertenecer a una Nomina.");
        }
        if (empleadoId == null) {
            throw new ReglaNegocioException("El Recibo_Nomina debe referirse a un Empleado.");
        }
        if (resultado == null) {
            throw new ReglaNegocioException("El resultado del calculo de nomina es obligatorio.");
        }
        BigDecimal percepciones = exigirNoNegativo(resultado.percepciones(), "Las percepciones");
        BigDecimal deducciones = exigirNoNegativo(resultado.deducciones(), "Las deducciones");
        BigDecimal subsidio = exigirNoNegativo(resultado.subsidio(), "El subsidio");
        BigDecimal neto = exigirNoNegativo(resultado.neto(), "El neto");

        // Verificacion de la identidad aritmetica (Req 41.1; Property 19).
        BigDecimal netoEsperado = percepciones.subtract(deducciones).add(subsidio)
                .setScale(ESCALA_MONETARIA, java.math.RoundingMode.HALF_UP);
        if (neto.compareTo(netoEsperado) != 0) {
            throw new ReglaNegocioException(
                    "El neto del Recibo_Nomina debe ser percepciones - deducciones + subsidio.");
        }

        ReciboNomina recibo = new ReciboNomina();
        recibo.id = UUID.randomUUID();
        recibo.nominaId = nominaId;
        recibo.empleadoId = empleadoId;
        recibo.percepciones = percepciones.setScale(ESCALA_MONETARIA);
        recibo.deducciones = deducciones.setScale(ESCALA_MONETARIA);
        recibo.subsidio = subsidio.setScale(ESCALA_MONETARIA);
        recibo.neto = neto.setScale(ESCALA_MONETARIA);
        recibo.estado = EstadoReciboNomina.CALCULADO;
        recibo.setCreatedBy(actor);
        recibo.setUpdatedBy(actor);
        return recibo;
    }

    /**
     * Registra el resultado exitoso del Timbrado del PAC (Folio_Fiscal, sello y fecha)
     * y transita {@code calculado -> timbrado} (Req 41.4). A partir de aqui los
     * importes y el Folio_Fiscal quedan inmutables (Req 41.7).
     *
     * @param folioFiscal   Folio_Fiscal (UUID del SAT); obligatorio.
     * @param selloSat      sello digital del SAT; obligatorio.
     * @param fechaTimbrado fecha/hora del Timbrado (UTC); obligatoria.
     * @param actor         identificador de quien timbra (auditoria).
     * @throws ReglaNegocioException       si faltan datos del Timbrado (422).
     * @throws TransicionInvalidaException si el Recibo_Nomina no esta {@code calculado} (409).
     */
    public void timbrar(UUID folioFiscal, String selloSat, Instant fechaTimbrado, String actor) {
        if (folioFiscal == null || selloSat == null || selloSat.isBlank() || fechaTimbrado == null) {
            throw new ReglaNegocioException(
                    "El Timbrado del Recibo_Nomina requiere Folio_Fiscal, sello y fecha del PAC.");
        }
        transitar(EstadoReciboNomina.TIMBRADO);
        this.folioFiscal = folioFiscal;
        this.selloSat = selloSat;
        this.fechaTimbrado = fechaTimbrado;
        this.setUpdatedBy(actor);
    }

    /**
     * Cancela el CFDI de nomina y transita {@code timbrado -> cancelado} (Req 41.5).
     * El Folio_Fiscal y el sello se conservan como historico inmutable (Req 41.7).
     *
     * @param actor identificador de quien cancela (auditoria).
     * @throws TransicionInvalidaException si el Recibo_Nomina no esta {@code timbrado} (409).
     */
    public void cancelar(String actor) {
        transitar(EstadoReciboNomina.CANCELADO);
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si los datos financieros del Recibo_Nomina son <strong>modificables</strong>:
     * solo mientras esta en {@code calculado} (Req 41.7). Una vez timbrado (o
     * cancelado) son inmutables (historico).
     *
     * @return {@code true} si el Recibo_Nomina esta en {@code calculado}.
     */
    public boolean datosModificables() {
        return this.estado == EstadoReciboNomina.CALCULADO;
    }

    /**
     * Actualiza los importes del Recibo_Nomina solo si sigue en {@code calculado}
     * (Req 41.7). Una vez timbrado, cualquier intento se rechaza con
     * {@link ReglaNegocioException} (422), preservando la inmutabilidad del CFDI de
     * nomina timbrado.
     *
     * @param resultado nuevos importes del calculo; obligatorio.
     * @param actor     identificador de quien modifica (auditoria).
     * @throws ReglaNegocioException si el Recibo_Nomina no esta {@code calculado} (422)
     *         o si los importes son invalidos.
     */
    public void recalcular(ResultadoNomina resultado, String actor) {
        if (!datosModificables()) {
            throw new ReglaNegocioException(
                    "No se pueden modificar los importes de un Recibo_Nomina en estado '"
                            + this.estado.valorBd() + "' (inmutabilidad del CFDI de nomina timbrado).");
        }
        if (resultado == null) {
            throw new ReglaNegocioException("El resultado del calculo de nomina es obligatorio.");
        }
        BigDecimal percepciones = exigirNoNegativo(resultado.percepciones(), "Las percepciones");
        BigDecimal deducciones = exigirNoNegativo(resultado.deducciones(), "Las deducciones");
        BigDecimal subsidio = exigirNoNegativo(resultado.subsidio(), "El subsidio");
        BigDecimal neto = exigirNoNegativo(resultado.neto(), "El neto");
        this.percepciones = percepciones.setScale(ESCALA_MONETARIA);
        this.deducciones = deducciones.setScale(ESCALA_MONETARIA);
        this.subsidio = subsidio.setScale(ESCALA_MONETARIA);
        this.neto = neto.setScale(ESCALA_MONETARIA);
        this.setUpdatedBy(actor);
    }

    private void transitar(EstadoReciboNomina destino) {
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

    private static BigDecimal exigirNoNegativo(BigDecimal valor, String etiqueta) {
        if (valor == null) {
            throw new ReglaNegocioException(etiqueta + " del Recibo_Nomina es obligatorio.");
        }
        if (valor.signum() < 0) {
            throw new ReglaNegocioException(etiqueta + " del Recibo_Nomina no puede ser negativo.");
        }
        return valor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getNominaId() {
        return nominaId;
    }

    public UUID getEmpleadoId() {
        return empleadoId;
    }

    public BigDecimal getPercepciones() {
        return percepciones;
    }

    public BigDecimal getDeducciones() {
        return deducciones;
    }

    public BigDecimal getSubsidio() {
        return subsidio;
    }

    public BigDecimal getNeto() {
        return neto;
    }

    public EstadoReciboNomina getEstado() {
        return estado;
    }

    public UUID getFolioFiscal() {
        return folioFiscal;
    }

    public String getSelloSat() {
        return selloSat;
    }

    public Instant getFechaTimbrado() {
        return fechaTimbrado;
    }
}
