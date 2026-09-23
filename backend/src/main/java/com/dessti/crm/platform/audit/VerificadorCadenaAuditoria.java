package com.dessti.crm.platform.audit;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificacion de integridad de la cadena de hash de la bitacora de auditoria
 * bajo demanda (Req 10.13).
 *
 * <p>Recorre los registros en orden ascendente por {@code id} y comprueba, para
 * cada uno, dos invariantes del encadenamiento:</p>
 * <ol>
 *   <li><strong>Enlace:</strong> el {@code hash_previo} del registro coincide
 *       con el {@code hash_actual} del registro inmediatamente anterior (o con
 *       la {@link CalculadoraHashCadena#HASH_SEMILLA semilla} para el primer
 *       registro de <em>toda</em> la cadena).</li>
 *   <li><strong>Contenido:</strong> el {@code hash_actual} almacenado coincide
 *       con {@code SHA-256(contenido || hash_previo)} recalculado a partir de
 *       los campos del registro, de modo que alterar cualquier campo se
 *       detecta.</li>
 * </ol>
 *
 * <p>Ante la <strong>primera</strong> discrepancia detiene el recorrido y
 * reporta la posicion de la ruptura (id del registro y motivo) en un
 * {@link ResultadoVerificacionCadena}. Si no hay discrepancias, confirma la
 * integridad del rango verificado.</p>
 *
 * <h2>Eficiencia</h2>
 * <p>La verificacion se procesa por <strong>lotes paginados</strong> ordenados
 * por {@code id} ({@value #TAMANO_LOTE} registros), de modo que no se carga la
 * bitacora completa en memoria. Ademas admite verificar un <strong>rango</strong>
 * por id (util para auditorias incrementales sobre bitacoras extensas).</p>
 *
 * <h2>Ambito de la verificacion</h2>
 * <p>La cadena es <strong>global</strong> (incluye eventos de plataforma con
 * {@code tenant_id} nulo), por lo que la verificacion no filtra por tenant: su
 * proposito es el no repudio de toda la bitacora. El control de acceso a esta
 * operacion (solo con permiso) corresponde a la capa de autorizacion (RBAC,
 * tarea 10); aqui solo reside la logica de verificacion.</p>
 */
@Service
public class VerificadorCadenaAuditoria {

    /** Tamano de lote para recorrer la cadena sin agotar memoria. */
    static final int TAMANO_LOTE = 500;

    private final RegistroAuditoriaRepository repositorio;

    /**
     * @param repositorio adaptador de lectura de la bitacora.
     */
    public VerificadorCadenaAuditoria(RegistroAuditoriaRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Verifica la integridad de <strong>toda</strong> la cadena de la bitacora
     * (desde el primer registro hasta el ultimo).
     *
     * @return resultado de la verificacion (intacta o con la primera ruptura).
     */
    @Transactional(readOnly = true)
    public ResultadoVerificacionCadena verificarCadenaCompleta() {
        return verificarRango(null, null);
    }

    /**
     * Verifica la integridad de la cadena en un rango de {@code id} (inclusive).
     *
     * <p>Cuando {@code idDesde} es {@code null} el rango arranca en el genesis y
     * el primer registro debe enlazar con la {@link CalculadoraHashCadena#HASH_SEMILLA
     * semilla}. Cuando {@code idDesde} acota el inicio en un registro intermedio,
     * el {@code hash_previo} de ese primer registro del rango se toma como valor
     * esperado de enlace inicial (se verifica el encadenamiento <em>interno</em>
     * del rango y el contenido de cada registro), lo que permite auditar tramos
     * concretos de bitacoras extensas.</p>
     *
     * @param idDesde limite inferior de id (inclusive); {@code null} = genesis.
     * @param idHasta limite superior de id (inclusive); {@code null} = ultimo.
     * @return resultado de la verificacion del rango.
     */
    @Transactional(readOnly = true)
    public ResultadoVerificacionCadena verificarRango(Long idDesde, Long idHasta) {
        long verificados = 0;
        String hashPrevioEsperado = null; // null => aun no fijado (primer registro del rango)
        int pagina = 0;

        while (true) {
            List<RegistroAuditoria> lote = repositorio
                    .recorrerCadenaPorId(idDesde, idHasta,
                            PageRequest.of(pagina, TAMANO_LOTE, Sort.by(Sort.Direction.ASC, "id")))
                    .getContent();
            if (lote.isEmpty()) {
                break;
            }

            for (RegistroAuditoria registro : lote) {
                // Enlace inicial: si el rango comienza en el genesis, se espera la
                // semilla; si comienza en un registro intermedio, se ancla al
                // hash_previo de ese primer registro para verificar el tramo.
                if (hashPrevioEsperado == null) {
                    hashPrevioEsperado = (idDesde == null)
                            ? CalculadoraHashCadena.HASH_SEMILLA
                            : registro.getHashPrevio();
                }

                if (!hashPrevioEsperado.equals(registro.getHashPrevio())) {
                    return ResultadoVerificacionCadena.rota(
                            verificados + 1, registro.getId(),
                            "El hash_previo del registro no enlaza con el hash_actual del anterior");
                }

                String hashRecalculado = CalculadoraHashCadena.calcular(
                        registro.getTenantId(),
                        registro.getActor(),
                        registro.getAccion(),
                        registro.getRecurso(),
                        registro.getDetalle(),
                        registro.getValorAnterior(),
                        registro.getValorNuevo(),
                        registro.getTraceId(),
                        registro.getTimestampUtc(),
                        registro.getHashPrevio());

                if (!hashRecalculado.equals(registro.getHashActual())) {
                    return ResultadoVerificacionCadena.rota(
                            verificados + 1, registro.getId(),
                            "El hash_actual no coincide con el hash del contenido (registro alterado)");
                }

                hashPrevioEsperado = registro.getHashActual();
                verificados++;
            }

            if (lote.size() < TAMANO_LOTE) {
                break; // ultimo lote
            }
            pagina++;
        }

        return ResultadoVerificacionCadena.intacta(verificados);
    }
}
