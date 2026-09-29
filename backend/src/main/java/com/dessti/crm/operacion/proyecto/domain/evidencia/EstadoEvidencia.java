package com.dessti.crm.operacion.proyecto.domain.evidencia;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estado de aprobacion de una {@link EvidenciaAvanceSitio} (deber-ser
 * enterprise). Modela el control de calidad real de una empresa: quien sube la
 * evidencia en campo la deja {@link #PENDIENTE}; un supervisor la
 * {@link #APROBADA} o {@link #RECHAZADA}. La fase de un Sitio solo puede
 * entregarse cuando la evidencia que la respalda esta aprobada (Req 3.2).
 *
 * <p>Maquina de estados: {@link #PENDIENTE} &rarr; {@link #APROBADA} /
 * {@link #RECHAZADA}. Ambos destinos son finales (la evidencia no cambia de
 * decision; si hace falta otra, se sube una nueva). Es un enum de dominio puro:
 * persiste su {@link #valorBd()} y se reconstruye con {@link #desdeValorBd(String)}.</p>
 */
public enum EstadoEvidencia {

    /** Recien subida; a la espera de decision de un supervisor (estado inicial). */
    PENDIENTE("pendiente"),

    /** Validada por un supervisor: respalda el avance de la fase. */
    APROBADA("aprobada"),

    /** Rechazada por un supervisor (con motivo); no respalda el avance. */
    RECHAZADA("rechazada");

    /** Maquina de estados: de PENDIENTE a APROBADA/RECHAZADA; ambos finales. */
    private static final MaquinaEstados<EstadoEvidencia> MAQUINA =
            MaquinaEstados.<EstadoEvidencia>builder(EstadoEvidencia.class)
                    .permitir(PENDIENTE, APROBADA)
                    .permitir(PENDIENTE, RECHAZADA)
                    .construir();

    private final String valorBd;

    EstadoEvidencia(String valorBd) {
        this.valorBd = valorBd;
    }

    /** Etiqueta persistida en {@code evidencia_avance_sitio.estado} (minusculas ASCII). */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado desde su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (p. ej. {@code 'aprobada'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoEvidencia desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la evidencia no puede ser nulo.");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoEvidencia estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de evidencia desconocido: " + valor);
    }

    /** Indica si desde este estado se puede transitar a {@code destino}. */
    public boolean puedeTransicionarA(EstadoEvidencia destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}
