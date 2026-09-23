package com.dessti.crm.comercial.cliente.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.comercial.cliente.domain.Cliente;
import com.dessti.crm.comercial.cliente.domain.TipoPersona;

/**
 * DTO de salida de un {@link Cliente} (Req 12.2, 5), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA.
 *
 * <p>Incluye los datos basicos de negocio opcionales (nombre comercial, tipo de
 * persona, telefono adicional, direccion desglosada y notas, V59) para permitir
 * la consulta y el prellenado de la edicion en el frontend.</p>
 *
 * @param id                identificador del Cliente.
 * @param nombre            razon social o nombre.
 * @param rfc               identificador fiscal (normalizado a mayusculas).
 * @param email             correo electronico; puede ser {@code null}.
 * @param telefono          telefono; puede ser {@code null}.
 * @param nombreComercial   nombre comercial (marca); puede ser {@code null}.
 * @param tipoPersona       tipo de persona ('fisica' | 'moral'); puede ser {@code null}.
 * @param telefonoAdicional telefono secundario; puede ser {@code null}.
 * @param direccionCalle    calle y numero; puede ser {@code null}.
 * @param direccionCiudad   ciudad; puede ser {@code null}.
 * @param direccionEstado   estado/provincia; puede ser {@code null}.
 * @param direccionCp       codigo postal; puede ser {@code null}.
 * @param direccionPais     pais; puede ser {@code null}.
 * @param notas             notas libres; puede ser {@code null}.
 * @param activo            {@code true} si el Cliente esta vigente (no dado de baja).
 * @param version           version para concurrencia optimista (Req 49).
 * @param createdAt         instante de alta (UTC).
 * @param updatedAt         instante de la ultima modificacion (UTC).
 */
public record ClienteDto(
        UUID id,
        String nombre,
        String rfc,
        String email,
        String telefono,
        String nombreComercial,
        String tipoPersona,
        String telefonoAdicional,
        String direccionCalle,
        String direccionCiudad,
        String direccionEstado,
        String direccionCp,
        String direccionPais,
        String notas,
        boolean activo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Cliente} a su DTO de salida. El tipo de persona
     * se expone como su clave en minusculas ({@code 'fisica'} / {@code 'moral'})
     * o {@code null} si no se registro.
     *
     * @param cliente entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ClienteDto de(Cliente cliente) {
        TipoPersona tipo = cliente.getTipoPersona();
        return new ClienteDto(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getRfc(),
                cliente.getEmail(),
                cliente.getTelefono(),
                cliente.getNombreComercial(),
                (tipo == null) ? null : tipo.clave(),
                cliente.getTelefonoAdicional(),
                cliente.getDireccionCalle(),
                cliente.getDireccionCiudad(),
                cliente.getDireccionEstado(),
                cliente.getDireccionCp(),
                cliente.getDireccionPais(),
                cliente.getNotas(),
                cliente.isActivo(),
                cliente.getVersion(),
                cliente.getCreatedAt(),
                cliente.getUpdatedAt());
    }
}
