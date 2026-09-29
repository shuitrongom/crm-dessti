package com.dessti.crm.operacion.proyecto.application.evidencia;

import java.time.Instant;

import com.dessti.crm.operacion.proyecto.domain.evidencia.EvidenciaAvanceSitio;

/**
 * DTO de salida de una {@link EvidenciaAvanceSitio} (Req 3.2). No expone la clave
 * de almacenamiento (dato interno del almacen de objetos): el binario se sirve por
 * el endpoint dedicado de archivo usando el {@code id} de la evidencia. Sí expone
 * los metadatos y el rastro de aprobacion para que el admin/encargado decida.
 *
 * @param id             identificador de la evidencia.
 * @param avanceSitioId  avance de Sitio que respalda.
 * @param fase           etiqueta de la fase que documenta (valorBd).
 * @param nombreOriginal nombre original del archivo (para mostrar/descargar).
 * @param tipoMime       tipo MIME (para elegir visor: imagen vs PDF).
 * @param tamanoBytes    tamano del archivo en bytes.
 * @param estado         etiqueta del estado de aprobacion (valorBd).
 * @param motivoRechazo  motivo si fue rechazada; {@code null} en otro caso.
 * @param subidaPor      actor que subio la evidencia.
 * @param subidaEn       instante UTC de la subida.
 * @param decididaPor    actor que decidio; {@code null} mientras esta pendiente.
 * @param decididaEn     instante UTC de la decision; {@code null} si pendiente.
 */
public record EvidenciaAvanceDto(
        String id,
        String avanceSitioId,
        String fase,
        String nombreOriginal,
        String tipoMime,
        long tamanoBytes,
        String estado,
        String motivoRechazo,
        String subidaPor,
        Instant subidaEn,
        String decididaPor,
        Instant decididaEn) {

    /** Proyecta la entidad a su DTO de salida (sin exponer la clave del almacen). */
    public static EvidenciaAvanceDto de(EvidenciaAvanceSitio e) {
        return new EvidenciaAvanceDto(
                e.getId().toString(),
                e.getAvanceSitioId().toString(),
                e.getFase().valorBd(),
                e.getNombreOriginal(),
                e.getTipoMime(),
                e.getTamanoBytes(),
                e.getEstado().valorBd(),
                e.getMotivoRechazo(),
                e.getCreatedBy(),
                e.getSubidaEn(),
                e.getDecididaPor(),
                e.getDecididaEn());
    }
}
