package com.dessti.crm.contabilidad.electronica.domain.modelo;

/**
 * Modelo plano de una cuenta para el Catalogo_XML del SAT (Anexo 24). Es la entrada
 * al {@code GeneradorCatalogoXml}, desacoplada de la entidad JPA.
 *
 * @param codAgrup   codigo agrupador del SAT amarrado (elemento {@code CodAgrup}).
 * @param numCta     codigo de la cuenta contable de la Empresa ({@code NumCta}).
 * @param desc       nombre/descripcion de la cuenta ({@code Desc}).
 * @param nivel      nivel de la cuenta en el catalogo (1 = mayor, 2 = subcuenta).
 * @param natur      naturaleza para el SAT: {@code D} (deudora) o {@code A} (acreedora).
 */
public record CuentaCatalogoSat(
        String codAgrup,
        String numCta,
        String desc,
        int nivel,
        String natur) {
}
