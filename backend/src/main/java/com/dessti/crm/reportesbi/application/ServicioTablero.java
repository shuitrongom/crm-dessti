package com.dessti.crm.reportesbi.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorAreaPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;

/**
 * Servicio de aplicacion de <strong>solo lectura</strong> que compone el Tablero de
 * indicadores por area (Req 22). Reune los indicadores de todas las areas del Req 22.1
 * invocando cada {@link IndicadorAreaPort puerto de indicadores} inyectado, para el
 * filtro por rango de fechas y Cliente indicado (Req 22.3). No modifica ningun dato de
 * origen (Req 22.2): las agregaciones viven en los adaptadores de cada puerto.
 *
 * <h2>Composicion por puertos desacoplados</h2>
 * <p>Spring inyecta la lista de todos los beans {@link IndicadorAreaPort} disponibles
 * (los adaptadores por defecto de {@code ReportesBiConfig} o los concretos que aporte
 * cada modulo). El servicio los indexa por {@link AreaIndicador area} y los presenta en
 * el orden del catalogo, de modo que el Tablero siempre lista todas las areas del
 * Req 22.1 (con cero metricas cuando aun no hay adaptador concreto). Esto evita acoplar
 * reportes-bi a los demas modulos y permite compilar el agregador de forma
 * independiente.</p>
 *
 * <h2>Autorizacion, aislamiento y auditoria (Req 22.5, 22.6, 23)</h2>
 * <ul>
 *   <li><strong>403 sin permiso (Req 22.5):</strong> las rutas del controlador exigen
 *       {@code tablero:leer} (consulta) y {@code reporte:exportar} (exportacion) via
 *       {@code @PreAuthorize}; sin el permiso se responde 403.</li>
 *   <li><strong>Multi-tenant (Req 23):</strong> el {@code tenant_id} se deriva del
 *       {@link TenantContext}; cada adaptador de puerto acota sus lecturas al tenant
 *       vigente.</li>
 *   <li><strong>Auditoria (Req 22.6):</strong> cada consulta y cada exportacion se
 *       audita via {@link AuditoriaPort}. El {@link Clock} inyectado hace determinista
 *       la marca {@code generadoEn}.</li>
 * </ul>
 */
@Service
public class ServicioTablero {

    /** Tipo de recurso de auditoria/RBAC del Tablero. */
    static final String RECURSO = "tablero";

    private final Map<AreaIndicador, IndicadorAreaPort> puertosPorArea =
            new EnumMap<>(AreaIndicador.class);
    private final AuditoriaPort auditoria;
    private final Clock clock;

    /**
     * @param puertos   todos los puertos de indicadores disponibles en el contexto
     *                  (adaptadores por defecto o concretos), indexados por area.
     * @param auditoria puerto de auditoria (Req 22.6).
     * @param clock     reloj para la marca temporal determinista.
     */
    public ServicioTablero(List<IndicadorAreaPort> puertos, AuditoriaPort auditoria, Clock clock) {
        for (IndicadorAreaPort puerto : puertos) {
            // Si hubiera mas de un bean por area, el ultimo gana; en la practica solo
            // hay uno (el por defecto o el concreto que lo reemplaza).
            this.puertosPorArea.put(puerto.area(), puerto);
        }
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * Compone el Tablero de indicadores por area para el filtro indicado (Req 22.1,
     * 22.3) y audita la consulta o exportacion (Req 22.6). Solo lectura (Req 22.2).
     *
     * @param desde     inicio del periodo (inclusivo); {@code null} no filtra.
     * @param hasta     fin del periodo (inclusivo); {@code null} no filtra.
     * @param clienteId Cliente a acotar (Req 22.3); {@code null} incluye a todos.
     * @param exportar  {@code true} si es una exportacion (Req 22.4, 22.6).
     * @return el {@link TableroDto} compuesto.
     * @throws ReglaNegocioException si el rango de fechas es incoherente (422).
     */
    @Transactional(readOnly = true)
    public TableroDto consultarTablero(LocalDate desde, LocalDate hasta, UUID clienteId,
                                       boolean exportar) {
        validarRango(desde, hasta);
        FiltroIndicadores filtro = FiltroIndicadores.deTablero(desde, hasta, clienteId);

        List<IndicadoresAreaDto> areas = new ArrayList<>();
        for (AreaIndicador area : AreaIndicador.values()) {
            IndicadorAreaPort puerto = puertosPorArea.get(area);
            IndicadoresArea indicadores = (puerto != null)
                    ? puerto.agregar(filtro)
                    : IndicadoresArea.vacio(area);
            areas.add(IndicadoresAreaDto.de(indicadores));
        }

        auditar(exportar, "tablero_indicadores", desde, hasta, clienteId, null);
        return new TableroDto(clock.instant(), desde, hasta, clienteId, areas);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ReglaNegocioException(
                    "El fin del periodo no puede ser anterior al inicio.");
        }
    }

    private void auditar(boolean exportar, String vista, LocalDate desde, LocalDate hasta,
                         UUID clienteId, String area) {
        String accion = exportar ? "exportar" : "consultar";
        String detalle = "tablero '" + vista + "' [desde=" + desde + ", hasta=" + hasta
                + ", cliente=" + clienteId + ", area=" + area + ", exportar=" + exportar + "]";
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
