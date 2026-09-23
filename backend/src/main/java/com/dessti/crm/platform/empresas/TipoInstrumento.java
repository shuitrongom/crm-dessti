package com.dessti.crm.platform.empresas;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Tipo de instrumento de contratacion que respalda una {@link Suscripcion} (el
 * "Contrato"), correspondiente a la columna {@code suscripcion.tipo_instrumento}
 * introducida en la migracion V64, cuyo CHECK admite exactamente las dos
 * etiquetas {@code 'plan'} y {@code 'suscripcion'}.
 *
 * <p>Determina que referencia lleva el Contrato bajo la invariante XOR
 * (exactamente uno no nulo): un {@link #PLAN} referencia {@code plan_id}, y una
 * {@link #SUSCRIPCION} referencia {@code paquete_suscripcion_id}.</p>
 *
 * <p>El valor persistido en la base de datos es la etiqueta en minusculas
 * ({@link #valorBd()}), no el nombre de la constante Java. El
 * {@link TipoInstrumentoConverter} realiza la traduccion bidireccional para que
 * el mapeo coincida <em>exactamente</em> con el CHECK de la migracion V64 y un
 * arranque con {@code ddl-auto=validate} valide sin conflictos. Se sigue el
 * mismo patron que {@link EstadoSuscripcion} para mantener la coherencia de
 * estilo en el modulo de plataforma.</p>
 *
 * <ul>
 *   <li>{@link #PLAN}: el Contrato referencia un {@link Plan} del catalogo.</li>
 *   <li>{@link #SUSCRIPCION}: el Contrato referencia un paquete de suscripcion
 *       del catalogo.</li>
 * </ul>
 */
public enum TipoInstrumento {

    /** El Contrato referencia un {@link Plan} del catalogo (via {@code plan_id}). */
    PLAN("plan"),

    /**
     * El Contrato referencia un paquete de suscripcion del catalogo (via
     * {@code paquete_suscripcion_id}).
     */
    SUSCRIPCION("suscripcion");

    private final String valorBd;

    TipoInstrumento(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta canonica en minusculas del tipo de instrumento. Es a la vez el
     * valor persistido en {@code suscripcion.tipo_instrumento} (tal como lo exige
     * el CHECK de la migracion V64) y el valor de representacion JSON hacia el
     * frontend.
     *
     * <p>La anotacion {@link JsonValue} hace que Jackson serialice el enum como
     * esta etiqueta ({@code "plan"}/{@code "suscripcion"}) en lugar del nombre de
     * la constante Java ({@code PLAN}), de modo que el contrato JSON coincida con
     * la etiqueta de base de datos y con el contrato del frontend (minusculas).
     * No altera el {@link TipoInstrumentoConverter} ni la semantica de
     * persistencia.</p>
     *
     * @return la etiqueta canonica en minusculas del tipo de instrumento.
     */
    @JsonValue
    public String valorBd() {
        return valorBd;
    }

    /**
     * Fabrica de deserializacion JSON: resuelve el enum a partir de su etiqueta
     * canonica en minusculas, tolerando mayusculas/minusculas y espacios (delega
     * en {@link #desdeValorBd(String)}). Permite aceptar tanto {@code "plan"}
     * como {@code "PLAN"} en las peticiones entrantes por robustez.
     *
     * @param valor etiqueta del tipo de instrumento recibida en el JSON.
     * @return el {@link TipoInstrumento} correspondiente.
     */
    @JsonCreator
    public static TipoInstrumento desdeJson(String valor) {
        return desdeValorBd(valor);
    }

    /**
     * Resuelve el enum a partir de la etiqueta persistida en la base de datos.
     *
     * @param valor etiqueta persistida (por ejemplo {@code "plan"}); admite
     *              espacios y mayusculas/minusculas por robustez.
     * @return el {@link TipoInstrumento} correspondiente.
     * @throws IllegalArgumentException si el valor no corresponde a ningun tipo.
     */
    public static TipoInstrumento desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException(
                    "El tipo de instrumento de la Suscripcion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (TipoInstrumento tipo : values()) {
            if (tipo.valorBd.equals(normalizado)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException(
                "Tipo de instrumento de Suscripcion desconocido: '" + valor + "'");
    }
}
