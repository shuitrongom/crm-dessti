package com.dessti.crm.contabilidad.cxc.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link CuentaPorCobrar} (CxC) y su maquina de estados
 * <strong>pura</strong> (Req 36). Sigue el patron de {@code EstadoFactura} /
 * {@code EstadoNotaCredito}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #PENDIENTE} — estado inicial: la CxC se registro al timbrar la
 *       Factura y su saldo es igual al total (Req 36.1); aun no se aplico ningun
 *       pago ni nota de credito.</li>
 *   <li>{@link #PARCIAL} — se aplicaron pagos y/o notas de credito por un monto
 *       menor al total: {@code 0 < saldo < total} (Req 36.2).</li>
 *   <li>{@link #PAGADA} — estado <strong>final</strong>: el saldo llego a cero
 *       ({@code saldo == 0}); la CxC queda liquidada (Req 36.2).</li>
 *   <li>{@link #CANCELADA} — estado <strong>final</strong>: la CxC se cancelo
 *       (por ejemplo, tras la cancelacion de su Factura); no admite mas cambios.</li>
 * </ul>
 *
 * <h2>Transiciones permitidas</h2>
 * <pre>
 *   pendiente -&gt; parcial
 *   pendiente -&gt; pagada
 *   pendiente -&gt; cancelada
 *   parcial   -&gt; pagada
 *   parcial   -&gt; cancelada
 *   (pagada, cancelada: finales, sin salida)
 * </pre>
 * El estado destino se <strong>deriva del saldo</strong> tras cada aplicacion
 * ({@code saldo == total -> pendiente}; {@code 0 < saldo < total -> parcial};
 * {@code saldo == 0 -> pagada}) y la transicion se valida por esta maquina. Toda
 * transicion no permitida —incluida cualquiera que parta de un estado final— se
 * rechaza con {@link com.dessti.crm.platform.error.TransicionInvalidaException}
 * (409).
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'pendiente'}, {@code 'parcial'}, {@code 'pagada'},
 * {@code 'cancelada'}, tal como exige el CHECK de la migracion V31.</p>
 */
public enum EstadoCuentaPorCobrar {

    /** Estado inicial: CxC registrada al timbrar, saldo == total (Req 36.1). */
    PENDIENTE("pendiente"),

    /** Se aplicaron pagos/notas por menos del total: 0 &lt; saldo &lt; total (Req 36.2). */
    PARCIAL("parcial"),

    /** Estado final: saldo == 0, CxC liquidada (Req 36.2). */
    PAGADA("pagada"),

    /** Estado final: CxC cancelada. */
    CANCELADA("cancelada");

    /**
     * Maquina de estados pura de la Cuenta_Por_Cobrar. Se construye una sola vez y
     * es inmutable. Los estados finales {@link #PAGADA} y {@link #CANCELADA} no
     * declaran transiciones salientes, por lo que la maquina los trata como finales.
     */
    private static final MaquinaEstados<EstadoCuentaPorCobrar> MAQUINA =
            MaquinaEstados.<EstadoCuentaPorCobrar>builder(EstadoCuentaPorCobrar.class)
                    .permitir(PENDIENTE, PARCIAL, PAGADA, CANCELADA)
                    .permitir(PARCIAL, PAGADA, CANCELADA)
                    .construir();

    private final String valorBd;

    EstadoCuentaPorCobrar(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code cuenta_por_cobrar.estado}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V31.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'parcial'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoCuentaPorCobrar desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Cuenta_Por_Cobrar no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoCuentaPorCobrar estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Cuenta_Por_Cobrar desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> ({@link #PAGADA} o
     * {@link #CANCELADA}).
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
    public boolean puedeTransicionarA(EstadoCuentaPorCobrar destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}
