package com.dessti.crm.platform.empresas;

import java.util.UUID;

/**
 * Puerto de consulta que indica si una Empresa (tenant) tiene datos de negocio
 * <strong>especificos del vertical</strong> de un Giro dado (Req 3.1, 3.2).
 *
 * <p>Lo consume {@code ServicioEmpresas.cambiarGiro} para aplicar la regla del
 * Requisito 3: el cambio de Giro de una Empresa solo se permite si la Empresa
 * <em>no</em> tiene datos de negocio del vertical de su Giro <strong>actual</strong>;
 * si los tiene, el cambio se rechaza con 422 para no corromper esos datos
 * (Req 3.2).</p>
 *
 * <h2>Decision de diseno: un unico puerto agregador (no una {@code List})</h2>
 * <p>Cada Modulo_Vertical (anuncios, manufactura, ...) sabe si una Empresa tiene
 * datos suyos, pero esa deteccion vive en cada vertical, que <strong>aun no esta
 * extraido</strong> (la extraccion es el bloque 8; los verticales aportan su
 * deteccion en los bloques 8/12). Se elige un <strong>unico puerto</strong> que
 * responde por clave de Giro en lugar de inyectar una {@code List<DatosVerticalPort>}
 * en el servicio, porque:</p>
 * <ul>
 *   <li>mantiene {@code ServicioEmpresas} desacoplado del numero de verticales y
 *       de su ciclo de vida (Req 4.2/5.4: el Nucleo no depende de implementaciones
 *       concretas de vertical);</li>
 *   <li>la pregunta es puntual —"¿tiene la Empresa datos del vertical de
 *       <em>este</em> Giro?"— y se resuelve por {@code giroClave}, sin que el
 *       servicio itere ni conozca los verticales;</li>
 *   <li>cuando existan varios verticales, un <strong>adaptador agregador</strong>
 *       podra implementar este mismo puerto delegando en cada
 *       {@code ContratoVertical}/deteccion por giro, sin tocar el servicio ni
 *       este contrato (extensible sin sobre-ingenieria hoy).</li>
 * </ul>
 *
 * <h2>Implementacion por defecto (placeholder) mientras no hay verticales</h2>
 * <p>Como HOY ningun vertical esta extraido, no existe implementacion real de
 * este puerto. Siguiendo el patron del proyecto ({@code PlanModulosPort} +
 * {@code @ConditionalOnMissingBean}), {@link DatosVerticalConfig} registra un
 * placeholder ({@link DatosVerticalNingunoPorDefecto}) que devuelve
 * {@code false} (ninguna Empresa tiene datos de vertical todavia). Asi
 * {@code cambiarGiro} funciona de extremo a extremo desde ya y se vuelve
 * <strong>estricto automaticamente</strong> en cuanto un vertical (bloque 8/12)
 * aporte su propia implementacion (o un adaptador agregador la componga),
 * desplazando al placeholder.</p>
 */
public interface DatosVerticalPort {

    /**
     * Indica si la Empresa indicada tiene datos de negocio especificos del
     * vertical del Giro dado (Req 3.1, 3.2).
     *
     * @param tenantId  identificador de la Empresa (tenant); nunca se toma de la
     *                  peticion sino del contexto de la operacion (Req 8.2).
     * @param giroClave clave canonica del Giro cuyo vertical se consulta (p. ej.
     *                  {@code anuncios-luminosos}); es la clave del Giro
     *                  <strong>actual</strong> de la Empresa al evaluar un cambio.
     * @return {@code true} si la Empresa tiene datos del vertical de ese Giro (lo
     *         que impide cambiar el Giro, 422, Req 3.2); {@code false} si no los
     *         tiene (el cambio se permite, Req 3.1).
     */
    boolean tieneDatosDeVertical(UUID tenantId, String giroClave);
}
