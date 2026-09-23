package com.dessti.crm.reportesbi.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.reportesbi.adapter.out.persistence.TableroPersonalizadoRepository;
import com.dessti.crm.reportesbi.application.GuardarTableroPersonalizadoCommand.WidgetCommand;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorAreaPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;
import com.dessti.crm.reportesbi.domain.TableroPersonalizado;
import com.dessti.crm.reportesbi.domain.TableroPersonalizado.DefinicionWidget;

/**
 * Servicio de aplicacion de la Inteligencia de Negocio consolidada (Req 48). Cumple dos
 * responsabilidades:
 *
 * <ol>
 *   <li><strong>Analisis consolidado (Req 48.1, 48.2, 48.4):</strong> agrega, de
 *       <strong>solo lectura</strong>, los indicadores de todas las areas (o del area
 *       filtrada) para el periodo actual y, cuando el periodo esta acotado, calcula el
 *       periodo anterior de igual longitud para derivar comparativos/tendencias
 *       read-only. Reune los indicadores invocando cada {@link IndicadorAreaPort}
 *       inyectado; no modifica dato de origen.</li>
 *   <li><strong>Tableros analiticos personalizados (Req 48.3):</strong> CRUD de
 *       {@link TableroPersonalizado} y sus widgets dentro de la Empresa, con aislamiento
 *       por tenant (Req 48.5).</li>
 * </ol>
 *
 * <h2>Autorizacion, aislamiento y auditoria (Req 48.5, 48.6, 48.7, 23)</h2>
 * <ul>
 *   <li><strong>403 sin permiso analitico (Req 48.6):</strong> las rutas del
 *       controlador exigen {@code inteligencia_negocio:{leer|gestionar|exportar}} via
 *       {@code @PreAuthorize}; sin el permiso se responde 403.</li>
 *   <li><strong>Aislamiento por tenant (Req 48.5):</strong> el {@code tenant_id} se
 *       deriva del {@link TenantContext} (nunca de la peticion, Req 23.4); los tableros
 *       personalizados quedan acotados por el filtro global de Hibernate y la RLS
 *       (V44).</li>
 *   <li><strong>Auditoria (Req 48.7):</strong> cada consulta, exportacion y mutacion se
 *       audita via {@link AuditoriaPort}. El {@link Clock} hace determinista la marca
 *       {@code generadoEn}.</li>
 * </ul>
 */
@Service
public class ServicioInteligenciaNegocio {

    /** Tipo de recurso de auditoria/RBAC de la Inteligencia de Negocio. */
    static final String RECURSO = "inteligencia_negocio";

    private final Map<AreaIndicador, IndicadorAreaPort> puertosPorArea =
            new EnumMap<>(AreaIndicador.class);
    private final TableroPersonalizadoRepository tableroRepository;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    /**
     * @param puertos           todos los puertos de indicadores disponibles, indexados
     *                          por area.
     * @param tableroRepository repositorio de los tableros personalizados (Req 48.3).
     * @param auditoria         puerto de auditoria (Req 48.7).
     * @param clock             reloj para la marca temporal determinista.
     */
    public ServicioInteligenciaNegocio(List<IndicadorAreaPort> puertos,
                                       TableroPersonalizadoRepository tableroRepository,
                                       AuditoriaPort auditoria,
                                       Clock clock) {
        for (IndicadorAreaPort puerto : puertos) {
            this.puertosPorArea.put(puerto.area(), puerto);
        }
        this.tableroRepository = tableroRepository;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Analisis consolidado (Req 48.1)
    // ------------------------------------------------------------------

    /**
     * Compone el analisis consolidado de todas las areas (o del area filtrada) para el
     * periodo indicado, con comparativos del periodo anterior de igual longitud cuando
     * el rango esta acotado (Req 48.1, 48.4). Solo lectura (Req 48.2). Audita la
     * consulta o exportacion (Req 48.7).
     *
     * @param desde     inicio del periodo (inclusivo); {@code null} no filtra por fecha.
     * @param hasta     fin del periodo (inclusivo); {@code null} no filtra por fecha.
     * @param area      area a filtrar (Req 48.4); {@code null} incluye a todas.
     * @param dimension dimension de analisis (Req 48.4); {@code null} no segmenta.
     * @param exportar  {@code true} si es una exportacion (Req 48.4, 48.7).
     * @return el {@link InteligenciaNegocioDto} consolidado.
     * @throws ReglaNegocioException si el rango de fechas es incoherente (422).
     */
    @Transactional(readOnly = true)
    public InteligenciaNegocioDto consolidado(LocalDate desde, LocalDate hasta, String area,
                                              String dimension, boolean exportar) {
        validarRango(desde, hasta);
        FiltroIndicadores filtro = FiltroIndicadores.deConsolidado(desde, hasta, area, dimension);

        // Periodo anterior de igual longitud, inmediatamente previo (Req 48.1). Solo se
        // calcula si el rango esta acotado en ambos extremos; en otro caso no hay
        // comparativo base.
        LocalDate desdeComparativo = null;
        LocalDate hastaComparativo = null;
        FiltroIndicadores filtroComparativo = null;
        if (desde != null && hasta != null) {
            long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
            hastaComparativo = desde.minusDays(1);
            desdeComparativo = hastaComparativo.minusDays(dias - 1);
            filtroComparativo = FiltroIndicadores.deConsolidado(
                    desdeComparativo, hastaComparativo, area, dimension);
        }

        List<IndicadoresAreaDto> areas = new ArrayList<>();
        for (AreaIndicador areaActual : AreaIndicador.values()) {
            if (!filtro.incluyeArea(areaActual)) {
                continue;
            }
            IndicadorAreaPort puerto = puertosPorArea.get(areaActual);
            IndicadoresArea actuales = (puerto != null)
                    ? puerto.agregar(filtro)
                    : IndicadoresArea.vacio(areaActual);
            IndicadoresArea previos = (puerto != null && filtroComparativo != null)
                    ? puerto.agregar(filtroComparativo)
                    : IndicadoresArea.vacio(areaActual);
            areas.add(IndicadoresAreaDto.de(fusionarComparativo(actuales, previos)));
        }

        auditarConsolidado(exportar, desde, hasta, area, dimension);
        return new InteligenciaNegocioDto(clock.instant(), desde, hasta, desdeComparativo,
                hastaComparativo, area, dimension, areas);
    }

    // ------------------------------------------------------------------
    // Tableros personalizados (Req 48.3)
    // ------------------------------------------------------------------

    /**
     * Crea un tablero analitico personalizado con sus widgets (Req 48.3) dentro de la
     * Empresa (Req 48.5) y audita el alta (Req 48.7).
     *
     * @param comando definicion del tablero y sus widgets.
     * @return el DTO del tablero creado.
     * @throws ReglaNegocioException si la definicion es invalida (422).
     */
    @Transactional
    public TableroPersonalizadoDto crearTablero(GuardarTableroPersonalizadoCommand comando) {
        String actor = actorActual();
        validarComando(comando);
        TableroPersonalizado tablero = TableroPersonalizado.crear(
                comando.nombre(), comando.descripcion(), comando.propietarioUsuarioId(), actor);
        tablero.reemplazarWidgets(aDefiniciones(comando.widgets()), actor);
        TableroPersonalizado guardado = tableroRepository.save(tablero);
        auditarTablero(actor, "crear", guardado.getId(),
                "creado tablero personalizado '" + guardado.getNombre() + "' con "
                        + guardado.getWidgets().size() + " widget(s)");
        return TableroPersonalizadoDto.de(guardado);
    }

    /**
     * Actualiza un tablero personalizado y reemplaza por completo sus widgets (Req 48.3).
     * Audita la modificacion (Req 48.7).
     *
     * @param tableroId identificador del tablero.
     * @param comando   nueva definicion del tablero y sus widgets.
     * @return el DTO del tablero actualizado.
     * @throws RecursoNoEncontradoException si el tablero no es accesible en el tenant (404).
     * @throws ReglaNegocioException        si la definicion es invalida (422).
     */
    @Transactional
    public TableroPersonalizadoDto actualizarTablero(UUID tableroId,
                                                     GuardarTableroPersonalizadoCommand comando) {
        String actor = actorActual();
        validarComando(comando);
        TableroPersonalizado tablero = cargar(tableroId, actor);
        tablero.actualizar(comando.nombre(), comando.descripcion(), actor);
        tablero.reemplazarWidgets(aDefiniciones(comando.widgets()), actor);
        TableroPersonalizado guardado = tableroRepository.save(tablero);
        auditarTablero(actor, "actualizar", guardado.getId(),
                "actualizado tablero personalizado '" + guardado.getNombre() + "' con "
                        + guardado.getWidgets().size() + " widget(s)");
        return TableroPersonalizadoDto.de(guardado);
    }

    /**
     * Elimina un tablero personalizado y sus widgets (cascada, Req 48.3). Audita la
     * baja (Req 48.7).
     *
     * @param tableroId identificador del tablero.
     * @throws RecursoNoEncontradoException si el tablero no es accesible en el tenant (404).
     */
    @Transactional
    public void eliminarTablero(UUID tableroId) {
        String actor = actorActual();
        TableroPersonalizado tablero = cargar(tableroId, actor);
        tableroRepository.delete(tablero);
        auditarTablero(actor, "eliminar", tableroId,
                "eliminado tablero personalizado '" + tablero.getNombre() + "'");
    }

    /**
     * Consulta un tablero personalizado por su identificador (Req 48.3, 23.3). Audita la
     * consulta (Req 48.7).
     *
     * @param tableroId identificador del tablero.
     * @return el DTO del tablero.
     * @throws RecursoNoEncontradoException si el tablero no es accesible en el tenant (404).
     */
    @Transactional(readOnly = true)
    public TableroPersonalizadoDto consultarTablero(UUID tableroId) {
        String actor = actorActual();
        TableroPersonalizado tablero = cargar(tableroId, actor);
        auditarTablero(actor, "consultar", tableroId,
                "consultado tablero personalizado '" + tablero.getNombre() + "'");
        return TableroPersonalizadoDto.de(tablero);
    }

    /**
     * Lista de forma paginada los tableros personalizados del tenant (Req 48.3). Sin
     * resultados devuelve una pagina vacia con total 0.
     *
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de tableros personalizados como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<TableroPersonalizadoDto> listarTableros(Pageable pageable) {
        return tableroRepository.findAll(pageable).map(TableroPersonalizadoDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Fusiona los indicadores del periodo actual con los del periodo anterior, tomando
     * el valor previo como comparativo de cada indicador con la misma clave (Req 48.1).
     * Preserva los comparativos que el propio adaptador ya hubiera aportado.
     */
    private IndicadoresArea fusionarComparativo(IndicadoresArea actuales, IndicadoresArea previos) {
        Map<String, ValorIndicador> previoPorClave = new LinkedHashMap<>();
        for (ValorIndicador previo : previos.indicadores()) {
            previoPorClave.put(previo.clave(), previo);
        }
        List<ValorIndicador> fusionados = new ArrayList<>();
        for (ValorIndicador actual : actuales.indicadores()) {
            if (actual.tieneComparativo()) {
                fusionados.add(actual);
                continue;
            }
            ValorIndicador previo = previoPorClave.get(actual.clave());
            if (previo != null) {
                fusionados.add(new ValorIndicador(actual.clave(), actual.etiqueta(),
                        actual.valor(), actual.unidad(), previo.valor()));
            } else {
                fusionados.add(actual);
            }
        }
        return new IndicadoresArea(actuales.area(), fusionados);
    }

    private TableroPersonalizado cargar(UUID tableroId, String actor) {
        if (tableroId == null) {
            throw new RecursoNoEncontradoException("No se encontro el tablero personalizado.");
        }
        return tableroRepository.findById(tableroId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, tableroId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro el tablero personalizado.");
                });
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ReglaNegocioException(
                    "El fin del periodo no puede ser anterior al inicio.");
        }
    }

    private void validarComando(GuardarTableroPersonalizadoCommand comando) {
        if (comando == null) {
            throw new ReglaNegocioException("La definicion del tablero es obligatoria.");
        }
        // El detalle de nombre/descripcion/widgets lo valida el dominio; aqui solo se
        // exige que el comando exista.
    }

    private List<DefinicionWidget> aDefiniciones(List<WidgetCommand> widgets) {
        if (widgets == null) {
            return List.of();
        }
        List<DefinicionWidget> definiciones = new ArrayList<>();
        for (WidgetCommand widget : widgets) {
            if (widget == null) {
                throw new ReglaNegocioException("Los widgets del tablero no pueden ser nulos.");
            }
            definiciones.add(new DefinicionWidget(widget.area(), widget.metrica(),
                    widget.orden(), widget.configuracionJson()));
        }
        return definiciones;
    }

    private void auditarConsolidado(boolean exportar, LocalDate desde, LocalDate hasta,
                                    String area, String dimension) {
        String accion = exportar ? "exportar" : "consultar";
        String detalle = "consolidado inteligencia de negocio [desde=" + desde + ", hasta="
                + hasta + ", area=" + area + ", dimension=" + dimension
                + ", exportar=" + exportar + "]";
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actorActual(), accion, RECURSO, detalle, null, null));
    }

    private void auditarTablero(String actor, String accion, UUID tableroId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO,
                detalle + " [id=" + tableroId + "]", null, null));
    }

    private void auditarAccesoCruzado(String actor, UUID tableroId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", RECURSO,
                "intento de acceso a tablero personalizado no disponible en el tenant [id="
                        + tableroId + "]", null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
