package com.dessti.crm.comercial.cliente.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.comercial.cliente.application.ActualizarClienteCommand;
import com.dessti.crm.comercial.cliente.application.ClienteDto;
import com.dessti.crm.comercial.cliente.application.ContactoDto;
import com.dessti.crm.comercial.cliente.application.CrearClienteCommand;
import com.dessti.crm.comercial.cliente.application.CrearContactoCommand;
import com.dessti.crm.comercial.cliente.application.ServicioClientes;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo comercial-crm para la gestion de
 * {@link ClienteDto Clientes} y sus {@link ContactoDto Contactos}
 * (Req 5, 12; tarea 15.2).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /clientes} — da de alta un Cliente
 *       ({@code @autorizador.tiene('cliente','crear')}); responde
 *       <strong>201 Created</strong> con el {@link ClienteDto}. 422 si los datos
 *       son invalidos, 409 si el RFC ya existe entre activos (Req 5.1–5.3).</li>
 *   <li>{@code GET /clientes/{id}} — consulta un Cliente
 *       ({@code @autorizador.tiene('cliente','leer')}); <strong>200 OK</strong>.
 *       404 si no existe/otro tenant (Req 4.3, 23.3).</li>
 *   <li>{@code PUT /clientes/{id}} — actualiza un Cliente
 *       ({@code @autorizador.tiene('cliente','actualizar')}); <strong>200 OK</strong>.
 *       404/409/422 segun corresponda (Req 5.4).</li>
 *   <li>{@code DELETE /clientes/{id}} — baja logica del Cliente
 *       ({@code @autorizador.tiene('cliente','eliminar')}); <strong>200 OK</strong>
 *       con el {@link ClienteDto} desactivado (se conserva el historico). 404 si
 *       no existe/ya inactivo/otro tenant (Req 5.9).</li>
 *   <li>{@code GET /clientes?filtro=&page=&size=} — listado paginado
 *       ({@code @autorizador.tiene('cliente','listar')}); <strong>200 OK</strong>
 *       con {@link PaginaResponse}. Tamano por defecto 20, maximo 100; filtro por
 *       nombre o RFC sin distinguir mayusculas (Req 5.7, 5.8, 12.1).</li>
 *   <li>{@code POST /clientes/{id}/contactos} — asocia un Contacto a un Cliente
 *       activo ({@code @autorizador.tiene('contacto','crear')}); responde
 *       <strong>201 Created</strong> con el {@link ContactoDto}. 404 si el Cliente
 *       no existe/otro tenant, 422 si esta inactivo (Req 5.5, 5.6).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 5)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Estos permisos
 * ({@code cliente:{crear,leer,listar,actualizar,eliminar}} y {@code contacto:crear})
 * se sembraron en V5/V11 y se asignaron al rol {@code ventas}. Sin el permiso,
 * Spring Security responde 403 por denegacion por defecto (Req 3.2).</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA. Los cuerpos de
 * entrada ({@code *Request}) se traducen a comandos de aplicacion en el
 * controlador; el {@code tenant_id} y el actor se derivan del contexto y nunca
 * se aceptan en la peticion. El manejo de errores lo centraliza
 * {@code ManejadorGlobalErrores}: el controlador se limita a dejar propagar las
 * excepciones de dominio.</p>
 */
@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final ServicioClientes servicioClientes;

    public ClienteController(ServicioClientes servicioClientes) {
        this.servicioClientes = servicioClientes;
    }

    /**
     * Da de alta un Cliente (Req 5.1–5.3). 422 si los datos son invalidos; 409
     * si ya existe un Cliente activo con el mismo RFC.
     *
     * @param request datos del Cliente a crear.
     * @return 201 Created con el {@link ClienteDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cliente','crear')")
    public ResponseEntity<ClienteDto> crear(@Valid @RequestBody CrearClienteRequest request) {
        ClienteDto dto = servicioClientes.crearCliente(new CrearClienteCommand(
                request.nombre(),
                request.rfc(),
                request.email(),
                request.telefono(),
                request.nombreComercial(),
                request.tipoPersona(),
                request.telefonoAdicional(),
                request.direccionCalle(),
                request.direccionCiudad(),
                request.direccionEstado(),
                request.direccionCp(),
                request.direccionPais(),
                request.notas()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Cliente por su identificador (Req 4.3, 23.3). 404 si no existe,
     * esta inactivo o pertenece a otro tenant.
     *
     * @param id identificador del Cliente.
     * @return 200 OK con el {@link ClienteDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cliente','leer')")
    public ResponseEntity<ClienteDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioClientes.consultarCliente(id));
    }

    /**
     * Actualiza los datos de un Cliente activo (Req 5.4). 404 si no es accesible;
     * 409 si el nuevo RFC colisiona con otro Cliente activo; 422 si los datos son
     * invalidos.
     *
     * @param id      identificador del Cliente.
     * @param request nuevos datos.
     * @return 200 OK con el {@link ClienteDto} actualizado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cliente','actualizar')")
    public ResponseEntity<ClienteDto> actualizar(@PathVariable("id") UUID id,
                                                  @Valid @RequestBody ActualizarClienteRequest request) {
        ClienteDto dto = servicioClientes.actualizarCliente(id, new ActualizarClienteCommand(
                request.nombre(),
                request.rfc(),
                request.email(),
                request.telefono(),
                request.nombreComercial(),
                request.tipoPersona(),
                request.telefonoAdicional(),
                request.direccionCalle(),
                request.direccionCiudad(),
                request.direccionEstado(),
                request.direccionCp(),
                request.direccionPais(),
                request.notas()));
        return ResponseEntity.ok(dto);
    }

    /**
     * Realiza el borrado logico de un Cliente activo (Req 5.9): lo marca inactivo
     * conservando su historico. Se responde 200 OK con el {@link ClienteDto}
     * desactivado para que el consumidor confirme el nuevo estado; 404 si no
     * existe, ya esta inactivo o pertenece a otro tenant.
     *
     * @param id identificador del Cliente.
     * @return 200 OK con el {@link ClienteDto} desactivado.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cliente','eliminar')")
    public ResponseEntity<ClienteDto> eliminar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioClientes.desactivarCliente(id));
    }

    /**
     * Lista los Clientes activos del tenant de forma paginada (20 por defecto,
     * 100 maximo) filtrando por nombre o RFC sin distinguir mayusculas
     * (Req 5.7, 5.8, 12.1). Un {@code filtro} nulo o en blanco lista todos los
     * Clientes activos; el {@code size} superior al maximo se acota a 100.
     *
     * @param filtro subcadena a buscar en nombre o RFC; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ClienteDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('cliente','listar')")
    public PaginaResponse<ClienteDto> listar(
            @RequestParam(name = "filtro", required = false) String filtro,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioClientes.listarClientes(filtro, pageable));
    }

    /**
     * Asocia un Contacto a un Cliente activo (Req 5.5, 5.6). El identificador del
     * Cliente se toma de la ruta; el cuerpo aporta los datos del Contacto. 404 si
     * el Cliente no existe/otro tenant, 422 si esta inactivo o el Contacto es
     * invalido.
     *
     * @param id      identificador del Cliente propietario.
     * @param request datos del Contacto.
     * @return 201 Created con el {@link ContactoDto} creado.
     */
    @PostMapping("/{id}/contactos")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('contacto','crear')")
    public ResponseEntity<ContactoDto> asociarContacto(@PathVariable("id") UUID id,
                                                        @Valid @RequestBody CrearContactoRequest request) {
        ContactoDto dto = servicioClientes.asociarContacto(new CrearContactoCommand(
                id,
                request.nombre(),
                request.email(),
                request.telefono()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
