package com.dessti.crm.social.domain;

import java.util.Locale;

/**
 * Estado de un {@link ConsentimientoCanal} (Req 64.8, 64.9): {@link #OPT_IN}
 * (consentimiento otorgado) u {@link #OPT_OUT} (revocado). Sigue el patron de enum
 * con etiqueta de base de datos de {@code EstadoActivoFijo}.
 *
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'opt_in'} u {@code 'opt_out'}, tal como exige el
 * CHECK de la migracion V41.</p>
 */
public enum EstadoConsentimiento {

    /** Consentimiento otorgado: el destinatario acepta recibir mensajes (Req 64.9). */
    OPT_IN("opt_in"),

    /** Consentimiento revocado: el destinatario ya no acepta mensajes (Req 64.9). */
    OPT_OUT("opt_out");

    private final String valorBd;

    EstadoConsentimiento(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code consentimiento_canal.estado}, en minusculas
     * ASCII.
     *
     * @return la etiqueta de base de datos del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Indica si este estado representa un Opt_In vigente (consentimiento otorgado).
     *
     * @return {@code true} si es {@link #OPT_IN}.
     */
    public boolean esVigente() {
        return this == OPT_IN;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'opt_in'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoConsentimiento desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de consentimiento no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoConsentimiento estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de consentimiento desconocido: " + valor);
    }
}
