package com.dessti.crm.platform.respaldo.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.respaldo.domain.Respaldo;

/**
 * DTO de salida de una ejecucion de {@link Respaldo} (Req 12.2). Expone
 * metadatos de la bitacora sin filtrar contenido ni material de llaves: solo el
 * <em>alias</em> de la llave (version), nunca su valor (Req 67.2).
 *
 * @param id           identificador de la ejecucion.
 * @param tipo         tipo ({@code respaldo} o {@code restauracion}).
 * @param estado       estado ({@code en_proceso}, {@code completado}, {@code fallido}).
 * @param alcance      alcance de la operacion.
 * @param instante     marca temporal UTC de la operacion.
 * @param ubicacion    referencia del artefacto cifrado (sin contenido).
 * @param aliasLlave   alias/version de la Llave_Cifrado usada (sin material).
 * @param checksum     hash SHA-256 del artefacto cifrado.
 * @param tamanoBytes  tamano del artefacto cifrado en bytes (o {@code null}).
 * @param actor        quien disparo la operacion.
 * @param detalleError detalle del fallo si el estado es {@code fallido}.
 * @param version      version optimista.
 */
public record RespaldoDto(
        UUID id,
        String tipo,
        String estado,
        String alcance,
        Instant instante,
        String ubicacion,
        String aliasLlave,
        String checksum,
        Long tamanoBytes,
        String actor,
        String detalleError,
        long version) {

    /**
     * Proyecta la entidad a su DTO de salida.
     *
     * @param r entidad de respaldo.
     * @return el DTO correspondiente.
     */
    public static RespaldoDto de(Respaldo r) {
        return new RespaldoDto(
                r.getId(),
                r.getTipo() == null ? null : r.getTipo().valor(),
                r.getEstado() == null ? null : r.getEstado().valor(),
                r.getAlcance(),
                r.getInstante(),
                r.getUbicacion(),
                r.getAliasLlave(),
                r.getChecksum(),
                r.getTamanoBytes(),
                r.getActor(),
                r.getDetalleError(),
                r.getVersion());
    }
}
