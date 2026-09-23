package com.dessti.crm.comercial.cliente.domain;

/**
 * Portador inmutable de los datos BASICOS de negocio OPCIONALES del
 * {@link Cliente} (Req 5, V59): nombre comercial, tipo de persona, telefono
 * adicional, direccion desglosada y notas libres.
 *
 * <p>Agrupa los campos complementarios del Cliente para no alargar las firmas
 * de {@link Cliente#crear} / {@link Cliente#actualizar}, replicando el patron de
 * {@code DatosDescriptivosEmpresa}. Todos los campos son <strong>opcionales</strong>:
 * un valor {@code null} o en blanco se interpreta como "sin dato".</p>
 *
 * <p>La normalizacion (recorte de espacios, {@code null} para blancos) y la
 * validacion de longitudes/valores contra las cotas de la V59 las aplica la
 * entidad {@link Cliente} al asignar estos datos; el record solo transporta los
 * valores en crudo entre capas. Un valor {@code null} de este mismo record
 * equivale a "todos los campos ausentes".</p>
 *
 * @param nombreComercial   nombre comercial (marca); opcional (max. 200).
 * @param tipoPersona       tipo de persona ('fisica' | 'moral'); opcional.
 * @param telefonoAdicional telefono secundario (10..15 digitos); opcional.
 * @param direccionCalle    calle y numero; opcional (max. 200).
 * @param direccionCiudad   ciudad; opcional (max. 120).
 * @param direccionEstado   estado/provincia; opcional (max. 120).
 * @param direccionCp       codigo postal; opcional (max. 10).
 * @param direccionPais     pais; opcional (max. 80).
 * @param notas             notas libres; opcional (max. 1000).
 */
public record DatosBasicosCliente(
        String nombreComercial,
        String tipoPersona,
        String telefonoAdicional,
        String direccionCalle,
        String direccionCiudad,
        String direccionEstado,
        String direccionCp,
        String direccionPais,
        String notas) {

    /** Instancia con todos los campos ausentes ({@code null}). */
    public static final DatosBasicosCliente VACIO =
            new DatosBasicosCliente(null, null, null, null, null, null, null, null, null);

    /**
     * Devuelve la instancia recibida o {@link #VACIO} si es {@code null}, para
     * que la entidad pueda tratar de forma uniforme la ausencia de datos basicos.
     *
     * @param datos datos basicos; puede ser {@code null}.
     * @return {@code datos} si no es nulo; {@link #VACIO} en otro caso.
     */
    public static DatosBasicosCliente orVacio(DatosBasicosCliente datos) {
        return (datos == null) ? VACIO : datos;
    }
}
