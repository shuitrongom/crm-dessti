package com.dessti.crm.platform.respaldo.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.respaldo.application.RespaldoDto;
import com.dessti.crm.platform.respaldo.application.ServicioRespaldo;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

/**
 * Adaptador REST de plataforma para el respaldo y la recuperacion de datos
 * (Req 50). Todas las rutas exigen permisos {@code respaldo:*}, que la migracion
 * V46 reserva EXCLUSIVAMENTE al {@code super_admin} (acceso restringido,
 * Req 50.3). {@code respaldo} es un recurso de plataforma (ver
 * {@code ClasificadorRecursosPlataforma}): ningun rol de empresa puede incluirlo.
 *
 * <p>La restauracion y el disparo de respaldo bajo demanda son operaciones
 * sensibles; ademas del RBAC, cada ejecucion queda registrada en la bitacora y
 * en auditoria (Req 50.4) por {@link ServicioRespaldo}.</p>
 */
@RestController
@RequestMapping("/respaldos")
public class RespaldoController {

    private final ServicioRespaldo servicioRespaldo;

    public RespaldoController(ServicioRespaldo servicioRespaldo) {
        this.servicioRespaldo = servicioRespaldo;
    }

    /**
     * Dispara un respaldo completo cifrado bajo demanda (Req 50.1, 50.3).
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('respaldo','ejecutar')")
    public ResponseEntity<RespaldoDto> ejecutar() {
        RespaldoDto dto = servicioRespaldo.ejecutarRespaldo();
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Restaura la base de datos a partir de un respaldo previo (Req 50.2).
     * Operacion critica y auditada, reservada al {@code super_admin} (Req 50.3).
     *
     * @param id identificador del respaldo de origen.
     */
    @PostMapping("/{id}/restaurar")
    @PreAuthorize("@autorizador.tiene('respaldo','restaurar')")
    public ResponseEntity<RespaldoDto> restaurar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioRespaldo.restaurar(id));
    }

    /**
     * Historial paginado de la bitacora de respaldos/restauraciones (Req 50.4).
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('respaldo','leer')")
    public PaginaResponse<RespaldoDto> listar(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioRespaldo.listar(pageable));
    }
}
