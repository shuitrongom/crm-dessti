package com.dessti.crm.operacion.inventario.avanzado.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Motor de costeo de inventario PURO del inventario avanzado por Almacen (Req 60, tarea
 * 23.2). Concentra la MATEMATICA del costeo promedio ponderado ({@link MetodoCosteo#PROMEDIO})
 * y del consumo por capas PEPS/FIFO ({@link MetodoCosteo#PEPS}) en metodos ESTATICOS SIN
 * estado, SIN acoplamiento a JPA ni a Spring, de modo que las propiedades de correctitud se
 * puedan ejercitar de forma directa (tareas 23.3-23.6).
 *
 * <h2>Contrato y separacion de responsabilidades</h2>
 * <p>El motor recibe el saldo actual, el costo promedio actual y la lista INMUTABLE de
 * capas PEPS ({@link CapaCostoValor}) y DEVUELVE un resultado inmutable
 * ({@link ResultadoEntrada}/{@link ResultadoSalida}) con el nuevo saldo, el nuevo costo
 * promedio, las capas resultantes y el costeo del movimiento. NO muta sus argumentos ni
 * persiste nada: el servicio de aplicacion reconcilia las filas JPA {@link CapaCosto} y
 * {@link ExistenciaAlmacen} con lo que el motor calcula.</p>
 *
 * <h2>Invariantes garantizadas (propiedades de correctitud)</h2>
 * <ul>
 *   <li><strong>Property 31 (no negatividad y perpetuidad):</strong> una entrada exige
 *       cantidad &gt; 0 y costo &gt;= 0; una salida exige cantidad &gt; 0 y se RECHAZA (sin
 *       mutar) si supera el saldo, con el mensaje "existencias insuficientes" coherente con
 *       el inventario base (Req 18.3, 60.10). El nuevo saldo nunca es negativo.</li>
 *   <li><strong>Property 33 (recosteo correcto segun metodo):</strong> en PROMEDIO el costo
 *       unitario promedio se recalcula en cada ENTRADA como media ponderada; en PEPS las
 *       SALIDAS consumen el costo de las capas mas antiguas primero (Req 60.5, 60.11).</li>
 *   <li><strong>Property 34 (Kardex con saldo acumulado):</strong> el costo del movimiento
 *       ({@link ResultadoEntrada#costoTotalMovimiento()}/{@link ResultadoSalida#costoTotalMovimiento()})
 *       y el nuevo saldo permiten al servicio proyectar el snapshot del Kardex (Req 60.12).</li>
 * </ul>
 *
 * <h2>Convenciones numericas (coherentes con V26)</h2>
 * <ul>
 *   <li>Cantidades: {@link BigDecimal} a escala {@value #ESCALA_CANTIDAD} ({@code NUMERIC(18,3)}).</li>
 *   <li>Costos: {@link BigDecimal} a escala {@value #ESCALA_COSTO} ({@code NUMERIC(18,4)}).</li>
 *   <li>Redondeo: siempre {@link RoundingMode#HALF_UP}.</li>
 *   <li>PEPS: el costo total de una salida es la SUMA EXACTA de los subtotales por capa
 *       ({@code cantidadConsumida * costoUnitarioCapa}) SIN redondeo intermedio; solo se
 *       redondea el total FINAL a escala 4 (evita sesgo por redondeos acumulados).</li>
 * </ul>
 *
 * <p><strong>Determinismo:</strong> las capas se consumen estrictamente en el orden de la
 * lista recibida; el servicio la suministra ordenada por {@code secuencia} ascendente
 * (primeras entradas primero), de modo que el resultado es reproducible.</p>
 */
public final class MotorCosteo {

    /** Escala decimal de las cantidades, coherente con NUMERIC(18,3) de V26. */
    public static final int ESCALA_CANTIDAD = 3;

    /** Escala decimal de los costos, coherente con NUMERIC(18,4) de V26. */
    public static final int ESCALA_COSTO = 4;

    private MotorCosteo() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * Aplica una ENTRADA de inventario y devuelve el nuevo saldo, el nuevo costo promedio,
     * las capas PEPS resultantes y el costeo del movimiento (Req 60.10, 60.11). Funcion PURA
     * y determinista; no muta los argumentos.
     *
     * <h3>PROMEDIO (Req 60.11)</h3>
     * <p>Recalcula el costo unitario promedio ponderado:
     * {@code nuevoPromedio = (saldo*promedioActual + cantidad*costoEntrada) / nuevoSaldo},
     * a escala 4 HALF_UP (si {@code nuevoSaldo == 0}, promedio 0). Las capas se devuelven
     * sin cambios (el metodo promedio no las utiliza).</p>
     *
     * <h3>PEPS (Req 60.11)</h3>
     * <p>Agrega una nueva capa {@code (cantidad, costoEntrada)} al FINAL de la lista (la mas
     * reciente al final, para que las salidas consuman primero las mas antiguas). El costo
     * promedio se mantiene informativo como media ponderada de TODAS las capas restantes,
     * para la consistencia del campo {@code costo_promedio} de {@link ExistenciaAlmacen} y del
     * {@code saldo_costo_total} del Kardex (DECISION 23.2).</p>
     *
     * <p>En ambos metodos el costo del movimiento es
     * {@code costoUnitarioMovimiento = costoEntrada} y
     * {@code costoTotalMovimiento = cantidad * costoEntrada} (escala 4).</p>
     *
     * @param metodo               metodo de costeo del Material; obligatorio.
     * @param saldoCantidad        cantidad en existencia antes de la entrada; &gt;= 0.
     * @param costoPromedioActual  costo promedio por unidad antes de la entrada; &gt;= 0.
     * @param capasActuales        capas PEPS actuales (orden FIFO); no nula (puede ser vacia).
     * @param cantidadEntrada      cantidad que ingresa; debe ser &gt; 0.
     * @param costoUnitarioEntrada costo unitario de la entrada; debe ser &gt;= 0.
     * @return el {@link ResultadoEntrada} con el nuevo saldo, promedio, capas y costeo.
     * @throws ReglaNegocioException si la cantidad no es positiva o el costo es negativo (422).
     */
    public static ResultadoEntrada aplicarEntrada(MetodoCosteo metodo,
                                                  BigDecimal saldoCantidad,
                                                  BigDecimal costoPromedioActual,
                                                  List<CapaCostoValor> capasActuales,
                                                  BigDecimal cantidadEntrada,
                                                  BigDecimal costoUnitarioEntrada) {
        exigirMetodo(metodo);
        BigDecimal saldo = normalizarSaldoEntrante(saldoCantidad);
        BigDecimal promedioActual = normalizarCostoEntrante(costoPromedioActual);
        List<CapaCostoValor> capas = normalizarCapas(capasActuales);
        if (cantidadEntrada == null || cantidadEntrada.signum() <= 0) {
            throw new ReglaNegocioException("La cantidad de una entrada de inventario debe ser mayor que 0.");
        }
        if (costoUnitarioEntrada == null || costoUnitarioEntrada.signum() < 0) {
            throw new ReglaNegocioException("El costo unitario de una entrada no puede ser negativo.");
        }

        BigDecimal nuevoSaldo = escalarCantidad(saldo.add(cantidadEntrada));
        BigDecimal costoUnitarioMovimiento = escalarCosto(costoUnitarioEntrada);
        BigDecimal costoTotalMovimiento = escalarCosto(cantidadEntrada.multiply(costoUnitarioEntrada));

        if (metodo == MetodoCosteo.PROMEDIO) {
            BigDecimal nuevoPromedio = promedioPonderado(saldo, promedioActual,
                    cantidadEntrada, costoUnitarioEntrada, nuevoSaldo);
            // El metodo promedio no utiliza capas: se devuelven sin cambios.
            return new ResultadoEntrada(nuevoSaldo, nuevoPromedio, capas,
                    costoUnitarioMovimiento, costoTotalMovimiento);
        }

        // PEPS: nueva capa al final (la mas reciente se consume al ultimo).
        List<CapaCostoValor> nuevasCapas = new ArrayList<>(capas);
        nuevasCapas.add(new CapaCostoValor(
                escalarCantidad(cantidadEntrada), escalarCosto(costoUnitarioEntrada)));
        BigDecimal nuevoPromedio = promedioDeCapas(nuevasCapas, nuevoSaldo);
        return new ResultadoEntrada(nuevoSaldo, nuevoPromedio, List.copyOf(nuevasCapas),
                costoUnitarioMovimiento, costoTotalMovimiento);
    }

    /**
     * Aplica una SALIDA de inventario y devuelve el nuevo saldo, el nuevo costo promedio,
     * las capas PEPS resultantes y el costeo del movimiento (Req 60.5, 60.10, 60.11).
     * Funcion PURA y determinista; no muta los argumentos.
     *
     * <p><strong>No negatividad (Property 31, Req 60.10):</strong> si {@code cantidadSalida}
     * supera el saldo se lanza {@link ReglaNegocioException} con el mensaje
     * "existencias insuficientes" ANTES de calcular nada (coherente con el inventario base,
     * Req 18.3), de modo que el saldo se conserva.</p>
     *
     * <h3>PROMEDIO (Req 60.11)</h3>
     * <p>El costo de la salida usa el costo promedio vigente:
     * {@code costoUnitarioMovimiento = promedioActual}, {@code costoTotal = cantidad * promedio}
     * (escala 4). El promedio no cambia y las capas se devuelven sin cambios.</p>
     *
     * <h3>PEPS (Req 60.11)</h3>
     * <p>Consume las capas mas antiguas primero (frente de la lista). El costo total es la
     * SUMA EXACTA de {@code cantidadConsumidaDeCapa * costoUnitarioCapa} SIN redondeo
     * intermedio; solo el total final se redondea a escala 4. El costo unitario del
     * movimiento es {@code costoTotal / cantidadSalida} (escala 4). Las capas totalmente
     * consumidas se eliminan y la primera capa parcial se reduce. El nuevo promedio es la
     * media ponderada de las capas restantes (0 si no quedan).</p>
     *
     * @param metodo              metodo de costeo del Material; obligatorio.
     * @param saldoCantidad       cantidad en existencia antes de la salida; &gt;= 0.
     * @param costoPromedioActual costo promedio por unidad antes de la salida; &gt;= 0.
     * @param capasActuales       capas PEPS actuales en orden FIFO; no nula (puede ser vacia).
     * @param cantidadSalida      cantidad que egresa; debe ser &gt; 0 y &lt;= saldo.
     * @return el {@link ResultadoSalida} con el nuevo saldo, promedio, capas y costeo.
     * @throws ReglaNegocioException si la cantidad no es positiva (422) o supera el saldo
     *         ("existencias insuficientes", 422).
     */
    public static ResultadoSalida aplicarSalida(MetodoCosteo metodo,
                                                BigDecimal saldoCantidad,
                                                BigDecimal costoPromedioActual,
                                                List<CapaCostoValor> capasActuales,
                                                BigDecimal cantidadSalida) {
        exigirMetodo(metodo);
        BigDecimal saldo = normalizarSaldoEntrante(saldoCantidad);
        BigDecimal promedioActual = normalizarCostoEntrante(costoPromedioActual);
        List<CapaCostoValor> capas = normalizarCapas(capasActuales);
        if (cantidadSalida == null || cantidadSalida.signum() <= 0) {
            throw new ReglaNegocioException("La cantidad de una salida de inventario debe ser mayor que 0.");
        }
        // Property 31 / Req 60.10: se rechaza ANTES de mutar; el saldo se conserva.
        if (cantidadSalida.compareTo(saldo) > 0) {
            throw new ReglaNegocioException("existencias insuficientes");
        }

        BigDecimal nuevoSaldo = escalarCantidad(saldo.subtract(cantidadSalida));

        if (metodo == MetodoCosteo.PROMEDIO) {
            BigDecimal costoUnitarioMovimiento = escalarCosto(promedioActual);
            BigDecimal costoTotalMovimiento = escalarCosto(cantidadSalida.multiply(promedioActual));
            // El promedio no cambia con una salida; las capas no se usan.
            return new ResultadoSalida(nuevoSaldo, escalarCosto(promedioActual), capas,
                    costoUnitarioMovimiento, costoTotalMovimiento);
        }

        // PEPS: consumir el frente de la lista, acumulando el costo con precision plena.
        List<CapaCostoValor> restantes = new ArrayList<>(capas.size());
        BigDecimal porConsumir = cantidadSalida;
        BigDecimal costoTotalExacto = BigDecimal.ZERO;
        for (CapaCostoValor capa : capas) {
            BigDecimal disponible = capa.cantidadRestante();
            if (porConsumir.signum() <= 0) {
                // Ya se satisfizo la salida: la capa permanece intacta.
                restantes.add(capa);
                continue;
            }
            if (disponible.compareTo(porConsumir) <= 0) {
                // Capa totalmente consumida: aporta todo su saldo y se elimina.
                costoTotalExacto = costoTotalExacto.add(disponible.multiply(capa.costoUnitario()));
                porConsumir = porConsumir.subtract(disponible);
            } else {
                // Capa parcialmente consumida: aporta lo necesario y se reduce.
                costoTotalExacto = costoTotalExacto.add(porConsumir.multiply(capa.costoUnitario()));
                BigDecimal remanente = escalarCantidad(disponible.subtract(porConsumir));
                restantes.add(new CapaCostoValor(remanente, capa.costoUnitario()));
                porConsumir = BigDecimal.ZERO;
            }
        }

        BigDecimal costoTotalMovimiento = escalarCosto(costoTotalExacto);
        BigDecimal costoUnitarioMovimiento = costoTotalMovimiento
                .divide(cantidadSalida, ESCALA_COSTO, RoundingMode.HALF_UP);
        BigDecimal nuevoPromedio = promedioDeCapas(restantes, nuevoSaldo);
        return new ResultadoSalida(nuevoSaldo, nuevoPromedio, List.copyOf(restantes),
                costoUnitarioMovimiento, costoTotalMovimiento);
    }

    // ------------------------------------------------------------------
    // Reglas internas (matematica pura)
    // ------------------------------------------------------------------

    private static void exigirMetodo(MetodoCosteo metodo) {
        if (metodo == null) {
            throw new ReglaNegocioException("El metodo de costeo es obligatorio.");
        }
    }

    /**
     * Costo promedio ponderado de una entrada:
     * {@code (saldo*promedioActual + cantidad*costoEntrada) / nuevoSaldo}, escala 4 HALF_UP;
     * 0 si {@code nuevoSaldo == 0}.
     */
    private static BigDecimal promedioPonderado(BigDecimal saldo, BigDecimal promedioActual,
                                                BigDecimal cantidadEntrada, BigDecimal costoEntrada,
                                                BigDecimal nuevoSaldo) {
        if (nuevoSaldo.signum() == 0) {
            return ceroCosto();
        }
        BigDecimal valorPrevio = saldo.multiply(promedioActual);
        BigDecimal valorEntrada = cantidadEntrada.multiply(costoEntrada);
        return valorPrevio.add(valorEntrada)
                .divide(nuevoSaldo, ESCALA_COSTO, RoundingMode.HALF_UP);
    }

    /**
     * Costo promedio ponderado de un conjunto de capas frente a un saldo dado:
     * {@code sum(cantidadRestante*costoUnitario) / saldo}, escala 4 HALF_UP; 0 si el saldo
     * es 0 o no hay capas. Usado para mantener {@code costo_promedio} informativo en PEPS.
     */
    private static BigDecimal promedioDeCapas(List<CapaCostoValor> capas, BigDecimal saldo) {
        if (saldo == null || saldo.signum() <= 0 || capas.isEmpty()) {
            return ceroCosto();
        }
        BigDecimal valor = BigDecimal.ZERO;
        for (CapaCostoValor capa : capas) {
            valor = valor.add(capa.cantidadRestante().multiply(capa.costoUnitario()));
        }
        return valor.divide(saldo, ESCALA_COSTO, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizarSaldoEntrante(BigDecimal saldo) {
        if (saldo == null || saldo.signum() < 0) {
            throw new ReglaNegocioException("El saldo de existencias no puede ser negativo.");
        }
        return saldo;
    }

    private static BigDecimal normalizarCostoEntrante(BigDecimal costo) {
        if (costo == null || costo.signum() < 0) {
            throw new ReglaNegocioException("El costo promedio no puede ser negativo.");
        }
        return costo;
    }

    private static List<CapaCostoValor> normalizarCapas(List<CapaCostoValor> capas) {
        if (capas == null) {
            throw new ReglaNegocioException("La lista de capas de costo es obligatoria.");
        }
        return capas;
    }

    private static BigDecimal escalarCantidad(BigDecimal valor) {
        return valor.setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
    }

    private static BigDecimal escalarCosto(BigDecimal valor) {
        return valor.setScale(ESCALA_COSTO, RoundingMode.HALF_UP);
    }

    private static BigDecimal ceroCosto() {
        return BigDecimal.ZERO.setScale(ESCALA_COSTO);
    }
}
