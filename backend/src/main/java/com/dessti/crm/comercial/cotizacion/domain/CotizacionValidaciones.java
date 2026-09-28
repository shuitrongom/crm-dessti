package com.dessti.crm.comercial.cotizacion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion, normalizacion y aritmetica monetaria compartidas por
 * las entidades del submodulo de Cotizaciones ({@link Cotizacion},
 * {@link PartidaCotizacion}) (Req 6). Centraliza las reglas de rango y el
 * redondeo <em>half-up</em> para evitar duplicarlas y garantizar un
 * comportamiento consistente y verificable (Property 2, Property 3).
 *
 * <h2>Reglas (Req 6)</h2>
 * <ul>
 *   <li><strong>Cantidad de la partida:</strong> entero en el rango cerrado
 *       [1, 999,999] (Req 6.3, 6.4). Fuera de rango se rechaza (Property 3).</li>
 *   <li><strong>Precio unitario de la partida:</strong> dentro del rango cerrado
 *       [0.01, 999,999,999.99], escala 2 (redondeo al valor mas cercano). Fuera
 *       de rango se rechaza (Req 6.3, 6.4; Property 3). El rango y el redondeo son
 *       <em>identicos</em> a {@code CatalogoValidaciones.validarPrecio} del
 *       submodulo de Productos.</li>
 *   <li><strong>Descripcion de la partida:</strong> obligatoria, hasta 500
 *       caracteres tras recortar (coincide con VARCHAR(500) de V14).</li>
 *   <li><strong>Numero de partidas de la Cotizacion:</strong> al crear, entre 1 y
 *       500 (Req 6.1, 6.2).</li>
 *   <li><strong>Subtotal de la partida:</strong> {@code round(cantidad *
 *       precio_unitario, 2)} half-up (Req 6.3; Property 2).</li>
 *   <li><strong>Total de la Cotizacion:</strong> {@code round(Σ subtotales, 2)}
 *       half-up (Req 6.5; Property 2).</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable. Las violaciones se senalan con
 * {@link ReglaNegocioException} (HTTP 422), coherente con el resto del dominio.</p>
 */
public final class CotizacionValidaciones {

    /** Longitud maxima de la descripcion de la partida (VARCHAR(500) de V14). */
    public static final int LONGITUD_MAXIMA_DESCRIPCION = 500;

    /** Escala monetaria del sistema (2 decimales). */
    public static final int ESCALA_MONETARIA = 2;

    /** Cantidad minima aceptada por partida (Req 6.3, 6.4). */
    public static final int CANTIDAD_MINIMA = 1;

    /** Cantidad maxima aceptada por partida (Req 6.3, 6.4). */
    public static final int CANTIDAD_MAXIMA = 999_999;

    /** Precio unitario minimo aceptado (Req 6.3, 6.4). */
    public static final BigDecimal PRECIO_MINIMO = new BigDecimal("0.01");

    /** Precio unitario maximo aceptado (Req 6.3, 6.4). */
    public static final BigDecimal PRECIO_MAXIMO = new BigDecimal("999999999.99");

    /** Numero minimo de Partida_Cotizacion al crear una Cotizacion (Req 6.1, 6.2). */
    public static final int PARTIDAS_MINIMAS = 1;

    /** Numero maximo de Partida_Cotizacion al crear una Cotizacion (Req 6.1, 6.2). */
    public static final int PARTIDAS_MAXIMAS = 500;

    /** Longitud maxima de condiciones y notas de la Cotizacion (VARCHAR(2000), V60). */
    public static final int LONGITUD_MAXIMA_TEXTO_LARGO = 2000;

    /** Moneda por defecto de la Cotizacion (ISO 4217) cuando no se indica (V60). */
    public static final String MONEDA_POR_DEFECTO = "MXN";

    private CotizacionValidaciones() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida y normaliza un texto descriptivo OPCIONAL de la Cotizacion
     * (condiciones o notas): recorta espacios, trata el blanco como ausente
     * ({@code null}) y verifica la cota de longitud (V60).
     *
     * @param valor  texto a normalizar; puede ser {@code null}/blanco (ausente).
     * @param nombre nombre del campo para el mensaje de error.
     * @return el texto recortado, o {@code null} si estaba ausente.
     * @throws ReglaNegocioException si excede {@link #LONGITUD_MAXIMA_TEXTO_LARGO} (422).
     */
    public static String normalizarTextoLargoOpcional(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_TEXTO_LARGO) {
            throw new ReglaNegocioException(
                    "El campo '" + nombre + "' no puede exceder "
                            + LONGITUD_MAXIMA_TEXTO_LARGO + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Normaliza la moneda ISO 4217 de la Cotizacion (V60): recorta y pasa a
     * mayusculas; el blanco cae al valor por defecto {@link #MONEDA_POR_DEFECTO}.
     * Valida que sean exactamente 3 letras.
     *
     * @param valor codigo de moneda; {@code null}/blanco usa el valor por defecto.
     * @return el codigo de moneda normalizado (3 letras mayusculas).
     * @throws ReglaNegocioException si el codigo no tiene 3 letras (422).
     */
    public static String normalizarMoneda(String valor) {
        if (valor == null || valor.isBlank()) {
            return MONEDA_POR_DEFECTO;
        }
        String normalizado = valor.strip().toUpperCase(java.util.Locale.ROOT);
        if (!normalizado.matches("[A-Z]{3}")) {
            throw new ReglaNegocioException(
                    "La moneda debe ser un codigo ISO 4217 de 3 letras (p. ej. MXN).");
        }
        return normalizado;
    }

    /**
     * Valida y normaliza la descripcion obligatoria de una partida (Req 6.1).
     *
     * @param valor descripcion a normalizar.
     * @return la descripcion recortada.
     * @throws ReglaNegocioException si es nula/vacia o excede
     *         {@link #LONGITUD_MAXIMA_DESCRIPCION}.
     */
    public static String normalizarDescripcion(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("La descripcion de la partida es obligatoria.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_DESCRIPCION) {
            throw new ReglaNegocioException(
                    "La descripcion no puede exceder " + LONGITUD_MAXIMA_DESCRIPCION + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Valida la cantidad entera de una partida dentro del rango [1, 999,999]
     * (Req 6.3, 6.4; Property 3).
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

    /**
     * Valida un precio unitario y lo normaliza a 2 decimales (redondeo al valor
     * mas cercano). El precio debe estar dentro del rango cerrado
     * [0.01, 999,999,999.99] (Req 6.3, 6.4; Property 3).
     *
     * <p>El redondeo se aplica <em>antes</em> de la comprobacion de rango, de
     * modo que un valor con mas de 2 decimales se evalua por su representacion
     * monetaria efectiva (por ejemplo, {@code 0.004} redondea a {@code 0.00} y se
     * rechaza).</p>
     *
     * @param valor precio unitario a validar; obligatorio.
     * @return el precio normalizado a escala 2.
     * @throws ReglaNegocioException si es nulo o queda fuera del rango (422).
     */
    public static BigDecimal validarPrecioUnitario(BigDecimal valor) {
        if (valor == null) {
            throw new ReglaNegocioException("El precio unitario de la partida es obligatorio.");
        }
        BigDecimal normalizado = valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(PRECIO_MINIMO) < 0 || normalizado.compareTo(PRECIO_MAXIMO) > 0) {
            throw new ReglaNegocioException(
                    "El precio unitario " + normalizado.toPlainString()
                            + " esta fuera del rango permitido [" + PRECIO_MINIMO.toPlainString()
                            + ", " + PRECIO_MAXIMO.toPlainString() + "].");
        }
        return normalizado;
    }

    /**
     * Calcula el subtotal de una partida como {@code round(cantidad *
     * precio_unitario, 2)} con redondeo half-up (Req 6.3; Property 2). Asume que
     * la cantidad y el precio ya se validaron.
     *
     * @param cantidad       cantidad de la partida (>= 1).
     * @param precioUnitario precio unitario ya normalizado a escala 2.
     * @return el subtotal a escala 2 (half-up).
     */
    public static BigDecimal calcularSubtotalPartida(int cantidad, BigDecimal precioUnitario) {
        return precioUnitario
                .multiply(BigDecimal.valueOf(cantidad))
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Normaliza un importe monetario a escala 2 (half-up). Se usa para consolidar
     * el total de la Cotizacion como {@code round(Σ subtotales, 2)} (Req 6.5;
     * Property 2).
     *
     * @param valor importe a normalizar; obligatorio.
     * @return el importe a escala 2 (half-up).
     */
    public static BigDecimal normalizarMonto(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Aritmetica fiscal CFDI (V80): descuentos, IVA por partida y retenciones.
    // Todo a escala 2 half-up, coherente con el resto de la aritmetica del
    // agregado. El IVA se calcula por partida sobre su base neta (importe bruto
    // menos descuento de partida) para respetar tasas mixtas.
    // ------------------------------------------------------------------

    /**
     * Calcula el importe bruto de una partida como {@code round(cantidad *
     * precio_unitario, 2)} half-up. Es la base antes de aplicar el descuento de
     * partida. Identico al antiguo {@link #calcularSubtotalPartida(int, BigDecimal)}.
     *
     * @param cantidad       cantidad de la partida (>= 1).
     * @param precioUnitario precio unitario ya normalizado a escala 2.
     * @return el importe bruto a escala 2 (half-up).
     */
    public static BigDecimal calcularImporteBruto(int cantidad, BigDecimal precioUnitario) {
        return calcularSubtotalPartida(cantidad, precioUnitario);
    }

    /**
     * Valida y normaliza el descuento (monto) de una partida: no puede ser nulo
     * negativo ni exceder el importe bruto de la partida (no se admite un
     * descuento mayor que el importe). Un valor nulo se interpreta como 0.
     *
     * @param descuento    descuento a validar; {@code null} equivale a 0.
     * @param importeBruto importe bruto de la partida (base del tope).
     * @return el descuento normalizado a escala 2 (0 &le; descuento &le; importeBruto).
     * @throws ReglaNegocioException si es negativo o excede el importe bruto (422).
     */
    public static BigDecimal validarDescuentoPartida(BigDecimal descuento, BigDecimal importeBruto) {
        BigDecimal valor = (descuento == null) ? BigDecimal.ZERO : descuento;
        BigDecimal normalizado = valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (normalizado.signum() < 0) {
            throw new ReglaNegocioException("El descuento de la partida no puede ser negativo.");
        }
        if (normalizado.compareTo(importeBruto) > 0) {
            throw new ReglaNegocioException(
                    "El descuento de la partida (" + normalizado.toPlainString()
                            + ") no puede exceder su importe (" + importeBruto.toPlainString() + ").");
        }
        return normalizado;
    }

    /**
     * Calcula la base neta de una partida: {@code importeBruto - descuento},
     * normalizada a escala 2. Es la base sobre la que se calcula el IVA de la
     * partida y la que suma al subtotal de la Cotizacion.
     *
     * @param importeBruto importe bruto de la partida (ya a escala 2).
     * @param descuento    descuento de la partida ya validado (ya a escala 2).
     * @return la base neta a escala 2 (nunca negativa: el descuento se acota al bruto).
     */
    public static BigDecimal calcularBaseNetaPartida(BigDecimal importeBruto, BigDecimal descuento) {
        return normalizarMonto(importeBruto.subtract(descuento));
    }

    /**
     * Calcula el IVA de una partida como {@code round(baseNeta * factorTasa, 2)}
     * half-up. Para las tasas {@code 0} y {@code exento} el factor es 0, de modo
     * que el IVA es 0.
     *
     * @param baseNeta base neta de la partida (ya a escala 2).
     * @param tasa     tasa de IVA de la partida; obligatoria.
     * @return el IVA de la partida a escala 2 (half-up).
     * @throws ReglaNegocioException si la tasa es nula (422).
     */
    public static BigDecimal calcularIvaPartida(BigDecimal baseNeta, TasaIva tasa) {
        if (tasa == null) {
            throw new ReglaNegocioException("La tasa de IVA de la partida es obligatoria.");
        }
        return baseNeta.multiply(tasa.factor()).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Valida y normaliza un monto fiscal opcional a nivel Cotizacion (descuento
     * global o retencion): no puede ser negativo. Un valor nulo se interpreta
     * como 0.
     *
     * @param valor  monto a validar; {@code null} equivale a 0.
     * @param nombre nombre del campo para el mensaje de error.
     * @return el monto normalizado a escala 2 (&ge; 0).
     * @throws ReglaNegocioException si es negativo (422).
     */
    public static BigDecimal validarMontoFiscalNoNegativo(BigDecimal valor, String nombre) {
        BigDecimal base = (valor == null) ? BigDecimal.ZERO : valor;
        BigDecimal normalizado = base.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (normalizado.signum() < 0) {
            throw new ReglaNegocioException("El campo '" + nombre + "' no puede ser negativo.");
        }
        return normalizado;
    }
}
