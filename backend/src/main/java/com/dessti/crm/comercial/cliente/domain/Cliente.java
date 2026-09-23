package com.dessti.crm.comercial.cliente.domain;

import java.util.UUID;

import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code cliente} (destinatario comercial de anuncios
 * luminosos), mapeada sobre la tabla {@code cliente} de la migracion V11
 * (Req 5, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y por tanto <em>hereda</em> las columnas {@code tenant_id} (asignada
 * automaticamente desde el {@link com.dessti.crm.platform.tenant.TenantContext}
 * al persistir, nunca desde la peticion, Req 23.4), {@code version}
 * (concurrencia optimista, Req 49) y las marcas de auditoria
 * {@code created_at}/{@code updated_at}/{@code created_by}/{@code updated_by}.
 * Esas columnas <em>no</em> se redeclaran aqui. Al extender la superclase, el
 * filtro global de Hibernate {@code tenantFilter} acota automaticamente las
 * consultas al tenant vigente (Capa 1), complementado por la Row-Level Security
 * de la tabla (Capa 2, V11).</p>
 *
 * <p>El mapeo de columnas (nombres, nulabilidad y tipos) coincide
 * <em>exactamente</em> con V11 para que un arranque con {@code ddl-auto=validate}
 * valide sin conflictos.</p>
 *
 * <h2>Reglas de dominio (Req 5)</h2>
 * <ul>
 *   <li>{@link #crear(String, String, String, String, DatosBasicosCliente, String)}
 *       valida los datos obligatorios (nombre 1..200, RFC 12..13 con formato, y
 *       al menos un dato de contacto: email valido o telefono de 10..15 digitos)
 *       y los datos basicos opcionales (Req 5.1/5.2, V59).</li>
 *   <li>{@link #actualizar(String, String, String, String, DatosBasicosCliente, String)}
 *       revalida y persiste los cambios (Req 5.4).</li>
 *   <li>{@link #desactivar(String)} realiza el borrado logico ({@code activo=false})
 *       conservando el historico (Req 5.9).</li>
 * </ul>
 */
@Entity
@Table(name = "cliente")
public class Cliente extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Identificador fiscal (RFC), normalizado a mayusculas. Unico por tenant entre activos. */
    @Column(name = "rfc", nullable = false)
    private String rfc;

    /** Correo electronico; {@code null} si no se proporciono (Req 5.1). */
    @Column(name = "email")
    private String email;

    /** Telefono (10..15 digitos); {@code null} si no se proporciono (Req 5.1). */
    @Column(name = "telefono")
    private String telefono;

    /** Nombre comercial (marca); {@code null} si no se proporciono (Req 5, V59). */
    @Column(name = "nombre_comercial")
    private String nombreComercial;

    /** Tipo de persona (fisica/moral); {@code null} si no se proporciono (Req 5, V59). */
    @Convert(converter = TipoPersonaConverter.class)
    @Column(name = "tipo_persona")
    private TipoPersona tipoPersona;

    /** Telefono secundario (10..15 digitos); {@code null} si no se proporciono (Req 5, V59). */
    @Column(name = "telefono_adicional")
    private String telefonoAdicional;

    /** Calle y numero de la direccion; {@code null} si no se proporciono (V59). */
    @Column(name = "direccion_calle")
    private String direccionCalle;

    /** Ciudad de la direccion; {@code null} si no se proporciono (V59). */
    @Column(name = "direccion_ciudad")
    private String direccionCiudad;

    /** Estado/provincia de la direccion; {@code null} si no se proporciono (V59). */
    @Column(name = "direccion_estado")
    private String direccionEstado;

    /** Codigo postal de la direccion; {@code null} si no se proporciono (V59). */
    @Column(name = "direccion_cp")
    private String direccionCp;

    /** Pais de la direccion; {@code null} si no se proporciono (V59). */
    @Column(name = "direccion_pais")
    private String direccionPais;

    /** Notas libres; {@code null} si no se proporcionaron (V59). */
    @Column(name = "notas")
    private String notas;

    /** Bandera de borrado logico (Req 5.9); {@code true} mientras el Cliente esta vigente. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Cliente() {
        // Requerido por JPA.
    }

    /**
     * Crea un Cliente nuevo y activo validando los datos obligatorios (Req 5.1,
     * 5.2). El {@code tenant_id} <strong>no</strong> se asigna aqui: lo fija
     * {@link TenantScopedEntity} desde el contexto autenticado al persistir
     * (Req 23.4).
     *
     * @param nombre   razon social o nombre; obligatorio (1..200).
     * @param rfc      identificador fiscal; obligatorio (12..13, formato valido;
     *                 se normaliza a mayusculas).
     * @param email    correo electronico; opcional (valido si se proporciona).
     * @param telefono telefono; opcional (10..15 digitos si se proporciona).
     * @param actor    identificador de quien crea el Cliente, para las columnas
     *                 de auditoria {@code created_by}/{@code updated_by}.
     * @return el Cliente listo para persistir, sin datos basicos de negocio.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si algun dato
     *         obligatorio falta o es invalido, o si no hay al menos un dato de
     *         contacto (Req 5.1, 5.2).
     */
    public static Cliente crear(String nombre, String rfc, String email, String telefono, String actor) {
        return crear(nombre, rfc, email, telefono, DatosBasicosCliente.VACIO, actor);
    }

    /**
     * Crea un Cliente nuevo y activo validando los datos obligatorios y los datos
     * basicos de negocio OPCIONALES (Req 5.1, 5.2, V59). El {@code tenant_id}
     * <strong>no</strong> se asigna aqui: lo fija {@link TenantScopedEntity} desde
     * el contexto autenticado al persistir (Req 23.4).
     *
     * @param nombre   razon social o nombre; obligatorio (1..200).
     * @param rfc      identificador fiscal; obligatorio (12..13, formato valido).
     * @param email    correo electronico; opcional (valido si se proporciona).
     * @param telefono telefono; opcional (10..15 digitos si se proporciona).
     * @param datosBasicos datos basicos de negocio OPCIONALES (nombre comercial,
     *                 tipo de persona, telefono adicional, direccion y notas);
     *                 puede ser {@code null} (equivale a todos ausentes).
     * @param actor    identificador de quien crea el Cliente, para
     *                 {@code created_by}/{@code updated_by}.
     * @return el Cliente listo para persistir.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si algun dato
     *         obligatorio falta o es invalido, si no hay al menos un dato de
     *         contacto, o si algun dato basico opcional es invalido (Req 5.1, 5.2).
     */
    public static Cliente crear(String nombre, String rfc, String email, String telefono,
                                DatosBasicosCliente datosBasicos, String actor) {
        Cliente cliente = new Cliente();
        cliente.id = UUID.randomUUID();
        cliente.nombre = DatosContacto.normalizarNombre(nombre);
        cliente.rfc = DatosContacto.normalizarRfc(rfc);
        cliente.email = DatosContacto.normalizarEmail(email);
        cliente.telefono = DatosContacto.normalizarTelefono(telefono);
        DatosContacto.exigirAlMenosUnContacto(cliente.email, cliente.telefono);
        cliente.aplicarDatosBasicos(datosBasicos);
        cliente.activo = true;
        cliente.setCreatedBy(actor);
        cliente.setUpdatedBy(actor);
        return cliente;
    }

    /**
     * Actualiza los datos del Cliente revalidando las reglas obligatorias, sin
     * tocar los datos basicos de negocio (Req 5.4, 5.1, 5.2). Sobrecarga de
     * conveniencia que delega en
     * {@link #actualizar(String, String, String, String, DatosBasicosCliente, String)}
     * limpiando (dejando en {@code null}) los campos basicos opcionales.
     *
     * @param nombre   nuevo nombre; obligatorio (1..200).
     * @param rfc      nuevo RFC; obligatorio (12..13, formato valido).
     * @param email    nuevo email; opcional (valido si se proporciona).
     * @param telefono nuevo telefono; opcional (10..15 digitos si se proporciona).
     * @param actor    identificador de quien actualiza, para {@code updated_by}.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si algun dato
     *         es invalido o falta al menos un dato de contacto (Req 5.1, 5.2).
     */
    public void actualizar(String nombre, String rfc, String email, String telefono, String actor) {
        actualizar(nombre, rfc, email, telefono, DatosBasicosCliente.VACIO, actor);
    }

    /**
     * Actualiza los datos del Cliente revalidando las reglas obligatorias y los
     * datos basicos de negocio OPCIONALES (Req 5.4, 5.1, 5.2, V59). No modifica el
     * estado {@code activo} (para eso esta {@link #desactivar(String)}).
     *
     * @param nombre   nuevo nombre; obligatorio (1..200).
     * @param rfc      nuevo RFC; obligatorio (12..13, formato valido).
     * @param email    nuevo email; opcional (valido si se proporciona).
     * @param telefono nuevo telefono; opcional (10..15 digitos si se proporciona).
     * @param datosBasicos nuevos datos basicos de negocio OPCIONALES; puede ser
     *                 {@code null} (equivale a limpiar todos los campos basicos).
     * @param actor    identificador de quien actualiza, para {@code updated_by}.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si algun dato
     *         es invalido o falta al menos un dato de contacto (Req 5.1, 5.2).
     */
    public void actualizar(String nombre, String rfc, String email, String telefono,
                           DatosBasicosCliente datosBasicos, String actor) {
        String nuevoNombre = DatosContacto.normalizarNombre(nombre);
        String nuevoRfc = DatosContacto.normalizarRfc(rfc);
        String nuevoEmail = DatosContacto.normalizarEmail(email);
        String nuevoTelefono = DatosContacto.normalizarTelefono(telefono);
        DatosContacto.exigirAlMenosUnContacto(nuevoEmail, nuevoTelefono);
        // Se validan/asignan primero los datos basicos: si alguno es invalido,
        // aplicarDatosBasicos lanza 422 SIN haber tocado aun los campos
        // obligatorios, dejando la entidad en su estado previo (actualizacion
        // atomica; el metodo normaliza en locales y solo asigna al final).
        this.aplicarDatosBasicos(datosBasicos);
        this.nombre = nuevoNombre;
        this.rfc = nuevoRfc;
        this.email = nuevoEmail;
        this.telefono = nuevoTelefono;
        this.setUpdatedBy(actor);
    }

    /**
     * Normaliza y asigna los datos basicos de negocio opcionales (Req 5, V59).
     * Un {@code datos} nulo se trata como {@link DatosBasicosCliente#VACIO}
     * (todos los campos quedan en {@code null}). Cada campo se valida contra su
     * cota; un valor invalido lanza {@link com.dessti.crm.platform.error.ReglaNegocioException}
     * (422) SIN mutar la entidad, porque la normalizacion ocurre en variables
     * locales antes de asignar.
     */
    private void aplicarDatosBasicos(DatosBasicosCliente datos) {
        DatosBasicosCliente d = DatosBasicosCliente.orVacio(datos);
        String nuevoNombreComercial = DatosContacto.normalizarTextoOpcional(
                d.nombreComercial(), "nombre comercial", DatosContacto.LONGITUD_MAXIMA_NOMBRE_COMERCIAL);
        TipoPersona nuevoTipoPersona = TipoPersona.desde(d.tipoPersona());
        String nuevoTelefonoAdicional = DatosContacto.normalizarTelefono(d.telefonoAdicional());
        String nuevaCalle = DatosContacto.normalizarTextoOpcional(
                d.direccionCalle(), "calle", DatosContacto.LONGITUD_MAXIMA_DIRECCION_CALLE);
        String nuevaCiudad = DatosContacto.normalizarTextoOpcional(
                d.direccionCiudad(), "ciudad", DatosContacto.LONGITUD_MAXIMA_DIRECCION_CIUDAD);
        String nuevoEstado = DatosContacto.normalizarTextoOpcional(
                d.direccionEstado(), "estado", DatosContacto.LONGITUD_MAXIMA_DIRECCION_ESTADO);
        String nuevoCp = DatosContacto.normalizarTextoOpcional(
                d.direccionCp(), "codigo postal", DatosContacto.LONGITUD_MAXIMA_DIRECCION_CP);
        String nuevoPais = DatosContacto.normalizarTextoOpcional(
                d.direccionPais(), "pais", DatosContacto.LONGITUD_MAXIMA_DIRECCION_PAIS);
        String nuevasNotas = DatosContacto.normalizarTextoOpcional(
                d.notas(), "notas", DatosContacto.LONGITUD_MAXIMA_NOTAS);

        this.nombreComercial = nuevoNombreComercial;
        this.tipoPersona = nuevoTipoPersona;
        this.telefonoAdicional = nuevoTelefonoAdicional;
        this.direccionCalle = nuevaCalle;
        this.direccionCiudad = nuevaCiudad;
        this.direccionEstado = nuevoEstado;
        this.direccionCp = nuevoCp;
        this.direccionPais = nuevoPais;
        this.notas = nuevasNotas;
    }

    /**
     * Realiza el borrado logico del Cliente: marca {@code activo=false}
     * conservando sus datos historicos (Req 5.9). Es idempotente: si ya estaba
     * inactivo, no cambia el estado (solo actualiza {@code updated_by}).
     *
     * <p>Al quedar inactivo, el Cliente libera su RFC frente al indice unico
     * parcial {@code uq_cliente_rfc_activo_por_tenant} (V11), de modo que un
     * nuevo Cliente activo puede reutilizarlo (Req 5.3).</p>
     *
     * @param actor identificador de quien realiza la baja, para {@code updated_by}.
     */
    public void desactivar(String actor) {
        this.activo = false;
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si el Cliente esta activo (no dado de baja logica, Req 5.9).
     *
     * @return {@code true} si el Cliente esta activo.
     */
    public boolean estaActivo() {
        return activo;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRfc() {
        return rfc;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getNombreComercial() {
        return nombreComercial;
    }

    public TipoPersona getTipoPersona() {
        return tipoPersona;
    }

    public String getTelefonoAdicional() {
        return telefonoAdicional;
    }

    public String getDireccionCalle() {
        return direccionCalle;
    }

    public String getDireccionCiudad() {
        return direccionCiudad;
    }

    public String getDireccionEstado() {
        return direccionEstado;
    }

    public String getDireccionCp() {
        return direccionCp;
    }

    public String getDireccionPais() {
        return direccionPais;
    }

    public String getNotas() {
        return notas;
    }

    public boolean isActivo() {
        return activo;
    }
}
