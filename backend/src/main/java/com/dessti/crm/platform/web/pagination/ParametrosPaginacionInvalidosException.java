package com.dessti.crm.platform.web.pagination;

/**
 * Se lanza cuando los parametros de paginacion de una peticion son invalidos:
 * numero de pagina negativo, tamano menor a 1 o tamano superior al maximo
 * permitido ({@link PaginacionConstantes#MAX_SIZE}).
 *
 * <p>El manejador global de errores la traduce a una respuesta Problem Details
 * con HTTP 400 (Req 8, 7.8), integrandose con el resto de errores de validacion
 * de entrada sin duplicar su logica.</p>
 */
public class ParametrosPaginacionInvalidosException extends RuntimeException {

    private final String campo;

    /**
     * @param campo   nombre del parametro invalido ({@code page} o {@code size})
     * @param mensaje mensaje de negocio en espanol que explica el rechazo
     */
    public ParametrosPaginacionInvalidosException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    /** @return el nombre del parametro que provoco el rechazo. */
    public String campo() {
        return campo;
    }
}
