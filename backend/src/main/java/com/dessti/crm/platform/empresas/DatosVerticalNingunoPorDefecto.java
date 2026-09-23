package com.dessti.crm.platform.empresas;

import java.util.UUID;

/**
 * Implementacion <strong>por defecto</strong> (placeholder) de
 * {@link DatosVerticalPort} que responde que ninguna Empresa tiene datos de
 * vertical: siempre devuelve {@code false} (Req 3.1).
 *
 * <h2>Por que existe</h2>
 * <p>Los Modulos_Vertical (anuncios, manufactura, ...) aun no estan extraidos
 * (esa extraccion es el bloque 8; los verticales aportan su deteccion de datos
 * en los bloques 8/12). Mientras tanto no hay quien sepa si una Empresa tiene
 * datos de un vertical, de modo que la respuesta correcta y conservadora es
 * "ninguna Empresa tiene datos de vertical todavia": asi
 * {@code ServicioEmpresas.cambiarGiro} puede funcionar de extremo a extremo
 * (permitiendo el cambio, Req 3.1) sin bloquear el desarrollo del Nucleo.</p>
 *
 * <h2>Sustituibilidad (patron del proyecto)</h2>
 * <p>Se registra como bean {@link DatosVerticalPort} mediante el metodo
 * {@code @Bean} {@code @ConditionalOnMissingBean} de {@link DatosVerticalConfig}
 * (mismo patron que {@code ImportacionBancariaConfig}/{@code ReportesBiConfig}):
 * en cuanto exista otra implementacion de {@link DatosVerticalPort} —la que
 * aporte cada Modulo_Vertical, o un adaptador agregador que componga las
 * detecciones por giro (bloque 8/12)— este placeholder deja de registrarse y el
 * cambio de Giro se vuelve <strong>estricto</strong> automaticamente (Req 3.2),
 * sin tocar {@code ServicioEmpresas}.</p>
 *
 * <p>TODO(bloque 8/12): cuando el Vertical_Anuncios (y despues Manufactura) se
 * extraigan, cada Modulo_Vertical aportara su propia deteccion de datos de
 * vertical por tenant (o un adaptador agregador la compondra por
 * {@code giroClave}) que desplazara a este placeholder.
 */
public class DatosVerticalNingunoPorDefecto implements DatosVerticalPort {

    /**
     * {@inheritDoc}
     *
     * <p>Devuelve siempre {@code false}: hasta que exista un Modulo_Vertical con
     * su deteccion de datos, se considera que ninguna Empresa tiene datos de
     * vertical (Req 3.1).</p>
     */
    @Override
    public boolean tieneDatosDeVertical(UUID tenantId, String giroClave) {
        return false;
    }
}
