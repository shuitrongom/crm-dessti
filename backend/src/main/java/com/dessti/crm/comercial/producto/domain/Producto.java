package com.dessti.crm.comercial.producto.domain;

import java.util.UUID;

import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code producto} (bien o servicio del catalogo de la Empresa,
 * por ejemplo un tipo de anuncio luminoso), mapeada sobre la tabla
 * {@code producto} de la migracion V12 (Req 59, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y por tanto <em>hereda</em> {@code tenant_id} (asignada automaticamente desde
 * el {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca
 * desde la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49)
 * y las marcas de auditoria {@code created_at}/{@code updated_at}/
 * {@code created_by}/{@code updated_by}. Esas columnas <em>no</em> se redeclaran
 * aqui. El mapeo de columnas coincide <em>exactamente</em> con V12.</p>
 *
 * <h2>Reglas de dominio (Req 59)</h2>
 * <ul>
 *   <li>{@link #crear} valida los datos obligatorios (nombre 1..200, unidad y
 *       descripcion) y rechaza los ausentes con 422 (Req 59.1, 59.2).</li>
 *   <li>{@link #actualizar} revalida y persiste los cambios.</li>
 *   <li>{@link #desactivar(String)} realiza el borrado logico
 *       ({@code activo=false}) conservando el historico (Req 59.6).</li>
 *   <li>La informacion comercial de apoyo (cliente meta, alianzas y competencia)
 *       se registra como datos descriptivos opcionales (Req 59.5).</li>
 * </ul>
 */
@Entity
@Table(name = "producto")
public class Producto extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Unidad de medida/venta del Producto (obligatoria, Req 59.1). */
    @Column(name = "unidad", nullable = false)
    private String unidad;

    /** Descripcion del Producto (obligatoria, Req 59.1/59.2). */
    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    /** Informacion comercial de apoyo: cliente meta (opcional, Req 59.5). */
    @Column(name = "cliente_meta")
    private String clienteMeta;

    /** Informacion comercial de apoyo: alianzas (opcional, Req 59.5). */
    @Column(name = "alianzas")
    private String alianzas;

    /** Informacion comercial de apoyo: competencia (opcional, Req 59.5). */
    @Column(name = "competencia")
    private String competencia;

    /**
     * Foto/imagen opcional del Producto para su identificacion visual (por
     * ejemplo, al mostrarlo en una Cotizacion, Req 59). Se almacena como una URL
     * o un {@code data URI} en linea (base64) en la columna {@code foto TEXT} de
     * V61, replicando la convencion del logotipo de branding de la Empresa: no
     * se sube ni almacena un binario aparte y no se fuerza un formato de imagen,
     * solo se acota el tamano (ver {@link #LONGITUD_MAXIMA_FOTO}).
     */
    @Column(name = "foto")
    private String foto;

    /** Bandera de borrado logico (Req 59.6); {@code true} mientras esta vigente. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    /**
     * Longitud maxima de la foto: 1 MiB de texto (1.048.576 caracteres) como
     * salvaguarda contra cargas abusivas que degradarian el almacenamiento y la
     * interfaz. Coincide con la cota del logotipo de branding
     * ({@code Empresa.LONGITUD_MAXIMA_LOGO}). Un valor mayor se rechaza con
     * {@link com.dessti.crm.platform.error.ReglaNegocioException} (HTTP 422).
     */
    static final int LONGITUD_MAXIMA_FOTO = 1_048_576;

    protected Producto() {
        // Requerido por JPA.
    }

    /**
     * Crea un Producto nuevo y activo validando los datos obligatorios (Req 59.1,
     * 59.2). El {@code tenant_id} <strong>no</strong> se asigna aqui: lo fija
     * {@link TenantScopedEntity} desde el contexto autenticado al persistir
     * (Req 23.4).
     *
     * @param nombre       nombre del Producto; obligatorio (1..200).
     * @param unidad       unidad de medida/venta; obligatoria.
     * @param descripcion  descripcion; obligatoria.
     * @param clienteMeta  cliente meta; opcional (Req 59.5).
     * @param alianzas     alianzas; opcional (Req 59.5).
     * @param competencia  competencia; opcional (Req 59.5).
     * @param foto         foto/imagen (URL o {@code data URI}); opcional. No debe
     *                     exceder {@value #LONGITUD_MAXIMA_FOTO} caracteres.
     * @param actor        identificador de quien crea, para las columnas de
     *                     auditoria {@code created_by}/{@code updated_by}.
     * @return el Producto listo para persistir.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si falta o es
     *         invalido algun dato obligatorio (422, Req 59.2) o si la foto
     *         excede su longitud maxima (422).
     */
    public static Producto crear(String nombre, String unidad, String descripcion,
                                 String clienteMeta, String alianzas, String competencia,
                                 String foto, String actor) {
        Producto producto = new Producto();
        producto.id = UUID.randomUUID();
        producto.nombre = CatalogoValidaciones.normalizarNombre(nombre);
        producto.unidad = CatalogoValidaciones.normalizarUnidad(unidad);
        producto.descripcion = CatalogoValidaciones.normalizarDescripcion(descripcion);
        producto.clienteMeta = CatalogoValidaciones.normalizarTextoOpcional(clienteMeta);
        producto.alianzas = CatalogoValidaciones.normalizarTextoOpcional(alianzas);
        producto.competencia = CatalogoValidaciones.normalizarTextoOpcional(competencia);
        producto.foto = normalizarFoto(foto);
        producto.activo = true;
        producto.setCreatedBy(actor);
        producto.setUpdatedBy(actor);
        return producto;
    }

    /**
     * Actualiza los datos del Producto revalidando las reglas obligatorias
     * (Req 59.1, 59.2). No modifica el estado {@code activo} (para eso esta
     * {@link #desactivar(String)}).
     *
     * @param nombre       nuevo nombre; obligatorio (1..200).
     * @param unidad       nueva unidad; obligatoria.
     * @param descripcion  nueva descripcion; obligatoria.
     * @param clienteMeta  cliente meta; opcional (Req 59.5).
     * @param alianzas     alianzas; opcional (Req 59.5).
     * @param competencia  competencia; opcional (Req 59.5).
     * @param foto         foto/imagen (URL o {@code data URI}); opcional.
     *                     {@code null}/blanco la limpia. No debe exceder
     *                     {@value #LONGITUD_MAXIMA_FOTO} caracteres.
     * @param actor        identificador de quien actualiza, para {@code updated_by}.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si falta o es
     *         invalido algun dato obligatorio (422, Req 59.2) o si la foto
     *         excede su longitud maxima (422).
     */
    public void actualizar(String nombre, String unidad, String descripcion,
                           String clienteMeta, String alianzas, String competencia,
                           String foto, String actor) {
        String nuevoNombre = CatalogoValidaciones.normalizarNombre(nombre);
        String nuevaUnidad = CatalogoValidaciones.normalizarUnidad(unidad);
        String nuevaDescripcion = CatalogoValidaciones.normalizarDescripcion(descripcion);
        String nuevoClienteMeta = CatalogoValidaciones.normalizarTextoOpcional(clienteMeta);
        String nuevasAlianzas = CatalogoValidaciones.normalizarTextoOpcional(alianzas);
        String nuevaCompetencia = CatalogoValidaciones.normalizarTextoOpcional(competencia);
        String nuevaFoto = normalizarFoto(foto);
        this.nombre = nuevoNombre;
        this.unidad = nuevaUnidad;
        this.descripcion = nuevaDescripcion;
        this.clienteMeta = nuevoClienteMeta;
        this.alianzas = nuevasAlianzas;
        this.competencia = nuevaCompetencia;
        this.foto = nuevaFoto;
        this.setUpdatedBy(actor);
    }

    /**
     * Normaliza la foto opcional del Producto (Req 59). Un valor {@code null} o
     * en blanco se interpreta como ausencia y devuelve {@code null} (permite
     * limpiarla). En caso contrario se recorta y se valida que no exceda
     * {@link #LONGITUD_MAXIMA_FOTO}; no se fuerza un formato de imagen (se acepta
     * una URL o un {@code data URI}), replicando la lenidad del logotipo de
     * branding de la Empresa.
     *
     * @param valor foto a normalizar; puede ser {@code null}.
     * @return la foto recortada, o {@code null} si no se proporciono.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si excede
     *         {@link #LONGITUD_MAXIMA_FOTO} (HTTP 422).
     */
    private static String normalizarFoto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_FOTO) {
            throw new com.dessti.crm.platform.error.ReglaNegocioException(
                    "La foto del Producto excede el tamano maximo permitido ("
                            + LONGITUD_MAXIMA_FOTO + " caracteres).");
        }
        return normalizado;
    }

    /**
     * Realiza el borrado logico del Producto: marca {@code activo=false}
     * conservando sus datos historicos (Req 59.6). Es idempotente.
     *
     * @param actor identificador de quien realiza la baja, para {@code updated_by}.
     */
    public void desactivar(String actor) {
        this.activo = false;
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si el Producto esta activo (no dado de baja logica, Req 59.6).
     *
     * @return {@code true} si el Producto esta activo.
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

    public String getUnidad() {
        return unidad;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getClienteMeta() {
        return clienteMeta;
    }

    public String getAlianzas() {
        return alianzas;
    }

    public String getCompetencia() {
        return competencia;
    }

    /**
     * Foto/imagen opcional del Producto (URL o {@code data URI}); {@code null}
     * si no se ha establecido (Req 59).
     *
     * @return la foto del Producto, o {@code null}.
     */
    public String getFoto() {
        return foto;
    }

    public boolean isActivo() {
        return activo;
    }
}
