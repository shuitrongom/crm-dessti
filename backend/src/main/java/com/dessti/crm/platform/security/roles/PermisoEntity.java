package com.dessti.crm.platform.security.roles;

import java.util.UUID;

import com.dessti.crm.platform.security.rbac.Permiso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del catalogo de {@code permiso} atomicos (Req 3.1, 28.1), mapeada
 * sobre la tabla {@code permiso} definida en la migracion V1 y sembrada por la
 * migracion V5.
 *
 * <p>Un permiso atomico es la tupla {@code (recurso, operacion)} y es un
 * catalogo <strong>de plataforma</strong> comun a todas las Empresas (no
 * tenant-scoped): los permisos son fijos del Sistema y los Roles los combinan.
 * Por eso esta entidad no hereda de {@code TenantScopedEntity}.</p>
 *
 * <p>Se nombra {@code PermisoEntity} para no colisionar con el value object de
 * dominio {@link Permiso} del paquete {@code rbac}, que representa el mismo
 * concepto en la capa de autorizacion. El metodo {@link #aValueObject()} tiende
 * el puente entre ambos.</p>
 */
@Entity
@Table(name = "permiso")
public class PermisoEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recurso", nullable = false, updatable = false)
    private String recurso;

    @Column(name = "operacion", nullable = false, updatable = false)
    private String operacion;

    protected PermisoEntity() {
        // Requerido por JPA.
    }

    public UUID getId() {
        return id;
    }

    public String getRecurso() {
        return recurso;
    }

    public String getOperacion() {
        return operacion;
    }

    /**
     * Convierte esta fila del catalogo al value object de dominio usado por el
     * {@code Autorizador} y por la representacion textual {@code recurso:operacion}.
     *
     * @return el {@link Permiso} equivalente.
     */
    public Permiso aValueObject() {
        return Permiso.de(recurso, operacion);
    }

    /**
     * @return la representacion textual {@code recurso:operacion} de este
     *         permiso (equivalente al {@code authority} de Spring Security).
     */
    public String authority() {
        return recurso + Permiso.SEPARADOR + operacion;
    }
}
