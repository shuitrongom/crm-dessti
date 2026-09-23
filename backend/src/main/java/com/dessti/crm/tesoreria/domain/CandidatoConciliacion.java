package com.dessti.crm.tesoreria.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Candidato de emparejamiento de un {@link MovimientoBancario}: una partida
 * contable (una {@code Poliza_Contable} o un {@code Pago}) con la que un movimiento
 * podria conciliarse por monto, fecha y referencia (Req 43.3).
 *
 * <p>Es un valor de dominio <strong>puro</strong> e inmutable, independiente de la
 * persistencia y del framework, para que las reglas de emparejamiento
 * ({@link ReglasConciliacion}) sean directamente verificables (Property 18). El
 * {@link Origen} distingue si el candidato proviene de una Poliza_Contable o de un
 * Pago, y {@link #id()} es el identificador de esa partida para enlazarlo al
 * movimiento conciliado.</p>
 *
 * @param origen     fuente contable del candidato (Poliza_Contable o Pago).
 * @param id         identificador de la partida contable; obligatorio.
 * @param monto      monto de la partida (magnitud positiva); se compara contra el
 *                   valor absoluto del monto (con signo) del Movimiento_Bancario.
 * @param fecha      fecha contable de la partida; se compara contra la fecha del
 *                   movimiento dentro de la tolerancia en dias.
 * @param referencia referencia de la partida (folio, UUID, etc.); puede ser
 *                   {@code null} si la partida no expone referencia.
 */
public record CandidatoConciliacion(
        Origen origen,
        UUID id,
        BigDecimal monto,
        LocalDate fecha,
        String referencia) {

    /** Fuente contable de un {@link CandidatoConciliacion} (Req 43.3). */
    public enum Origen {

        /** El candidato proviene de una {@code Poliza_Contable} (Req 38). */
        POLIZA_CONTABLE,

        /** El candidato proviene de un {@code Pago} de Cliente (Req 36). */
        PAGO;
    }

    /**
     * Construye un candidato originado en una {@code Poliza_Contable}.
     *
     * @param id         identificador de la poliza; obligatorio.
     * @param monto      monto (magnitud positiva) de la poliza.
     * @param fecha      fecha contable de la poliza.
     * @param referencia referencia de la poliza; opcional.
     * @return el candidato de origen Poliza_Contable.
     */
    public static CandidatoConciliacion dePoliza(UUID id, BigDecimal monto, LocalDate fecha,
                                                 String referencia) {
        return new CandidatoConciliacion(Origen.POLIZA_CONTABLE, id, monto, fecha, referencia);
    }

    /**
     * Construye un candidato originado en un {@code Pago} de Cliente.
     *
     * @param id         identificador del pago; obligatorio.
     * @param monto      monto (magnitud positiva) del pago.
     * @param fecha      fecha del pago.
     * @param referencia referencia del pago; opcional.
     * @return el candidato de origen Pago.
     */
    public static CandidatoConciliacion dePago(UUID id, BigDecimal monto, LocalDate fecha,
                                               String referencia) {
        return new CandidatoConciliacion(Origen.PAGO, id, monto, fecha, referencia);
    }
}
