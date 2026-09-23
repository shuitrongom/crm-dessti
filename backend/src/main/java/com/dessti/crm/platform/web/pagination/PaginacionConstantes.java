package com.dessti.crm.platform.web.pagination;

/**
 * Limites centralizados de la paginacion estandar del CRM (Req 12).
 *
 * <p>Todos los listados del sistema comparten estos limites para garantizar un
 * comportamiento consistente: tamano por defecto {@value #DEFAULT_SIZE} y
 * tamano maximo {@value #MAX_SIZE}. Un tamano superior al maximo se rechaza con
 * HTTP 400 conforme a la politica estandar (ver
 * {@link PageRequestFactory}).</p>
 */
public final class PaginacionConstantes {

    /** Numero de pagina por defecto (0-index). */
    public static final int DEFAULT_PAGE = 0;

    /** Tamano de pagina por defecto cuando el cliente no lo especifica. */
    public static final int DEFAULT_SIZE = 20;

    /** Tamano de pagina maximo permitido; un valor superior se rechaza (Req 7.8). */
    public static final int MAX_SIZE = 100;

    private PaginacionConstantes() {
        // Clase de utilidades: no instanciable.
    }
}
