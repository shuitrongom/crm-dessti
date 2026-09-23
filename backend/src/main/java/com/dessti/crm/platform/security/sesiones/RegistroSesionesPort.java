package com.dessti.crm.platform.security.sesiones;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de dominio del almacen de Sesiones (Token_Refresco) y de la denylist
 * de refresco revocados (Req 68). Los casos de uso de autenticacion y de
 * gestion de usuarios dependen de esta interfaz (no de su implementacion),
 * preservando la portabilidad del nucleo.
 *
 * <p><strong>Modelo elegido (Req 68.3):</strong> se persiste una <b>fila por
 * Sesion</b> con una bandera {@code revocado}, en lugar de una denylist
 * separada. Esta modalidad permite, con una sola tabla:</p>
 * <ul>
 *   <li><b>Rechazar</b> un Token_Refresco revocado consultando {@code jti}
 *       ({@link #estaRevocado(String)}, Req 1.9, 68.3), y</li>
 *   <li><b>Listar</b> las sesiones <em>activas</em> (no revocadas y no
 *       expiradas) de una cuenta de forma paginada
 *       ({@link #listarActivasDeUsuario(UUID, Clock, Pageable)}, Req 68.5).</li>
 * </ul>
 *
 * <p>Un {@code jti} desconocido se trata como <b>revocado</b> por
 * {@link #estaRevocado(String)}: si un Token_Refresco no fue registrado al
 * emitirse (por ejemplo, tras una purga o un almacen inconsistente) se rechaza
 * de forma conservadora, cerrando el acceso ante la duda (Req 68).</p>
 */
public interface RegistroSesionesPort {

    /**
     * Registra una Sesion (Token_Refresco) recien emitida (Req 68.3). Es
     * idempotente respecto al {@code jti}: registrar dos veces el mismo
     * {@code jti} no crea filas duplicadas.
     *
     * @param sesion metadatos de la sesion a registrar (sin el valor del token).
     */
    void registrar(RegistroSesion sesion);

    /**
     * Indica si el Token_Refresco identificado por {@code jti} debe rechazarse
     * (Req 1.9, 68.3).
     *
     * @param jti identificador del Token_Refresco (claim {@code jti}).
     * @return {@code true} si el {@code jti} esta revocado o es desconocido;
     *         {@code false} si corresponde a una sesion registrada y vigente.
     */
    boolean estaRevocado(String jti);

    /**
     * Revoca una unica Sesion por su {@code jti} (Req 68.1). Marca la fila como
     * revocada con el motivo y el instante indicados. Si el {@code jti} no
     * existe o ya estaba revocado, la operacion no tiene efecto adicional.
     *
     * @param jti    identificador del Token_Refresco a revocar.
     * @param motivo causa de la revocacion.
     * @return el numero de sesiones revocadas por esta operacion (0 o 1).
     */
    int revocar(String jti, MotivoRevocacion motivo);

    /**
     * Revoca <b>todas</b> las Sesiones vigentes de una cuenta (Req 68.2, 68.4).
     * Se usa en la revocacion por Administrador, por desactivacion de la cuenta
     * y ante un evento de seguridad como el cambio de contrasena.
     *
     * @param usuarioId cuenta cuyas sesiones se revocan.
     * @param motivo    causa de la revocacion.
     * @return el numero de sesiones revocadas por esta operacion.
     */
    int revocarTodasDeUsuario(UUID usuarioId, MotivoRevocacion motivo);

    /**
     * Lista de forma paginada las Sesiones <b>activas</b> (no revocadas y no
     * expiradas segun {@code clock}) de una cuenta (Req 68.5).
     *
     * @param usuarioId cuenta cuyas sesiones activas se consultan.
     * @param clock     reloj (UTC) para descartar las sesiones expiradas.
     * @param pageable  parametros de paginacion (20 por defecto, 100 maximo).
     * @return una pagina de vistas de sesiones activas, ordenada de forma
     *         estable por instante de emision descendente.
     */
    Page<SesionActivaView> listarActivasDeUsuario(UUID usuarioId, Clock clock, Pageable pageable);
}
