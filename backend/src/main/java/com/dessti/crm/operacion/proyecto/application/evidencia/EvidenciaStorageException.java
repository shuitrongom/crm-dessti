package com.dessti.crm.operacion.proyecto.application.evidencia;

/**
 * Error de infraestructura del almacen de evidencias (escritura/lectura). Es una
 * excepcion no comprobada: el {@code ManejadorGlobalErrores} la traduce al 500
 * generico sin filtrar detalles internos. No se usa para reglas de negocio (para
 * eso estan {@code ReglaNegocioException}/{@code RecursoNoEncontradoException}).
 */
public class EvidenciaStorageException extends RuntimeException {

    public EvidenciaStorageException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public EvidenciaStorageException(String mensaje) {
        super(mensaje);
    }
}
