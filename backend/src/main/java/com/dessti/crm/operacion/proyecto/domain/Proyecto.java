package com.dessti.crm.operacion.proyecto.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code proyecto}: un Proyecto asociado a un
 * Cliente que agrupa uno o varios {@link Sitio Sitios}, mapeada sobre la tabla
 * {@code proyecto} de la migracion V25 (Req 21, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V25
 * para que un arranque con {@code ddl-auto=validate} valide sin conflictos.</p>
 *
 * <h2>Reglas de dominio (Req 21)</h2>
 * <ul>
 *   <li>{@link #crear(UUID, String, String)} valida los datos obligatorios: el
 *       Cliente asociado (Req 21.1) y el nombre entre 1 y 200 caracteres tras
 *       recortar espacios (Req 21.1). La existencia del Cliente en el tenant la
 *       verifica la capa de aplicacion antes de invocar esta fabrica.</li>
 *   <li>{@link #renombrar(String, String)} revalida y actualiza el nombre
 *       conservando el vinculo al Cliente (soporte a la modificacion del Req 21.6).</li>
 * </ul>
 *
 * <p>El estado consolidado del Proyecto (Req 21.4) NO se almacena en esta entidad:
 * es una funcion pura derivada del avance de sus Sitios
 * ({@link DerivacionEstadoProyecto}), calculada en tiempo de consulta.</p>
 */
@Entity
@Table(name = "proyecto")
public class Proyecto extends TenantScopedEntity {

    /** Longitud maxima del nombre (coincide con VARCHAR(200) de V25). */
    public static final int LONGITUD_MAXIMA_NOMBRE = 200;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Cliente al que pertenece el Proyecto (Req 21.1). Inmutable; se fija al crear
     * el Proyecto. Referencia con FK a {@code cliente} (V11); la existencia en el
     * tenant la verifica la capa de aplicacion.
     */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    /** Nombre del Proyecto (Req 21.1), 1..200 tras recortar espacios. */
    @Column(name = "nombre", nullable = false)
    private String nombre;

    protected Proyecto() {
        // Requerido por JPA.
    }

    /**
     * Crea un Proyecto nuevo asociado a un Cliente, validando los datos
     * obligatorios (Req 21.1). El {@code tenant_id} <strong>no</strong> se asigna
     * aqui: lo fija {@link TenantScopedEntity} desde el contexto autenticado al
     * persistir (Req 23.4).
     *
     * <p>La existencia del Cliente en el tenant NO se comprueba aqui (requiere
     * consultar otro agregado): la aplicacion la verifica antes de invocar esta
     * fabrica, o bien se apoya en la FK {@code fk_proyecto_cliente} de V25.</p>
     *
     * @param clienteId identificador del Cliente asociado; obligatorio (Req 21.1).
     * @param nombre    nombre del Proyecto; obligatorio (1..200, Req 21.1).
     * @param actor     identificador de quien crea, para las columnas de auditoria
     *                  {@code created_by}/{@code updated_by} (Req 21.6).
     * @return el Proyecto listo para persistir.
     * @throws ReglaNegocioException si falta el Cliente o el nombre es invalido (422).
     */
    public static Proyecto crear(UUID clienteId, String nombre, String actor) {
        if (clienteId == null) {
            throw new ReglaNegocioException(
                    "El Proyecto debe asociarse a un Cliente existente.");
        }
        Proyecto proyecto = new Proyecto();
        proyecto.id = UUID.randomUUID();
        proyecto.clienteId = clienteId;
        proyecto.nombre = normalizarNombre(nombre);
        proyecto.setCreatedBy(actor);
        proyecto.setUpdatedBy(actor);
        return proyecto;
    }

    /**
     * Renombra el Proyecto revalidando la regla del nombre (Req 21.1) y registra al
     * actor de la modificacion (Req 21.6). No altera el Cliente asociado.
     *
     * @param nombre nuevo nombre; obligatorio (1..200).
     * @param actor  identificador de quien modifica, para {@code updated_by}.
     * @throws ReglaNegocioException si el nombre es invalido (422).
     */
    public void renombrar(String nombre, String actor) {
        this.nombre = normalizarNombre(nombre);
        this.setUpdatedBy(actor);
    }

    /**
     * Valida y normaliza el nombre del Proyecto (Req 21.1): obligatorio y entre 1 y
     * 200 caracteres tras recortar espacios, coherente con el CHECK
     * {@code ck_proyecto_nombre_longitud} de V25.
     *
     * @param valor nombre a normalizar.
     * @return el nombre recortado.
     * @throws ReglaNegocioException si es nulo/vacio o excede el maximo.
     */
    private static String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El nombre del Proyecto es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new ReglaNegocioException(
                    "El nombre del Proyecto no puede exceder " + LONGITUD_MAXIMA_NOMBRE
                            + " caracteres.");
        }
        return normalizado;
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
}
