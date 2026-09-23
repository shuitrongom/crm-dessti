package com.dessti.crm.compras.requisicion.domain;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion de rangos del submodulo de Requisiciones de Compra
 * ({@link RequisicionCompra}, {@link PartidaRequisicion}) (Req 30). Centraliza las
 * cotas de cantidad por Material y el numero minimo de partidas, para evitar
 * duplicarlas y garantizar un comportamiento consistente y verificable.
 *
 * <h2>Reglas (Req 30)</h2>
 * <ul>
 *   <li><strong>Cantidad de la partida:</strong> entero en el rango cerrado
 *       [1, 999,999] por Material (Req 30.1). Fuera de rango se rechaza (422).</li>
 *   <li><strong>Numero de partidas:</strong> al crear, al menos 1 Material
 *       (Req 30.1).</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable. Las violaciones se senalan con
 * {@link ReglaNegocioException} (HTTP 422), coherente con el resto del dominio.</p>
 */
public final class RequisicionCompraValidaciones {

    /** Cantidad minima aceptada por Material (Req 30.1). */
    public static final int CANTIDAD_MINIMA = 1;

    /** Cantidad maxima aceptada por Material (Req 30.1). */
    public static final int CANTIDAD_MAXIMA = 999_999;

    /** Numero minimo de Partida_Requisicion al crear una Requisicion_Compra (Req 30.1). */
    public static final int PARTIDAS_MINIMAS = 1;

    private RequisicionCompraValidaciones() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida la cantidad entera de una partida dentro del rango [1, 999,999]
     * (Req 30.1).
     *
     * @param cantidad cantidad a validar.
     * @return la misma cantidad si es valida.
     * @throws ReglaNegocioException si esta fuera del rango permitido (422).
     */
    public static int validarCantidad(int cantidad) {
        if (cantidad < CANTIDAD_MINIMA || cantidad > CANTIDAD_MAXIMA) {
            throw new ReglaNegocioException(
                    "La cantidad " + cantidad + " esta fuera del rango permitido ["
                            + CANTIDAD_MINIMA + ", " + CANTIDAD_MAXIMA + "].");
        }
        return cantidad;
    }
}
