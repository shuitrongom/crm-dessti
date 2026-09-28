package com.dessti.crm.reportesbi.application.ia;

import java.time.LocalDate;
import java.util.List;

/**
 * Solicitud de generacion de insights ejecutivos a partir del consolidado de
 * Inteligencia de Negocio (Req 48, suite BI+IA). Es un <em>record</em> inmutable,
 * agnostico del proveedor, que reune el contexto del periodo analizado y la lista de
 * indicadores agregados (con sus comparativos) que el {@link GeneradorInsightsPort}
 * debe interpretar para producir un resumen ejecutivo en espanol de Mexico.
 *
 * <p>No transporta secretos ni credenciales: estos viven en la configuracion del
 * adaptador ({@code crm.ia.*}) y nunca cruzan la frontera de la aplicacion.</p>
 *
 * @param desde       inicio del periodo analizado (inclusivo); {@code null} si no se filtro.
 * @param hasta       fin del periodo analizado (inclusivo); {@code null} si no se filtro.
 * @param area        etiqueta del area filtrada; {@code null} si abarca todas las areas.
 * @param indicadores indicadores agregados del periodo; nunca {@code null} (puede estar
 *                    vacia cuando no hay datos).
 */
public record SolicitudInsights(
        LocalDate desde,
        LocalDate hasta,
        String area,
        List<IndicadorInsight> indicadores) {

    /**
     * Copia defensiva de la lista para preservar la inmutabilidad del registro.
     *
     * @throws IllegalArgumentException si {@code indicadores} es nulo.
     */
    public SolicitudInsights {
        if (indicadores == null) {
            throw new IllegalArgumentException("La lista de indicadores es obligatoria.");
        }
        indicadores = List.copyOf(indicadores);
    }

    /**
     * Indica si la solicitud no aporta indicadores con datos, en cuyo caso el resumen
     * debe reflejar la ausencia de informacion en lugar de inventar hallazgos.
     *
     * @return {@code true} si no hay indicadores.
     */
    public boolean sinDatos() {
        return indicadores.isEmpty();
    }
}
