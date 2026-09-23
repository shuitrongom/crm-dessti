package com.dessti.crm.platform.empresas;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Estado del ciclo de vida de una {@link Suscripcion} (Req 25.2),
 * correspondiente a la columna {@code suscripcion.estado}, cuyo CHECK
 * {@code ck_suscripcion_estado} fue ampliado en la migracion V64 y admite
 * exactamente los cinco valores {@code 'activa'}, {@code 'en_prueba'},
 * {@code 'suspendida'}, {@code 'cancelada'} y {@code 'vencida'} (partiendo del
 * conjunto {@code 'activa'}/{@code 'suspendida'}/{@code 'cancelada'} definido
 * originalmente en la migracion V1).
 *
 * <p>El valor persistido en la base de datos es la etiqueta en minusculas
 * ({@link #valorBd()}), no el nombre de la constante Java. El
 * {@link EstadoSuscripcionConverter} realiza la traduccion bidireccional para
 * que el mapeo coincida <em>exactamente</em> con el CHECK ampliado en V64 y un
 * arranque con {@code ddl-auto=validate} valide sin conflictos. Se sigue el
 * mismo patron que {@link EstadoEmpresa} para mantener la coherencia de estilo
 * en el modulo de plataforma.</p>
 *
 * <ul>
 *   <li>{@link #ACTIVA}: Suscripcion vigente; la Empresa opera con su Plan.</li>
 *   <li>{@link #EN_PRUEBA}: Contrato de suscripcion en periodo de prueba; da acceso.</li>
 *   <li>{@link #SUSPENDIDA}: Suscripcion suspendida por el {@code super_admin}.</li>
 *   <li>{@link #CANCELADA}: Suscripcion cancelada (estado final).</li>
 *   <li>{@link #VENCIDA}: Contrato vencido; no da acceso.</li>
 * </ul>
 */
public enum EstadoSuscripcion {

    /** Suscripcion vigente; la Empresa opera con su Plan. */
    ACTIVA("activa"),

    /**
     * Contrato de suscripcion en periodo de prueba; otorga acceso a la Empresa.
     * Etiqueta {@code 'en_prueba'} admitida por el CHECK {@code ck_suscripcion_estado}
     * ampliado en la migracion V64.
     */
    EN_PRUEBA("en_prueba"),

    /** Suscripcion suspendida por el {@code super_admin}. */
    SUSPENDIDA("suspendida"),

    /** Suscripcion cancelada (estado final). */
    CANCELADA("cancelada"),

    /**
     * Contrato vencido; no otorga acceso. Etiqueta {@code 'vencida'} admitida por
     * el CHECK {@code ck_suscripcion_estado} ampliado en la migracion V64. El flujo
     * normal trata el vencimiento como estado <em>derivado</em> (comparando la
     * vigencia con la fecha actual); esta constante existe por compatibilidad y
     * para permitir registrar explicitamente un contrato vencido.
     */
    VENCIDA("vencida");

    private final String valorBd;

    EstadoSuscripcion(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta canonica en minusculas del estado. Es a la vez el valor persistido
     * en {@code suscripcion.estado} (tal como lo exige el CHECK ampliado en la
     * migracion V64) y el valor de representacion JSON hacia el frontend.
     *
     * <p>La anotacion {@link JsonValue} hace que Jackson serialice el enum como
     * esta etiqueta ({@code "activa"}/{@code "en_prueba"}/{@code "suspendida"}/
     * {@code "cancelada"}/{@code "vencida"}) en lugar del nombre de la constante
     * Java ({@code ACTIVA}), de modo que el contrato JSON coincida con la etiqueta
     * de base de datos y con el contrato del frontend (minusculas). No altera el
     * {@link EstadoSuscripcionConverter} ni la semantica de persistencia.</p>
     *
     * @return la etiqueta canonica en minusculas del estado.
     */
    @JsonValue
    public String valorBd() {
        return valorBd;
    }

    /**
     * Fabrica de deserializacion JSON: resuelve el enum a partir de su etiqueta
     * canonica en minusculas, tolerando mayusculas/minusculas y espacios (delega
     * en {@link #desdeValorBd(String)}). Permite aceptar tanto {@code "activa"}
     * como {@code "ACTIVA"} en las peticiones entrantes por robustez.
     *
     * @param valor etiqueta del estado recibida en el JSON.
     * @return el {@link EstadoSuscripcion} correspondiente.
     */
    @JsonCreator
    public static EstadoSuscripcion desdeJson(String valor) {
        return desdeValorBd(valor);
    }

    /**
     * Resuelve el enum a partir de la etiqueta persistida en la base de datos.
     *
     * @param valor etiqueta persistida (por ejemplo {@code "activa"}); admite
     *              espacios y mayusculas/minusculas por robustez.
     * @return el {@link EstadoSuscripcion} correspondiente.
     * @throws IllegalArgumentException si el valor no corresponde a ningun estado.
     */
    public static EstadoSuscripcion desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Suscripcion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoSuscripcion estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Suscripcion desconocido: '" + valor + "'");
    }
}
