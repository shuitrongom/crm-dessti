package com.dessti.crm.platform.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Pruebas unitarias acotadas del mapeo {@code Page<T>} → {@link PaginaResponse}
 * (Req 12). Verifican el contenido y los metadatos, incluyendo
 * {@code totalPages = ceil(totalElements / size)}.
 */
class PaginaResponseTest {

    @Test
    @DisplayName("de(Page) copia contenido y metadatos; totalPages = ceil(totalElements/size)")
    void de_page_copiaContenidoYMetadatos() {
        // 137 elementos con size 20 → ceil(137/20) = 7 paginas
        Page<String> page = new PageImpl<>(
                List.of("a", "b", "c"),
                PageRequest.of(0, 20),
                137);

        PaginaResponse<String> resp = PaginaResponse.de(page);

        assertThat(resp.content()).containsExactly("a", "b", "c");
        assertThat(resp.page()).isEqualTo(0);
        assertThat(resp.size()).isEqualTo(20);
        assertThat(resp.totalElements()).isEqualTo(137);
        assertThat(resp.totalPages()).isEqualTo(7);
    }

    @Test
    @DisplayName("totalPages es 0 cuando no hay elementos")
    void de_page_vacia_totalPagesCero() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        PaginaResponse<String> resp = PaginaResponse.de(page);

        assertThat(resp.content()).isEmpty();
        assertThat(resp.totalElements()).isZero();
        assertThat(resp.totalPages()).isZero();
    }

    @Test
    @DisplayName("de(Page, mapeador) transforma cada elemento y preserva los metadatos")
    void de_conMapeador_transformaElementos() {
        Page<Integer> page = new PageImpl<>(
                List.of(1, 2, 3),
                PageRequest.of(1, 3),
                10);

        PaginaResponse<String> resp = PaginaResponse.de(page, n -> "n" + n);

        assertThat(resp.content()).containsExactly("n1", "n2", "n3");
        assertThat(resp.page()).isEqualTo(1);
        assertThat(resp.size()).isEqualTo(3);
        assertThat(resp.totalElements()).isEqualTo(10);
        // ceil(10/3) = 4
        assertThat(resp.totalPages()).isEqualTo(4);
    }
}
