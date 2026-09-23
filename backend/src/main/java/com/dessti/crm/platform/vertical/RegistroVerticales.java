package com.dessti.crm.platform.vertical;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Componente del Nucleo que descubre y registra todas las implementaciones del
 * {@link ContratoVertical} disponibles y las indexa por su Giro (Req 4.2, 4.3).
 *
 * <p>Spring inyecta por constructor la {@code List<ContratoVertical>} con todos
 * los beans que implementan el puerto (puede venir vacia si no hay ningun
 * vertical enchufado). Durante la construccion —es decir, en el arranque del
 * contexto de Spring— se construye un indice inmutable {@code giro -> contrato}
 * y otro {@code modulo -> giro} sobre las claves <strong>normalizadas</strong>
 * (minusculas, sin espacios extremos), coherente con la normalizacion de la
 * clave de Giro y de las claves de modulo del dominio.</p>
 *
 * <h2>Fail-fast en el arranque (Req 4.4)</h2>
 * <p>El registro es <em>estricto</em>: si dos verticales declaran la misma clave
 * de Giro, o la misma clave de modulo, la construccion lanza
 * {@link IllegalStateException}. Al crearse como bean singleton en el arranque,
 * esa excepcion <strong>rompe el arranque de Spring</strong>, evitando que la
 * plataforma opere con verticales ambiguos. La unicidad de modulo entre
 * verticales es coherente con el aislamiento entre verticales (Req 4.6) y hace
 * deterministica la resolucion de {@link #giroDeModulo(String)}.</p>
 *
 * <p>El Nucleo depende <strong>unicamente</strong> del puerto
 * {@link ContratoVertical} (Req 4.2): este componente no conoce ninguna
 * implementacion concreta de vertical.</p>
 */
@Component
public class RegistroVerticales {

    /** Indice inmutable de contratos por su clave de Giro normalizada. */
    private final Map<String, ContratoVertical> porGiro;

    /** Indice inmutable de clave de Giro por cada clave de modulo normalizada. */
    private final Map<String, String> giroPorModulo;

    /** Indice inmutable de clave de Giro por cada recurso RBAC normalizado. */
    private final Map<String, String> giroPorRecurso;

    /**
     * Construye el registro indexando todos los verticales descubiertos por
     * Spring, validando la unicidad de Giro, de modulo y de recurso (fail-fast,
     * Req 4.4).
     *
     * @param verticales todas las implementaciones de {@link ContratoVertical}
     *                   disponibles; nunca nula (puede estar vacia).
     * @throws IllegalStateException si dos verticales declaran la misma clave de
     *                               Giro, la misma clave de modulo o el mismo
     *                               recurso RBAC.
     */
    public RegistroVerticales(List<ContratoVertical> verticales) {
        Map<String, ContratoVertical> indicePorGiro = new LinkedHashMap<>();
        Map<String, String> indiceGiroPorModulo = new LinkedHashMap<>();
        Map<String, String> indiceGiroPorRecurso = new LinkedHashMap<>();

        for (ContratoVertical vertical : verticales) {
            String giro = normalizar(vertical.giro());
            if (giro == null) {
                throw new IllegalStateException(
                        "Un ContratoVertical declaro una clave de Giro nula o en blanco: "
                                + vertical.getClass().getName());
            }
            ContratoVertical previo = indicePorGiro.putIfAbsent(giro, vertical);
            if (previo != null) {
                throw new IllegalStateException(
                        "Conflicto de Giro duplicado en el registro de verticales: la clave '"
                                + giro + "' la declaran '" + previo.getClass().getName()
                                + "' y '" + vertical.getClass().getName()
                                + "'. Cada Giro debe ser aportado por un unico vertical.");
            }

            for (String moduloCrudo : vertical.modulos()) {
                String modulo = normalizar(moduloCrudo);
                if (modulo == null) {
                    throw new IllegalStateException(
                            "El vertical del Giro '" + giro
                                    + "' declaro una clave de modulo nula o en blanco.");
                }
                String giroPrevio = indiceGiroPorModulo.putIfAbsent(modulo, giro);
                if (giroPrevio != null) {
                    throw new IllegalStateException(
                            "Conflicto de modulo duplicado entre verticales: la clave de modulo '"
                                    + modulo + "' la declaran los Giros '" + giroPrevio
                                    + "' y '" + giro
                                    + "'. Un modulo debe pertenecer a un unico Giro para "
                                    + "preservar el aislamiento entre verticales.");
                }
            }

            for (String recursoCrudo : vertical.recursos()) {
                String recurso = normalizar(recursoCrudo);
                if (recurso == null) {
                    throw new IllegalStateException(
                            "El vertical del Giro '" + giro
                                    + "' declaro un recurso RBAC nulo o en blanco.");
                }
                String giroPrevio = indiceGiroPorRecurso.putIfAbsent(recurso, giro);
                if (giroPrevio != null) {
                    throw new IllegalStateException(
                            "Conflicto de recurso duplicado entre verticales: el recurso '"
                                    + recurso + "' lo declaran los Giros '" + giroPrevio
                                    + "' y '" + giro
                                    + "'. Un recurso de vertical debe pertenecer a un unico "
                                    + "Giro para clasificar de forma deterministica los "
                                    + "permisos por Giro (Req 7.2, 7.4).");
                }
            }
        }

        this.porGiro = Map.copyOf(indicePorGiro);
        this.giroPorModulo = Map.copyOf(indiceGiroPorModulo);
        this.giroPorRecurso = Map.copyOf(indiceGiroPorRecurso);
    }

    /**
     * Recupera el vertical registrado para una clave de Giro (Req 4.3).
     *
     * <p>La clave se normaliza (minusculas, sin espacios extremos) antes de
     * consultar, de forma consistente con el indexado.</p>
     *
     * @param giro clave de Giro a consultar; puede ser nula o en blanco.
     * @return el {@link ContratoVertical} de ese Giro, o vacio si no hay ningun
     *         vertical registrado para esa clave.
     */
    public Optional<ContratoVertical> porGiro(String giro) {
        String clave = normalizar(giro);
        if (clave == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(porGiro.get(clave));
    }

    /**
     * Resuelve la clave de Giro cuyo vertical aporta el modulo indicado.
     *
     * <p>Devuelve vacio cuando ningun vertical declara ese modulo, lo que
     * significa que el modulo es del <strong>Nucleo Comun</strong> (transversal
     * a todo Giro) y por tanto no queda sujeto al gating por Giro (Req 6.4). La
     * unicidad de modulo entre verticales, validada en construccion, garantiza
     * que la resolucion sea deterministica.</p>
     *
     * @param modulo clave de modulo a resolver; puede ser nula o en blanco.
     * @return la clave de Giro que aporta el modulo, o vacio si es un modulo de
     *         Nucleo.
     */
    public Optional<String> giroDeModulo(String modulo) {
        String clave = normalizar(modulo);
        if (clave == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(giroPorModulo.get(clave));
    }

    /**
     * Resuelve la clave de Giro cuyo vertical introduce el recurso RBAC indicado.
     *
     * <p>Devuelve vacio cuando ningun vertical declara ese recurso, lo que
     * significa que el recurso es del <strong>Nucleo Comun</strong> (transversal
     * a todo Giro) y por tanto es aplicable a cualquier Empresa con independencia
     * de su Giro (Req 7.2). La unicidad de recurso entre verticales, validada en
     * construccion (fail-fast, Req 4.4), garantiza que la resolucion
     * recurso&rarr;Giro sea deterministica: un mismo recurso jamas pertenece a
     * dos verticales distintos.</p>
     *
     * <p>Es el analogo de {@link #giroDeModulo(String)} para la clasificacion de
     * permisos por Giro: lo consume el {@code ClasificadorRecursosVertical} para
     * ofrecer/validar permisos de {@code Rol_Personalizado} segun el Giro de la
     * Empresa (Req 7.2, 7.3, 7.4).</p>
     *
     * @param recurso nombre del recurso RBAC a resolver; puede ser nulo o en
     *                blanco.
     * @return la clave de Giro que introduce el recurso, o vacio si es un recurso
     *         de Nucleo (transversal a todo Giro).
     */
    public Optional<String> giroDeRecurso(String recurso) {
        String clave = normalizar(recurso);
        if (clave == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(giroPorRecurso.get(clave));
    }

    /**
     * Devuelve el conjunto inmutable de claves de Giro registradas (Req 4.3).
     *
     * @return claves de Giro con al menos un vertical registrado; nunca nulo
     *         (puede estar vacio).
     */
    public Set<String> girosRegistrados() {
        return porGiro.keySet();
    }

    /**
     * Devuelve el indice inmutable {@code claveModulo -> claveGiro} de todos los
     * modulos aportados por algun vertical registrado.
     *
     * <p>Las claves son las de modulo <strong>normalizadas</strong> (minusculas,
     * sin espacios extremos) y el valor es la clave de Giro que las aporta. Un
     * modulo del <strong>Nucleo Comun</strong> (transversal, sin Giro) no aparece
     * en este mapa: la ausencia de una clave equivale a {@code giroDeModulo(...)}
     * vacio. El mapa es inmutable (construido con {@link Map#copyOf(Map)} en el
     * arranque), por lo que puede exponerse sin copiar.</p>
     *
     * <p>Lo consume el {@code CatalogoModulosService} para componer el catalogo de
     * modulos de la plataforma atribuyendo cada modulo de vertical a su Giro,
     * complementando los modulos de Nucleo del {@code catalogo_modulo} (Req 6.4).</p>
     *
     * @return mapa inmutable {@code modulo -> giro} de los modulos de vertical;
     *         nunca nulo (puede estar vacio).
     */
    public Map<String, String> modulosDeVerticalPorGiro() {
        return giroPorModulo;
    }

    /**
     * Normaliza una clave (Giro o modulo) a minusculas y sin espacios extremos,
     * de forma consistente con el dominio.
     *
     * @param valor clave cruda a normalizar.
     * @return la clave normalizada, o {@code null} si es nula o queda en blanco.
     */
    private static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        return normalizado.isEmpty() ? null : normalizado;
    }
}
