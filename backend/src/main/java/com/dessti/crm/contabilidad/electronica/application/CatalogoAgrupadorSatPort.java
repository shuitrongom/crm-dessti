package com.dessti.crm.contabilidad.electronica.application;

import java.util.List;
import java.util.Optional;

import com.dessti.crm.contabilidad.electronica.domain.CodigoAgrupadorSat;

/**
 * Puerto de aplicacion sobre el catalogo oficial de codigos agrupadores del SAT
 * (Anexo 24). Abstrae el acceso al catalogo de plataforma para:
 * <ul>
 *   <li>validar que un codigo agrupador existe al amarrar una Cuenta_Contable
 *       (Req 1.3);</li>
 *   <li>resolver la naturaleza/nivel de un codigo al generar el Catalogo_XML
 *       (Req 2.3);</li>
 *   <li>buscar codigos para el autocompletar del frontend (Req 1.2).</li>
 * </ul>
 */
public interface CatalogoAgrupadorSatPort {

    /**
     * Indica si el codigo agrupador existe en el catalogo oficial del SAT.
     *
     * @param codigo codigo agrupador (p. ej. {@code 101.01}).
     * @return {@code true} si el codigo existe.
     */
    boolean existe(String codigo);

    /**
     * Recupera un codigo agrupador por su clave.
     *
     * @param codigo codigo agrupador.
     * @return el codigo agrupador, o vacio si no existe.
     */
    Optional<CodigoAgrupadorSat> buscarPorCodigo(String codigo);

    /**
     * Busca codigos agrupadores por coincidencia en codigo o nombre.
     *
     * @param q     texto de busqueda; nulo/vacio devuelve el inicio del catalogo.
     * @param limite numero maximo de resultados.
     * @return los codigos que coinciden, ordenados por codigo.
     */
    List<CodigoAgrupadorSat> buscar(String q, int limite);
}
