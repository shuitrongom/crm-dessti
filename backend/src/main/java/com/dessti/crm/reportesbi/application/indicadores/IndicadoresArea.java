package com.dessti.crm.reportesbi.application.indicadores;

import java.util.List;

/**
 * Conjunto inmutable de {@link ValorIndicador indicadores} agregados de un
 * {@link AreaIndicador area} concreta (Req 22.1, 48.1). Es el resultado que devuelve
 * cada {@link IndicadorAreaPort puerto de indicadores}: agrupa la lista de metricas de
 * solo lectura del area para un {@link FiltroIndicadores filtro} dado.
 *
 * @param area        area a la que pertenecen los indicadores; obligatoria.
 * @param indicadores lista de indicadores agregados del area; nunca {@code null}
 *                    (puede estar vacia cuando no hay datos o el area aun no tiene un
 *                    adaptador concreto conectado).
 */
public record IndicadoresArea(AreaIndicador area, List<ValorIndicador> indicadores) {

    /**
     * Copia defensiva de la lista para preservar la inmutabilidad del registro.
     *
     * @throws IllegalArgumentException si {@code area} o {@code indicadores} es nulo.
     */
    public IndicadoresArea {
        if (area == null) {
            throw new IllegalArgumentException("El area de los indicadores es obligatoria.");
        }
        if (indicadores == null) {
            throw new IllegalArgumentException("La lista de indicadores es obligatoria.");
        }
        indicadores = List.copyOf(indicadores);
    }

    /**
     * Crea un conjunto de indicadores vacio para el area indicada. Lo usan los
     * adaptadores por defecto ({@code @ConditionalOnMissingBean}) mientras el modulo
     * de cada area no aporta su adaptador concreto, de modo que el Tablero se compone
     * y renderiza con el area presente pero sin metricas (Req 22.1, 22.2).
     *
     * @param area area a representar; obligatoria.
     * @return un {@link IndicadoresArea} con lista vacia.
     */
    public static IndicadoresArea vacio(AreaIndicador area) {
        return new IndicadoresArea(area, List.of());
    }
}
