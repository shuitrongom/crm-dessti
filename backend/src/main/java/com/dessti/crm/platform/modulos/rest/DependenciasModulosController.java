package com.dessti.crm.platform.modulos.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.modulos.CatalogoDependenciasModulos;

/**
 * Adaptador de entrada REST que expone, de forma <strong>consultable</strong>, el
 * catalogo de dependencias entre modulos de la plataforma (Req 6.2), para que la
 * UX del super_admin pueda derivar avisos y bloqueos de deseleccion sin listas de
 * modulos hardcodeadas en la vista.
 *
 * <p>Ruta (relativa al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /plataforma/dependencias-modulos} — devuelve el mapa
 *       clave dependiente &rarr; lista de requeridos directos derivado de
 *       {@link CatalogoDependenciasModulos} (p. ej.
 *       {@code {"inventario-avanzado":["operacion"]}}, o {@code {}} si el catalogo
 *       estuviera vacio). 200.</li>
 * </ul>
 *
 * <h2>Autorizacion de plataforma</h2>
 * <p>Se reutiliza el <strong>mismo permiso</strong> que {@code /plataforma/modulos}:
 * {@code plan:listar} ({@code @autorizador.tiene('plan','listar')}), que posee
 * unicamente el {@code super_admin}. Cualquier otro rol recibe 403 por la
 * denegacion por defecto de Spring Security. Se replica el estilo de
 * {@link ModuloController}: {@code @RestController} + {@code @RequestMapping},
 * {@code @PreAuthorize} por operacion y proyeccion a una estructura inmutable.</p>
 *
 * <p>Se implementa como clase hermana de {@link ModuloController} (mismo paquete
 * {@code platform.modulos.rest}) porque su ruta base difiere de
 * {@code /plataforma/modulos}; asi no se altera el contrato de ese endpoint.</p>
 */
@RestController
@RequestMapping("/plataforma/dependencias-modulos")
public class DependenciasModulosController {

    /**
     * Lista el catalogo de dependencias entre modulos.
     *
     * @return 200 con un mapa {@code clave dependiente -> lista de requeridos
     *         directos}, derivado de {@link CatalogoDependenciasModulos#todas()};
     *         {@code {}} si no hay dependencias declaradas.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('plan','listar')")
    public Map<String, List<String>> listar() {
        Map<String, List<String>> resultado = new LinkedHashMap<>();
        CatalogoDependenciasModulos.todas().forEach(
                (dependiente, requeridos) -> resultado.put(dependiente, List.copyOf(requeridos)));
        return resultado;
    }
}
