package com.dessti.crm.platform.empresas;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA de la {@code empresa} (Tenant), mapeada sobre la tabla
 * {@code empresa} definida en la migracion V1 (Req 24, 23).
 *
 * <p><strong>La Empresa ES el tenant:</strong> su PK {@code id} es el
 * {@code tenant_id} que segmenta todos los datos de negocio (Req 23.1). Por eso
 * <em>no</em> hereda de {@code TenantScopedEntity} ni activa el filtro global de
 * Hibernate por tenant: el {@code super_admin} debe poder crear y listar
 * Empresas (Req 24.1, 24.5), operaciones imposibles bajo dicho filtro. La tabla
 * {@code empresa} tampoco lleva politicas RLS (decision documentada en V1/V2).</p>
 *
 * <p>El mapeo de columnas (nombres, nulabilidad, tipos y valores del CHECK de
 * {@code estado}) coincide <em>exactamente</em> con V1 para que un arranque con
 * {@code ddl-auto=validate} valide sin conflictos. El {@code estado} se persiste
 * mediante {@link EstadoEmpresaConverter} como la etiqueta en minusculas.</p>
 *
 * <p><strong>Alcance:</strong> esta entidad modela el alta y los cambios de
 * estado (activar/suspender, tarea 14.1), la personalizacion de marca
 * (branding: nombre visible, logotipo y color primario de marca, Req 26/6,
 * tarea 14.3) mediante
 * {@link #actualizarBranding(String, String, String, String)}, y el ciclo de vida de
 * <strong>offboarding</strong> del tenant (Req 69, tarea 14.4) mediante
 * {@link #cancelar(Instant, Duration, String)}: la cancelacion es una
 * transicion <em>terminal</em> (one-way) que fija {@code estado=CANCELADA},
 * {@code fecha_cancelacion} y {@code fin_periodo_gracia}. La reactivacion de una
 * Empresa cancelada esta <strong>fuera de alcance</strong> (activar/suspender la
 * rechazan) y sustenta la eliminacion definitiva tras el Periodo_Gracia; la
 * orquestacion (exportar/eliminar) vive en {@code ServicioOffboarding}.</p>
 */
@Entity
@Table(name = "empresa")
public class Empresa {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Identificador fiscal (RFC). Se normaliza a mayusculas al crear la Empresa. */
    @Column(name = "rfc", nullable = false)
    private String rfc;

    /**
     * Giro (vertical de negocio) al que pertenece la Empresa; referencia a
     * {@code giro.id} (FK anadida en la migracion V51, Req 2.3). Se modela como
     * un identificador {@code UUID} plano (sin {@code @ManyToOne}) para mantener
     * desacoplados los agregados de plataforma, replicando el estilo de
     * {@code Suscripcion.planId} (referencia a {@code plan.id}). Es
     * <strong>obligatorio</strong> (Req 2.1) y {@code updatable} porque
     * {@link #cambiarGiro(UUID, String)} lo modifica (Req 3.1).
     */
    @Column(name = "giro_id", nullable = false)
    private UUID giroId;

    @Convert(converter = EstadoEmpresaConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoEmpresa estado;

    /** Nombre visible de branding (Req 26); {@code null} si no se personaliza. */
    @Column(name = "branding_nombre_visible")
    private String brandingNombreVisible;

    /** Logotipo de branding como URL o data URI (Req 26); {@code null} si no se personaliza. */
    @Column(name = "branding_logo")
    private String brandingLogo;

    /**
     * Color primario de marca de la Empresa en formato hexadecimal
     * {@code #RRGGBB} (Req 6.1, 6.3). Es la semilla a partir de la cual el
     * frontend deriva la paleta tematizada del tenant. Es
     * <strong>opcional</strong>: un valor {@code null} indica "sin color de
     * marca", en cuyo caso la interfaz conserva el tema corporativo por defecto
     * (Req 9.1). Se persiste normalizado en minusculas y validado contra el
     * patron {@code ^#[0-9a-fA-F]{6}$}, alineado con el {@code CHECK} de la
     * columna {@code branding_color_primario VARCHAR(7)} de la migracion V67.
     */
    @Column(name = "branding_color_primario")
    private String brandingColorPrimario;

    // ------------------------------------------------------------------
    // Datos descriptivos / de contacto opcionales de la Empresa (V54, Req 24).
    // Todos NULLABLE: son datos de ficha de plataforma, no obligatorios.
    // ------------------------------------------------------------------

    /** Nombre comercial (marca) de la Empresa; {@code null} si no se registra. */
    @Column(name = "nombre_comercial")
    private String nombreComercial;

    /** Correo de contacto de la Empresa (normalizado a minusculas); {@code null} si no se registra. */
    @Column(name = "email_contacto")
    private String emailContacto;

    /** Telefono de contacto de la Empresa; {@code null} si no se registra. */
    @Column(name = "telefono")
    private String telefono;

    /** Sitio web de la Empresa; {@code null} si no se registra. */
    @Column(name = "sitio_web")
    private String sitioWeb;

    /** Calle y numero de la direccion de la Empresa; {@code null} si no se registra. */
    @Column(name = "direccion_calle")
    private String direccionCalle;

    /** Ciudad de la direccion de la Empresa; {@code null} si no se registra. */
    @Column(name = "direccion_ciudad")
    private String direccionCiudad;

    /** Estado/provincia de la direccion de la Empresa; {@code null} si no se registra. */
    @Column(name = "direccion_estado")
    private String direccionEstado;

    /** Codigo postal de la direccion de la Empresa; {@code null} si no se registra. */
    @Column(name = "direccion_cp")
    private String direccionCp;

    /** Pais de la direccion de la Empresa; {@code null} si no se registra. */
    @Column(name = "direccion_pais")
    private String direccionPais;

    /** Notas libres del super_admin sobre la Empresa; {@code null} si no se registran. */
    @Column(name = "notas")
    private String notas;

    /** Fecha de cancelacion (offboarding, Req 69, tarea 14.4); solo mapeo aqui. */
    @Column(name = "fecha_cancelacion")
    private Instant fechaCancelacion;

    /** Fin del periodo de gracia (offboarding, Req 69, tarea 14.4); solo mapeo aqui. */
    @Column(name = "fin_periodo_gracia")
    private Instant finPeriodoGracia;

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

    protected Empresa() {
        // Requerido por JPA.
    }

    /**
     * Crea una Empresa nueva en estado {@link EstadoEmpresa#ACTIVA} con un
     * {@code tenant_id} unico (su propia PK, Req 24.2) y ligada a un Giro
     * obligatorio (Req 2.1, 2.3).
     *
     * <p>La regla de dominio exige que el {@code giroId} este presente
     * (obligatoriedad, Req 2.1). La comprobacion de que el Giro <em>existe</em> y
     * esta <em>activo</em> es responsabilidad del servicio de aplicacion
     * ({@code ServicioEmpresas.crearEmpresa}, tarea 4.3), no del dominio.</p>
     *
     * @param nombre nombre de la Empresa; obligatorio.
     * @param rfc    identificador fiscal; obligatorio (se normaliza a mayusculas).
     * @param giroId Giro (vertical) al que pertenece la Empresa; obligatorio
     *               (Req 2.1). Su existencia/estado activo los valida el servicio
     *               (tarea 4.3).
     * @param actor  identificador de quien crea la Empresa (super_admin), para
     *               las columnas de auditoria {@code created_by}/{@code updated_by}.
     * @return la Empresa lista para persistir.
     * @throws ReglaNegocioException si el nombre o el RFC son vacios, o si el
     *                               {@code giroId} es nulo (Req 2.1).
     */
    public static Empresa crear(String nombre, String rfc, UUID giroId, String actor) {
        Empresa empresa = new Empresa();
        empresa.id = UUID.randomUUID();
        empresa.nombre = normalizarNombre(nombre);
        empresa.rfc = normalizarRfc(rfc);
        empresa.giroId = exigirGiroId(giroId);
        empresa.estado = EstadoEmpresa.ACTIVA;
        empresa.createdBy = actor;
        empresa.updatedBy = actor;
        return empresa;
    }

    /**
     * Cambia el Giro (vertical) de la Empresa (Req 3.1). Es una operacion de
     * dominio pura: valida que el nuevo Giro sea obligatorio, reasigna el
     * {@code giro_id} y actualiza {@code updated_by} para la trazabilidad.
     *
     * <p><strong>Alcance:</strong> el dominio NO decide si el cambio es
     * admisible en funcion de si la Empresa ya tiene datos del vertical actual;
     * esa regla (Req 3.2/3.3: rechazar el cambio si existen datos del vertical y
     * auditar el giro anterior/nuevo) la aplica el servicio de aplicacion
     * ({@code ServicioEmpresas.cambiarGiro}, tarea 4.4) apoyandose en los puertos
     * de existencia de datos del vertical. Aqui solo se garantiza la
     * obligatoriedad del nuevo Giro (Req 2.1).</p>
     *
     * @param nuevoGiroId nuevo Giro al que se reasigna la Empresa; obligatorio
     *                    (Req 2.1).
     * @param actor       identificador de quien realiza la operacion
     *                    (super_admin), para {@code updated_by}.
     * @throws ReglaNegocioException si {@code nuevoGiroId} es nulo (Req 2.1).
     */
    public void cambiarGiro(UUID nuevoGiroId, String actor) {
        this.giroId = exigirGiroId(nuevoGiroId);
        this.updatedBy = actor;
    }

    private static UUID exigirGiroId(UUID giroId) {
        if (giroId == null) {
            throw new ReglaNegocioException("El Giro de la Empresa es obligatorio.");
        }
        return giroId;
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

    /**
     * Activa la Empresa (Req 24.1). Es idempotente respecto al estado
     * {@link EstadoEmpresa#ACTIVA}. No es aplicable a una Empresa cancelada, cuyo
     * ciclo de vida corresponde al offboarding (tarea 14.4).
     *
     * @param actor identificador de quien realiza la operacion (super_admin).
     * @throws ReglaNegocioException si la Empresa esta cancelada.
     */
    public void activar(String actor) {
        if (estado == EstadoEmpresa.CANCELADA) {
            throw new ReglaNegocioException(
                    "No se puede activar una Empresa cancelada.");
        }
        this.estado = EstadoEmpresa.ACTIVA;
        this.updatedBy = actor;
    }

    /**
     * Suspende la Empresa (Req 24.4). Mientras dure la suspension, sus Usuarios
     * no pueden iniciar sesion (lo aplica {@code ServicioAutenticacion} via
     * {@link EstadoEmpresaPort}). Es idempotente respecto al estado
     * {@link EstadoEmpresa#SUSPENDIDA}. No es aplicable a una Empresa cancelada.
     *
     * @param actor identificador de quien realiza la operacion (super_admin).
     * @throws ReglaNegocioException si la Empresa esta cancelada.
     */
    public void suspender(String actor) {
        if (estado == EstadoEmpresa.CANCELADA) {
            throw new ReglaNegocioException(
                    "No se puede suspender una Empresa cancelada.");
        }
        this.estado = EstadoEmpresa.SUSPENDIDA;
        this.updatedBy = actor;
    }

    /**
     * Cancela la Empresa e inicia su Periodo_Gracia de offboarding (Req 69.2).
     * Es una transicion <strong>terminal</strong> (one-way): fija el estado a
     * {@link EstadoEmpresa#CANCELADA}, registra el instante de cancelacion
     * ({@code fecha_cancelacion}) y calcula el fin del Periodo_Gracia
     * ({@code fin_periodo_gracia = ahora + periodoGracia}). Durante ese periodo
     * los datos de negocio se conservan y el acceso queda restringido conforme
     * al estado de la Empresa (Req 24.4/69.2); tras su expiracion, el
     * {@code super_admin} puede ejecutar la eliminacion definitiva (Req 69.3).
     *
     * <p>La operacion es <strong>idempotente</strong> respecto a una Empresa ya
     * cancelada: si ya lo estaba, no se reinicia el Periodo_Gracia (se conserva
     * la {@code fecha_cancelacion}/{@code fin_periodo_gracia} originales) para no
     * extender indebidamente la retencion; solo se actualiza {@code updated_by}.
     * La reactivacion de una Empresa cancelada esta fuera de alcance.</p>
     *
     * @param ahora         instante de la cancelacion en UTC; obligatorio.
     * @param periodoGracia duracion del Periodo_Gracia configurable antes de
     *                      cualquier eliminacion; obligatorio y no negativo.
     * @param actor         identificador de quien cancela (super_admin), para
     *                      {@code updated_by}.
     * @throws ReglaNegocioException si {@code ahora} es nulo, o si
     *                               {@code periodoGracia} es nulo o negativo.
     */
    public void cancelar(Instant ahora, Duration periodoGracia, String actor) {
        if (ahora == null) {
            throw new ReglaNegocioException("El instante de cancelacion es obligatorio.");
        }
        if (periodoGracia == null || periodoGracia.isNegative()) {
            throw new ReglaNegocioException(
                    "El Periodo_Gracia debe ser una duracion no negativa.");
        }
        this.updatedBy = actor;
        if (estado == EstadoEmpresa.CANCELADA) {
            // Idempotente: no se reinicia el Periodo_Gracia ya en curso.
            return;
        }
        this.estado = EstadoEmpresa.CANCELADA;
        this.fechaCancelacion = ahora;
        this.finPeriodoGracia = ahora.plus(periodoGracia);
    }

    /**
     * Indica si la Empresa esta en estado {@link EstadoEmpresa#CANCELADA}
     * (offboarding iniciado, Req 69.2/69.3).
     *
     * @return {@code true} si la Empresa esta cancelada.
     */
    public boolean estaCancelada() {
        return estado == EstadoEmpresa.CANCELADA;
    }

    /**
     * Indica si el Periodo_Gracia de la Empresa ya expiro respecto al instante
     * indicado (Req 69.3), condicion necesaria para la eliminacion definitiva.
     *
     * <p>Devuelve {@code true} unicamente cuando la Empresa esta cancelada y
     * existe un {@code fin_periodo_gracia} que <em>no</em> es posterior a
     * {@code ahora} (es decir, {@code fin_periodo_gracia <= ahora}). Para una
     * Empresa no cancelada, o sin {@code fin_periodo_gracia}, devuelve
     * {@code false} (fail-safe: no se habilita la eliminacion).</p>
     *
     * @param ahora instante de referencia en UTC; obligatorio.
     * @return {@code true} si el Periodo_Gracia ya expiro; {@code false} en otro caso.
     * @throws ReglaNegocioException si {@code ahora} es nulo.
     */
    public boolean periodoGraciaExpirado(Instant ahora) {
        if (ahora == null) {
            throw new ReglaNegocioException("El instante de referencia es obligatorio.");
        }
        if (estado != EstadoEmpresa.CANCELADA || finPeriodoGracia == null) {
            return false;
        }
        return !finPeriodoGracia.isAfter(ahora);
    }

    /**
     * Longitud maxima aceptada para el logotipo de branding (Req 26.1). La
     * columna {@code branding_logo} es {@code TEXT} en V1 y el logotipo se
     * almacena como una <em>referencia</em> (URL) o un pequeno {@code data URI}
     * en linea, no como un binario subido al Sistema. Se acota a 1 MiB de texto
     * (1.048.576 caracteres) como salvaguarda contra cargas abusivas que
     * degradarian el almacenamiento y la interfaz; un valor mas largo se
     * rechaza con {@link ReglaNegocioException} (HTTP 422). No se implementa
     * subida ni almacenamiento de archivos en esta tarea.
     */
    static final int LONGITUD_MAXIMA_LOGO = 1_048_576;

    /**
     * Longitud maxima del nombre visible de branding, alineada con la columna
     * {@code branding_nombre_visible VARCHAR(200)} de V1 (Req 26.1).
     */
    static final int LONGITUD_MAXIMA_NOMBRE_VISIBLE = 200;

    /**
     * Patron de validacion del color primario de marca: exactamente un
     * {@code #} seguido de 6 digitos hexadecimales ({@code #RRGGBB}, Req 6.4).
     * Coincide con el {@code CHECK} de la columna {@code branding_color_primario}
     * de la migracion V67 y con la validacion del contrato REST
     * ({@code ActualizarBrandingRequest}, tarea 4.3).
     */
    static final String PATRON_COLOR_PRIMARIO = "^#[0-9a-fA-F]{6}$";

    /**
     * Aplica la personalizacion de marca (branding) de la Empresa: nombre
     * visible, logotipo y color primario de marca (Req 26.1, 6.3). Los tres
     * datos son <strong>opcionales</strong> (personalizacion): un valor
     * {@code null} o en blanco se interpreta como "sin personalizar" y se
     * almacena como {@code null}, permitiendo tanto establecer como limpiar cada
     * campo de forma independiente. El nombre visible se normaliza recortando
     * espacios; el logotipo se conserva tal cual (una URL o {@code data URI})
     * salvo el recorte de espacios envolventes; el color se recorta, se valida
     * contra el formato {@code #RRGGBB} y se normaliza a minusculas.
     *
     * <p>La operacion fija SIEMPRE los tres campos de marca a partir de los
     * argumentos recibidos (Req 6.7): al actualizar el color se preservan
     * explicitamente el nombre visible y el logotipo pasados, evitando efectos
     * colaterales. Actualiza {@code updated_by} para la trazabilidad de
     * auditoria (Req 26.3) y es aplicable con independencia del estado de la
     * Empresa, ya que la personalizacion no altera su ciclo de vida
     * (activar/suspender/cancelar).</p>
     *
     * @param nombreVisible nombre visible; {@code null}/blanco lo limpia. No
     *                      debe exceder {@value #LONGITUD_MAXIMA_NOMBRE_VISIBLE}
     *                      caracteres (tras recortar).
     * @param logo          logotipo como URL o {@code data URI}; {@code null}/
     *                      blanco lo limpia. No debe exceder
     *                      {@value #LONGITUD_MAXIMA_LOGO} caracteres (tras recortar).
     * @param colorPrimario color primario de marca en formato {@code #RRGGBB};
     *                      {@code null}/blanco lo limpia (tema corporativo). Se
     *                      normaliza a minusculas y debe cumplir el formato
     *                      hexadecimal (Req 6.4).
     * @param actor         identificador de quien realiza la operacion
     *                      ({@code admin_empresa}), para {@code updated_by}.
     * @throws ReglaNegocioException si el nombre visible o el logotipo exceden
     *                               su longitud maxima permitida, o si el color
     *                               no cumple el formato {@code #RRGGBB} (HTTP 422).
     */
    public void actualizarBranding(String nombreVisible, String logo, String colorPrimario, String actor) {
        this.brandingNombreVisible = normalizarBrandingNombreVisible(nombreVisible);
        this.brandingLogo = normalizarBrandingLogo(logo);
        this.brandingColorPrimario = normalizarBrandingColorPrimario(colorPrimario);
        this.updatedBy = actor;
    }

    private static String normalizarBrandingColorPrimario(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (!normalizado.matches(PATRON_COLOR_PRIMARIO)) {
            throw new ReglaNegocioException(
                    "El color de marca debe tener el formato #RRGGBB.");
        }
        return normalizado.toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizarBrandingNombreVisible(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOMBRE_VISIBLE) {
            throw new ReglaNegocioException(
                    "El nombre visible de branding no puede exceder "
                            + LONGITUD_MAXIMA_NOMBRE_VISIBLE + " caracteres.");
        }
        return normalizado;
    }

    private static String normalizarBrandingLogo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_LOGO) {
            throw new ReglaNegocioException(
                    "El logotipo de branding excede el tamano maximo permitido ("
                            + LONGITUD_MAXIMA_LOGO + " caracteres).");
        }
        return normalizado;
    }

    // ------------------------------------------------------------------
    // Datos descriptivos / de contacto opcionales (V54, Req 24).
    // Longitudes maximas alineadas EXACTAMENTE con las columnas de V54.
    // ------------------------------------------------------------------

    static final int LONGITUD_MAXIMA_NOMBRE_COMERCIAL = 200;
    static final int LONGITUD_MAXIMA_EMAIL_CONTACTO = 255;
    static final int LONGITUD_MAXIMA_TELEFONO = 40;
    static final int LONGITUD_MAXIMA_SITIO_WEB = 255;
    static final int LONGITUD_MAXIMA_DIRECCION_CALLE = 255;
    static final int LONGITUD_MAXIMA_DIRECCION_CIUDAD = 120;
    static final int LONGITUD_MAXIMA_DIRECCION_ESTADO = 120;
    static final int LONGITUD_MAXIMA_DIRECCION_CP = 12;
    static final int LONGITUD_MAXIMA_DIRECCION_PAIS = 80;

    /**
     * Asigna (o reemplaza) los datos DESCRIPTIVOS y de CONTACTO opcionales de la
     * Empresa a partir de un {@link DatosDescriptivosEmpresa} (Req 24). Todos los
     * campos son opcionales: cada valor {@code null} o en blanco se normaliza a
     * {@code null} (permite tanto establecer como limpiar cada campo). Las
     * cadenas se recortan; el correo se normaliza ademas a minusculas. Cada campo
     * respeta la longitud maxima de su columna (V54); un exceso se rechaza con
     * {@link ReglaNegocioException} (HTTP 422), replicando el patron de
     * {@link #actualizarBranding(String, String, String, String)}.
     *
     * <p>Si {@code datos} incluye un {@code logo} (URL o {@code data URI}), se fija
     * el logotipo de branding reutilizando la misma normalizacion/cota que
     * {@link #actualizarBranding(String, String, String, String)}, sin tocar el nombre
     * visible. Actualiza {@code updated_by} para la trazabilidad de auditoria.</p>
     *
     * @param datos portador de los datos descriptivos; si es {@code null} no se
     *              modifica nada.
     * @param actor identificador de quien realiza la operacion, para {@code updated_by}.
     * @throws ReglaNegocioException si algun campo excede su longitud maxima (HTTP 422).
     */
    public void asignarDatosDescriptivos(DatosDescriptivosEmpresa datos, String actor) {
        if (datos == null) {
            return;
        }
        this.nombreComercial = normalizarTexto(datos.nombreComercial(),
                LONGITUD_MAXIMA_NOMBRE_COMERCIAL, "nombre comercial");
        this.emailContacto = normalizarEmail(datos.emailContacto());
        this.telefono = normalizarTexto(datos.telefono(),
                LONGITUD_MAXIMA_TELEFONO, "telefono");
        this.sitioWeb = normalizarTexto(datos.sitioWeb(),
                LONGITUD_MAXIMA_SITIO_WEB, "sitio web");
        this.direccionCalle = normalizarTexto(datos.direccionCalle(),
                LONGITUD_MAXIMA_DIRECCION_CALLE, "calle de la direccion");
        this.direccionCiudad = normalizarTexto(datos.direccionCiudad(),
                LONGITUD_MAXIMA_DIRECCION_CIUDAD, "ciudad de la direccion");
        this.direccionEstado = normalizarTexto(datos.direccionEstado(),
                LONGITUD_MAXIMA_DIRECCION_ESTADO, "estado de la direccion");
        this.direccionCp = normalizarTexto(datos.direccionCp(),
                LONGITUD_MAXIMA_DIRECCION_CP, "codigo postal de la direccion");
        this.direccionPais = normalizarTexto(datos.direccionPais(),
                LONGITUD_MAXIMA_DIRECCION_PAIS, "pais de la direccion");
        // 'notas' se mapea a una columna TEXT sin cota de longitud fija.
        this.notas = (datos.notas() == null || datos.notas().isBlank())
                ? null
                : datos.notas().strip();
        // El logo opcional reutiliza la cota/normalizacion de branding, sin
        // alterar el nombre visible de branding ya existente.
        if (datos.logo() != null) {
            this.brandingLogo = normalizarBrandingLogo(datos.logo());
        }
        this.updatedBy = actor;
    }

    /**
     * Actualiza los datos de PLATAFORMA de la Empresa editables por el
     * {@code super_admin} (CHANGE 1): identidad ({@code nombre}), identificador
     * fiscal ({@code rfc}) y la ficha descriptiva/de contacto completa
     * ({@link DatosDescriptivosEmpresa}). NO altera el {@code estado} (tiene su
     * propio flujo: activar/suspender/cancelar), ni el {@code giro} (flujo
     * dedicado {@code cambiarGiro}, Req 3), ni el Plan/Suscripcion (flujos de
     * monetizacion): la reasignacion de esos agregados queda fuera de este metodo
     * por diseno.
     *
     * <p>El {@code nombre} es obligatorio (misma regla que el alta) y el
     * {@code rfc} se normaliza a mayusculas y se valida estructuralmente
     * reutilizando {@link RfcValidador#normalizarYValidar(String)} (el MISMO
     * validador del alta): un RFC mal formado se rechaza con
     * {@link ReglaNegocioException} (HTTP 422). La ficha descriptiva se aplica con
     * la misma semantica de {@link #asignarDatosDescriptivos(DatosDescriptivosEmpresa, String)}
     * (reemplazo completo: cada campo ausente/en blanco queda en {@code null}),
     * por lo que este metodo REEMPLAZA el conjunto descriptivo. Actualiza
     * {@code updated_by} para la trazabilidad de auditoria.</p>
     *
     * @param nombre nuevo nombre de la Empresa; obligatorio (no vacio tras recortar).
     * @param rfc    nuevo identificador fiscal; obligatorio (se normaliza/valida).
     * @param datos  ficha descriptiva/de contacto a aplicar; si es {@code null}
     *               no se tocan los campos descriptivos (solo nombre y rfc).
     * @param actor  identificador de quien realiza la operacion (super_admin).
     * @throws ReglaNegocioException si el nombre es vacio, el RFC es invalido, o
     *                               algun campo descriptivo excede su longitud (HTTP 422).
     */
    public void actualizarDatosPlataforma(String nombre, String rfc,
                                          DatosDescriptivosEmpresa datos, String actor) {
        this.nombre = normalizarNombre(nombre);
        this.rfc = normalizarRfc(rfc);
        if (datos != null) {
            asignarDatosDescriptivos(datos, actor);
        }
        this.updatedBy = actor;
    }

    /**
     * Actualiza el PERFIL de CONTACTO de la propia Empresa editable por su
     * {@code admin_empresa} (CHANGE 2): nombre, correo de contacto, telefono,
     * direccion desglosada y logotipo. A diferencia de
     * {@link #actualizarDatosPlataforma(String, String, DatosDescriptivosEmpresa, String)},
     * este metodo NO toca el identificador fiscal ({@code rfc}), el {@code giro},
     * el Plan ni el {@code estado}: son atributos de plataforma que un
     * {@code admin_empresa} no puede modificar.
     *
     * <p><strong>Alcance acotado y NO destructivo:</strong> solo se reemplazan
     * los campos de contacto que el {@code admin_empresa} gestiona; el
     * {@code nombre_comercial}, el {@code sitio_web} y las {@code notas} (que fija
     * el {@code super_admin} desde la ficha de plataforma) se <em>conservan</em>
     * intactos. Cada campo de contacto se normaliza y acota reutilizando la misma
     * logica que el alta (recorte, correo a minusculas, cotas de V54); un
     * {@code null}/blanco limpia el campo correspondiente. El {@code logo} se fija
     * reutilizando la cota/normalizacion del branding, sin alterar el nombre
     * visible. Actualiza {@code updated_by} para la trazabilidad de auditoria.</p>
     *
     * @param nombre          nuevo nombre de la Empresa; obligatorio (no vacio tras recortar).
     * @param emailContacto   correo de contacto; obligatorio a nivel web, aqui se
     *                        normaliza a minusculas ({@code null}/blanco lo limpia).
     * @param telefono        telefono de contacto; {@code null}/blanco lo limpia.
     * @param direccionCalle  calle y numero; {@code null}/blanco lo limpia.
     * @param direccionCiudad ciudad; {@code null}/blanco lo limpia.
     * @param direccionEstado estado/provincia; {@code null}/blanco lo limpia.
     * @param direccionCp     codigo postal; {@code null}/blanco lo limpia.
     * @param direccionPais   pais; {@code null}/blanco lo limpia.
     * @param logo            logotipo (URL o data URI); {@code null}/blanco lo limpia.
     * @param actor           identificador de quien realiza la operacion (admin_empresa).
     * @throws ReglaNegocioException si el nombre es vacio o algun campo excede su
     *                               longitud maxima (HTTP 422).
     */
    public void actualizarPerfilContacto(String nombre, String emailContacto, String telefono,
                                         String direccionCalle, String direccionCiudad,
                                         String direccionEstado, String direccionCp,
                                         String direccionPais, String logo, String actor) {
        this.nombre = normalizarNombre(nombre);
        this.emailContacto = normalizarEmail(emailContacto);
        this.telefono = normalizarTexto(telefono, LONGITUD_MAXIMA_TELEFONO, "telefono");
        this.direccionCalle = normalizarTexto(direccionCalle,
                LONGITUD_MAXIMA_DIRECCION_CALLE, "calle de la direccion");
        this.direccionCiudad = normalizarTexto(direccionCiudad,
                LONGITUD_MAXIMA_DIRECCION_CIUDAD, "ciudad de la direccion");
        this.direccionEstado = normalizarTexto(direccionEstado,
                LONGITUD_MAXIMA_DIRECCION_ESTADO, "estado de la direccion");
        this.direccionCp = normalizarTexto(direccionCp,
                LONGITUD_MAXIMA_DIRECCION_CP, "codigo postal de la direccion");
        this.direccionPais = normalizarTexto(direccionPais,
                LONGITUD_MAXIMA_DIRECCION_PAIS, "pais de la direccion");
        // El logo reutiliza la cota/normalizacion de branding, sin tocar el
        // nombre visible de branding ya existente.
        this.brandingLogo = normalizarBrandingLogo(logo);
        this.updatedBy = actor;
    }

    private static String normalizarTexto(String valor, int longitudMaxima, String etiqueta) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > longitudMaxima) {
            throw new ReglaNegocioException(
                    "El campo '" + etiqueta + "' no puede exceder "
                            + longitudMaxima + " caracteres.");
        }
        return normalizado;
    }

    private static String normalizarEmail(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip().toLowerCase(java.util.Locale.ROOT);
        if (normalizado.length() > LONGITUD_MAXIMA_EMAIL_CONTACTO) {
            throw new ReglaNegocioException(
                    "El campo 'correo de contacto' no puede exceder "
                            + LONGITUD_MAXIMA_EMAIL_CONTACTO + " caracteres.");
        }
        return normalizado;
    }

    private static String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El nombre de la Empresa es obligatorio.");
        }
        return valor.strip();
    }

    private static String normalizarRfc(String valor) {
        // Normaliza a mayusculas y valida la ESTRUCTURA del RFC mexicano (Req 24):
        // sustituye la antigua comprobacion de "solo no vacio" por normalizar +
        // regex, rechazando con 422 (ReglaNegocioException) un RFC mal formado.
        return RfcValidador.normalizarYValidar(valor);
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

    /**
     * Giro (vertical de negocio) al que pertenece la Empresa; referencia a
     * {@code giro.id} (Req 2.3).
     *
     * @return el identificador del Giro; nunca {@code null} en una Empresa creada
     *         via {@link #crear(String, String, UUID, String)}.
     */
    public UUID getGiroId() {
        return giroId;
    }

    public EstadoEmpresa getEstado() {
        return estado;
    }

    public String getBrandingNombreVisible() {
        return brandingNombreVisible;
    }

    public String getBrandingLogo() {
        return brandingLogo;
    }

    /**
     * Color primario de marca en formato {@code #RRGGBB} (Req 6.3);
     * {@code null} si la Empresa no personaliza color (tema corporativo).
     *
     * @return el color de marca normalizado en minusculas, o {@code null}.
     */
    public String getBrandingColorPrimario() {
        return brandingColorPrimario;
    }

    public String getNombreComercial() {
        return nombreComercial;
    }

    public String getEmailContacto() {
        return emailContacto;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getSitioWeb() {
        return sitioWeb;
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

    public Instant getFechaCancelacion() {
        return fechaCancelacion;
    }

    public Instant getFinPeriodoGracia() {
        return finPeriodoGracia;
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
