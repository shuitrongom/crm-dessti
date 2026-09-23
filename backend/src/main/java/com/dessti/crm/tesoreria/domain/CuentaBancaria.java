package com.dessti.crm.tesoreria.domain;

import java.util.Locale;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code cuenta_bancaria}: una cuenta bancaria de la
 * Empresa sobre la que se importan estados de cuenta y se ejecuta la conciliacion
 * bancaria (Req 43.1), mapeada sobre la tabla {@code cuenta_bancaria} de la
 * migracion V35.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignado desde el {@code TenantContext} al persistir,
 * nunca desde la peticion, Req 23.4), {@code version} (concurrencia optimista,
 * Req 49) y las marcas de auditoria. El mapeo de columnas coincide
 * <em>exactamente</em> con V35.</p>
 *
 * <h2>Reglas de dominio (Req 43.1)</h2>
 * <ul>
 *   <li>{@link #crear(String, String, String, String, String)} valida el nombre
 *       (no vacio), el banco (no vacio) y normaliza la moneda a 3 letras ASCII
 *       mayusculas (por defecto {@code MXN}); la CLABE es opcional (18 digitos si se
 *       provee) y crea la cuenta {@link #activa}.</li>
 *   <li>{@link #desactivar(String)} realiza el borrado logico (bandera
 *       {@link #activa} en {@code false}), preservando el historico.</li>
 * </ul>
 */
@Entity
@Table(name = "cuenta_bancaria")
public class CuentaBancaria extends TenantScopedEntity {

    /** Moneda por defecto cuando no se especifica (peso mexicano). */
    public static final String MONEDA_POR_DEFECTO = "MXN";

    /** Longitud exacta de una CLABE interbancaria mexicana. */
    public static final int LONGITUD_CLABE = 18;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Nombre descriptivo de la cuenta; no vacio (Req 43.1). */
    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    /** Banco de la cuenta; no vacio (Req 43.1). */
    @Column(name = "banco", nullable = false, length = 120)
    private String banco;

    /** CLABE interbancaria (18 digitos); opcional. */
    @Column(name = "clabe", length = 18)
    private String clabe;

    /** Moneda de la cuenta (ISO 4217, 3 letras); por defecto {@value #MONEDA_POR_DEFECTO}. */
    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda;

    /** Bandera de borrado logico: {@code true} si la cuenta esta activa. */
    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected CuentaBancaria() {
        // Requerido por JPA.
    }

    /**
     * Crea una Cuenta_Bancaria activa de la Empresa (Req 43.1).
     *
     * @param nombre nombre descriptivo; obligatorio y no vacio.
     * @param banco  banco de la cuenta; obligatorio y no vacio.
     * @param clabe  CLABE interbancaria (18 digitos); opcional.
     * @param moneda moneda ISO 4217 (3 letras); {@code null} o vacio usa
     *               {@value #MONEDA_POR_DEFECTO}.
     * @param actor  identificador de quien crea (auditoria).
     * @return la Cuenta_Bancaria lista para persistir, activa.
     * @throws ReglaNegocioException si falta o es invalido algun dato (422).
     */
    public static CuentaBancaria crear(String nombre, String banco, String clabe, String moneda,
                                       String actor) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("La Cuenta_Bancaria debe indicar el nombre.");
        }
        if (banco == null || banco.isBlank()) {
            throw new ReglaNegocioException("La Cuenta_Bancaria debe indicar el banco.");
        }
        String clabeNormalizada = normalizarClabe(clabe);
        String monedaNormalizada = normalizarMoneda(moneda);

        CuentaBancaria cuenta = new CuentaBancaria();
        cuenta.id = UUID.randomUUID();
        cuenta.nombre = nombre.strip();
        cuenta.banco = banco.strip();
        cuenta.clabe = clabeNormalizada;
        cuenta.moneda = monedaNormalizada;
        cuenta.activa = true;
        cuenta.setCreatedBy(actor);
        cuenta.setUpdatedBy(actor);
        return cuenta;
    }

    /**
     * Realiza el borrado logico de la cuenta (bandera {@link #activa} en
     * {@code false}), preservando el historico de estados de cuenta y conciliaciones.
     *
     * @param actor identificador de quien desactiva (auditoria).
     */
    public void desactivar(String actor) {
        this.activa = false;
        this.setUpdatedBy(actor);
    }

    private static String normalizarClabe(String clabe) {
        if (clabe == null || clabe.isBlank()) {
            return null;
        }
        String limpia = clabe.strip();
        if (limpia.length() != LONGITUD_CLABE || !limpia.chars().allMatch(Character::isDigit)) {
            throw new ReglaNegocioException(
                    "La CLABE de la Cuenta_Bancaria debe tener 18 digitos.");
        }
        return limpia;
    }

    private static String normalizarMoneda(String moneda) {
        if (moneda == null || moneda.isBlank()) {
            return MONEDA_POR_DEFECTO;
        }
        String limpia = moneda.strip().toUpperCase(Locale.ROOT);
        if (limpia.length() != 3) {
            throw new ReglaNegocioException(
                    "La moneda de la Cuenta_Bancaria debe ser un codigo ISO de 3 letras.");
        }
        return limpia;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getBanco() {
        return banco;
    }

    public String getClabe() {
        return clabe;
    }

    public String getMoneda() {
        return moneda;
    }

    public boolean isActiva() {
        return activa;
    }
}
