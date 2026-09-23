package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de dominio de <strong>solo lectura</strong> que provee los indicadores
 * agregados de un {@link AreaIndicador area} de negocio para el Tablero (Req 22) y el
 * analisis consolidado de Inteligencia de Negocio (Req 48). Es la pieza central del
 * diseno del modulo reportes-bi.
 *
 * <h2>Por que puertos y no dependencias directas</h2>
 * <p>El Tablero AGREGA indicadores de casi todos los modulos del sistema (comercial,
 * produccion, instalacion, mantenimiento, inventario, compras, finanzas, RH, tesoreria,
 * CxP, estrategia, presupuesto, redes sociales, ...). Si {@code ServicioTablero}
 * importara los servicios/entidades de cada modulo se crearia una telarana de
 * dependencias que ademas impediria compilar este modulo de forma independiente y
 * colisionaria con el trabajo en paralelo de otros equipos. En su lugar, reportes-bi
 * <strong>declara</strong> este puerto (uno por area, ver las subinterfaces del
 * paquete) y consume unicamente la abstraccion. Cada modulo de area podra aportar mas
 * adelante su adaptador concreto (que lee sus propios repositorios de solo lectura) sin
 * tocar este modulo. Mientras tanto se registra un adaptador por defecto
 * ({@code @ConditionalOnMissingBean}) que devuelve {@link IndicadoresArea#vacio(AreaIndicador)
 * indicadores vacios}, de modo que el modulo compila y el Tablero renderiza el area con
 * cero metricas hasta que exista el adaptador real. Sigue el mismo patron que
 * {@code NotificadorStockPort} + {@code NotificadorStockRegistroLog} del inventario.</p>
 *
 * <h2>Solo lectura (Req 22.2, 48.2)</h2>
 * <p>Los adaptadores de este puerto NUNCA modifican los datos de origen: son
 * agregaciones puras. El aislamiento por tenant (Req 23, 48.5) lo garantiza cada
 * adaptador concreto derivando el {@code tenant_id} del {@code TenantContext} y
 * apoyandose en el filtro global de Hibernate y la RLS de su propio modulo.</p>
 */
public interface IndicadorAreaPort {

    /**
     * Area de negocio que cubre este puerto (Req 22.1). Permite al servicio agrupar
     * los indicadores por area y aplicar el filtro por area del Req 48.4.
     *
     * @return el area de negocio de este puerto; nunca {@code null}.
     */
    AreaIndicador area();

    /**
     * Calcula los indicadores agregados del area para el filtro indicado, en modo
     * <strong>solo lectura</strong> (Req 22.2, 48.2). No debe modificar dato alguno.
     *
     * @param filtro filtro por fecha/Cliente/area/dimension (Req 22.3, 48.4); nunca
     *               {@code null}. Un criterio nulo dentro del filtro no restringe.
     * @return el conjunto de indicadores del area; nunca {@code null} (puede ser
     *         {@link IndicadoresArea#vacio(AreaIndicador) vacio}).
     */
    IndicadoresArea agregar(FiltroIndicadores filtro);
}
