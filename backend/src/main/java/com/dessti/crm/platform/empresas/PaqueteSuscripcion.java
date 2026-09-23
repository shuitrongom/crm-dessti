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
 * Entidad JPA de {@code paquete_suscripcion} (Req 3), mapeada sobre la tabla
 * {@code paquete_suscripcion} definida en la migracion V64.
 *
 * <p>Un Paquete de Suscripcion es el catalogo de <strong>contratos de corto
 * plazo</strong> (duracion de <strong>un año o menos</strong>, {@code
 * duracionDias &le; 365}) con opcion de <strong>periodo de prueba</strong>. Es
 * el instrumento comercial complementario y excluyente del {@link Plan}
 * (contratos de largo plazo, mayores a un año). Cada Empresa se contrata con
 * exactamente uno de los dos instrumentos.</p>
 *
 * <p>Al igual que {@link Plan} y {@code giro}, es un catalogo de
 * <strong>plataforma</strong> (dato NO tenant-scoped, <strong>sin RLS</strong>)
 * administrado por el {@code super_admin}. Espeja el patron de {@link Plan}:</p>
 * <ul>
 *   <li>pertenece a <strong>un Giro</strong> ({@code giro_id}, FK a {@code giro});</li>
 *   <li>se cotiza en <strong>una moneda</strong> ({@code moneda_codigo}, FK a
 *       {@code moneda});</li>
 *   <li>almacena un <strong>precio por modulo</strong> ({@code precios_modulos}
 *       JSONB, {@code clave -> precio}); su <strong>total</strong> es la suma de
 *       esos precios ({@link #getTotal()});</li>
 *   <li>conserva {@code max_usuarios} como limite de Usuarios.</li>
 * </ul>
 *
 * <h2>Atributos propios del Paquete (Req 3.2)</h2>
 * <ul>
 *   <li><strong>{@code duracionDias}</strong>: duracion del contrato en dias; el
 *       dominio exige {@code 0 < duracionDias <= 365} (coherente con el CHECK
 *       {@code ck_paquete_duracion_dias} de la V64). Un compromiso mayor a un año
 *       debe registrarse como {@link Plan} (Req 3.3, 9.1).</li>
 *   <li><strong>{@code admitePrueba}</strong>: indica si el Paquete ofrece un
 *       periodo de prueba.</li>
 *   <li><strong>{@code duracionPruebaMeses}</strong>: duracion de la prueba en
 *       meses; solo es relevante cuando {@code admitePrueba} es {@code true}. En
 *       ese caso debe ser {@code > 0} (Req 3.5) y no puede exceder la duracion del
 *       contrato (Req 3.6). Cuando {@code admitePrueba} es {@code false} queda en
 *       {@code null} (se ignora).</li>
 * </ul>
 *
 * <h2>Coherencia de claves</h2>
 * <p>{@code modulos_habilitados} (array JSONB) es la lista
 * <strong>autoritativa</strong> de claves habilitadas y se deriva de las claves
 * de {@code precios_modulos}: ambos conjuntos coinciden exactamente. Se conserva
 * para alimentar el gating de modulos igual que en {@link Plan}.</p>
 */
@Entity
@Table(name = "paquete_suscripcion")
public class PaqueteSuscripcion {

    /**
     * Aproximacion de dias por mes usada para comparar la duracion de la prueba
     * (expresada en meses) contra la duracion del contrato (expresada en dias).
     * Se emplea el convencional de 30 dias/mes; es una aproximacion documentada,
     * suficiente para la validacion de dominio "prueba &le; contrato" (Req 3.6).
     */
    static final int DIAS_POR_MES = 30;

    /** Duracion maxima de contrato de un Paquete de Suscripcion, en dias (un año). */
    static final int DURACION_MAXIMA_DIAS = 365;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Numero maximo de Usuarios del Paquete; el gating lo aplica el servicio. */
    @Column(name = "max_usuarios", nullable = false)
    private int maxUsuarios;

    /**
     * Duracion del contrato de este Paquete en dias (V64). El dominio exige
     * {@code 0 < duracionDias <= 365}: un Paquete de Suscripcion representa un
     * compromiso de un año o menos, coherente con el CHECK
     * {@code ck_paquete_duracion_dias} (duracion_dias &gt; 0 AND duracion_dias
     * &le; 365). Un contrato mayor a un año debe registrarse como {@link Plan}.
     */
    @Column(name = "duracion_dias", nullable = false)
    private int duracionDias;

    /** Indica si el Paquete ofrece un periodo de prueba (Req 3.2). */
    @Column(name = "admite_prueba", nullable = false)
    private boolean admitePrueba;

    /**
     * Duracion de la prueba en meses (Req 3.2). Solo aplica cuando
     * {@link #admitePrueba} es {@code true}; en ese caso es {@code > 0} y su
     * equivalente en dias no excede {@link #duracionDias}. Cuando el Paquete no
     * admite prueba queda {@code null}.
     */
    @Column(name = "duracion_prueba_meses")
    private Integer duracionPruebaMeses;

    /** Giro (vertical) al que pertenece el Paquete (FK {@code giro.id}). Nullable en filas legado. */
    @Column(name = "giro_id")
    private UUID giroId;

    /** Codigo ISO 4217 de la moneda de cotizacion (FK {@code moneda.codigo}). Nullable en filas legado. */
    @Column(name = "moneda_codigo", length = 3)
    private String monedaCodigo;

    /**
     * Precio por modulo del Paquete, persistido como JSONB (objeto
     * {@code clave -> precio}). Las claves estan normalizadas (minusculas, sin
     * espacios) y los precios validados a escala 2 dentro del rango permitido.
     * Nunca es {@code null}: la columna V64 tiene {@code DEFAULT '{}'}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "precios_modulos", nullable = false)
    private Map<String, BigDecimal> preciosModulos = new LinkedHashMap<>();

    /**
     * Conjunto de modulos habilitados por el Paquete, persistido como JSONB
     * (array de cadenas). Lista <strong>autoritativa</strong> derivada de las
     * claves de {@link #preciosModulos}: nombres canonicos normalizados
     * (minusculas, sin espacios extra), sin duplicados. Nunca es {@code null}: la
     * columna V64 tiene {@code DEFAULT '[]'}.
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

    protected PaqueteSuscripcion() {
        // Requerido por JPA.
    }

    /**
     * Crea un nuevo Paquete de Suscripcion (Req 3.1) con su Giro, su moneda de
     * cotizacion, su limite de Usuarios, su precio por modulo, su duracion de
     * contrato y su configuracion de prueba.
     *
     * @param nombre              nombre del Paquete (unico por
     *                            {@code uq_paquete_suscripcion_nombre}, V64);
     *                            obligatorio.
     * @param maxUsuarios         numero maximo de Usuarios; debe ser {@code >= 0}
     *                            (CHECK {@code ck_paquete_max_usuarios} en V64).
     * @param giroId              Giro al que pertenece el Paquete;
     *                            <strong>obligatorio</strong>.
     * @param monedaCodigo        codigo ISO 4217 de la moneda; obligatorio. Se
     *                            normaliza a mayusculas.
     * @param preciosPorModulo    mapa {@code clave -> precio} de los modulos; se
     *                            normalizan claves y precios. {@code null}
     *                            equivale a un mapa vacio (Paquete sin modulos).
     * @param duracionDias        duracion del contrato en dias; debe cumplir
     *                            {@code 0 < duracionDias <= 365} (Req 3.3, 3.4,
     *                            9.1).
     * @param admitePrueba        {@code true} si el Paquete ofrece prueba.
     * @param duracionPruebaMeses duracion de la prueba en meses; si
     *                            {@code admitePrueba} es {@code true} debe ser
     *                            {@code > 0} y su equivalente en dias no puede
     *                            exceder {@code duracionDias} (Req 3.5, 3.6). Si
     *                            {@code admitePrueba} es {@code false} se ignora.
     * @param actor               identificador de quien crea el Paquete
     *                            (super_admin).
     * @return el Paquete listo para persistir.
     * @throws ReglaNegocioException si el nombre es vacio, {@code maxUsuarios < 0},
     *                               el Giro es {@code null}, la moneda es invalida,
     *                               algun precio queda fuera de rango, la duracion
     *                               esta fuera de {@code (0, 365]}, o la
     *                               configuracion de prueba es incoherente.
     */
    public static PaqueteSuscripcion crear(String nombre, int maxUsuarios, UUID giroId, String monedaCodigo,
                                           Map<String, BigDecimal> preciosPorModulo, int duracionDias,
                                           boolean admitePrueba, Integer duracionPruebaMeses, String actor) {
        PaqueteSuscripcion paquete = new PaqueteSuscripcion();
        paquete.id = UUID.randomUUID();
        paquete.nombre = requerirNombre(nombre);
        paquete.maxUsuarios = requerirMaxUsuarios(maxUsuarios);
        paquete.giroId = requerirGiro(giroId);
        paquete.monedaCodigo = MonetizacionValidaciones.normalizarCodigoMoneda(monedaCodigo);
        paquete.aplicarPrecios(preciosPorModulo);
        paquete.aplicarDuracionYPrueba(duracionDias, admitePrueba, duracionPruebaMeses);
        paquete.createdBy = actor;
        paquete.updatedBy = actor;
        return paquete;
    }

    /**
     * Actualiza un Paquete de Suscripcion existente (Req 3.1): nombre, Giro,
     * moneda, limite de Usuarios, precio por modulo, duracion de contrato y
     * configuracion de prueba.
     *
     * @param nombre              nuevo nombre; obligatorio.
     * @param maxUsuarios         nuevo maximo de Usuarios; {@code >= 0}.
     * @param giroId              nuevo Giro; <strong>obligatorio</strong>.
     * @param monedaCodigo        nueva moneda; obligatoria (ISO 4217).
     * @param preciosPorModulo    nuevo mapa {@code clave -> precio}; se normaliza.
     * @param duracionDias        nueva duracion del contrato en dias;
     *                            {@code 0 < duracionDias <= 365} (Req 3.3, 3.4,
     *                            9.1).
     * @param admitePrueba        {@code true} si el Paquete ofrece prueba.
     * @param duracionPruebaMeses nueva duracion de la prueba en meses; sujeta a
     *                            las mismas reglas que en {@link #crear} (Req 3.5,
     *                            3.6).
     * @param actor               identificador de quien actualiza el Paquete
     *                            (super_admin).
     * @throws ReglaNegocioException si el nombre es vacio, {@code maxUsuarios < 0},
     *                               el Giro es {@code null}, la moneda es invalida,
     *                               algun precio queda fuera de rango, la duracion
     *                               esta fuera de {@code (0, 365]}, o la
     *                               configuracion de prueba es incoherente.
     */
    public void actualizar(String nombre, int maxUsuarios, UUID giroId, String monedaCodigo,
                           Map<String, BigDecimal> preciosPorModulo, int duracionDias,
                           boolean admitePrueba, Integer duracionPruebaMeses, String actor) {
        this.nombre = requerirNombre(nombre);
        this.maxUsuarios = requerirMaxUsuarios(maxUsuarios);
        this.giroId = requerirGiro(giroId);
        this.monedaCodigo = MonetizacionValidaciones.normalizarCodigoMoneda(monedaCodigo);
        aplicarPrecios(preciosPorModulo);
        aplicarDuracionYPrueba(duracionDias, admitePrueba, duracionPruebaMeses);
        this.updatedBy = actor;
    }

    /**
     * Indica si un modulo esta habilitado por el Paquete. La comparacion es
     * insensible a mayusculas/minusculas y a espacios sobrantes.
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
            throw new ReglaNegocioException("El nombre de la Suscripcion es obligatorio");
        }
        return nombre.strip();
    }

    private static int requerirMaxUsuarios(int maxUsuarios) {
        if (maxUsuarios < 0) {
            throw new ReglaNegocioException("max_usuarios de la Suscripcion no puede ser negativo");
        }
        return maxUsuarios;
    }

    private static UUID requerirGiro(UUID giroId) {
        if (giroId == null) {
            throw new ReglaNegocioException("El Giro de la Suscripcion es obligatorio");
        }
        return giroId;
    }

    /**
     * Valida y aplica la duracion del contrato y la configuracion de prueba.
     *
     * <p>Reglas de dominio:</p>
     * <ul>
     *   <li>{@code 0 < duracionDias <= 365} (Req 3.3, 3.4, 9.1); un contrato mayor
     *       a un año debe registrarse como Plan.</li>
     *   <li>Si {@code admitePrueba}, entonces {@code duracionPruebaMeses != null &&
     *       duracionPruebaMeses > 0} (Req 3.5).</li>
     *   <li>Si {@code admitePrueba}, la prueba expresada en dias
     *       ({@code duracionPruebaMeses * 30}, aproximando 30 dias/mes) no puede
     *       exceder {@code duracionDias} (Req 3.6).</li>
     *   <li>Si NO {@code admitePrueba}, {@code duracionPruebaMeses} se ignora y
     *       queda {@code null}.</li>
     * </ul>
     *
     * @param duracionDias        duracion del contrato en dias.
     * @param admitePrueba        si el Paquete ofrece prueba.
     * @param duracionPruebaMeses duracion de la prueba en meses (relevante solo si
     *                            {@code admitePrueba}).
     * @throws ReglaNegocioException si alguna regla anterior se incumple.
     */
    private void aplicarDuracionYPrueba(int duracionDias, boolean admitePrueba, Integer duracionPruebaMeses) {
        if (duracionDias <= 0 || duracionDias > DURACION_MAXIMA_DIAS) {
            throw new ReglaNegocioException(
                    "La duración de una Suscripción debe estar entre 1 y 365 días "
                            + "(un contrato mayor a un año debe registrarse como Plan).");
        }
        this.duracionDias = duracionDias;
        this.admitePrueba = admitePrueba;
        if (admitePrueba) {
            if (duracionPruebaMeses == null || duracionPruebaMeses <= 0) {
                throw new ReglaNegocioException("La duración de la prueba debe ser mayor a 0 meses.");
            }
            if (duracionPruebaMeses * DIAS_POR_MES > duracionDias) {
                throw new ReglaNegocioException("La duración de la prueba no puede exceder la duración del contrato.");
            }
            this.duracionPruebaMeses = duracionPruebaMeses;
        } else {
            this.duracionPruebaMeses = null;
        }
    }

    /**
     * Normaliza y aplica el mapa de precios por modulo, y deriva de sus claves la
     * lista autoritativa {@code modulos_habilitados}. Descarta claves nulas/vacias,
     * normaliza cada clave (recorte + minusculas) y valida cada precio via
     * {@link MonetizacionValidaciones#validarPrecio(BigDecimal)} (escala 2, rango
     * permitido). Preserva el orden de insercion y deduplica por clave.
     *
     * <p><strong>Dependencia de modulos (Req 7, 8).</strong> Tras construir el
     * mapa normalizado, se agregan los modulos <em>requeridos</em> declarados en
     * {@link CatalogoDependenciasModulos}: por ejemplo, si el Paquete incluye
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
        // Dependencia de modulos (Req 7.1-7.4, 8.1): agregar los requeridos ausentes
        // con precio 0.00. Se deriva del catalogo (fuente unica) iterando sobre las
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
     * @return la duracion del contrato de este Paquete en dias
     *         ({@code 0 < duracionDias <= 365}).
     */
    public int getDuracionDias() {
        return duracionDias;
    }

    /**
     * @return {@code true} si el Paquete ofrece un periodo de prueba.
     */
    public boolean isAdmitePrueba() {
        return admitePrueba;
    }

    /**
     * @return la duracion de la prueba en meses cuando el Paquete admite prueba
     *         ({@code > 0}); {@code null} si no admite prueba.
     */
    public Integer getDuracionPruebaMeses() {
        return duracionPruebaMeses;
    }

    /**
     * @return el Giro (vertical) al que pertenece el Paquete, o {@code null} en
     *         filas legado sin Giro definido.
     */
    public UUID getGiroId() {
        return giroId;
    }

    /**
     * @return el codigo ISO 4217 de la moneda de cotizacion del Paquete, o
     *         {@code null} en filas legado.
     */
    public String getMonedaCodigo() {
        return monedaCodigo;
    }

    /**
     * @return el precio por modulo del Paquete (copia inmutable {@code clave ->
     *         precio}, escala 2). Nunca {@code null} (puede estar vacio).
     */
    public Map<String, BigDecimal> getPreciosModulos() {
        return Map.copyOf(preciosModulos);
    }

    /**
     * @return el total del Paquete: suma de los precios de sus modulos, con escala
     *         monetaria 2. {@code 0.00} si el Paquete no tiene precios.
     */
    public BigDecimal getTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal precio : preciosModulos.values()) {
            total = total.add(precio);
        }
        return total.setScale(MonetizacionValidaciones.ESCALA_MONETARIA);
    }

    /**
     * @return los modulos habilitados por el Paquete (copia inmutable de nombres
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
