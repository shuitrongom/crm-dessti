package com.dessti.crm.platform.audit;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Resultado estructurado de la verificacion de integridad de la cadena de hash
 * de la bitacora de auditoria (Req 10.13).
 *
 * <p>La verificacion recorre los registros en orden por {@code id} y comprueba,
 * para cada uno, que su {@code hash_actual} coincide con
 * {@code SHA-256(contenido || hash_previo)} y que su {@code hash_previo} enlaza
 * con el {@code hash_actual} del registro anterior (o con la semilla para el
 * primero del rango cuando se verifica la cadena completa). Ante la primera
 * discrepancia, la verificacion se detiene y <strong>reporta la posicion de la
 * ruptura</strong> (id del registro y motivo), sustentando el no repudio.</p>
 *
 * <p>Es un objeto de dominio inmutable, libre de dependencias de framework.</p>
 *
 * @param intacta         {@code true} si todos los registros del rango
 *                        verificado encadenan correctamente; {@code false} si se
 *                        detecto una ruptura.
 * @param registrosVerificados numero de registros efectivamente recorridos.
 * @param idRuptura       id del primer registro donde se detecta la ruptura;
 *                        vacio si la cadena esta intacta.
 * @param motivo          descripcion legible de la ruptura (sin secretos);
 *                        vacio si la cadena esta intacta.
 */
public record ResultadoVerificacionCadena(
        boolean intacta,
        long registrosVerificados,
        OptionalLong idRuptura,
        Optional<String> motivo) {

    /** Normaliza los opcionales para evitar {@code null}. */
    public ResultadoVerificacionCadena {
        idRuptura = (idRuptura == null) ? OptionalLong.empty() : idRuptura;
        motivo = (motivo == null) ? Optional.empty() : motivo;
    }

    /**
     * Crea un resultado que confirma la integridad de la cadena.
     *
     * @param registrosVerificados numero de registros recorridos.
     * @return resultado intacto.
     */
    public static ResultadoVerificacionCadena intacta(long registrosVerificados) {
        return new ResultadoVerificacionCadena(
                true, registrosVerificados, OptionalLong.empty(), Optional.empty());
    }

    /**
     * Crea un resultado que reporta la primera ruptura detectada.
     *
     * @param registrosVerificados numero de registros recorridos hasta la ruptura
     *                             (incluido el registro roto).
     * @param idRuptura            id del registro donde se rompe la cadena.
     * @param motivo               descripcion de la ruptura.
     * @return resultado con ruptura.
     */
    public static ResultadoVerificacionCadena rota(
            long registrosVerificados, long idRuptura, String motivo) {
        return new ResultadoVerificacionCadena(
                false, registrosVerificados, OptionalLong.of(idRuptura), Optional.of(motivo));
    }
}
