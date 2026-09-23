package com.dessti.crm.platform.security.rbac;

/**
 * Convencion de nombres de las operaciones atomicas de los permisos RBAC
 * (Req 3).
 *
 * <p>Estas constantes estandarizan el vocabulario de operaciones que se combinan
 * con un recurso para formar un {@link Permiso} (p. ej.
 * {@code Permiso.de("cliente", Operaciones.CREAR)}). No es una enumeracion
 * cerrada: un modulo puede definir operaciones especificas (p. ej.
 * {@code timbrar}, {@code cambiar_estado}); estas constantes cubren las
 * operaciones CRUD y de listado mas frecuentes para evitar cadenas magicas
 * dispersas por el codigo.</p>
 *
 * <p>El nombrado sigue la convencion en minusculas y sin espacios, coherente con
 * la normalizacion de {@link Permiso}.</p>
 */
public final class Operaciones {

    private Operaciones() {
        // Utilidad de constantes: no instanciable.
    }

    /** Alta de un recurso. */
    public static final String CREAR = "crear";
    /** Consulta de un recurso individual. */
    public static final String LEER = "leer";
    /** Listado paginado de recursos. */
    public static final String LISTAR = "listar";
    /** Modificacion de un recurso existente. */
    public static final String ACTUALIZAR = "actualizar";
    /** Eliminacion o baja logica de un recurso. */
    public static final String ELIMINAR = "eliminar";
    /** Cambio de estado dentro de una maquina de estados. */
    public static final String CAMBIAR_ESTADO = "cambiar_estado";
    /** Exportacion de datos del recurso. */
    public static final String EXPORTAR = "exportar";
}
