package com.dessti.crm.operacion.inventario.application;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de entrada del inventario para la ENTRADA de Materiales por una
 * Recepcion_Mercancia (Req 32.4). Expone el <strong>mecanismo</strong> que el
 * submodulo de Recepciones (bloque 27) invocara para dar de alta en existencias
 * los Materiales recibidos contra una Orden_Compra, registrando un
 * Movimiento_Inventario de tipo {@code entrada} por cada Material e incrementando
 * su cantidad de existencias.
 *
 * <p>Es el <em>simetrico</em> de {@link ConsumoMaterialPort} (que registra
 * {@code salida} por consumo de una Orden_Fabricacion): aqui se registra
 * {@code entrada} por recepcion de compra. Se define como interfaz estable en la
 * capa de aplicacion del inventario para que el modulo consumidor dependa del
 * contrato y no de la implementacion, preservando la arquitectura hexagonal. La
 * implementacion es {@code ServicioInventario}, que reutiliza el mismo mecanismo
 * transaccional y de auditoria del consumo (Req 18.4) con
 * {@code TipoMovimientoInventario.ENTRADA}.</p>
 *
 * <p><strong>Coordinacion (bloque 27):</strong> cuando y con que entradas se
 * invoca lo decide la logica de recepcion (ServicioRecepciones), tras validar la
 * precondicion de estado de la Orden_Compra (Req 32.2) y el tope acumulado por
 * partida (Req 32.3); aqui solo se implementa el mecanismo de entrada y su
 * semantica transaccional y de auditoria.</p>
 */
public interface RecepcionMaterialPort {

    /**
     * Da de alta en inventario una lista de Materiales recibidos por una
     * Recepcion_Mercancia, registrando un Movimiento_Inventario de tipo
     * {@code entrada} por cada Material e incrementando la cantidad correspondiente
     * de sus existencias (Req 32.4). Al ejecutarse dentro de una transaccion, si
     * algun renglon se rechaza (por datos invalidos o Material inaccesible) se
     * revierten todas las entradas de la operacion (atomicidad).
     *
     * @param recepcionId Recepcion_Mercancia de origen de la entrada; obligatoria.
     * @param entradas    lineas de entrada (Material + cantidad); no vacia. Se
     *                    reutiliza el objeto de valor {@link ConsumoMaterial}
     *                    (Material + cantidad), cuyo campo {@code cantidad}
     *                    representa aqui la cantidad recibida a ingresar.
     * @return los movimientos de {@code entrada} registrados, en el mismo orden de
     *         entrada.
     * @throws com.dessti.crm.platform.error.RecursoNoEncontradoException si algun
     *         Material no existe o no es accesible en el tenant (404, Req 23.3).
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si algun dato es
     *         invalido (422).
     */
    List<MovimientoInventarioDto> recibirDeOrdenCompra(
            UUID recepcionId, List<ConsumoMaterial> entradas);
}
