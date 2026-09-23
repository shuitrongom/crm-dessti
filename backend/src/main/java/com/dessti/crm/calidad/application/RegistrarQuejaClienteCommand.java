package com.dessti.crm.calidad.application;

import java.util.UUID;

import com.dessti.crm.calidad.domain.OrigenQueja;

/**
 * Comando de aplicacion para registrar una {@link com.dessti.crm.calidad.domain.QuejaCliente}
 * (Req 70.1, 70.8). El {@code clienteId} debe venir ya resuelto; una queja de origen
 * social se crea pasando {@code origen = SOCIAL} y el {@code canalSocialId} de la
 * Conversacion, sin acoplar {@code calidad} a {@code social}.
 *
 * @param clienteId     Cliente asociado; obligatorio.
 * @param origen        origen de la queja; obligatorio.
 * @param canalSocialId referencia al canal social de origen; opcional (solo social).
 * @param descripcion   descripcion de la queja; obligatoria.
 */
public record RegistrarQuejaClienteCommand(
        UUID clienteId,
        OrigenQueja origen,
        UUID canalSocialId,
        String descripcion) {
}
