package com.dessti.crm.platform.vertical;

import java.util.List;
import java.util.Set;

/**
 * Puerto de entrada del plugin de giro (Contrato de Vertical), hexagonal-puro.
 *
 * <p>Cada Modulo-Vertical implementa esta interfaz —tipicamente como un bean de
 * Spring— para <strong>enchufarse al Nucleo</strong> de forma uniforme, aislada
 * y verificable, declarando su Giro y todo lo que aporta: las claves de modulo
 * (para componerse con el catalogo de modulos y el gating por Plan), los recursos
 * RBAC atomicos que introduce y su metadato de navegacion (Req 4.1).</p>
 *
 * <p>El Nucleo Comun depende <strong>unicamente</strong> de este puerto y nunca
 * de una implementacion concreta de vertical (Req 4.2). El
 * {@code RegistroVerticales} descubre todas las implementaciones disponibles en
 * el arranque y las indexa por su {@link #giro() Giro}. Las implementaciones, a
 * su vez, consumen las capacidades del Nucleo (Cliente, Cotizacion, Facturacion,
 * Inventario y demas) exclusivamente a traves de puertos del Nucleo, sin acceder
 * a las clases internas de persistencia del Nucleo ni de otro vertical (Req 4.5).</p>
 */
public interface ContratoVertical {

    /**
     * Clave canonica del Giro que aporta este vertical.
     *
     * <p>Debe estar normalizada (minusculas, formato kebab), p. ej.
     * {@code "anuncios-luminosos"}. El {@code RegistroVerticales} usa esta clave
     * para indexar el vertical y rechaza el arranque si dos verticales declaran
     * la misma clave de Giro (Req 4.3, 4.4).</p>
     *
     * @return la clave canonica normalizada del Giro; no nula ni en blanco.
     */
    String giro();

    /**
     * Claves de modulo que aporta el vertical.
     *
     * <p>Se componen con el catalogo de modulos del Nucleo para el gating por
     * Plan (Req 25.4 del spec base) y permiten al Nucleo resolver a que Giro
     * pertenece un modulo dado (gating por Giro, Req 6).</p>
     *
     * @return conjunto de claves de modulo del vertical; nunca nulo (puede estar
     *         vacio).
     */
    Set<String> modulos();

    /**
     * Recursos RBAC atomicos que introduce el vertical.
     *
     * <p>Son los nombres de recurso (parte {@code recurso} del permiso atomico
     * {@code recurso:operacion}, Req 7.1) especificos de este Giro. El
     * {@code ClasificadorRecursosVertical} los usa para resolver que recursos
     * pertenecen a que Giro y ofrecer/validar permisos por Giro (Req 7.2-7.4).</p>
     *
     * @return conjunto de recursos RBAC del vertical; nunca nulo (puede estar
     *         vacio).
     */
    Set<String> recursos();

    /**
     * Metadato de navegacion que aporta el vertical.
     *
     * <p>Lo consume el endpoint de contexto de sesion del frontend para cargar
     * dinamicamente los menus y rutas del vertical activo segun el Giro de la
     * Empresa del Usuario (Req 9.2), respetando el filtrado por permiso de cada
     * {@link ItemNavegacionVertical}.</p>
     *
     * @return lista ordenada de items de navegacion del vertical; nunca nula
     *         (puede estar vacia).
     */
    List<ItemNavegacionVertical> navegacion();
}
