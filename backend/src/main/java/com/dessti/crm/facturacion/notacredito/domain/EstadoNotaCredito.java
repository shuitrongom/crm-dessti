package com.dessti.crm.facturacion.notacredito.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link NotaCredito} (CFDI de egreso) y su maquina de estados
 * <strong>pura</strong> (Req 37). Sigue el patron de {@code EstadoFactura}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #BORRADOR} — estado inicial de la Nota de Credito recien emitida.</li>
 *   <li>{@link #TIMBRADA} — la Nota de Credito fue timbrada por el PAC y tiene
 *       Folio_Fiscal (Req 37.1). Su CFDI de egreso es inmutable a partir de aqui
 *       (Req 37.3).</li>
 *   <li>{@link #CANCELADA} — estado <strong>final</strong>: la Nota de Credito se
 *       cancelo. El CFDI de egreso y su Folio_Fiscal se conservan como historico
 *       inmutable (Req 37.3).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas</h2>
 * <pre>
 *   borrador -&gt; timbrada
 *   timbrada -&gt; cancelada
 *   (cancelada: final, sin salida)
 * </pre>
 * Cualquier otra transicion es invalida y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409).
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'borrador'}, {@code 'timbrada'}, {@code 'cancelada'},
 * tal como exige el CHECK de la migracion V30.</p>
 */
public enum EstadoNotaCredito {

    /** Estado inicial de una Nota de Credito recien emitida. */
    BORRADOR("borrador"),

    /** La Nota de Credito fue timbrada por el PAC y tiene Folio_Fiscal (Req 37.1). */
    TIMBRADA("timbrada"),

    /** Estado final: Nota de Credito cancelada (Req 37.3). */
    CANCELADA("cancelada");

    /**
     * Maquina de estados pura de la Nota de Credito. Se construye una sola vez y es
     * inmutable. El estado final {@link #CANCELADA} no declara transiciones
     * salientes, por lo que la maquina lo trata como final.
     */
    private static final MaquinaEstados<EstadoNotaCredito> MAQUINA =
            MaquinaEstados.<EstadoNotaCredito>builder(EstadoNotaCredito.class)
                    .permitir(BORRADOR, TIMBRADA)
                    .permitir(TIMBRADA, CANCELADA)
                    .construir();

    private final String valorBd;

    EstadoNotaCredito(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code nota_credito.estado}, en minusculas
     * ASCII, tal como la exige el CHECK de la migracion V30.
     *
     * @return la etiqueta de base de datos del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta de base de datos (inversa de
     * {@link #valorBd()}). Insensible a mayusculas; recorta espacios.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'timbrada'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoNotaCredito desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Nota de Credito no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoNotaCredito estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Nota de Credito desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> ({@link #CANCELADA}).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a {@code destino}.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoNotaCredito destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}
