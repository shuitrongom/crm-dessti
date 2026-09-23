package com.dessti.crm.rhnomina.nomina.application;

/**
 * Comando de aplicacion para crear una Nomina de un Periodo_Nomina (Req 41.1). El
 * {@code tenant_id} y el actor se derivan del contexto de seguridad (Req 23.4); no
 * viajan en el comando.
 *
 * @param periodoNomina codigo del Periodo_Nomina en formato {@code AAAA-MM}
 *                      (coherente con V32); obligatorio.
 */
public record CrearNominaCommand(String periodoNomina) {
}
