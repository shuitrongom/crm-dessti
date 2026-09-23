package com.dessti.crm.platform.empresas.rest;

import java.util.List;

/**
 * Respuesta de {@code GET /empresa/modulos}: la lista de claves canonicas de los
 * modulos <strong>efectivamente contratados</strong> por la Empresa (tenant) del
 * Usuario autenticado (Req 25.4).
 *
 * <p>Es la <em>version viva</em> del claim {@code modulos} del JWT: mientras el
 * claim se congela al emitir el token (y solo se refresca al re-iniciar sesion),
 * este endpoint devuelve el estado ACTUAL, resuelto en el momento de la consulta
 * por la MISMA fuente unica de verdad
 * ({@link com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort}). Asi el
 * frontend puede repintar el menu tras un cambio de Plan sin exigir re-login.</p>
 *
 * <p>Las claves coinciden EXACTAMENTE con las del catalogo de modulos y con las
 * del claim {@code modulos} del JWT. Una Empresa sin modulos habilitados devuelve
 * la lista <em>vacia</em> ({@code []}: cero modulos, distinto de "sin
 * restriccion").</p>
 *
 * @param modulos claves canonicas de los modulos habilitados (nunca {@code null};
 *                lista posiblemente vacia).
 */
public record ModulosEmpresaDto(List<String> modulos) {

    public ModulosEmpresaDto {
        // Normaliza a lista inmutable no nula: el contrato garantiza siempre un
        // arreglo (vacio si no hay modulos), nunca ausente/null.
        modulos = (modulos == null) ? List.of() : List.copyOf(modulos);
    }
}
