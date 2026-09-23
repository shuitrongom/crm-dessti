package com.dessti.crm.platform.modulos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.dessti.crm.platform.monetizacion.adapter.out.persistence.CatalogoModuloRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.MonedaRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.PrecioModuloRepository;
import com.dessti.crm.platform.monetizacion.application.MonetizacionProperties;
import com.dessti.crm.platform.monetizacion.domain.CatalogoModulo;
import com.dessti.crm.platform.monetizacion.domain.Moneda;
import com.dessti.crm.platform.monetizacion.domain.PrecioModulo;
import com.dessti.crm.platform.vertical.RegistroVerticales;

/**
 * Servicio de aplicacion de <strong>solo lectura</strong> que compone el
 * catalogo unificado de claves de modulo que la plataforma reconoce, para que el
 * frontend (editor de Planes, alta de Empresas) pinte checkboxes reales en lugar
 * de un campo de texto libre separado por comas, y ademas pueda cotizar el Plan
 * (suma de precios de modulo) en la <strong>moneda principal</strong>.
 *
 * <h2>Fuentes de verdad (sin claves inventadas)</h2>
 * <p>El catalogo se compone <strong>exclusivamente</strong> de claves reales que
 * ya existen en el sistema, unificadas y deduplicadas:</p>
 * <ul>
 *   <li><strong>Modulos de Nucleo Comun.</strong> Son los del catalogo maestro
 *       {@code catalogo_modulo} (entidad {@link CatalogoModulo}, sembrado en la
 *       migracion V22) que <em>no</em> pertenecen a ningun vertical. Son las
 *       claves canonicas de facturacion de plataforma (p. ej. {@code comercial},
 *       {@code facturacion}, {@code rh-nomina}, {@code reportes-bi}), transversales
 *       a todo Giro, por lo que su {@link ModuloCatalogoDto#giro()} es {@code null}.</li>
 *   <li><strong>Modulos de vertical.</strong> Son las claves que declara cada
 *       {@code ContratoVertical} en su {@code modulos()}, indexadas por
 *       {@link RegistroVerticales#modulosDeVerticalPorGiro()}. Cada una lleva la
 *       clave de su Giro (p. ej. {@code produccion-industrial} del Giro
 *       {@code manufactura}).</li>
 * </ul>
 *
 * <p><strong>Union y prioridad.</strong> El catalogo incluye TODA clave que
 * aparezca en el {@code modulos()} de cualquier vertical y TODA clave del
 * {@code catalogo_modulo}. Si una misma clave figura en ambos lugares (p. ej.
 * {@code operacion} y {@code mantenimiento}, que estan en {@code catalogo_modulo}
 * y ademas los aporta el Vertical_Anuncios), <strong>gana el vertical</strong>:
 * la entrada se atribuye a su Giro, no al Nucleo. Asi la resolucion es coherente
 * con {@link RegistroVerticales#giroDeModulo(String)}.</p>
 *
 * <h2>Precio en moneda principal</h2>
 * <p>Cada entrada expone su {@link ModuloCatalogoDto#catalogoModuloId()} (el id de
 * {@code catalogo_modulo} para esa clave cuando existe; {@code null} para claves
 * solo de vertical) y su {@link ModuloCatalogoDto#precio()} de LISTA en la
 * <strong>moneda principal</strong> (de {@code precio_modulo}; {@code null} si aun
 * no hay precio). El {@link ModuloCatalogoDto#monedaCodigo()} es el mismo para
 * todas las entradas. La moneda principal se toma de
 * {@link MonetizacionProperties}; si el codigo configurado no existe como
 * {@code moneda} activa, se degrada de forma controlada a {@code MXN} y se registra
 * una advertencia (no se interrumpe el servicio).</p>
 *
 * <h2>Etiquetas humanas</h2>
 * <p>El {@link ModuloCatalogoDto#nombreVisible()} se resuelve, en este orden:
 * (1) por el mapa documentado {@link #ETIQUETAS} de overrides en espanol
 * cuidados; (2) si no hay override, por el {@code nombre} del
 * {@code catalogo_modulo} cuando la clave existe alli; (3) en ultimo termino, por
 * una <em>humanizacion</em> de la clave (guiones/guiones bajos por espacios y
 * capitalizacion). Esto evita etiquetas crudas para claves de vertical que no
 * estan en {@code catalogo_modulo} (p. ej. {@code produccion-industrial}).</p>
 *
 * <h2>Orden determinista</h2>
 * <p>Primero los modulos de Nucleo, ordenados alfabeticamente por su etiqueta;
 * despues los modulos de vertical, agrupados por clave de Giro (alfabetica) y,
 * dentro de cada Giro, por etiqueta. El orden es estable para que la UI sea
 * predecible y las pruebas deterministas.</p>
 */
@Service
public class CatalogoModulosService {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogoModulosService.class);

    /**
     * Overrides de etiqueta humana (clave canonica &rarr; nombre visible en
     * espanol). Documentado y cerrado: cubre las claves reales del
     * {@code catalogo_modulo} (V22) y las claves de vertical conocidas. Para
     * cualquier clave sin override se usa el {@code nombre} del catalogo o, en su
     * defecto, la humanizacion de la clave (ver {@link #humanizar(String)}).
     */
    static final Map<String, String> ETIQUETAS = Map.ofEntries(
            // --- Modulos de Nucleo (claves reales de catalogo_modulo, V22) ---
            Map.entry("comercial", "Comercial (CRM)"),
            Map.entry("redes-sociales", "Redes sociales"),
            Map.entry("operacion", "Operación y producción"),
            Map.entry("inventario-avanzado", "Inventario avanzado"),
            Map.entry("mantenimiento", "Mantenimiento"),
            Map.entry("compras", "Compras"),
            Map.entry("facturacion", "Facturacion (CFDI)"),
            Map.entry("contabilidad", "Contabilidad y finanzas"),
            Map.entry("tesoreria", "Tesoreria"),
            Map.entry("activos-fijos", "Activos fijos"),
            Map.entry("rh-nomina", "RH y nomina"),
            Map.entry("portal-cliente", "Portal del cliente"),
            Map.entry("estrategia", "Estrategia"),
            Map.entry("presupuestos", "Presupuestos"),
            Map.entry("reportes-bi", "Reportes y BI"),
            // --- Claves de vertical no presentes en catalogo_modulo ---
            Map.entry("produccion-industrial", "Produccion industrial"));

    /**
     * Claves que se exponen como modulos de <strong>Nucleo</strong> (giro null) en
     * el catalogo de MONETIZACION (Planes/Paquetes/Empresa), aunque un vertical
     * tambien las declare. Motivo: tienen fila real en {@code catalogo_modulo} y
     * son monetizables/seleccionables en cualquier Giro. Esto NO altera la
     * propiedad de giro del RBAC ({@link RegistroVerticales#giroDeModulo(String)} y
     * {@code Autorizador.giroCorresponde(...)} siguen viendo el mapeo del vertical),
     * de modo que el gating por Giro de los flujos especificos del vertical
     * (p. ej. Levantamiento/Permiso de anuncios) se conserva intacto (Req 16.6).
     *
     * <p>{@code operacion}: es requisito de {@code inventario-avanzado} (ver
     * {@link CatalogoDependenciasModulos}); al ofrecerse como Nucleo, un Plan de
     * cualquier Giro puede incluir Inventario avanzado sin que la validacion de
     * {@code CatalogoModulosGiroValidacion} lo rechace por pertenecer a otro Giro.</p>
     */
    static final java.util.Set<String> MONETIZACION_NUCLEO = java.util.Set.of("operacion");

    private final CatalogoModuloRepository catalogoModuloRepository;
    private final PrecioModuloRepository precioModuloRepository;
    private final MonedaRepository monedaRepository;
    private final MonetizacionProperties monetizacionProperties;
    private final RegistroVerticales registroVerticales;

    public CatalogoModulosService(CatalogoModuloRepository catalogoModuloRepository,
                                  PrecioModuloRepository precioModuloRepository,
                                  MonedaRepository monedaRepository,
                                  MonetizacionProperties monetizacionProperties,
                                  RegistroVerticales registroVerticales) {
        this.catalogoModuloRepository = catalogoModuloRepository;
        this.precioModuloRepository = precioModuloRepository;
        this.monedaRepository = monedaRepository;
        this.monetizacionProperties = monetizacionProperties;
        this.registroVerticales = registroVerticales;
    }

    /**
     * Compone el catalogo unificado de modulos de la plataforma, enriquecido con
     * el id de {@code catalogo_modulo} y el precio de lista en la moneda principal.
     *
     * @return lista inmutable de {@link ModuloCatalogoDto} deduplicada y ordenada
     *         de forma determinista (Nucleo primero por etiqueta, luego verticales
     *         agrupados por Giro).
     */
    public List<ModuloCatalogoDto> listar() {
        // 0) Moneda principal efectiva (con degradacion controlada a MXN).
        String monedaPrincipal = resolverMonedaPrincipal();

        // 1) Modulos de vertical: clave -> giro (gana el vertical ante colision).
        Map<String, String> giroPorModulo = registroVerticales.modulosDeVerticalPorGiro();

        // 2) Nombres e id del catalogo maestro por clave (etiqueta de fallback + id).
        Map<String, String> nombreCatalogoPorClave = new LinkedHashMap<>();
        Map<String, UUID> idCatalogoPorClave = new LinkedHashMap<>();
        for (CatalogoModulo modulo : catalogoModuloRepository.findAll()) {
            String claveNorm = normalizar(modulo.getClave());
            nombreCatalogoPorClave.put(claveNorm, modulo.getNombre());
            idCatalogoPorClave.put(claveNorm, modulo.getId());
        }

        // 3) Precios de lista en la moneda principal, indexados por id de modulo,
        //    en UNA sola consulta.
        Map<UUID, BigDecimal> precioPorModuloId = new HashMap<>();
        for (PrecioModulo precio : precioModuloRepository.findByMonedaCodigo(monedaPrincipal)) {
            precioPorModuloId.put(precio.getCatalogoModuloId(), precio.getPrecio());
        }

        // 4) Union de claves: catalogo_modulo (Nucleo candidato) + verticales.
        Map<String, String> giroPorClave = new LinkedHashMap<>();
        for (String claveCatalogo : nombreCatalogoPorClave.keySet()) {
            giroPorClave.putIfAbsent(claveCatalogo, null); // Nucleo por defecto.
        }
        // El vertical gana: sobrescribe el giro (o incorpora claves que no estan
        // en catalogo_modulo, p. ej. produccion-industrial). EXCEPCION: las claves
        // de MONETIZACION_NUCLEO se exponen como Nucleo (giro null) en este catalogo
        // aunque un vertical las declare, para que sean seleccionables en Planes/
        // Paquetes/Empresa de cualquier Giro (no afecta el gating RBAC por Giro).
        for (Map.Entry<String, String> entrada : giroPorModulo.entrySet()) {
            if (MONETIZACION_NUCLEO.contains(entrada.getKey())) {
                giroPorClave.putIfAbsent(entrada.getKey(), null);
                continue;
            }
            giroPorClave.put(entrada.getKey(), entrada.getValue());
        }

        // 5) Proyeccion a DTO con etiqueta, id y precio resueltos. Las claves solo
        //    de vertical (sin fila en catalogo_modulo) llevan id y precio null.
        List<ModuloCatalogoDto> catalogo = new ArrayList<>(giroPorClave.size());
        for (Map.Entry<String, String> entrada : giroPorClave.entrySet()) {
            String clave = entrada.getKey();
            String giro = entrada.getValue();
            String etiqueta = etiquetaDe(clave, nombreCatalogoPorClave.get(clave));
            UUID catalogoModuloId = idCatalogoPorClave.get(clave);
            BigDecimal precio = catalogoModuloId == null ? null : precioPorModuloId.get(catalogoModuloId);
            catalogo.add(new ModuloCatalogoDto(clave, etiqueta, giro, catalogoModuloId, precio, monedaPrincipal));
        }

        // 6) Orden determinista: Nucleo (giro null) primero por etiqueta; luego
        //    verticales agrupados por Giro y por etiqueta. Desempate por clave.
        catalogo.sort(Comparator
                .comparing((ModuloCatalogoDto m) -> m.giro() != null) // false (Nucleo) antes que true.
                .thenComparing(m -> m.giro() == null ? "" : m.giro())
                .thenComparing(m -> m.nombreVisible().toLowerCase(Locale.ROOT))
                .thenComparing(ModuloCatalogoDto::clave));

        return List.copyOf(catalogo);
    }

    /**
     * Resuelve el codigo de la moneda principal efectiva. Toma el codigo
     * configurado en {@link MonetizacionProperties}; si esa moneda no existe como
     * {@code moneda} activa (p. ej. codigo mal configurado o desactivado), degrada
     * de forma controlada a {@code MXN} y registra una advertencia, sin interrumpir
     * el servicio.
     *
     * @return el codigo ISO 4217 de la moneda principal efectiva.
     */
    private String resolverMonedaPrincipal() {
        String configurada = monetizacionProperties.monedaPrincipal();
        if (esMonedaActiva(configurada)) {
            return configurada;
        }
        String porDefecto = MonetizacionProperties.MONEDA_PRINCIPAL_POR_DEFECTO;
        if (!porDefecto.equals(configurada)) {
            LOG.warn("La moneda principal configurada '{}' no existe como moneda activa; "
                    + "se usa '{}' por defecto.", configurada, porDefecto);
        } else {
            LOG.warn("La moneda principal por defecto '{}' no existe como moneda activa; "
                    + "los precios se resolveran sin coincidencias hasta sembrarla.", porDefecto);
        }
        return porDefecto;
    }

    /** Indica si el codigo corresponde a una {@code moneda} existente y activa. */
    private boolean esMonedaActiva(String codigo) {
        return monedaRepository.findByCodigo(codigo)
                .map(Moneda::isActivo)
                .orElse(false);
    }

    /**
     * Resuelve la etiqueta humana de una clave: override documentado, luego
     * nombre del catalogo maestro, luego humanizacion de la clave.
     *
     * @param clave         clave canonica normalizada.
     * @param nombreCatalogo nombre del {@code catalogo_modulo} para esa clave, o
     *                       {@code null} si la clave no esta en el catalogo maestro.
     * @return la etiqueta visible; nunca en blanco.
     */
    private static String etiquetaDe(String clave, String nombreCatalogo) {
        String override = ETIQUETAS.get(clave);
        if (override != null) {
            return override;
        }
        if (nombreCatalogo != null && !nombreCatalogo.isBlank()) {
            return nombreCatalogo;
        }
        return humanizar(clave);
    }

    /**
     * Humaniza una clave: reemplaza {@code -} y {@code _} por espacios y
     * capitaliza la primera letra (p. ej. {@code "orden-trabajo"} &rarr;
     * {@code "Orden trabajo"}).
     *
     * @param clave clave canonica.
     * @return la clave humanizada.
     */
    private static String humanizar(String clave) {
        String texto = clave.replace('-', ' ').replace('_', ' ').strip();
        if (texto.isEmpty()) {
            return clave;
        }
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /**
     * Normaliza una clave a minusculas y sin espacios extremos, coherente con la
     * normalizacion del dominio y del {@link RegistroVerticales}.
     */
    private static String normalizar(String clave) {
        return clave == null ? null : clave.strip().toLowerCase(Locale.ROOT);
    }
}
