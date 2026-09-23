package com.dessti.crm.platform.modulos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de ejemplos y casos borde del catalogo estatico de
 * dependencias entre modulos {@link CatalogoDependenciasModulos}. Cubren la
 * consulta de requeridos directos ({@link CatalogoDependenciasModulos#requeridosDe(String)})
 * y el cierre transitivo con normalizacion de claves
 * ({@link CatalogoDependenciasModulos#normalizar(java.util.Collection)}).
 *
 * <p>Complementan a las pruebas basadas en propiedades (jqwik) verificando
 * ejemplos concretos, normalizacion de mayusculas/espacios, entradas
 * nulas/vacias, no-duplicacion y preservacion del orden de insercion.</p>
 *
 * <p>No arrancan Spring ni base de datos: la clase bajo prueba es utilidad
 * estatica pura.</p>
 *
 * <p>Requisitos: 6.1, 6.3, 7.3, 8.3.</p>
 */
class CatalogoDependenciasModulosTest {

    // ---------------------------------------------------------------------
    // requeridosDe(String)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("requeridosDe('inventario-avanzado') devuelve {'operacion'} (Req 6.1)")
    void requeridosDeInventarioAvanzado() {
        assertThat(CatalogoDependenciasModulos.requeridosDe("inventario-avanzado"))
                .containsExactly("operacion");
    }

    @Test
    @DisplayName("requeridosDe('operacion') es vacio: la dependencia es unidireccional (Req 6.1)")
    void requeridosDeOperacionEsVacio() {
        assertThat(CatalogoDependenciasModulos.requeridosDe("operacion")).isEmpty();
    }

    @Test
    @DisplayName("requeridosDe con clave nula, vacia o de espacios devuelve vacio (Req 6.1)")
    void requeridosDeClaveNulaVaciaOEspacios() {
        assertThat(CatalogoDependenciasModulos.requeridosDe(null)).isEmpty();
        assertThat(CatalogoDependenciasModulos.requeridosDe("")).isEmpty();
        assertThat(CatalogoDependenciasModulos.requeridosDe("   ")).isEmpty();
    }

    @Test
    @DisplayName("requeridosDe normaliza mayusculas/espacios en la clave de entrada (Req 6.1)")
    void requeridosDeNormalizaClave() {
        assertThat(CatalogoDependenciasModulos.requeridosDe("  INVENTARIO-AVANZADO  "))
                .containsExactly("operacion");
    }

    // ---------------------------------------------------------------------
    // normalizar(Collection<String>)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("normalizar con 'inventario-avanzado' agrega 'operacion' (Req 6.1, 8.3)")
    void normalizarAgregaOperacion() {
        assertThat(CatalogoDependenciasModulos.normalizar(List.of("inventario-avanzado")))
                .containsExactly("inventario-avanzado", "operacion");
    }

    @Test
    @DisplayName("normalizar con 'operacion' ya presente NO duplica la clave (Req 7.3, 8.3)")
    void normalizarNoDuplicaOperacionPreexistente() {
        Set<String> resultado =
                CatalogoDependenciasModulos.normalizar(List.of("inventario-avanzado", "operacion"));

        assertThat(resultado).containsExactly("inventario-avanzado", "operacion");
        assertThat(resultado).hasSize(2);
    }

    @Test
    @DisplayName("normalizar(null) devuelve un conjunto vacio (Req 6.1)")
    void normalizarNuloEsVacio() {
        assertThat(CatalogoDependenciasModulos.normalizar(null)).isEmpty();
    }

    @Test
    @DisplayName("normalizar(vacio) devuelve un conjunto vacio (Req 6.1)")
    void normalizarVacioEsVacio() {
        assertThat(CatalogoDependenciasModulos.normalizar(List.of())).isEmpty();
    }

    @Test
    @DisplayName("normalizar ignora elementos nulos y vacios/espacios (Req 6.3)")
    void normalizarIgnoraNulosYVacios() {
        // Se usa una lista con nulos (List.of no admite null).
        java.util.List<String> entrada = new java.util.ArrayList<>();
        entrada.add(null);
        entrada.add("");
        entrada.add("   ");
        entrada.add("inventario-avanzado");

        assertThat(CatalogoDependenciasModulos.normalizar(entrada))
                .containsExactly("inventario-avanzado", "operacion");
    }

    @Test
    @DisplayName("normalizar preserva el orden de insercion: entradas primero, requeridos despues (Req 6.3)")
    void normalizarPreservaOrdenDeInsercion() {
        Set<String> resultado = CatalogoDependenciasModulos.normalizar(
                List.of("comercial", "inventario-avanzado", "estrategia", "redes-sociales"));

        // Las claves de entrada conservan su orden original y 'operacion' se
        // agrega al final, tras las entradas.
        assertThat(resultado).containsExactly(
                "comercial", "inventario-avanzado", "estrategia", "redes-sociales", "operacion");
    }

    @Test
    @DisplayName("normalizar normaliza claves con mayusculas/espacios (recorte + minusculas) (Req 6.3)")
    void normalizarNormalizaClaves() {
        assertThat(CatalogoDependenciasModulos.normalizar(
                List.of("  INVENTARIO-AVANZADO  ", "Comercial")))
                .containsExactly("inventario-avanzado", "comercial", "operacion");
    }
}
