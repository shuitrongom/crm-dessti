package com.dessti.crm.contabilidad.polizas.domain;

import java.util.Locale;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code cuenta_contable}: una cuenta del catalogo
 * contable de la Empresa usada para clasificar los movimientos de las Polizas
 * (Req 38.1), mapeada sobre la tabla {@code cuenta_contable} de la migracion V33
 * (Req 38, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignado desde el {@code TenantContext} al persistir,
 * nunca desde la peticion, Req 23.4), {@code version} (concurrencia optimista,
 * Req 49) y las marcas de auditoria. El mapeo de columnas coincide
 * <em>exactamente</em> con V33.</p>
 *
 * <h2>Reglas de dominio (Req 38.1)</h2>
 * <ul>
 *   <li>{@link #crear(String, String, TipoCuentaContable, NaturalezaCuenta, String)}
 *       valida el codigo (no vacio), el nombre (no vacio), el tipo y la naturaleza,
 *       y crea la cuenta {@link #activa}. El {@code codigo} es UNICO por tenant
 *       (indice UNIQUE en V33); la aplicacion detecta el duplicado.</li>
 *   <li>{@link #desactivar(String)} realiza el borrado logico (bandera
 *       {@link #activa} en {@code false}), preservando el historico contable.</li>
 * </ul>
 */
@Entity
@Table(name = "cuenta_contable")
public class CuentaContable extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Codigo de la cuenta; unico por tenant (Req 38.1). Inmutable. */
    @Column(name = "codigo", nullable = false, updatable = false, length = 30)
    private String codigo;

    /** Nombre descriptivo de la cuenta; no vacio (Req 38.1). */
    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    /** Tipo contable (activo, pasivo, capital, ingreso, gasto). Inmutable. */
    @Column(name = "tipo", nullable = false, updatable = false, length = 12)
    private String tipo;

    /** Naturaleza del saldo (deudora o acreedora). Inmutable. */
    @Column(name = "naturaleza", nullable = false, updatable = false, length = 8)
    private String naturaleza;

    /** Bandera de borrado logico: {@code true} si la cuenta esta activa. */
    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected CuentaContable() {
        // Requerido por JPA.
    }

    /**
     * Crea una Cuenta_Contable activa del catalogo del tenant (Req 38.1).
     *
     * @param codigo     codigo de la cuenta; obligatorio y no vacio (unico por tenant).
     * @param nombre     nombre descriptivo; obligatorio y no vacio.
     * @param tipo       tipo contable; obligatorio.
     * @param naturaleza naturaleza del saldo; obligatoria.
     * @param actor      identificador de quien crea (auditoria).
     * @return la Cuenta_Contable lista para persistir, activa.
     * @throws ReglaNegocioException si falta o es invalido algun dato (422).
     */
    public static CuentaContable crear(String codigo, String nombre, TipoCuentaContable tipo,
                                       NaturalezaCuenta naturaleza, String actor) {
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaNegocioException("La Cuenta_Contable debe indicar el codigo.");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("La Cuenta_Contable debe indicar el nombre.");
        }
        if (tipo == null) {
            throw new ReglaNegocioException("La Cuenta_Contable debe indicar el tipo.");
        }
        if (naturaleza == null) {
            throw new ReglaNegocioException("La Cuenta_Contable debe indicar la naturaleza.");
        }
        CuentaContable cuenta = new CuentaContable();
        cuenta.id = UUID.randomUUID();
        cuenta.codigo = codigo.strip().toUpperCase(Locale.ROOT);
        cuenta.nombre = nombre.strip();
        cuenta.tipo = tipo.valorBd();
        cuenta.naturaleza = naturaleza.valorBd();
        cuenta.activa = true;
        cuenta.setCreatedBy(actor);
        cuenta.setUpdatedBy(actor);
        return cuenta;
    }

    /**
     * Realiza el borrado logico de la cuenta (bandera {@link #activa} en
     * {@code false}), preservando el historico contable.
     *
     * @param actor identificador de quien desactiva (auditoria).
     */
    public void desactivar(String actor) {
        this.activa = false;
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoCuentaContable getTipo() {
        return TipoCuentaContable.desdeValorBd(tipo);
    }

    public NaturalezaCuenta getNaturaleza() {
        return NaturalezaCuenta.desdeValorBd(naturaleza);
    }

    public boolean isActiva() {
        return activa;
    }
}
