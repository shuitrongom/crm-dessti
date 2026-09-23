package com.dessti.crm.platform.empresas;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.modulos.CatalogoDependenciasModulos;
import com.dessti.crm.platform.monetizacion.domain.MonetizacionValidaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA de {@code plan} (Req 25), mapeada sobre la tabla {@code plan}
 * definida en la migracion V1 y extendida por V55.
 *
 * <p>Un Plan es un catalogo de <strong>plataforma</strong> (no tenant-scoped)
 * administrado por el {@code super_admin}. Tras el rediseno de
 * {@code plataforma-multigiro} un Plan:</p>
 * <ul>
 *   <li>pertenece a <strong>un Giro</strong> ({@code giro_id}, FK a {@code giro});</li>
 *   <li>se cotiza en <strong>una moneda</strong> ({@code moneda_codigo}, FK a
 *       {@code moneda});</li>
 *   <li>almacena un <strong>precio por modulo</strong> ({@code precios_modulos}
 *       JSONB, {@code clave -> precio}); su <strong>total</strong> es la suma de
 *       esos precios ({@link #getTotal()}).</li>
 *   <li>conserva {@code max_usuarios} como limite de Usuarios (Req 25.1, 25.3).</li>
 * </ul>
 *
 * <h2>Coherencia de claves</h2>
 * <p>{@code modulos_habilitados} (array JSONB, V1) sigue siendo la lista
 * <strong>autoritativa</strong> de claves habilitadas y se deriva de las claves
 * de {@code precios_modulos}: ambos conjuntos coinciden exactamente. Se conserva
 * para no romper el gating de modulos que ya lo consume
 * ({@code ServicioEmpresas}, {@link ModulosPlanValidacion} y la facturacion de
 * renta).</p>
 *
 * <h2>Compatibilidad con Planes legado</h2>
 * <p>Las columnas {@code giro_id} y {@code moneda_codigo} son NULLABLE en V55:
 * los Planes creados antes del rediseno pueden leerse con Giro/moneda sin
 * definir y {@code precios_modulos} vacio (total {@code 0.00}). Las factorias de
 * creacion/actualizacion, en cambio, <strong>exigen</strong> Giro y moneda a los
 * Planes nuevos y actualizados.</p>
 */
@Entity
@Table(name = "plan")
public class Plan {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Numero maximo de Usuarios del Plan (Req 25.1, 25.3); el gating lo aplica el servicio. */
    @Column(name = "max_usuarios", nullable = false)
    private int maxUsuarios;

    /**
     * Duracion del contrato de este Plan en dias (V64). El dominio exige
     * {@code > 365}: un Plan representa un compromiso mayor a un año, coherente con
     * el CHECK {@code ck_plan_duracion_dias} (duracion_dias &gt; 365) de la
     * migracion V64. Un contrato de un año o menos debe registrarse como
     * Suscripcion (Paquete de Suscripcion).
     */
    @Column(name = "duracion_dias", nullable = false)
    private int duracionDias;

    /** Giro (vertical) al que pertenece el Plan (FK {@code giro.id}, V50/V55). Nullable en filas legado. */
    @Column(name = "giro_id")
    private UUID giroId;

    /** Codigo ISO 4217 de la moneda de cotizacion del Plan (FK {@code moneda.codigo}, V22/V55). Nullable en filas legado. */
    @Column(name = "moneda_codigo", length = 3)
    private String monedaCodigo;

    /**
     * Precio por modulo del Plan (V55), persistido como JSONB (objeto
     * {@code clave -> precio}). Las claves estan normalizadas (minusculas, sin
     * espacios) y los precios validados a escala 2 dentro del rango permitido.
     * Nunca es {@code null}: la columna V55 tiene {@code DEFAULT '{}'}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "precios_modulos", nullable = false)
    private Map<String, BigDecimal> preciosModulos = new LinkedHashMap<>();

    /**
     * Conjunto de modulos habilitados por el Plan (Req 25.1, 25.4), persistido
     * como JSONB (array de cadenas). Lista <strong>autoritativa</strong> derivada
     * de las claves de {@link #preciosModulos}: nombres canonicos normalizados
     * (minusculas, sin espacios extra), sin duplicados. Nunca es {@code null}:
     * la columna V1 tiene {@code DEFAULT '[]'}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modulos_habilitados", nullable = false)
    private List<String> modulosHabilitados = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    protected Plan() {
        // Requerido por JPA.
    }

    /**
     * Crea un nuevo Plan (Req 25.1) con su Giro, su moneda de cotizacion, su
     * limite de Usuarios y su precio por modulo.
     *
     * @param nombre           nombre del Plan (unico por {@code uq_plan_nombre},
     *                         V1); obligatorio.
     * @param maxUsuarios      numero maximo de Usuarios; debe ser {@code >= 0}
     *                         (CHECK {@code ck_plan_max_usuarios} en V1).
     * @param duracionDias     duracion del contrato del Plan en dias; debe ser
     *                         {@code > 365} (CHECK {@code ck_plan_duracion_dias}
     *                         en V64).
     * @param giroId           Giro al que pertenece el Plan; <strong>obligatorio</strong>.
     * @param monedaCodigo     codigo ISO 4217 de la moneda; obligatorio. Se
     *                         normaliza a mayusculas.
     * @param preciosPorModulo mapa {@code clave -> precio} de los modulos del
     *                         Plan; se normalizan claves y precios. {@code null}
     *                         equivale a un mapa vacio (Plan sin modulos).
     * @param actor            identificador de quien crea el Plan (super_admin).
     * @return el Plan listo para persistir.
     * @throws ReglaNegocioException si el nombre es vacio, {@code maxUsuarios < 0},
     *                               {@code duracionDias <= 365}, el Giro es
     *                               {@code null}, la moneda es invalida o algun
     *                               precio queda fuera de rango.
     */
    public static Plan crear(String nombre, int maxUsuarios, int duracionDias, UUID giroId,
                             String monedaCodigo,
                             Map<String, BigDecimal> preciosPorModulo, String actor) {
        Plan plan = new Plan();
        plan.id = UUID.randomUUID();
        plan.nombre = requerirNombre(nombre);
        plan.maxUsuarios = requerirMaxUsuarios(maxUsuarios);
        plan.duracionDias = requerirDuracionPlan(duracionDias);
        plan.giroId = requerirGiro(giroId);
        plan.monedaCodigo = MonetizacionValidaciones.normalizarCodigoMoneda(monedaCodigo);
        plan.aplicarPrecios(preciosPorModulo);
        plan.createdBy = actor;
        plan.updatedBy = actor;
        return plan;
    }

    /**
     * Actualiza un Plan existente (Req 25.1): nombre, Giro, moneda, limite de
     * Usuarios y precio por modulo.
     *
     * @param nombre           nuevo nombre; obligatorio.
     * @param maxUsuarios      nuevo maximo de Usuarios; {@code >= 0}.
     * @param duracionDias     nueva duracion del contrato en dias; {@code > 365}.
     * @param giroId           nuevo Giro; <strong>obligatorio</strong>.
     * @param monedaCodigo     nueva moneda; obligatoria (ISO 4217).
     * @param preciosPorModulo nuevo mapa {@code clave -> precio}; se normaliza.
     * @param actor            identificador de quien actualiza el Plan (super_admin).
     * @throws ReglaNegocioException si el nombre es vacio, {@code maxUsuarios < 0},
     *                               {@code duracionDias <= 365}, el Giro es
     *                               {@code null}, la moneda es invalida o algun
     *                               precio queda fuera de rango.
     */
    public void actualizar(String nombre, int maxUsuarios, int duracionDias, UUID giroId,
                           String monedaCodigo,
                           Map<String, BigDecimal> preciosPorModulo, String actor) {
        this.nombre = requerirNombre(nombre);
        this.maxUsuarios = requerirMaxUsuarios(maxUsuarios);
        this.duracionDias = requerirDuracionPlan(duracionDias);
        this.giroId = requerirGiro(giroId);
        this.monedaCodigo = MonetizacionValidaciones.normalizarCodigoMoneda(monedaCodigo);
        aplicarPrecios(preciosPorModulo);
        this.updatedBy = actor;
    }

    /**
     * Indica si un modulo esta habilitado por el Plan (Req 25.4). La comparacion
     * es insensible a mayusculas/minusculas y a espacios sobrantes.
     *
     * @param modulo nombre del modulo a comprobar.
     * @return {@code true} si el modulo esta habilitado; {@code false} en caso
     *         contrario o si {@code modulo} es {@code null}/vacio.
     */
    public boolean tieneModulo(String modulo) {
        if (modulo == null) {
            return false;
        }
        String normalizado = modulo.strip().toLowerCase(Locale.ROOT);
        if (normalizado.isEmpty()) {
            return false;
        }
        return modulosHabilitados.contains(normalizado);
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------
    // Normalizacion / validacion
    // ------------------------------------------------------------------

    private static String requerirNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre del Plan es obligatorio");
        }
        return nombre.strip();
    }

    private static int requerirMaxUsuarios(int maxUsuarios) {
        if (maxUsuarios < 0) {
            throw new ReglaNegocioException("max_usuarios del Plan no puede ser negativo");
        }
        return maxUsuarios;
    }

    /**
     * Exige que la duracion del contrato de un Plan sea mayor a 365 dias, en
     * coherencia con el CHECK {@code ck_plan_duracion_dias} de V64. Un contrato de
     * un año o menos corresponde a una Suscripcion, no a un Plan.
     *
     * @param duracionDias duracion propuesta, en dias.
     * @return la misma {@code duracionDias} si es valida.
     * @throws ReglaNegocioException si {@code duracionDias <= 365}.
     */
    private static int requerirDuracionPlan(int duracionDias) {
        if (duracionDias <= 365) {
            throw new ReglaNegocioException(
                    "La duración de un Plan debe ser mayor a 365 días (un contrato de un año o menos debe registrarse como Suscripción).");
        }
        return duracionDias;
    }

    private static UUID requerirGiro(UUID giroId) {
        if (giroId == null) {
            throw new ReglaNegocioException("El Giro del Plan es obligatorio");
        }
        return giroId;
    }

    /**
     * Normaliza y aplica el mapa de precios por modulo, y deriva de sus claves la
     * lista autoritativa {@code modulos_habilitados}. Descarta claves nulas/vacias,
     * normaliza cada clave (recorte + minusculas) y valida cada precio via
     * {@link MonetizacionValidaciones#validarPrecio(BigDecimal)} (escala 2, rango
     * permitido). Preserva el orden de insercion y deduplica por clave.
     *
     * <p><strong>Dependencia de modulos (Req 7).</strong> Tras construir el mapa
     * normalizado, se agregan los modulos <em>requeridos</em> declarados en
     * {@link CatalogoDependenciasModulos}: por ejemplo, si el Plan incluye
     * {@code inventario-avanzado} pero no {@code operacion}, se agrega
     * {@code operacion} con precio {@code 0.00} (escala monetaria) para que quede
     * reflejado en {@code modulos_habilitados} y en el total. La regla vive en el
     * catalogo (no se codifican claves aqui): se recorre cada clave presente y se
     * consultan sus requeridos. Es unidireccional (tener {@code operacion} sin
     * {@code inventario-avanzado} no agrega {@code inventario-avanzado}) y respeta
     * el precio ya capturado por el super_admin: si la clave requerida ya existe,
     * <strong>no</strong> se sobrescribe su precio.</p>
     *
     * @param preciosPorModulo mapa crudo {@code clave -> precio}; {@code null} =
     *                         mapa vacio.
     * @throws ReglaNegocioException si algun precio es nulo o queda fuera de rango.
     */
    private void aplicarPrecios(Map<String, BigDecimal> preciosPorModulo) {
        Map<String, BigDecimal> normalizados = new LinkedHashMap<>();
        if (preciosPorModulo != null) {
            for (Map.Entry<String, BigDecimal> entrada : preciosPorModulo.entrySet()) {
                String clave = entrada.getKey();
                if (clave == null) {
                    continue;
                }
                String claveNorm = clave.strip().toLowerCase(Locale.ROOT);
                if (claveNorm.isEmpty()) {
                    continue;
                }
                BigDecimal precio = MonetizacionValidaciones.validarPrecio(entrada.getValue());
                normalizados.put(claveNorm, precio);
            }
        }
        // Dependencia de modulos (Req 7.1-7.4): agregar los requeridos ausentes con
        // precio 0.00. Se deriva del catalogo (fuente unica) iterando sobre las
        // claves capturadas; unidireccional y respetando el precio ya capturado.
        for (String clave : new ArrayList<>(normalizados.keySet())) {
            for (String requerido : CatalogoDependenciasModulos.requeridosDe(clave)) {
                normalizados.putIfAbsent(requerido, MonetizacionValidaciones.PRECIO_MINIMO);
            }
        }
        this.preciosModulos = normalizados;
        this.modulosHabilitados = new ArrayList<>(normalizados.keySet());
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getMaxUsuarios() {
        return maxUsuarios;
    }

    /**
     * @return la duracion del contrato del Plan en dias (siempre {@code > 365}
     *         para Planes creados/actualizados por el dominio; V64).
     */
    public int getDuracionDias() {
        return duracionDias;
    }

    /**
     * @return el Giro (vertical) al que pertenece el Plan, o {@code null} en
     *         Planes legado anteriores al rediseno (V55).
     */
    public UUID getGiroId() {
        return giroId;
    }

    /**
     * @return el codigo ISO 4217 de la moneda de cotizacion del Plan, o
     *         {@code null} en Planes legado.
     */
    public String getMonedaCodigo() {
        return monedaCodigo;
    }

    /**
     * @return el precio por modulo del Plan (copia inmutable {@code clave ->
     *         precio}, escala 2). Nunca {@code null} (puede estar vacio).
     */
    public Map<String, BigDecimal> getPreciosModulos() {
        return Map.copyOf(preciosModulos);
    }

    /**
     * @return el total del Plan: suma de los precios de sus modulos, con escala
     *         monetaria 2. {@code 0.00} si el Plan no tiene precios (p. ej. legado).
     */
    public BigDecimal getTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal precio : preciosModulos.values()) {
            total = total.add(precio);
        }
        return total.setScale(MonetizacionValidaciones.ESCALA_MONETARIA);
    }

    /**
     * @return los modulos habilitados por el Plan (copia inmutable de nombres
     *         canonicos normalizados; claves de {@link #getPreciosModulos()}).
     *         Nunca {@code null}.
     */
    public List<String> getModulosHabilitados() {
        return List.copyOf(modulosHabilitados);
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
