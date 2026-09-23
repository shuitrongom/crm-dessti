package com.dessti.crm.reportesbi.application.indicadores;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtro inmutable compartido por todos los {@link IndicadorAreaPort puertos de
 * indicadores} del modulo reportes-bi (Req 22.3, 48.4). Acota las agregaciones de
 * <strong>solo lectura</strong> por un rango de fechas opcional y, cuando aplica, por
 * un Cliente (Req 22.3) y por una dimension de analisis (Req 48.4).
 *
 * <p>Es un objeto de valor libre de dependencias de framework: la capa de aplicacion
 * lo construye a partir de los parametros de la peticion y lo pasa a cada puerto. Un
 * campo {@code null} significa "no filtrar por ese criterio"; los puertos concretos
 * que aporten los modulos de cada area (comercial, produccion, finanzas, social, etc.)
 * lo interpretan de forma coherente. El {@code tenant_id} NUNCA viaja en este filtro:
 * se deriva del {@code TenantContext} en los adaptadores concretos (Req 23.4, 48.5).</p>
 *
 * @param desde     inicio del periodo (inclusivo); {@code null} no filtra por fecha.
 * @param hasta     fin del periodo (inclusivo); {@code null} no filtra por fecha.
 * @param clienteId Cliente a acotar (Req 22.3); {@code null} incluye a todos.
 * @param area      etiqueta ASCII del area a acotar (Req 48.4); {@code null} incluye a
 *                  todas. Coherente con {@link AreaIndicador#etiqueta()}.
 * @param dimension dimension de analisis opcional (Req 48.4), p. ej. {@code "cliente"},
 *                  {@code "canal"}, {@code "periodo"}; {@code null} no segmenta.
 */
public record FiltroIndicadores(
        LocalDate desde,
        LocalDate hasta,
        UUID clienteId,
        String area,
        String dimension) {

    /**
     * Crea un filtro solo con rango de fechas y Cliente, tipico del Tablero por area
     * (Req 22.3), sin restriccion de area ni dimension.
     *
     * @param desde     inicio del periodo (inclusivo); opcional.
     * @param hasta     fin del periodo (inclusivo); opcional.
     * @param clienteId Cliente a acotar; opcional.
     * @return el filtro del Tablero por area.
     */
    public static FiltroIndicadores deTablero(LocalDate desde, LocalDate hasta, UUID clienteId) {
        return new FiltroIndicadores(desde, hasta, clienteId, null, null);
    }

    /**
     * Crea un filtro para el analisis consolidado de Inteligencia de Negocio, que
     * admite ademas area y dimension (Req 48.4).
     *
     * @param desde     inicio del periodo (inclusivo); opcional.
     * @param hasta     fin del periodo (inclusivo); opcional.
     * @param area      area a acotar; opcional.
     * @param dimension dimension de analisis; opcional.
     * @return el filtro del analisis consolidado.
     */
    public static FiltroIndicadores deConsolidado(LocalDate desde, LocalDate hasta,
                                                  String area, String dimension) {
        return new FiltroIndicadores(desde, hasta, null, area, dimension);
    }

    /**
     * Indica si el filtro restringe al area indicada. Un filtro sin area
     * ({@code area == null} o en blanco) incluye a todas las areas.
     *
     * @param candidata area a comprobar; obligatoria.
     * @return {@code true} si el filtro no restringe area o si coincide con la
     *         candidata (sin distinguir mayusculas).
     */
    public boolean incluyeArea(AreaIndicador candidata) {
        if (area == null || area.isBlank()) {
            return true;
        }
        return candidata != null && candidata.etiqueta().equalsIgnoreCase(area.trim());
    }
}
