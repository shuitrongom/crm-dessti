package com.dessti.crm.contabilidad.polizas.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.contabilidad.polizas.domain.TipoPoliza;

/**
 * Puerto de entrada del submodulo de Polizas Contables que los <strong>productores
 * de eventos contables</strong> (facturacion, inventario, pagos, compras) pueden
 * invocar para generar una Poliza_Contable balanceada ante un evento relevante
 * (Req 38.2). Es la frontera hexagonal que desacopla a esos modulos de la
 * implementacion concreta de la contabilidad.
 *
 * <h2>Alcance del bloque 30 (decision documentada)</h2>
 * <p>Este bloque implementa la <strong>capacidad</strong> de generar polizas
 * balanceadas y garantiza el balance (funcion pura del dominio, Property 16), pero
 * <em>no</em> cablea todavia cada fuente de evento (factura timbrada,
 * movimiento_inventario, etc.). Los modulos productores podran invocar
 * {@link #generarPolizaDeEvento(LocalDate, TipoPoliza, String, String, UUID, List)}
 * cuando se integren, sin re-abrir la contabilidad. Retro-encajar llamadas en cada
 * modulo excede el alcance de este bloque y se documenta como trabajo posterior para
 * mantener el bloque cohesivo y compilable.</p>
 */
public interface PolizaContablePort {

    /**
     * Genera y persiste una Poliza_Contable balanceada a partir de los renglones de
     * un evento contable (Req 38.2). El balance (suma de cargos == suma de abonos) lo
     * garantiza el dominio: si los renglones no balancean, se rechaza con 422
     * informando la diferencia y no se persiste nada (Req 38.4). Audita el registro
     * (Req 38.7).
     *
     * @param fecha     fecha contable de la poliza; obligatoria.
     * @param tipo      tipo de poliza; obligatorio.
     * @param concepto  concepto descriptivo; obligatorio y no vacio.
     * @param origen    evento contable de origen (por ejemplo {@code 'factura_timbrada'}).
     * @param origenId  identificador del recurso de origen; opcional.
     * @param renglones renglones de cargo/abono; obligatorio y balanceado.
     * @return el identificador de la Poliza_Contable generada.
     */
    UUID generarPolizaDeEvento(LocalDate fecha, TipoPoliza tipo, String concepto,
                               String origen, UUID origenId, List<RenglonPolizaCommand> renglones);
}
