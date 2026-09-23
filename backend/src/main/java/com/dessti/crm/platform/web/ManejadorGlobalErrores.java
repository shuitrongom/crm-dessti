package com.dessti.crm.platform.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.LimiteSolicitudesExcedidoException;
import com.dessti.crm.platform.error.NoAutorizadoException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.security.auth.AutenticacionException;
import com.dessti.crm.platform.web.pagination.ParametrosPaginacionInvalidosException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Manejador global de errores del CRM.
 *
 * <p>Traduce las excepciones de dominio e infraestructura a respuestas con
 * formato <b>Problem Details (RFC 7807)</b> mediante {@link ProblemDetail}
 * (soporte nativo de Spring 6 / Boot 3). Garantiza que nunca se filtren
 * detalles internos (trazas de pila, SQL ni nombres de clases) al cliente
 * (Req 56.4): los detalles tecnicos se registran en el log del servidor y al
 * cliente se le devuelven mensajes de negocio en espanol (Req 56.2).</p>
 *
 * <p>Cada respuesta incluye {@code type}, {@code title}, {@code status} y
 * {@code detail}; ante errores de validacion se agrega el arreglo
 * {@code errors} con el detalle por campo (Req 8.2). Cuando esta disponible un
 * identificador de correlacion en el {@link MDC} (clave {@code traceId}) se
 * propaga en la propiedad {@code traceId} del problema para facilitar el
 * diagnostico sin exponer internals.</p>
 */
@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    /** Espacio de nombres base para el campo {@code type} de los problemas. */
    private static final String BASE_TIPO = "https://crm/errors/";

    private static final String MSG_VALIDACION = "Uno o mas campos son invalidos.";
    private static final String MSG_CUERPO_ILEGIBLE =
            "La solicitud no pudo interpretarse. Verifique el formato de los datos enviados.";
    private static final String MSG_GENERICO =
            "Ocurrio un error inesperado. Intente de nuevo mas tarde o contacte al administrador.";

    // ---------------------------------------------------------------------
    // 400 - Validacion de entrada (Bean Validation en DTOs)
    // ---------------------------------------------------------------------

    /** Fallos de validacion de {@code @Valid} sobre el cuerpo de la peticion. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarArgumentoNoValido(MethodArgumentNotValidException ex) {
        List<ErrorCampo> errores = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errores.add(new ErrorCampo(fe.getField(),
                    fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Valor invalido"));
        }
        ProblemDetail pd = crear(HttpStatus.BAD_REQUEST, "Datos invalidos", MSG_VALIDACION, "validation");
        pd.setProperty("errors", errores);
        return pd;
    }

    /** Fallos de validacion a nivel de parametros/metodo (ConstraintViolation). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail manejarViolacionRestriccion(ConstraintViolationException ex) {
        List<ErrorCampo> errores = new ArrayList<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            errores.add(new ErrorCampo(String.valueOf(cv.getPropertyPath()), cv.getMessage()));
        }
        ProblemDetail pd = crear(HttpStatus.BAD_REQUEST, "Datos invalidos", MSG_VALIDACION, "validation");
        pd.setProperty("errors", errores);
        return pd;
    }

    /**
     * Parametros de paginacion invalidos ({@code page} negativa, {@code size}
     * menor a 1 o {@code size} superior al maximo permitido, Req 7.8). Se
     * reutiliza el mismo formato {@code errors[]} que el resto de errores de
     * validacion de entrada para ofrecer un contrato uniforme (Req 8.2).
     */
    @ExceptionHandler(ParametrosPaginacionInvalidosException.class)
    public ProblemDetail manejarPaginacionInvalida(ParametrosPaginacionInvalidosException ex) {
        List<ErrorCampo> errores = List.of(new ErrorCampo(ex.campo(), ex.getMessage()));
        ProblemDetail pd = crear(HttpStatus.BAD_REQUEST, "Datos invalidos", MSG_VALIDACION, "validation");
        pd.setProperty("errors", errores);
        return pd;
    }

    /** Cuerpo de peticion ilegible, tipo incorrecto o parametro requerido ausente. */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ProblemDetail manejarEntradaMalFormada(Exception ex) {
        // No se expone el detalle tecnico de la excepcion; solo se registra.
        log.warn("Entrada mal formada: {}", ex.getClass().getSimpleName());
        return crear(HttpStatus.BAD_REQUEST, "Solicitud invalida", MSG_CUERPO_ILEGIBLE, "bad-request");
    }

    // ---------------------------------------------------------------------
    // 401 - Autenticacion (credenciales invalidas / token de refresco invalido)
    // ---------------------------------------------------------------------

    /**
     * Error de autenticacion (Req 1.3, 1.6, 1.9): credenciales invalidas o
     * Token_Refresco no valido/expirado. El mensaje es generico y no revela la
     * causa exacta.
     */
    @ExceptionHandler(AutenticacionException.class)
    public ProblemDetail manejarAutenticacion(AutenticacionException ex) {
        return crear(HttpStatus.UNAUTHORIZED, "No autenticado", ex.getMessage(), "unauthorized");
    }

    // ---------------------------------------------------------------------
    // 403 - Autorizacion
    // ---------------------------------------------------------------------

    /** Acceso denegado por RBAC de dominio (Req 3.2, 3.6). */
    @ExceptionHandler(NoAutorizadoException.class)
    public ProblemDetail manejarNoAutorizado(NoAutorizadoException ex) {
        return crear(HttpStatus.FORBIDDEN, "Acceso denegado", ex.getMessage(), "forbidden");
    }

    /** Acceso denegado por Spring Security. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail manejarAccesoDenegado(AccessDeniedException ex) {
        return crear(HttpStatus.FORBIDDEN, "Acceso denegado",
                "No cuenta con permisos para realizar esta operacion.", "forbidden");
    }

    // ---------------------------------------------------------------------
    // 404 - Recurso inexistente o de otro tenant
    // ---------------------------------------------------------------------

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return crear(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), "not-found");
    }

    // ---------------------------------------------------------------------
    // 409 - Conflictos (unicidad, transicion, concurrencia optimista)
    // ---------------------------------------------------------------------

    @ExceptionHandler(ConflictoUnicidadException.class)
    public ProblemDetail manejarConflictoUnicidad(ConflictoUnicidadException ex) {
        return crear(HttpStatus.CONFLICT, "Conflicto de unicidad", ex.getMessage(), "conflict");
    }

    @ExceptionHandler(TransicionInvalidaException.class)
    public ProblemDetail manejarTransicionInvalida(TransicionInvalidaException ex) {
        return crear(HttpStatus.CONFLICT, "Transicion de estado invalida", ex.getMessage(), "invalid-state");
    }

    /**
     * Conflicto de concurrencia optimista (version obsoleta, Req 49). Cubre
     * tanto {@code OptimisticLockingFailureException} como su subtipo
     * {@code ObjectOptimisticLockingFailureException}.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail manejarConcurrencia(OptimisticLockingFailureException ex) {
        log.warn("Conflicto de concurrencia optimista: {}", ex.getClass().getSimpleName());
        return crear(HttpStatus.CONFLICT, "Conflicto de concurrencia",
                "Otro Usuario modifico este registro. Vuelva a cargarlo e intente de nuevo.", "concurrency");
    }

    // ---------------------------------------------------------------------
    // 429 - Limite de tasa superado (rate limiting por IP)
    // ---------------------------------------------------------------------

    /**
     * Limite de tasa superado por la direccion de origen (Req 2.4). El mensaje
     * es generico y no revela contadores ni ventanas internas. El
     * {@code Retry-After} lo agrega, cuando aplica, el filtro que detecta el
     * exceso; aqui solo se formatea el cuerpo Problem Details de forma uniforme.
     */
    @ExceptionHandler(LimiteSolicitudesExcedidoException.class)
    public ProblemDetail manejarLimiteSolicitudes(LimiteSolicitudesExcedidoException ex) {
        return crear(HttpStatus.TOO_MANY_REQUESTS, "Limite de solicitudes excedido",
                ex.getMessage(), "rate-limit");
    }

    // ---------------------------------------------------------------------
    // 422 - Regla de negocio incumplida
    // ---------------------------------------------------------------------

    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail manejarReglaNegocio(ReglaNegocioException ex) {
        return crear(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio no cumplida",
                ex.getMessage(), "business-rule");
    }

    // ---------------------------------------------------------------------
    // 500 - Fallback: infraestructura y errores no previstos (sin fugas)
    // ---------------------------------------------------------------------

    /**
     * Ultimo recurso: cualquier excepcion no contemplada se traduce a un 500
     * generico. El detalle tecnico se registra en el servidor y jamas se
     * devuelve al cliente para no filtrar internals (Req 56.4).
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarGenerico(Exception ex) {
        log.error("Error no controlado", ex);
        return crear(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", MSG_GENERICO, "internal");
    }

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    /**
     * Construye un {@link ProblemDetail} con los campos estandar de RFC 7807 y,
     * si esta disponible, el identificador de correlacion del {@link MDC}.
     *
     * @param status  codigo HTTP resultante
     * @param titulo  titulo breve del problema
     * @param detalle mensaje de negocio en espanol (nunca detalles internos)
     * @param tipo    sufijo del campo {@code type} (namespace del error)
     */
    private ProblemDetail crear(HttpStatus status, String titulo, String detalle, String tipo) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status,
                detalle != null ? detalle : MSG_GENERICO);
        pd.setTitle(titulo);
        pd.setType(URI.create(BASE_TIPO + tipo));
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            pd.setProperty("traceId", traceId);
        }
        return pd;
    }
}
