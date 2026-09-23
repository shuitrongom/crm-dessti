package com.dessti.crm.social.adapter.out.meta;

import java.util.UUID;

import com.dessti.crm.social.application.EstadoCampanaExterno;
import com.dessti.crm.social.application.PublicacionSocialPort;
import com.dessti.crm.social.application.ResultadoPublicacion;
import com.dessti.crm.social.application.SolicitudPublicacion;
import com.dessti.crm.social.domain.CanalSocial;

/**
 * Adaptador <strong>stub</strong> del {@link PublicacionSocialPort}: implementacion
 * determinista de la publicacion y la consulta de estado de campaña de Meta para
 * desarrollo y pruebas (Req 65). No realiza ninguna llamada de red; genera
 * resultados reproducibles que permiten ejercitar la publicacion <em>exitosa</em>,
 * el <em>fallo</em> (para la politica de reintentos, Req 65.6) y la consulta de
 * estado de SOLO LECTURA (Req 65.9) sin depender de las APIs reales ni de
 * credenciales. Sigue el patron de {@link MetaStubAdapter}.
 *
 * <h2>Sustituibilidad</h2>
 * <p>Se registra como bean {@link PublicacionSocialPort} mediante el metodo
 * {@code @Bean} {@code @ConditionalOnMissingBean} de {@link MetaConfig}: en cuanto
 * exista otro bean {@link PublicacionSocialPort} (el futuro adaptador HTTP a la Graph
 * API / Marketing API), este stub deja de registrarse, de modo que la integracion
 * real se intercambia sin tocar la aplicacion.</p>
 *
 * <h2>Simulacion de fallo (para pruebas y reintentos)</h2>
 * <p>La publicacion falla de forma determinista cuando el contenido contiene el
 * centinela {@value #CONTENIDO_SIMULA_FALLO}, lo que permite a las pruebas ejercitar
 * la rama de fallo y la politica de reintentos (Req 65.6) sin flags externos.
 * Cualquier otro contenido produce una publicacion exitosa con un id externo
 * generado.</p>
 *
 * <h2>Credenciales (Req 11)</h2>
 * <p>Este stub <strong>no</strong> usa los tokens de acceso de Meta; reutiliza las
 * {@link MetaProperties} solo para coherencia de configuracion. Nunca se escriben
 * credenciales en logs.</p>
 */
public class PublicacionSocialStubAdapter implements PublicacionSocialPort {

    /**
     * Centinela que este stub reconoce en el contenido para simular un fallo de
     * publicacion en pruebas (Req 65.6). Permite ejercitar la politica de reintentos.
     */
    public static final String CONTENIDO_SIMULA_FALLO = "FALLO_PUBLICACION";

    /** Motivo de fallo simulado cuando el contenido contiene el centinela. */
    static final String MOTIVO_FALLO_SIMULADO =
            "El proveedor rechazo la publicacion: contenido no aceptado (simulado).";

    /** Estado externo determinista devuelto por el stub para una campaña conocida. */
    static final String ESTADO_EXTERNO_SIMULADO = "activa";

    @Override
    public ResultadoPublicacion publicar(SolicitudPublicacion solicitud) {
        if (solicitud == null) {
            return ResultadoPublicacion.fallido("Solicitud de publicacion nula.");
        }
        if (contenidoSimulaFallo(solicitud.contenido())) {
            return ResultadoPublicacion.fallido(MOTIVO_FALLO_SIMULADO);
        }
        String externoId = "PUB-STUB-" + UUID.randomUUID();
        return ResultadoPublicacion.exitoso(externoId);
    }

    @Override
    public EstadoCampanaExterno consultarEstadoCampana(String externoId, CanalSocial canal) {
        // Stub determinista: sin id externo no hay campaña que consultar en Meta.
        if (externoId == null || externoId.isBlank()) {
            return EstadoCampanaExterno.noDisponible(
                    externoId, "La Campaña_Publicitaria no tiene id externo en la Marketing API.");
        }
        return EstadoCampanaExterno.disponible(
                externoId.strip(), ESTADO_EXTERNO_SIMULADO,
                "Estado de SOLO LECTURA obtenido de la Marketing API (simulado).");
    }

    private static boolean contenidoSimulaFallo(String contenido) {
        return contenido != null
                && contenido.toUpperCase(java.util.Locale.ROOT).contains(CONTENIDO_SIMULA_FALLO);
    }
}
