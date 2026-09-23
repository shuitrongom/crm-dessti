package com.dessti.crm.platform.web.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Fabrica centralizada de {@link Pageable} a partir de los parametros de
 * paginacion de una peticion REST (Req 12).
 *
 * <p>Aplica los limites estandar definidos en {@link PaginacionConstantes}:
 * numero de pagina 0-index (por defecto {@value PaginacionConstantes#DEFAULT_PAGE})
 * y tamano de pagina por defecto {@value PaginacionConstantes#DEFAULT_SIZE} con
 * maximo {@value PaginacionConstantes#MAX_SIZE}.</p>
 *
 * <h2>Politica de tamano de pagina</h2>
 * <ul>
 *   <li><b>Rechazar (comportamiento estandar):</b> un {@code size} superior a
 *   {@link PaginacionConstantes#MAX_SIZE} lanza
 *   {@link ParametrosPaginacionInvalidosException}, que el manejador global
 *   traduce a HTTP 400. Es el comportamiento por defecto por ser coherente con
 *   el Req 7.8 (rechazo explicito) y evitar respuestas ambiguas.</li>
 *   <li><b>Acotar (opcional):</b> {@link #acotando(Integer, Integer)} limita el
 *   {@code size} a {@link PaginacionConstantes#MAX_SIZE} en lugar de rechazar,
 *   util para endpoints donde el requisito solo exige acotar.</li>
 * </ul>
 *
 * <p>En ambos modos, un {@code page} negativo o un {@code size} menor a 1 se
 * rechazan siempre con 400, ya que no representan una pagina valida.</p>
 */
public final class PageRequestFactory {

    private PageRequestFactory() {
        // Clase de utilidades: no instanciable.
    }

    /**
     * Construye un {@link Pageable} aplicando la politica estandar de
     * <b>rechazo</b>: un {@code size} superior al maximo se rechaza con 400.
     *
     * @param page numero de pagina 0-index; {@code null} usa el valor por defecto
     * @param size tamano de pagina; {@code null} usa el valor por defecto
     * @return un {@link Pageable} sin ordenamiento
     * @throws ParametrosPaginacionInvalidosException si {@code page < 0},
     *         {@code size < 1} o {@code size > }{@link PaginacionConstantes#MAX_SIZE}
     */
    public static Pageable of(Integer page, Integer size) {
        return of(page, size, Sort.unsorted());
    }

    /**
     * Variante de {@link #of(Integer, Integer)} que ademas aplica un
     * ordenamiento.
     *
     * @param page numero de pagina 0-index; {@code null} usa el valor por defecto
     * @param size tamano de pagina; {@code null} usa el valor por defecto
     * @param sort criterio de ordenamiento (nunca {@code null}; use
     *             {@link Sort#unsorted()})
     * @return un {@link Pageable} con el ordenamiento indicado
     * @throws ParametrosPaginacionInvalidosException si los parametros son invalidos
     */
    public static Pageable of(Integer page, Integer size, Sort sort) {
        int pageEfectiva = resolverPagina(page);
        int sizeEfectivo = size == null ? PaginacionConstantes.DEFAULT_SIZE : size;

        if (sizeEfectivo < 1) {
            throw new ParametrosPaginacionInvalidosException("size",
                    "El tamano de pagina debe ser al menos 1.");
        }
        if (sizeEfectivo > PaginacionConstantes.MAX_SIZE) {
            throw new ParametrosPaginacionInvalidosException("size",
                    "El tamano de pagina no puede exceder " + PaginacionConstantes.MAX_SIZE + ".");
        }
        return PageRequest.of(pageEfectiva, sizeEfectivo, sort);
    }

    /**
     * Construye un {@link Pageable} aplicando la politica de <b>acotamiento</b>:
     * un {@code size} superior al maximo se limita a
     * {@link PaginacionConstantes#MAX_SIZE} en lugar de rechazarse. Un
     * {@code size} menor a 1 o un {@code page} negativo siguen rechazandose con 400.
     *
     * @param page numero de pagina 0-index; {@code null} usa el valor por defecto
     * @param size tamano de pagina; {@code null} usa el valor por defecto
     * @return un {@link Pageable} sin ordenamiento con el tamano acotado
     * @throws ParametrosPaginacionInvalidosException si {@code page < 0} o {@code size < 1}
     */
    public static Pageable acotando(Integer page, Integer size) {
        int pageEfectiva = resolverPagina(page);
        int sizeEfectivo = size == null ? PaginacionConstantes.DEFAULT_SIZE : size;

        if (sizeEfectivo < 1) {
            throw new ParametrosPaginacionInvalidosException("size",
                    "El tamano de pagina debe ser al menos 1.");
        }
        sizeEfectivo = Math.min(sizeEfectivo, PaginacionConstantes.MAX_SIZE);
        return PageRequest.of(pageEfectiva, sizeEfectivo);
    }

    private static int resolverPagina(Integer page) {
        int pageEfectiva = page == null ? PaginacionConstantes.DEFAULT_PAGE : page;
        if (pageEfectiva < 0) {
            throw new ParametrosPaginacionInvalidosException("page",
                    "El numero de pagina no puede ser negativo.");
        }
        return pageEfectiva;
    }
}
