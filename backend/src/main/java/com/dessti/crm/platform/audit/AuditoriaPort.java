package com.dessti.crm.platform.audit;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de dominio del servicio de auditoria inmutable (Req 10). Los casos de
 * uso de todos los modulos dependen de esta interfaz (no de su implementacion)
 * para registrar eventos relevantes y para consultar/exportar la bitacora,
 * preservando la portabilidad del nucleo.
 *
 * <p>La bitacora es <strong>append-only</strong> y esta <strong>encadenada por
 * hash</strong> (SHA-256): la interfaz solo permite <em>agregar</em> eventos y
 * <em>leer</em>; no ofrece operaciones de modificacion ni borrado (Req 10.4).</p>
 *
 * <p><strong>Sin secretos (Req 10.10):</strong> quien invoca
 * {@link #registrar(EventoAuditoria)} es responsable de no incluir contrasenas,
 * claves ni credenciales en el detalle ni en los valores anterior/nuevo.</p>
 *
 * <p>La deteccion de patrones (Alerta_Auditoria) y la verificacion de
 * integridad de la cadena bajo demanda NO forman parte de este puerto en la
 * Tarea 7.1; se anaden en la Tarea 7.2.</p>
 */
public interface AuditoriaPort {

    /**
     * Registra un evento de auditoria, calculando su hash encadenado a partir
     * del contenido del evento y del hash del ultimo registro de la cadena
     * (Req 10.4, 10.7). El {@code trace_id} se toma del contexto de correlacion
     * (MDC) si esta disponible (Req 10.12) y la marca temporal se fija en UTC.
     *
     * @param evento evento a registrar; no debe contener secretos (Req 10.10).
     * @return la vista de solo lectura del registro persistido, incluyendo su
     *         {@code hashActual} y {@code hashPrevio}.
     */
    RegistroAuditoriaView registrar(EventoAuditoria evento);

    /**
     * Registra especificamente un evento de <em>lectura o exportacion de datos
     * sensibles</em> (Req 10.6). Es un atajo semantico sobre
     * {@link #registrar(EventoAuditoria)} que marca la accion como acceso a
     * datos sensibles. Lo invocaran, en tareas futuras, los modulos que
     * manejan datos sensibles (por ejemplo nomina o facturacion) al leer o
     * exportar dichos datos; en esta tarea solo se deja disponible el punto de
     * extension.
     *
     * @param evento evento que describe el acceso (actor, recurso, tenant,
     *               detalle sin secretos).
     * @return la vista del registro persistido.
     */
    RegistroAuditoriaView registrarAccesoDatosSensibles(EventoAuditoria evento);

    /**
     * Consulta la bitacora de forma paginada y filtrable por actor, tipo de
     * recurso y rango de fechas (Req 10.5).
     *
     * @param filtro    criterios de filtrado (todos opcionales).
     * @param pageable  parametros de paginacion (pagina/tamano).
     * @return una pagina de vistas de registros que cumplen el filtro, ordenada
     *         de forma estable por {@code id}.
     */
    Page<RegistroAuditoriaView> consultar(FiltroAuditoria filtro, Pageable pageable);

    /**
     * Exporta la bitacora que cumple el filtro indicado (Req 10.9). Devuelve la
     * secuencia completa (no paginada) ordenada de forma estable por {@code id},
     * apta para volcarse a un formato estructurado por el adaptador de entrada.
     *
     * @param filtro criterios de filtrado (todos opcionales).
     * @return la lista de vistas de registros que cumplen el filtro.
     */
    List<RegistroAuditoriaView> exportar(FiltroAuditoria filtro);
}
