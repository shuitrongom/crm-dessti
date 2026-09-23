package com.dessti.crm.tesoreria.application;

import java.time.LocalDate;
import java.util.List;

/**
 * Comando de importacion de un Estado_Cuenta_Bancario para una Cuenta_Bancaria
 * (Req 43.2). Objeto de entrada de la capa de aplicacion, distinto de la entidad y
 * del contrato del puerto. No incluye el {@code tenant_id} (se deriva del contexto,
 * Req 23.4) ni la Cuenta_Bancaria (viaja como argumento del caso de uso).
 *
 * <p>{@code movimientos} permite proveer directamente las lineas a importar (util
 * para el stub y las pruebas); un adaptador de importacion real puede obtenerlas de
 * la fuente (archivo/API) y este campo quedaria {@code null} o vacio.</p>
 *
 * @param referenciaArchivo referencia del origen (archivo/API); opcional.
 * @param periodoInicio     inicio del periodo del estado de cuenta; obligatorio.
 * @param periodoFin        fin del periodo del estado de cuenta; obligatorio.
 * @param movimientos       lineas a importar; opcional.
 */
public record ImportarEstadoCuentaCommand(
        String referenciaArchivo,
        LocalDate periodoInicio,
        LocalDate periodoFin,
        List<MovimientoImportado> movimientos) {
}
