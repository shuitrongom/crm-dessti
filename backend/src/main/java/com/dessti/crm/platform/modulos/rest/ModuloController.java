package com.dessti.crm.platform.modulos.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.modulos.CatalogoModulosService;
import com.dessti.crm.platform.modulos.ModuloCatalogoDto;

/**
 * Adaptador de entrada REST que expone el catalogo de modulos de la plataforma
 * para que el editor de Planes y el alta de Empresas del frontend pinten
 * checkboxes reales de modulos habilitados.
 *
 * <p>Ruta (relativa al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /plataforma/modulos} — devuelve el catalogo completo de
 *       modulos ({@link ModuloCatalogoDto}) ordenado de forma determinista
 *       (Nucleo primero, luego verticales por Giro). 200.</li>
 * </ul>
 *
 * <h2>Autorizacion de plataforma</h2>
 * <p>Se reutiliza el permiso de plataforma {@code plan:listar}
 * ({@code @autorizador.tiene('plan','listar')}): lo posee unicamente el
 * {@code super_admin} y encaja con el uso del catalogo (el editor de Planes lo
 * consume para elegir los modulos del Plan). Cualquier otro rol recibe 403 por la
 * denegacion por defecto de Spring Security. Se replica el patron de
 * {@code platform.giros.rest.GiroController}: {@code @RestController} +
 * {@code @RequestMapping}, {@code @PreAuthorize} por operacion y proyeccion a DTO
 * inmutable.</p>
 */
@RestController
@RequestMapping("/plataforma/modulos")
public class ModuloController {

    private final CatalogoModulosService catalogoModulosService;

    public ModuloController(CatalogoModulosService catalogoModulosService) {
        this.catalogoModulosService = catalogoModulosService;
    }

    /**
     * Lista el catalogo completo de modulos de la plataforma.
     *
     * @return 200 con la lista de {@link ModuloCatalogoDto} (clave, nombre visible
     *         y clave de Giro; {@code giro} nulo para modulos de Nucleo).
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('plan','listar')")
    public List<ModuloCatalogoDto> listar() {
        return catalogoModulosService.listar();
    }
}
