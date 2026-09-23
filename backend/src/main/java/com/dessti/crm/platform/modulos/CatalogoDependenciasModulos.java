package com.dessti.crm.platform.modulos;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Catalogo de <strong>dependencias entre modulos</strong> de la plataforma
 * multigiro. Es la <strong>fuente unica de verdad</strong> de la regla de negocio
 * segun la cual activar un modulo (dependiente) obliga a activar otro modulo
 * (requerido).
 *
 * <h2>Regla declarada</h2>
 * <p>Hoy declara una sola dependencia, unidireccional:</p>
 * <ul>
 *   <li>{@code inventario-avanzado} <strong>requiere</strong> {@code operacion}.</li>
 * </ul>
 *
 * <p>La relacion es unidireccional: tener {@code operacion} sin
 * {@code inventario-avanzado} no arrastra {@code inventario-avanzado}.</p>
 *
 * <h2>Por que un catalogo estatico y no una tabla</h2>
 * <p>Las claves de modulo son un catalogo de plataforma versionado en codigo
 * (igual que {@link CatalogoModulosService#ETIQUETAS} o el catalogo de roles por
 * modulo). Se mantiene el mismo patron para coherencia y testabilidad. Si en el
 * futuro se requiere edicion en tiempo de ejecucion, la interfaz
 * ({@link #requeridosDe(String)} / {@link #normalizar(Collection)}) permite
 * respaldarla por base de datos sin cambiar a los llamadores.</p>
 *
 * <h2>Extensibilidad (Req 6.3)</h2>
 * <p>Agregar futuras dependencias es simplemente agregar entradas al mapa
 * {@link #DEPENDENCIAS}: {@link #normalizar(Collection)} ya calcula el cierre
 * transitivo, sin cambiar la firma ni la regla existente.</p>
 */
public final class CatalogoDependenciasModulos {

    /**
     * Mapa estatico de dependencias: clave <em>dependiente</em> &rarr; conjunto de
     * claves <em>requeridas</em> directas. Las claves ya estan normalizadas
     * (recorte + minusculas). Para agregar nuevas dependencias basta anadir
     * entradas aqui.
     */
    private static final Map<String, Set<String>> DEPENDENCIAS =
            Map.of("inventario-avanzado", Set.of("operacion"));

    private CatalogoDependenciasModulos() {
        // Clase de utilidad estatica; no se instancia.
    }

    /**
     * Devuelve una <strong>vista inmutable</strong> del mapa completo de
     * dependencias declaradas: clave <em>dependiente</em> &rarr; conjunto de
     * claves <em>requeridas</em> directas (ya normalizadas). Pensado para
     * exponer la regla de forma consultable (Req 6.2) sin permitir su
     * modificacion desde fuera.
     *
     * <p>El {@link Map#of(Object, Object) Map.of(...)} subyacente ya es inmutable
     * y cada conjunto es un {@link Set#of() Set.of(...)} inmutable, de modo que la
     * vista no puede alterar el catalogo. Al agregar futuras dependencias al mapa
     * {@link #DEPENDENCIAS}, esta vista las refleja sin cambios adicionales.</p>
     *
     * @return mapa inmutable clave dependiente &rarr; requeridos directos.
     */
    public static Map<String, Set<String>> todas() {
        return DEPENDENCIAS;
    }

    /**
     * Devuelve los requeridos <strong>directos</strong> de una clave de modulo.
     * La clave de entrada se normaliza (recorte + minusculas) antes de buscar.
     *
     * @param modulo clave de modulo a consultar; puede venir con espacios o
     *               mayusculas, o ser {@code null}/vacia.
     * @return conjunto inmutable de claves requeridas directas (ya normalizadas),
     *         o un conjunto vacio si el modulo no tiene dependencias declaradas o
     *         la clave es nula/vacia.
     */
    public static Set<String> requeridosDe(String modulo) {
        String clave = normalizarClave(modulo);
        if (clave == null) {
            return Set.of();
        }
        return DEPENDENCIAS.getOrDefault(clave, Set.of());
    }

    /**
     * Calcula el <strong>cierre transitivo</strong> de la dependencia: dado un
     * conjunto de modulos, devuelve el mismo conjunto <strong>mas</strong> todos
     * los requeridos (directos e indirectos) de cada modulo presente.
     *
     * <p>Caracteristicas:</p>
     * <ul>
     *   <li><strong>Idempotente:</strong> {@code normalizar(x)} produce el mismo
     *       resultado que {@code normalizar(normalizar(x))}.</li>
     *   <li><strong>Normaliza claves:</strong> recorte + minusculas.</li>
     *   <li><strong>Preserva el orden de insercion</strong> (usa
     *       {@link LinkedHashSet}): primero los modulos de entrada en su orden
     *       original, luego los requeridos que se van agregando.</li>
     *   <li><strong>No duplica</strong> claves.</li>
     *   <li><strong>Ignora</strong> claves nulas o vacias.</li>
     * </ul>
     *
     * @param modulos conjunto de claves de modulo de entrada; puede ser
     *                {@code null} o contener elementos nulos/vacios.
     * @return un {@link Set} nuevo, mutable, que preserva el orden de insercion,
     *         con las claves de entrada normalizadas mas sus requeridos; vacio si
     *         la entrada es {@code null} o no aporta claves validas.
     */
    public static Set<String> normalizar(Collection<String> modulos) {
        Set<String> resultado = new LinkedHashSet<>();
        if (modulos == null) {
            return resultado;
        }

        // 1) Sembrar con las claves de entrada normalizadas, preservando el orden.
        for (String modulo : modulos) {
            String clave = normalizarClave(modulo);
            if (clave != null) {
                resultado.add(clave);
            }
        }

        // 2) Cierre transitivo: recorrer la cola de pendientes agregando los
        //    requeridos de cada clave hasta no descubrir nada nuevo. Al iterar
        //    sobre una copia y agregar solo lo ausente, el resultado es estable
        //    e idempotente y no duplica claves.
        java.util.Deque<String> pendientes = new java.util.ArrayDeque<>(resultado);
        while (!pendientes.isEmpty()) {
            String clave = pendientes.poll();
            for (String requerido : DEPENDENCIAS.getOrDefault(clave, Set.of())) {
                if (resultado.add(requerido)) {
                    pendientes.add(requerido);
                }
            }
        }

        return resultado;
    }

    /**
     * Normaliza una clave de modulo a minusculas y sin espacios extremos,
     * coherente con la normalizacion del resto de la plataforma
     * ({@link CatalogoModulosService}). Devuelve {@code null} para claves nulas o
     * que quedan vacias tras el recorte, para que los llamadores las descarten.
     *
     * @param clave clave cruda; puede ser {@code null}.
     * @return la clave normalizada, o {@code null} si es nula o vacia.
     */
    private static String normalizarClave(String clave) {
        if (clave == null) {
            return null;
        }
        String normalizada = clave.strip().toLowerCase(Locale.ROOT);
        return normalizada.isEmpty() ? null : normalizada;
    }
}
