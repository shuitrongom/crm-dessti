package com.dessti.crm.operacion.inventario.application;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de entrada del inventario para el consumo de Materiales por una
 * Orden_Fabricacion (Req 18.4). Expone el <strong>mecanismo</strong> que el modulo de
 * Ordenes de Fabricacion (bloques 19/22) invocara para descontar los Materiales que se
 * consumen al fabricar, registrando un Movimiento_Inventario de tipo {@code salida} por
 * cada Material y descontando su cantidad de las existencias.
 *
 * <p>Se define como interfaz estable en la capa de aplicacion del inventario para que el
 * modulo consumidor dependa del contrato y no de la implementacion, preservando la
 * arquitectura hexagonal (mismo principio que {@code CotizacionParaFabricacionPort}). La
 * implementacion es {@code ServicioInventario}.</p>
 *
 * <p><strong>Coordinacion (bloques 19/22):</strong> el cableado completo desde la
 * Orden_Fabricacion (cuando y con que consumos se invoca) corresponde a la logica de
 * fabricacion/instalacion de esos bloques; aqui se implementa el mecanismo de consumo y
 * su semantica transaccional y de auditoria.</p>
 */
public interface ConsumoMaterialPort {

    /**
     * Consume una lista de Materiales asociada a una Orden_Fabricacion, registrando un
     * Movimiento_Inventario de tipo {@code salida} por cada Material y descontando la
     * cantidad correspondiente de sus existencias (Req 18.4). Si alguna salida dejaria
     * las existencias por debajo de 0, se rechaza con
     * {@link com.dessti.crm.platform.error.ReglaNegocioException} (422) y, al ejecutarse
     * dentro de una transaccion, ningun consumo de la operacion se persiste
     * (atomicidad, Property 9).
     *
     * @param ordenFabricacionId Orden_Fabricacion de origen del consumo; obligatoria.
     * @param consumos           lineas de consumo (Material + cantidad); no vacia.
     * @return los movimientos de {@code salida} registrados, en el mismo orden de entrada.
     * @throws com.dessti.crm.platform.error.RecursoNoEncontradoException si algun
     *         Material no existe o no es accesible en el tenant (404, Req 23.3).
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si algun consumo dejaria
     *         existencias negativas o los datos son invalidos (422, Req 18.3).
     */
    List<MovimientoInventarioDto> consumirParaOrdenFabricacion(
            UUID ordenFabricacionId, List<ConsumoMaterial> consumos);
}
