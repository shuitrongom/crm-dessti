package com.dessti.crm.platform.web.pagination;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Respuesta paginada generica y reutilizable por todos los modulos (Req 12).
 *
 * <p>Su estructura coincide con el contrato de API documentado en el diseno:
 * {@code content}, {@code page}, {@code size}, {@code totalElements} y
 * {@code totalPages}. Se expone como DTO (nunca la entidad JPA) para desacoplar
 * el modelo interno del contrato REST.</p>
 *
 * @param <T>           tipo de los elementos de la pagina
 * @param content       elementos de la pagina actual
 * @param page          numero de pagina 0-index
 * @param size          tamano de pagina solicitado
 * @param totalElements total de elementos en todas las paginas
 * @param totalPages    total de paginas ({@code ceil(totalElements / size)})
 */
public record PaginaResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /**
     * Convierte una {@link Page} de Spring Data en un {@link PaginaResponse}
     * conservando su contenido tal cual.
     *
     * @param page pagina de origen de Spring Data
     * @param <T>  tipo de los elementos
     * @return la representacion como DTO paginado
     */
    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /**
     * Convierte una {@link Page} de entidades en un {@link PaginaResponse} de
     * DTOs aplicando la funcion de mapeo indicada a cada elemento. Los
     * metadatos de paginacion (pagina, tamano, totales) se preservan.
     *
     * @param page      pagina de origen de Spring Data (entidades)
     * @param mapeador  funcion que transforma cada entidad en su DTO
     * @param <E>       tipo de la entidad de origen
     * @param <T>       tipo del DTO de destino
     * @return la representacion como DTO paginado
     */
    public static <E, T> PaginaResponse<T> de(Page<E> page, Function<? super E, ? extends T> mapeador) {
        return de(page.map(mapeador));
    }
}
