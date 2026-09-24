package com.dessti.crm.contabilidad.electronica.application;

/**
 * Vista previa del Polizas_XML antes de exportar (Req 5.1): numero de polizas del
 * periodo y de transacciones (renglones) que se incluiran.
 *
 * @param numeroPolizas      numero de polizas del periodo.
 * @param numeroTransacciones numero total de renglones (cargos/abonos) del periodo.
 */
public record VistaPreviaPolizasDto(
        int numeroPolizas,
        int numeroTransacciones) {
}
