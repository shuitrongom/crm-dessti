package com.dessti.crm.vertical.anuncios.mantenimiento.domain;

import java.util.Locale;

/**
 * Tipo de un {@link ContratoMantenimiento} (Req 20.1). Sigue el mismo patron que
 * {@code TipoPermisoInstalacion}.
 *
 * <h2>Valores</h2>
 * <ul>
 *   <li>{@link #PREVENTIVO} — contrato de mantenimiento preventivo (programado);
 *       puede originar Tickets_Servicio automaticos (Req 20.2).</li>
 *   <li>{@link #CORRECTIVO} — contrato de mantenimiento correctivo (a demanda).</li>
 * </ul>
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'preventivo'} o {@code 'correctivo'}, tal como
 * exige el CHECK de la migracion V27. El {@link TipoContratoMantenimientoConverter}
 * traduce entre el enum y esta etiqueta.</p>
 */
public enum TipoContratoMantenimiento {

    /** Contrato de mantenimiento preventivo (programado). */
    PREVENTIVO("preventivo"),

    /** Contrato de mantenimiento correctivo (a demanda). */
    CORRECTIVO("correctivo");

    private final String valorBd;

    TipoContratoMantenimiento(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code contrato_mantenimiento.tipo}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V27.
     *
     * @return la etiqueta de base de datos del tipo.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el tipo a partir de su etiqueta de base de datos (inversa de
     * {@link #valorBd()}). La comparacion es insensible a mayusculas y recorta
     * espacios.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'preventivo'}).
     * @return el tipo correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun tipo conocido.
     */
    public static TipoContratoMantenimiento desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El tipo del Contrato_Mantenimiento no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (TipoContratoMantenimiento tipo : values()) {
            if (tipo.valorBd.equals(normalizado)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de Contrato_Mantenimiento desconocido: " + valor);
    }
}
