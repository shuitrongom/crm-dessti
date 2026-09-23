package com.dessti.crm.social.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code cuenta_canal_social}: la conexion de la
 * Empresa a un {@link CanalSocial} (numero de WhatsApp Business, pagina de Facebook
 * o perfil de Instagram), mapeada sobre la tabla {@code cuenta_canal_social} de la
 * migracion V41 (Req 64.1, 64.2, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignado desde el {@code TenantContext} al persistir,
 * Req 23.4), {@code version} (concurrencia optimista, Req 49) y las marcas de
 * auditoria. El mapeo de columnas coincide <em>exactamente</em> con V41.</p>
 *
 * <h2>Credenciales por referencia (Req 11)</h2>
 * <p>El campo {@link #credencialesRef credenciales_ref} guarda <strong>solo una
 * referencia</strong> (clave logica) al almacen de secretos donde residen los
 * tokens de acceso de Meta; <strong>nunca</strong> el valor del secreto. El
 * adaptador de {@code MensajeriaSocialPort} resuelve el secreto desde el
 * entorno/vault a partir de esta referencia y jamas lo escribe en logs.</p>
 */
@Entity
@Table(name = "cuenta_canal_social")
public class CuentaCanalSocial extends TenantScopedEntity {

    /** Longitud maxima del identificador externo (coincide con VARCHAR(120) de V41). */
    static final int MAX_IDENTIFICADOR = 120;

    /** Longitud maxima del nombre (coincide con VARCHAR(200) de V41). */
    static final int MAX_NOMBRE = 200;

    /** Longitud maxima de la referencia de credenciales (coincide con VARCHAR(200) de V41). */
    static final int MAX_CRED_REF = 200;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Canal_Social de la cuenta; inmutable (Req 64.1). */
    @Convert(converter = CanalSocialConverter.class)
    @Column(name = "canal", nullable = false, length = 12, updatable = false)
    private CanalSocial canal;

    /** Identificador externo (numero WA / page id / ig id); inmutable (Req 64.1). */
    @Column(name = "identificador_externo", nullable = false, length = MAX_IDENTIFICADOR, updatable = false)
    private String identificadorExterno;

    /** Nombre descriptivo de la cuenta (Req 64.1). */
    @Column(name = "nombre", nullable = false, length = MAX_NOMBRE)
    private String nombre;

    /** Referencia al secreto (NUNCA el valor, Req 11). */
    @Column(name = "credenciales_ref", nullable = false, length = MAX_CRED_REF)
    private String credencialesRef;

    /** Bandera de actividad de la cuenta; {@code true} mientras esta operativa. */
    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected CuentaCanalSocial() {
        // Requerido por JPA.
    }

    /**
     * Registra una Cuenta_Canal_Social nueva y activa validando los datos
     * obligatorios (Req 64.1, 64.2). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param canal                Canal_Social; obligatorio.
     * @param identificadorExterno identificador en el canal; obligatorio (1..120).
     * @param nombre               nombre descriptivo; obligatorio (1..200).
     * @param credencialesRef      referencia al secreto (NUNCA el valor, Req 11);
     *                             obligatoria (1..200).
     * @param actor                identificador de quien registra (auditoria).
     * @return la Cuenta_Canal_Social lista para persistir, activa.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido
     *         (422, Req 64.2).
     */
    public static CuentaCanalSocial crear(CanalSocial canal, String identificadorExterno,
                                          String nombre, String credencialesRef, String actor) {
        if (canal == null) {
            throw new ReglaNegocioException("La Cuenta_Canal_Social debe indicar el Canal_Social.");
        }
        String idExterno = normalizar(identificadorExterno, MAX_IDENTIFICADOR,
                "El identificador externo de la Cuenta_Canal_Social");
        String nombreNorm = normalizar(nombre, MAX_NOMBRE,
                "El nombre de la Cuenta_Canal_Social");
        String credRef = normalizar(credencialesRef, MAX_CRED_REF,
                "La referencia de credenciales de la Cuenta_Canal_Social");

        CuentaCanalSocial cuenta = new CuentaCanalSocial();
        cuenta.id = UUID.randomUUID();
        cuenta.canal = canal;
        cuenta.identificadorExterno = idExterno;
        cuenta.nombre = nombreNorm;
        cuenta.credencialesRef = credRef;
        cuenta.activa = true;
        cuenta.setCreatedBy(actor);
        cuenta.setUpdatedBy(actor);
        return cuenta;
    }

    /**
     * Desactiva la cuenta (borrado logico) conservando su historico. Es idempotente.
     *
     * @param actor identificador de quien desactiva, para {@code updated_by}.
     */
    public void desactivar(String actor) {
        this.activa = false;
        this.setUpdatedBy(actor);
    }

    private static String normalizar(String valor, int max, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException(campo + " es obligatorio.");
        }
        String limpio = valor.strip();
        if (limpio.length() > max) {
            throw new ReglaNegocioException(campo + " no puede exceder " + max + " caracteres.");
        }
        return limpio;
    }

    public UUID getId() {
        return id;
    }

    public CanalSocial getCanal() {
        return canal;
    }

    public String getIdentificadorExterno() {
        return identificadorExterno;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCredencialesRef() {
        return credencialesRef;
    }

    public boolean isActiva() {
        return activa;
    }
}
