package com.dessti.crm.social.analitica.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.reportesbi.application.indicadores.RangoPeriodo;
import com.dessti.crm.social.adapter.out.persistence.MensajeSocialRepository;
import com.dessti.crm.social.adapter.out.persistence.MetricaSocialProjection;
import com.dessti.crm.social.analitica.domain.CalculoMetricasSociales;
import com.dessti.crm.social.analitica.domain.FilaMetricaSocial;
import com.dessti.crm.social.analitica.domain.MetricasSociales;
import com.dessti.crm.social.domain.CanalSocial;

/**
 * Servicio de aplicacion de <strong>solo lectura</strong> que produce las metricas
 * sociales por Canal_Social y periodo (Req 66): alcance, interacciones, Mensajes_Social
 * recibidos y enviados, tiempo de respuesta promedio y conversiones/leads. Todas son
 * <strong>agregaciones que no modifican los datos de origen</strong> (Req 66.1); se
 * apoyan en la consulta de agregacion de solo lectura del
 * {@link MensajeSocialRepository} (acotada al tenant vigente, Req 66.6) y en la
 * funcion pura {@link CalculoMetricasSociales}.
 *
 * <h2>Filtros y exportacion (Req 66.4)</h2>
 * <p>Admite filtro por rango de fechas ({@code desde}/{@code hasta}) y por
 * Canal_Social. El rango se convierte a instantes UTC {@code [desde 00:00,
 * hasta+1 00:00)} para acotar la marca temporal del Mensaje_Social. El resultado se
 * devuelve como un payload estructurado ({@link ResumenAnaliticaSocialDto}) apto para
 * consulta y exportacion.</p>
 *
 * <h2>Segmentacion por canal de venta (Req 66.2, 63)</h2>
 * <p>La segmentacion por Canal_Venta se expresa por el {@code canalVentaId} de la
 * Oportunidad vinculada a la Conversacion. En el modelo actual el modulo social no
 * almacena un vinculo directo Conversacion-Canal_Venta: la relacion se resuelve a
 * traves de la Oportunidad del lead. Este servicio recibe y propaga el
 * {@code canalVentaId} (registrandolo en el payload y la auditoria) y deja el
 * <em>hook</em> {@link #segmentarPorCanalVenta} para que un adaptador futuro aplique
 * el join con la Oportunidad sin romper esta API. Mientras ese adaptador no exista, el
 * filtro se documenta como no restrictivo.</p>
 *
 * <h2>Consolidacion en Tablero/BI (Req 66.3, 22, 48)</h2>
 * <p>El metodo publico {@link #metricasDominio(LocalDate, LocalDate, CanalSocial)}
 * devuelve las metricas como valores de dominio ({@link MetricasSociales}),
 * reutilizables por un futuro adaptador de {@code IndicadorSocialPort} del modulo
 * reportes-bi (bloque 56). Este servicio <strong>no</strong> importa ni referencia el
 * paquete {@code com.dessti.crm.reportesbi}: solo provee el asiento (seam) para no
 * acoplarse ni colisionar con desarrollos en paralelo.</p>
 *
 * <h2>Autorizacion, aislamiento y auditoria</h2>
 * <ul>
 *   <li><strong>403 sin permiso (Req 66.5):</strong> las rutas del controlador exigen
 *       {@code analitica_social:leer} via {@code @PreAuthorize}; sin el permiso
 *       analitico/comercial se responde 403.</li>
 *   <li><strong>Multi-tenant (Req 23, 66.6):</strong> las metricas solo incluyen datos
 *       cuyo {@code tenant_id} coincide con la Empresa del Usuario, derivado del
 *       {@link TenantContext} (nunca de la peticion, Req 23.4).</li>
 *   <li><strong>Auditoria (Req 66.7):</strong> cada consulta y cada exportacion se
 *       audita via {@link AuditoriaPort} con actor, accion, recurso, Canal_Social,
 *       tenant_id y marca UTC.</li>
 * </ul>
 */
@Service
public class ServicioAnaliticaSocial {

    /** Tipo de recurso de auditoria/RBAC de la analitica social. */
    static final String RECURSO = "analitica_social";

    private final MensajeSocialRepository mensajeRepository;
    private final AuditoriaPort auditoria;

    public ServicioAnaliticaSocial(MensajeSocialRepository mensajeRepository,
                                   AuditoriaPort auditoria) {
        this.mensajeRepository = mensajeRepository;
        this.auditoria = auditoria;
    }

    /**
     * Calcula las metricas sociales por Canal_Social y periodo del tenant vigente,
     * con filtros opcionales, para <strong>consulta</strong> (Req 66.1, 66.4). Audita
     * la consulta (Req 66.7).
     *
     * @param desde        inicio del periodo (inclusivo); {@code null} no filtra.
     * @param hasta        fin del periodo (inclusivo); {@code null} no filtra.
     * @param canal        Canal_Social a filtrar; {@code null} incluye todos.
     * @param canalVentaId Canal_Venta al que segmentar (Req 66.2); {@code null} no segmenta.
     * @return el resumen de metricas por canal.
     * @throws ReglaNegocioException si el rango de fechas es invalido (422).
     */
    @Transactional(readOnly = true)
    public ResumenAnaliticaSocialDto consultarMetricas(LocalDate desde, LocalDate hasta,
                                                       CanalSocial canal, UUID canalVentaId) {
        return calcular(desde, hasta, canal, canalVentaId, false);
    }

    /**
     * Calcula las metricas sociales por Canal_Social y periodo del tenant vigente,
     * con filtros opcionales, para <strong>exportacion</strong> (Req 66.4). Devuelve
     * el mismo payload estructurado marcado como exportacion y audita la exportacion
     * (Req 66.7).
     *
     * @param desde        inicio del periodo (inclusivo); {@code null} no filtra.
     * @param hasta        fin del periodo (inclusivo); {@code null} no filtra.
     * @param canal        Canal_Social a filtrar; {@code null} incluye todos.
     * @param canalVentaId Canal_Venta al que segmentar (Req 66.2); {@code null} no segmenta.
     * @return el resumen de metricas por canal para exportar.
     * @throws ReglaNegocioException si el rango de fechas es invalido (422).
     */
    @Transactional(readOnly = true)
    public ResumenAnaliticaSocialDto exportarMetricas(LocalDate desde, LocalDate hasta,
                                                      CanalSocial canal, UUID canalVentaId) {
        return calcular(desde, hasta, canal, canalVentaId, true);
    }

    /**
     * <strong>Asiento (seam) para el Tablero/BI (Req 66.3, 22, 48).</strong> Devuelve
     * las metricas como valores de dominio ({@link MetricasSociales}) del tenant
     * vigente, reutilizables por un futuro adaptador de {@code IndicadorSocialPort}
     * del modulo reportes-bi (bloque 56), sin acoplar este modulo a reportes-bi. No
     * audita (lo hara el consumidor de indicadores) y es de solo lectura (Req 66.1).
     *
     * @param desde inicio del periodo (inclusivo); {@code null} no filtra.
     * @param hasta fin del periodo (inclusivo); {@code null} no filtra.
     * @param canal Canal_Social a filtrar; {@code null} incluye todos.
     * @return las metricas por Canal_Social como valores de dominio.
     * @throws ReglaNegocioException si el rango de fechas es invalido (422).
     */
    @Transactional(readOnly = true)
    public List<MetricasSociales> metricasDominio(LocalDate desde, LocalDate hasta,
                                                  CanalSocial canal) {
        return agregarDominio(desde, hasta, canal, null);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private ResumenAnaliticaSocialDto calcular(LocalDate desde, LocalDate hasta, CanalSocial canal,
                                               UUID canalVentaId, boolean exportar) {
        List<MetricasSociales> metricas = agregarDominio(desde, hasta, canal, canalVentaId);
        auditar(desde, hasta, canal, canalVentaId, exportar);
        String etiquetaCanal = (canal == null) ? null : canal.valorBd();
        return ResumenAnaliticaSocialDto.de(metricas, desde, hasta, etiquetaCanal, canalVentaId, exportar);
    }

    private List<MetricasSociales> agregarDominio(LocalDate desde, LocalDate hasta,
                                                  CanalSocial canal, UUID canalVentaId) {
        validarRango(desde, hasta);
        List<MetricaSocialProjection> proyecciones = mensajeRepository.agregarFilasMetricas(
                canal, inicioDe(desde), finExclusivoDe(hasta));
        List<FilaMetricaSocial> filas = proyecciones.stream()
                .filter(p -> segmentarPorCanalVenta(p, canalVentaId))
                .map(ServicioAnaliticaSocial::aFila)
                .toList();
        // La agregacion es una funcion pura de solo lectura sobre las filas ya
        // acotadas al tenant vigente por el filtro/RLS (Req 66.1, 66.6).
        return CalculoMetricasSociales.porCanal(filas);
    }

    /**
     * Hook de segmentacion por Canal_Venta (Req 66.2, 63). En el modelo actual el
     * modulo social no almacena el vinculo directo Conversacion-Canal_Venta; la
     * relacion se resuelve por la Oportunidad del lead. Mientras no exista un adaptador
     * que aporte ese join, el filtro no es restrictivo (acepta todas las filas). Al
     * introducir dicho adaptador (o una columna de vinculo), esta funcion sera el punto
     * unico donde aplicar la restriccion sin cambiar la API publica del servicio.
     *
     * @param proyeccion   fila fuente de la analitica.
     * @param canalVentaId Canal_Venta objetivo; {@code null} no segmenta.
     * @return {@code true} si la fila debe incluirse en la agregacion.
     */
    private boolean segmentarPorCanalVenta(MetricaSocialProjection proyeccion, UUID canalVentaId) {
        // Sin vinculo directo disponible: no se restringe (Req 66.2 — hook documentado).
        return true;
    }

    private static FilaMetricaSocial aFila(MetricaSocialProjection proyeccion) {
        return new FilaMetricaSocial(
                proyeccion.getTenantId(),
                proyeccion.getCanal(),
                proyeccion.getConversacionId(),
                proyeccion.getDireccion(),
                proyeccion.getInstante(),
                proyeccion.getEsLead());
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ReglaNegocioException(
                    "El fin del periodo no puede ser anterior al inicio.");
        }
    }

    /**
     * Instante UTC del inicio del dia {@code fecha}. Una {@code fecha} nula se traduce a
     * {@link RangoPeriodo#INSTANTE_MINIMO} (epoca Unix) en lugar de {@code null}: el bind
     * viaja SIEMPRE tipado para que PostgreSQL infiera su tipo (evita el error "could not
     * determine data type of parameter" del patron {@code (:desde IS NULL OR ...)}),
     * preservando la semantica "sin limite inferior" para datos reales.
     */
    private Instant inicioDe(LocalDate fecha) {
        return (fecha == null)
                ? RangoPeriodo.INSTANTE_MINIMO
                : fecha.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * Instante UTC del inicio del dia siguiente a {@code fecha} (limite exclusivo). Una
     * {@code fecha} nula se traduce a {@link RangoPeriodo#INSTANTE_MAXIMO} (9999-12-31 UTC)
     * en lugar de {@code null}: el bind viaja SIEMPRE tipado para que PostgreSQL infiera su
     * tipo (evita el error "could not determine data type of parameter" del patron
     * {@code (:hasta IS NULL OR ...)}), preservando la semantica "sin limite superior" para
     * datos reales.
     */
    private Instant finExclusivoDe(LocalDate fecha) {
        return (fecha == null)
                ? RangoPeriodo.INSTANTE_MAXIMO
                : fecha.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private void auditar(LocalDate desde, LocalDate hasta, CanalSocial canal,
                         UUID canalVentaId, boolean exportar) {
        String accion = exportar ? "exportar" : "consultar";
        String etiquetaCanal = (canal == null) ? "todos" : canal.valorBd();
        String detalle = "analitica social [desde=" + desde + ", hasta=" + hasta
                + ", canal=" + etiquetaCanal + ", canalVentaId=" + canalVentaId
                + ", exportar=" + exportar + "]";
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actorActual(), accion, RECURSO, detalle, null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
