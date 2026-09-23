package com.dessti.crm.operacion.inventario.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.operacion.inventario.adapter.out.persistence.MaterialRepository;
import com.dessti.crm.operacion.inventario.adapter.out.persistence.MovimientoInventarioRepository;
import com.dessti.crm.operacion.inventario.domain.Material;
import com.dessti.crm.operacion.inventario.domain.MovimientoInventario;
import com.dessti.crm.operacion.inventario.domain.ResultadoMovimiento;
import com.dessti.crm.operacion.inventario.domain.TipoMovimientoInventario;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el inventario de Materiales (Req 18). Replica el
 * patron establecido por {@code ServicioOrdenesFabricacion}. Implementa ademas el
 * {@link ConsumoMaterialPort} para que el modulo de Ordenes de Fabricacion (bloques 19/22)
 * pueda consumir Materiales (Req 18.4) y el {@link RecepcionMaterialPort} para que el
 * submodulo de Recepciones (bloque 27) pueda dar de alta en existencias los Materiales
 * recibidos contra una Orden_Compra (Req 32.4).
 *
 * <h2>Operaciones (Req 18)</h2>
 * <ul>
 *   <li><strong>crearMaterial (Req 18.1, 18.7):</strong> da de alta un Material con
 *       existencias iniciales 0 y audita.</li>
 *   <li><strong>registrarMovimiento (Req 18.2, 18.3, 18.5, 18.7):</strong> carga el
 *       Material (404), aplica el movimiento en el dominio (422 "existencias insuficientes"
 *       si una salida dejaria el saldo &lt; 0), persiste el saldo del Material y agrega el
 *       movimiento al historial, audita y, si el Material quedo en stock bajo, notifica
 *       via {@link NotificadorStockPort}.</li>
 *   <li><strong>consumirParaOrdenFabricacion (Req 18.4):</strong> registra una salida por
 *       cada Material consumido por una Orden_Fabricacion, de forma atomica.</li>
 *   <li><strong>recibirDeOrdenCompra (Req 32.4):</strong> registra una entrada por cada
 *       Material recibido por una Recepcion_Mercancia, de forma atomica.</li>
 *   <li><strong>consultar / listarMateriales (Req 18.6):</strong> consulta puntual (404 +
 *       auditoria si no es accesible) y listado paginado (20/100) con filtro por nombre y
 *       por condicion de stock bajo.</li>
 *   <li><strong>desactivar (Req 18, 3.1):</strong> baja logica del Material.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23), concurrencia (Req 49) y auditoria (Req 18.7)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la peticion,
 * Req 23.4). Cada operacion relevante se registra via {@link AuditoriaPort} con el actor
 * derivado del contexto. La actualizacion de {@code existencias} se protege con la
 * concurrencia optimista de {@code TenantScopedEntity} (columna {@code version}, Req 49):
 * dos movimientos concurrentes sobre el mismo Material que partan de un saldo obsoleto
 * provocan una {@code OptimisticLockingFailureException} que el manejador global traduce a
 * 409; el cliente reintenta con el saldo fresco. Este comportamiento preserva la no
 * negatividad (Property 9) sin serializar todo el inventario.</p>
 */
@Service
public class ServicioInventario implements ConsumoMaterialPort, RecepcionMaterialPort {

    /** Tipo de recurso de auditoria/RBAC del Material. */
    static final String RECURSO_MATERIAL = "material";

    /** Tipo de recurso de auditoria/RBAC del Movimiento_Inventario. */
    static final String RECURSO_MOVIMIENTO = "movimiento_inventario";

    private final MaterialRepository materialRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final NotificadorStockPort notificadorStock;
    private final AuditoriaPort auditoria;

    public ServicioInventario(MaterialRepository materialRepository,
                              MovimientoInventarioRepository movimientoRepository,
                              NotificadorStockPort notificadorStock,
                              AuditoriaPort auditoria) {
        this.materialRepository = materialRepository;
        this.movimientoRepository = movimientoRepository;
        this.notificadorStock = notificadorStock;
        this.auditoria = auditoria;
    }

    /**
     * Da de alta un Material con existencias iniciales 0 (Req 18.1) y audita (Req 18.7).
     *
     * @param nombre       nombre del Material (1..200); obligatorio.
     * @param unidadMedida unidad de medida; obligatoria.
     * @param stockMinimo  stock minimo (&gt;= 0); obligatorio.
     * @return el DTO del Material creado (existencias 0).
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    @Transactional
    public MaterialDto crearMaterial(String nombre, String unidadMedida, BigDecimal stockMinimo) {
        String actor = actorActual();
        Material material = Material.crear(nombre, unidadMedida, stockMinimo, actor);
        Material guardado = materialRepository.save(material);
        auditar(actor, "crear", RECURSO_MATERIAL, guardado.getId(),
                "alta de Material '" + guardado.getNombre() + "' [unidad="
                        + guardado.getUnidadMedida() + ", stockMinimo=" + guardado.getStockMinimo()
                        + ", existencias=" + guardado.getExistencias() + "]",
                null, "existencias=" + guardado.getExistencias());
        return MaterialDto.de(guardado);
    }

    /**
     * Registra un Movimiento_Inventario sobre un Material y actualiza sus existencias
     * (Req 18.2). Aplica la regla de no negatividad del dominio (Req 18.3, Property 9),
     * agrega el movimiento al historial (Req 18.7), audita y, si el Material quedo por
     * debajo de su stock minimo, notifica la condicion de stock bajo (Req 18.5).
     *
     * @param materialId identificador del Material.
     * @param tipo       etiqueta del tipo de movimiento ({@code entrada}/{@code salida}/
     *                   {@code ajuste}); obligatoria.
     * @param cantidad   cantidad del movimiento; obligatoria.
     * @param motivo     nota opcional (por ejemplo razon del ajuste).
     * @return el DTO del movimiento registrado.
     * @throws RecursoNoEncontradoException si el Material no es accesible (404, Req 23.3).
     * @throws ReglaNegocioException si el tipo/cantidad son invalidos o una salida dejaria
     *         existencias negativas (422, Req 18.3).
     */
    @Transactional
    public MovimientoInventarioDto registrarMovimiento(UUID materialId, String tipo,
                                                       BigDecimal cantidad, String motivo) {
        String actor = actorActual();
        TipoMovimientoInventario tipoMovimiento = interpretarTipo(tipo);
        Material material = cargar(materialId, actor);
        MovimientoInventario movimiento =
                aplicarYRegistrar(material, tipoMovimiento, cantidad, null, motivo, actor);
        return MovimientoInventarioDto.de(movimiento);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Registra una {@code salida} por cada {@link ConsumoMaterial} de forma atomica:
     * al ejecutarse dentro de una unica transaccion, si algun consumo se rechaza
     * (existencias insuficientes) se revierten todos (Req 18.4, Property 9).</p>
     */
    @Override
    @Transactional
    public List<MovimientoInventarioDto> consumirParaOrdenFabricacion(
            UUID ordenFabricacionId, List<ConsumoMaterial> consumos) {
        String actor = actorActual();
        if (ordenFabricacionId == null) {
            throw new ReglaNegocioException(
                    "El consumo de Materiales debe asociarse a una Orden_Fabricacion.");
        }
        if (consumos == null || consumos.isEmpty()) {
            throw new ReglaNegocioException("El consumo debe incluir al menos un Material.");
        }
        List<MovimientoInventarioDto> registrados = new ArrayList<>(consumos.size());
        for (ConsumoMaterial consumo : consumos) {
            if (consumo == null || consumo.materialId() == null) {
                throw new ReglaNegocioException("Cada consumo debe referirse a un Material.");
            }
            Material material = cargar(consumo.materialId(), actor);
            String motivo = "consumo por Orden_Fabricacion " + ordenFabricacionId;
            MovimientoInventario movimiento = aplicarYRegistrar(
                    material, TipoMovimientoInventario.SALIDA, consumo.cantidad(),
                    ordenFabricacionId, motivo, actor);
            registrados.add(MovimientoInventarioDto.de(movimiento));
        }
        return registrados;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Registra una {@code entrada} por cada {@link ConsumoMaterial} de forma atomica:
     * al ejecutarse dentro de una unica transaccion, si algun renglon se rechaza (Material
     * inaccesible o datos invalidos) se revierten todas las entradas (Req 32.4). El
     * {@code ordenFabricacionId} del movimiento queda {@code null} (es una entrada de
     * compra, no un consumo de produccion); la trazabilidad de la Recepcion_Mercancia se
     * refleja en el {@code motivo}.</p>
     */
    @Override
    @Transactional
    public List<MovimientoInventarioDto> recibirDeOrdenCompra(
            UUID recepcionId, List<ConsumoMaterial> entradas) {
        String actor = actorActual();
        if (recepcionId == null) {
            throw new ReglaNegocioException(
                    "La entrada de Materiales debe asociarse a una Recepcion_Mercancia.");
        }
        if (entradas == null || entradas.isEmpty()) {
            throw new ReglaNegocioException("La recepcion debe incluir al menos un Material.");
        }
        List<MovimientoInventarioDto> registrados = new ArrayList<>(entradas.size());
        for (ConsumoMaterial entrada : entradas) {
            if (entrada == null || entrada.materialId() == null) {
                throw new ReglaNegocioException("Cada entrada debe referirse a un Material.");
            }
            Material material = cargar(entrada.materialId(), actor);
            String motivo = "recepcion de mercancia " + recepcionId;
            MovimientoInventario movimiento = aplicarYRegistrar(
                    material, TipoMovimientoInventario.ENTRADA, entrada.cantidad(),
                    null, motivo, actor);
            registrados.add(MovimientoInventarioDto.de(movimiento));
        }
        return registrados;
    }

    /**
     * Consulta puntual de un Material activo del tenant (Req 23.3).
     *
     * @param materialId identificador del Material.
     * @return el DTO del Material.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public MaterialDto consultar(UUID materialId) {
        String actor = actorActual();
        return MaterialDto.de(cargar(materialId, actor));
    }

    /**
     * Listado paginado de Materiales activos del tenant con filtros opcionales por nombre
     * y por condicion de stock bajo (Req 18.6). Un filtro nulo/false no restringe; sin
     * coincidencias devuelve una pagina vacia con total 0.
     *
     * @param nombre        fragmento del nombre a filtrar; {@code null}/blanco no filtra.
     * @param soloStockBajo si {@code true}, restringe a Materiales en stock bajo.
     * @param pageable      parametros de paginacion ya acotados (20/100).
     * @return la pagina de Materiales como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<MaterialDto> listarMateriales(String nombre, boolean soloStockBajo,
                                              Pageable pageable) {
        String filtroNombre = (nombre == null || nombre.isBlank()) ? null : nombre.trim();
        return materialRepository.buscarConFiltros(filtroNombre, soloStockBajo, pageable)
                .map(MaterialDto::de);
    }

    /**
     * Da de baja logica un Material (Req 18, 3.1) y audita.
     *
     * @param materialId identificador del Material.
     * @return el DTO del Material desactivado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public MaterialDto desactivar(UUID materialId) {
        String actor = actorActual();
        Material material = cargar(materialId, actor);
        material.desactivar(actor);
        Material guardado = materialRepository.save(material);
        auditar(actor, "eliminar", RECURSO_MATERIAL, guardado.getId(),
                "baja logica del Material '" + guardado.getNombre() + "'", "activo", "inactivo");
        return MaterialDto.de(guardado);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Aplica el movimiento en el dominio, persiste el saldo del Material, agrega el
     * movimiento al historial, audita y notifica stock bajo si procede. Compartido por
     * {@link #registrarMovimiento} y {@link #consumirParaOrdenFabricacion}.
     */
    private MovimientoInventario aplicarYRegistrar(Material material, TipoMovimientoInventario tipo,
                                                   BigDecimal cantidad, UUID ordenFabricacionId,
                                                   String motivo, String actor) {
        // Req 18.3 / Property 9: si la salida dejaria existencias < 0, aplicarMovimiento
        // lanza ReglaNegocioException (422) ANTES de mutar, conservando las existencias.
        ResultadoMovimiento resultado = material.aplicarMovimiento(tipo, cantidad, actor);

        materialRepository.save(material);
        MovimientoInventario movimiento = MovimientoInventario.registrar(
                material.getId(), tipo, cantidad, resultado.existenciasResultantes(),
                ordenFabricacionId, motivo, actor);
        MovimientoInventario guardado = movimientoRepository.save(movimiento);

        auditar(actor, "registrar_movimiento", RECURSO_MOVIMIENTO, guardado.getId(),
                "movimiento '" + tipo.valorBd() + "' cantidad=" + guardado.getCantidad()
                        + " sobre Material " + material.getId()
                        + " -> existencias=" + resultado.existenciasResultantes()
                        + (ordenFabricacionId == null ? ""
                                : " [orden_fabricacion=" + ordenFabricacionId + "]"),
                null, "existencias=" + resultado.existenciasResultantes());

        if (resultado.stockBajo()) {
            // Req 18.5: notificar la condicion de stock bajo (placeholder hasta Tarea 43).
            notificadorStock.notificarStockBajo(new NotificacionStockBajo(
                    TenantContext.require(), material.getId(), material.getNombre(),
                    resultado.existenciasResultantes(), material.getStockMinimo()));
        }
        return guardado;
    }

    private Material cargar(UUID materialId, String actor) {
        if (materialId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Material solicitado.");
        }
        return materialRepository.findByIdAndActivoTrue(materialId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_MATERIAL, materialId);
                    throw new RecursoNoEncontradoException("No se encontro el Material solicitado.");
                });
    }

    private TipoMovimientoInventario interpretarTipo(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El tipo de Movimiento_Inventario es obligatorio.");
        }
        try {
            return TipoMovimientoInventario.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException(
                    "Tipo de Movimiento_Inventario desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, String recurso, UUID recursoId,
                         String detalle, String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, recurso,
                detalle + " [id=" + recursoId + "]",
                aJson(valorAnterior), aJson(valorNuevo)));
    }

    /**
     * Coacciona un valor a una cadena JSON valida para las columnas JSONB
     * {@code valor_anterior}/{@code valor_nuevo} de la bitacora (evita el error
     * 22P02 de PostgreSQL -> HTTP 500). Un {@code null} o texto en blanco se
     * conserva como {@code null}; un texto que ya es JSON valido (objeto,
     * arreglo, cadena entrecomillada, numero, booleano o {@code null}) se
     * respeta; cualquier otro texto plano se envuelve como literal de cadena
     * JSON con el escape adecuado.
     */
    private static String aJson(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        char inicio = limpio.charAt(0);
        boolean pareceJson = inicio == '{' || inicio == '[' || inicio == '"'
                || "true".equals(limpio) || "false".equals(limpio) || "null".equals(limpio)
                || limpio.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?");
        if (pareceJson) {
            return limpio;
        }
        StringBuilder sb = new StringBuilder(limpio.length() + 2);
        sb.append('"');
        for (int i = 0; i < limpio.length(); i++) {
            char ch = limpio.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private void auditarAccesoCruzado(String actor, String recurso, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", recurso,
                "intento de acceso a " + recurso + " no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
