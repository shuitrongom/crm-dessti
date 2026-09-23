package com.dessti.crm.comercial.cliente.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code contacto} (persona asociada a un {@link Cliente} con
 * datos de contacto para gestion comercial), mapeada sobre la tabla
 * {@code contacto} de la migracion V11 (Req 5.5, 5.6, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignado automaticamente al persistir),
 * {@code version} y las marcas de auditoria; no se redeclaran aqui. El
 * {@code cliente_id} referencia al Cliente propietario dentro del mismo tenant.</p>
 *
 * <h2>Regla de asociacion (Req 5.5, 5.6)</h2>
 * <p>Un Contacto solo puede asociarse a un Cliente <strong>activo</strong>. La
 * fabrica {@link #paraCliente(Cliente, String, String, String, String)} exige
 * esa condicion y deriva el {@code cliente_id} del Cliente indicado. Un Cliente
 * inactivo se rechaza (regla de negocio, Req 5.6); la inexistencia del Cliente
 * la resuelve la capa de aplicacion con 404 (Req 5.6, 23.3).</p>
 */
@Entity
@Table(name = "contacto")
public class Contacto extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cliente propietario del Contacto (mismo tenant). No modificable tras crear. */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Correo electronico; {@code null} si no se proporciono. */
    @Column(name = "email")
    private String email;

    /** Telefono (10..15 digitos); {@code null} si no se proporciono. */
    @Column(name = "telefono")
    private String telefono;

    /** Bandera de borrado logico, por consistencia con el patron del modulo. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Contacto() {
        // Requerido por JPA.
    }

    /**
     * Crea un Contacto asociado a un Cliente <strong>activo</strong> (Req 5.5,
     * 5.6), validando el nombre y los datos de contacto opcionales. El
     * {@code cliente_id} se deriva del Cliente indicado y el {@code tenant_id}
     * lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * <p>A diferencia del Cliente, el Contacto <em>no</em> exige que exista al
     * menos un dato de contacto: puede registrarse por nombre. Si se proporcionan
     * email/telefono, se validan sus formatos (Req 5.1/5.2).</p>
     *
     * @param cliente  Cliente propietario; obligatorio y debe estar activo.
     * @param nombre   nombre del Contacto; obligatorio (1..200).
     * @param email    email; opcional (valido si se proporciona).
     * @param telefono telefono; opcional (10..15 digitos si se proporciona).
     * @param actor    identificador de quien crea el Contacto, para auditoria.
     * @return el Contacto listo para persistir.
     * @throws ReglaNegocioException       si el Cliente es {@code null} o esta
     *                                     inactivo (Req 5.6), o si algun dato es
     *                                     invalido (Req 5.2).
     */
    public static Contacto paraCliente(Cliente cliente, String nombre, String email,
                                       String telefono, String actor) {
        if (cliente == null) {
            // La inexistencia del Cliente se traduce a 404 en la capa de aplicacion.
            throw new RecursoNoEncontradoException("El Cliente indicado no esta disponible.");
        }
        if (!cliente.estaActivo()) {
            throw new ReglaNegocioException(
                    "No se puede asociar un Contacto a un Cliente inactivo.");
        }
        Contacto contacto = new Contacto();
        contacto.id = UUID.randomUUID();
        contacto.clienteId = cliente.getId();
        contacto.nombre = DatosContacto.normalizarNombre(nombre);
        contacto.email = DatosContacto.normalizarEmail(email);
        contacto.telefono = DatosContacto.normalizarTelefono(telefono);
        contacto.activo = true;
        contacto.setCreatedBy(actor);
        contacto.setUpdatedBy(actor);
        return contacto;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public boolean isActivo() {
        return activo;
    }
}
