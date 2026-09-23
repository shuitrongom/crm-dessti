package com.dessti.crm.social.application;

import java.util.UUID;

/**
 * DTO de salida de la consulta de estado <strong>de SOLO LECTURA</strong> de una
 * Campaña_Publicitaria (Req 65.9). Envuelve la instantanea
 * {@link EstadoCampanaExterno} obtenida del adaptador junto al identificador de la
 * campaña local, dejando explicito que el estado no es modificable desde el CRM.
 *
 * @param campanaId  identificador de la Campaña_Publicitaria en el CRM.
 * @param externoId  id de la campaña en la Marketing API; {@code null} si no aplica.
 * @param estado     etiqueta del estado externo; {@code null} si no disponible.
 * @param detalle    descripcion adicional del estado; opcional.
 * @param disponible {@code true} si la consulta a la API externa se resolvio.
 * @param mensaje    mensaje informativo (motivo de indisponibilidad); opcional.
 * @param soloLectura siempre {@code true}: el estado externo no se puede modificar (Req 65.9).
 */
public record EstadoCampanaExternoDto(
        UUID campanaId,
        String externoId,
        String estado,
        String detalle,
        boolean disponible,
        String mensaje,
        boolean soloLectura) {

    /**
     * Construye el DTO a partir de la instantanea del adaptador, marcandolo siempre
     * como de SOLO LECTURA (Req 65.9).
     *
     * @param campanaId identificador de la campaña local.
     * @param estado    instantanea de estado externo del adaptador; obligatoria.
     * @return el DTO de SOLO LECTURA.
     */
    public static EstadoCampanaExternoDto de(UUID campanaId, EstadoCampanaExterno estado) {
        return new EstadoCampanaExternoDto(
                campanaId,
                estado.externoId(),
                estado.estado(),
                estado.detalle(),
                estado.disponible(),
                estado.mensaje(),
                true);
    }
}
