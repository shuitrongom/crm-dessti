package com.dessti.crm.social.adapter.out.meta;

import java.util.UUID;

import com.dessti.crm.social.application.MensajeriaSocialPort;
import com.dessti.crm.social.application.ResultadoEnvioSocial;
import com.dessti.crm.social.application.SolicitudEnvioSocial;
import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.EstadoEntrega;

/**
 * Adaptador <strong>stub</strong> del {@link MensajeriaSocialPort}: implementacion
 * determinista de la mensajeria de Meta para desarrollo y pruebas (Req 64). No
 * realiza ninguna llamada de red; genera resultados reproducibles que permiten
 * ejercitar el envio <em>exitoso</em>, el <em>fallo</em> (para la politica de
 * reintentos, Req 64.13) y el mapeo de {@code estado_entrega} sin depender de las
 * APIs reales ni de credenciales. Sigue el patron de {@code PacStubAdapter}.
 *
 * <h2>Sustituibilidad</h2>
 * <p>Se registra como bean {@link MensajeriaSocialPort} mediante el metodo
 * {@code @Bean} {@code @ConditionalOnMissingBean} de {@link MetaConfig}: en cuanto
 * exista otro bean {@link MensajeriaSocialPort} (los futuros adaptadores HTTP por
 * canal: WhatsApp Business Cloud API, Graph API), este stub deja de registrarse, de
 * modo que la integracion real se intercambia sin tocar la aplicacion.</p>
 *
 * <h2>Simulacion de fallo (para pruebas y reintentos)</h2>
 * <p>El envio falla de forma determinista cuando el destinatario es el centinela
 * {@value #DESTINATARIO_SIMULA_FALLO}, lo que permite a las pruebas ejercitar la
 * rama de fallo y la politica de reintentos (Req 64.13) sin flags externos.
 * Cualquier otro destinatario produce un envio exitoso con un id externo generado y
 * {@link EstadoEntrega#ENVIADO}.</p>
 *
 * <h2>Credenciales (Req 11)</h2>
 * <p>Este stub <strong>no</strong> usa los tokens de acceso de Meta; las
 * {@link MetaProperties} solo se declaran para los adaptadores reales y para la
 * validacion de la firma del webhook. Nunca se escriben credenciales en logs.</p>
 */
public class MetaStubAdapter implements MensajeriaSocialPort {

    /**
     * Destinatario centinela que este stub usa para simular un fallo de envio en
     * pruebas (Req 64.13). Permite ejercitar la politica de reintentos.
     */
    public static final String DESTINATARIO_SIMULA_FALLO = "FALLO";

    /** Motivo de fallo simulado cuando el destinatario es el centinela. */
    static final String MOTIVO_FALLO_SIMULADO =
            "El proveedor rechazo el envio: destinatario no disponible (simulado).";

    @Override
    public ResultadoEnvioSocial enviarTexto(SolicitudEnvioSocial solicitud) {
        return despachar(solicitud);
    }

    @Override
    public ResultadoEnvioSocial enviarPlantilla(SolicitudEnvioSocial solicitud) {
        return despachar(solicitud);
    }

    @Override
    public ResultadoEnvioSocial enviarInteractivo(SolicitudEnvioSocial solicitud) {
        return despachar(solicitud);
    }

    @Override
    public EstadoEntrega consultarEstado(String externoId, CanalSocial canal) {
        // Stub determinista: un mensaje con id externo se considera entregado.
        if (externoId == null || externoId.isBlank()) {
            return EstadoEntrega.FALLIDO;
        }
        return EstadoEntrega.ENTREGADO;
    }

    private ResultadoEnvioSocial despachar(SolicitudEnvioSocial solicitud) {
        if (solicitud == null) {
            return ResultadoEnvioSocial.fallido("Solicitud de envio nula.");
        }
        if (destinatarioSimulaFallo(solicitud.destinatario())) {
            return ResultadoEnvioSocial.fallido(MOTIVO_FALLO_SIMULADO);
        }
        String externoId = "MSG-STUB-" + UUID.randomUUID();
        return ResultadoEnvioSocial.exitoso(externoId, EstadoEntrega.ENVIADO);
    }

    private static boolean destinatarioSimulaFallo(String destinatario) {
        return destinatario != null && DESTINATARIO_SIMULA_FALLO.equalsIgnoreCase(destinatario.strip());
    }
}
