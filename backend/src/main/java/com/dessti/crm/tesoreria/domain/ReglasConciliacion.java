package com.dessti.crm.tesoreria.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reglas <strong>puras</strong> de la conciliacion bancaria (Req 43.3, 43.4, 43.5).
 * Todos los metodos son estaticos, sin estado y deterministas: no acceden a la base
 * de datos ni al contexto de Spring, de modo que las invariantes de conciliacion
 * se verifican universalmente sobre entradas arbitrarias
 * (<strong>Property 18</strong>).
 *
 * <h2>Emparejamiento (Req 43.3)</h2>
 * <p>{@link #emparejar(BigDecimal, LocalDate, String, List, int)} empareja un
 * Movimiento_Bancario con un {@link CandidatoConciliacion} (Poliza_Contable o Pago)
 * <strong>solo</strong> cuando coinciden las tres condiciones:</p>
 * <ol>
 *   <li><strong>Monto:</strong> el valor absoluto del monto (con signo) del
 *       movimiento es exactamente igual (por {@code compareTo}, escala 2) al monto
 *       del candidato. El signo del movimiento (deposito {@code +} / retiro
 *       {@code -}) no altera la magnitud comparada.</li>
 *   <li><strong>Fecha:</strong> la fecha del candidato esta dentro de la tolerancia
 *       en dias respecto a la fecha del movimiento
 *       ({@code |dias| <= toleranciaDias}).</li>
 *   <li><strong>Referencia:</strong> cuando el movimiento trae referencia, esta
 *       debe coincidir (sin distincion de mayusculas/minusculas ni espacios) con la
 *       del candidato; si el movimiento no trae referencia, esta condicion no
 *       excluye candidatos.</li>
 * </ol>
 *
 * <h2>Diferencia y completitud (Req 43.5; Property 18)</h2>
 * <ul>
 *   <li>{@link #calcularDiferencia(BigDecimal, BigDecimal)} devuelve
 *       {@code saldoBancario - saldoContable} (escala 2).</li>
 *   <li>{@link #esCompleta(BigDecimal, int)} devuelve {@code true} <strong>si y solo
 *       si</strong> la diferencia es cero <em>y</em> no quedan movimientos en
 *       excepcion (partidas sin explicar). Esta es exactamente la Property 18:
 *       conciliacion COMPLETA solo con diferencia cero una vez explicadas las
 *       partidas.</li>
 * </ul>
 */
public final class ReglasConciliacion {

    /** Escala monetaria (NUMERIC(18,2) en V35). */
    public static final int ESCALA_MONETARIA = 2;

    private ReglasConciliacion() {
        // Clase de utilidades: no instanciable.
    }

    /**
     * Empareja un Movimiento_Bancario con el primer {@link CandidatoConciliacion}
     * que cumpla monto, fecha (dentro de tolerancia) y referencia (Req 43.3).
     *
     * @param montoMovimiento   monto con signo del movimiento (+ deposito / - retiro);
     *                          se compara por su valor absoluto. Obligatorio.
     * @param fechaMovimiento   fecha del movimiento. Obligatoria.
     * @param referenciaMovimiento referencia del movimiento; puede ser {@code null}.
     * @param candidatos        partidas contables candidatas; puede estar vacia.
     * @param toleranciaDias    tolerancia de fecha en dias (>= 0).
     * @return el primer candidato que empareja, o {@link Optional#empty()} si ninguno
     *         coincide (el movimiento debera marcarse como excepcion, Req 43.4).
     */
    public static Optional<CandidatoConciliacion> emparejar(
            BigDecimal montoMovimiento,
            LocalDate fechaMovimiento,
            String referenciaMovimiento,
            List<CandidatoConciliacion> candidatos,
            int toleranciaDias) {
        if (montoMovimiento == null || fechaMovimiento == null || candidatos == null) {
            return Optional.empty();
        }
        for (CandidatoConciliacion candidato : candidatos) {
            if (candidato != null
                    && coincide(montoMovimiento, fechaMovimiento, referenciaMovimiento,
                    candidato, toleranciaDias)) {
                return Optional.of(candidato);
            }
        }
        return Optional.empty();
    }

    /**
     * Indica si un candidato coincide con el movimiento por monto exacto (en valor
     * absoluto), fecha dentro de tolerancia y referencia (cuando el movimiento la
     * trae). Regla PURA usada por {@link #emparejar} y verificada por Property 18.
     *
     * @param montoMovimiento      monto con signo del movimiento; obligatorio.
     * @param fechaMovimiento      fecha del movimiento; obligatoria.
     * @param referenciaMovimiento referencia del movimiento; opcional.
     * @param candidato            candidato a evaluar; obligatorio.
     * @param toleranciaDias       tolerancia de fecha en dias (>= 0).
     * @return {@code true} si el candidato empareja con el movimiento.
     */
    public static boolean coincide(
            BigDecimal montoMovimiento,
            LocalDate fechaMovimiento,
            String referenciaMovimiento,
            CandidatoConciliacion candidato,
            int toleranciaDias) {
        if (montoMovimiento == null || fechaMovimiento == null || candidato == null
                || candidato.monto() == null || candidato.fecha() == null) {
            return false;
        }
        return montoCoincide(montoMovimiento, candidato.monto())
                && fechaDentroDeTolerancia(fechaMovimiento, candidato.fecha(), toleranciaDias)
                && referenciaCoincide(referenciaMovimiento, candidato.referencia());
    }

    /**
     * Compara el monto del movimiento (en valor absoluto) contra el monto del
     * candidato, ambos a escala 2, por {@code compareTo} (Req 43.3).
     *
     * @param montoMovimiento monto con signo del movimiento.
     * @param montoCandidato  monto (magnitud) del candidato.
     * @return {@code true} si las magnitudes son exactamente iguales.
     */
    public static boolean montoCoincide(BigDecimal montoMovimiento, BigDecimal montoCandidato) {
        if (montoMovimiento == null || montoCandidato == null) {
            return false;
        }
        BigDecimal magnitudMovimiento = montoMovimiento.abs()
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        BigDecimal magnitudCandidato = montoCandidato.abs()
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        return magnitudMovimiento.compareTo(magnitudCandidato) == 0;
    }

    /**
     * Indica si la fecha del candidato esta dentro de la tolerancia en dias respecto
     * a la fecha del movimiento (Req 43.3).
     *
     * @param fechaMovimiento fecha del movimiento; obligatoria.
     * @param fechaCandidato  fecha del candidato; obligatoria.
     * @param toleranciaDias  tolerancia en dias; una tolerancia negativa se trata
     *                        como cero (solo empareja el mismo dia).
     * @return {@code true} si {@code |dias entre ambas fechas| <= toleranciaDias}.
     */
    public static boolean fechaDentroDeTolerancia(LocalDate fechaMovimiento,
                                                  LocalDate fechaCandidato,
                                                  int toleranciaDias) {
        if (fechaMovimiento == null || fechaCandidato == null) {
            return false;
        }
        int tolerancia = Math.max(toleranciaDias, 0);
        long dias = Math.abs(ChronoUnit.DAYS.between(fechaMovimiento, fechaCandidato));
        return dias <= tolerancia;
    }

    /**
     * Indica si la referencia coincide. Cuando el movimiento no trae referencia
     * (nula o en blanco), la condicion no excluye al candidato; cuando la trae, debe
     * coincidir con la del candidato, ignorando mayusculas/minusculas y espacios
     * (Req 43.3).
     *
     * @param referenciaMovimiento referencia del movimiento; opcional.
     * @param referenciaCandidato  referencia del candidato; opcional.
     * @return {@code true} si la referencia no excluye el emparejamiento.
     */
    public static boolean referenciaCoincide(String referenciaMovimiento,
                                             String referenciaCandidato) {
        String refMovimiento = normalizar(referenciaMovimiento);
        if (refMovimiento == null) {
            // El movimiento no aporta referencia: no se excluye por este criterio.
            return true;
        }
        String refCandidato = normalizar(referenciaCandidato);
        return refMovimiento.equals(refCandidato);
    }

    /**
     * Calcula la diferencia de la conciliacion: {@code saldoBancario - saldoContable}
     * a escala 2 (Req 43.5).
     *
     * @param saldoBancario  saldo final del Estado_Cuenta_Bancario; obligatorio.
     * @param saldoContable  saldo contable derivado de las partidas conciliadas;
     *                       obligatorio.
     * @return la diferencia a escala 2 (positiva, negativa o cero).
     */
    public static BigDecimal calcularDiferencia(BigDecimal saldoBancario,
                                               BigDecimal saldoContable) {
        BigDecimal bancario = normalizarMonto(saldoBancario);
        BigDecimal contable = normalizarMonto(saldoContable);
        return bancario.subtract(contable).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Determina si la conciliacion esta COMPLETA (Req 43.5;
     * <strong>Property 18</strong>): devuelve {@code true} <strong>si y solo si</strong>
     * la diferencia es exactamente cero (por {@code compareTo}) <em>y</em> no quedan
     * movimientos sin explicar (excepciones). Con diferencia distinta de cero, o con
     * cualquier partida en excepcion, la conciliacion NO es completa.
     *
     * @param diferencia            diferencia calculada (saldo bancario - contable).
     * @param movimientosSinExplicar numero de movimientos en excepcion (>= 0).
     * @return {@code true} si la conciliacion es completa; {@code false} en otro caso.
     */
    public static boolean esCompleta(BigDecimal diferencia, int movimientosSinExplicar) {
        if (diferencia == null) {
            return false;
        }
        boolean diferenciaCero = diferencia.compareTo(BigDecimal.ZERO) == 0;
        boolean sinPartidasPendientes = movimientosSinExplicar <= 0;
        return diferenciaCero && sinPartidasPendientes;
    }

    private static BigDecimal normalizarMonto(BigDecimal monto) {
        BigDecimal valor = (monto == null) ? BigDecimal.ZERO : monto;
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.strip();
        return limpio.isEmpty() ? null : limpio.toUpperCase(Locale.ROOT);
    }
}
