package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Catalogo de las areas de negocio cuyos indicadores agrega el Tablero (Req 22.1) y
 * el analisis consolidado de Inteligencia de Negocio (Req 48.1). Cada area expone una
 * <strong>etiqueta ASCII estable</strong> (sin acentos) que se usa como clave de
 * agrupacion en los DTO de salida, en el filtro por area (Req 48.4) y en la columna
 * {@code area} de los widgets de los tableros personalizados (Req 48.3).
 *
 * <p>El orden del enum define el orden de presentacion por defecto del Tablero. La
 * lista cubre exactamente las areas enumeradas en el Req 22.1 y en el Req 48.1.</p>
 */
public enum AreaIndicador {

    /** Comercial: pipeline de Oportunidades y Cotizaciones por estado (Req 22.1). */
    COMERCIAL("comercial"),

    /** Produccion: Ordenes de Fabricacion por estado (Req 22.1). */
    PRODUCCION("produccion"),

    /** Instalacion: cumplimiento de fechas programadas (Req 22.1). */
    INSTALACION("instalacion"),

    /** Mantenimiento: cumplimiento de SLA de Tickets de servicio (Req 22.1). */
    MANTENIMIENTO("mantenimiento"),

    /** Inventario de Materiales: stock bajo minimo y existencias (Req 22.1). */
    INVENTARIO("inventario"),

    /** Compras: Ordenes de compra por estado y discrepancias (Req 22.1). */
    COMPRAS("compras"),

    /** Finanzas/facturacion: facturacion del periodo, CxC vencidas, IVA (Req 22.1). */
    FINANZAS("finanzas"),

    /** RH/nomina: costo de nomina del periodo (Req 22.1). */
    RH_NOMINA("rh_nomina"),

    /** Tesoreria: saldos bancarios y partidas en conciliacion (Req 22.1). */
    TESORERIA("tesoreria"),

    /** Cuentas por pagar: CxP vencidas (Req 22.1). */
    CXP("cxp"),

    /** Estrategia: avance de Objetivos estrategicos (Req 22.1). */
    ESTRATEGIA("estrategia"),

    /** Inventario avanzado por Almacen: valuacion y existencias (Req 22.1). */
    INVENTARIO_AVANZADO("inventario_avanzado"),

    /** Presupuesto: variacion presupuestal (Req 22.1). */
    PRESUPUESTO("presupuesto"),

    /** Redes sociales/mensajeria omnicanal: mensajes, tiempo de respuesta (Req 22.1). */
    REDES_SOCIALES("redes_sociales");

    private final String etiqueta;

    AreaIndicador(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /**
     * Etiqueta ASCII estable del area (sin acentos), usada como clave en los DTO,
     * el filtro por area (Req 48.4) y la columna {@code area} de los widgets.
     *
     * @return la etiqueta del area.
     */
    public String etiqueta() {
        return etiqueta;
    }
}
