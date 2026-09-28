package com.dessti.crm.comercial.cotizacion.domain;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Tasa de IVA aplicable a una {@link PartidaCotizacion} en el desglose fiscal
 * mexicano (CFDI) (V80). Distingue explicitamente la tasa 0% del caso exento,
 * que en el CFDI son conceptos distintos aunque ambos no generen IVA.
 *
 * <h2>Tasas</h2>
 * <ul>
 *   <li>{@link #DIECISEIS} — 16%, tasa general del pais.</li>
 *   <li>{@link #OCHO} — 8%, tasa de la region fronteriza norte.</li>
 *   <li>{@link #CERO} — 0%, actos gravados a tasa cero (p. ej. ciertos
 *       alimentos/exportaciones); genera IVA de 0 pero es un acto gravado.</li>
 *   <li>{@link #EXENTO} — acto exento (no objeto de IVA); no genera IVA.</li>
 * </ul>
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII ({@link #valorBd()}):
 * {@code '16'}, {@code '8'}, {@code '0'}, {@code 'exento'}, respetando el CHECK
 * {@code ck_partida_tasa_iva} de V80. {@link TasaIvaConverter} traduce entre el
 * enum y esta etiqueta.</p>
 */
public enum TasaIva {

    /** Tasa general 16%. */
    DIECISEIS("16", new BigDecimal("0.16")),

    /** Tasa fronteriza 8%. */
    OCHO("8", new BigDecimal("0.08")),

    /** Tasa 0% (acto gravado a tasa cero). */
    CERO("0", BigDecimal.ZERO),

    /** Acto exento (no genera IVA). */
    EXENTO("exento", BigDecimal.ZERO);

    /** Tasa de IVA por defecto de una partida cuando no se indica: 16%. */
    public static final TasaIva POR_DEFECTO = DIECISEIS;

    private final String valorBd;
    private final BigDecimal factor;

    TasaIva(String valorBd, BigDecimal factor) {
        this.valorBd = valorBd;
        this.factor = factor;
    }

    /**
     * Etiqueta persistida en la columna {@code partida_cotizacion.tasa_iva}, tal
     * como la exige el CHECK de la migracion V80.
     *
     * @return la etiqueta de base de datos de la tasa.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Factor multiplicativo de la tasa (p. ej. 0.16 para 16%). Para {@link #CERO}
     * y {@link #EXENTO} es 0, de modo que el IVA calculado es 0 en ambos casos; la
     * distincion entre ambos es fiscal/documental, no aritmetica.
     *
     * @return el factor de la tasa, escala fija.
     */
    public BigDecimal factor() {
        return factor;
    }

    /**
     * Reconstruye la tasa a partir de su etiqueta de base de datos (inversa de
     * {@link #valorBd()}). La comparacion es insensible a mayusculas y recorta
     * espacios.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code '16'}).
     * @return la tasa correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static TasaIva desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("La tasa de IVA no puede ser nula");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (TasaIva tasa : values()) {
            if (tasa.valorBd.equals(normalizado)) {
                return tasa;
            }
        }
        throw new IllegalArgumentException("Tasa de IVA desconocida: " + valor);
    }
}
