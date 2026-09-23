package com.dessti.crm.facturacion.adapter.out.pac;

import java.time.Clock;
import java.util.UUID;

import com.dessti.crm.facturacion.application.PacPort;
import com.dessti.crm.facturacion.application.ResultadoCancelacion;
import com.dessti.crm.facturacion.application.ResultadoTimbrado;
import com.dessti.crm.facturacion.application.SolicitudCancelacion;
import com.dessti.crm.facturacion.application.SolicitudTimbrado;

/**
 * Adaptador <strong>stub</strong> del {@link PacPort}: implementacion determinista
 * de un PAC para desarrollo y pruebas (Req 35.8). No realiza ninguna llamada de
 * red; genera resultados reproducibles que permiten ejercitar el flujo de
 * Timbrado <em>exitoso</em>, el <em>rechazo</em> del PAC y la <em>cancelacion</em>
 * sin depender de un proveedor real ni de credenciales.
 *
 * <h2>Sustituibilidad (Req 35.8)</h2>
 * <p>Se registra como bean {@link PacPort} mediante el metodo {@code @Bean}
 * {@code @ConditionalOnMissingBean} de {@link PacConfig}: en cuanto exista otro bean
 * {@link PacPort} (el futuro adaptador HTTP real), este stub deja de registrarse,
 * de modo que la integracion real se intercambia sin tocar la aplicacion. La
 * aplicacion depende siempre de la interfaz {@link PacPort}, no de esta clase.</p>
 *
 * <h2>Simulacion de rechazo (para pruebas)</h2>
 * <p>El Timbrado se rechaza de forma determinista cuando el RFC del receptor es el
 * RFC generico de publico en general del SAT ({@value #RFC_SIMULA_RECHAZO}), lo que
 * permite a las pruebas ejercitar la rama de rechazo (Req 35.2) sin flags externos.
 * Cualquier otro RFC produce Timbrado exitoso con un {@code Folio_Fiscal} (UUID)
 * generado aleatoriamente y un sello simulado.</p>
 *
 * <h2>Credenciales (Req 11)</h2>
 * <p>Este stub <strong>no</strong> usa las credenciales del PAC; las
 * {@link PacProperties} solo se declaran para el adaptador real. Nunca se escriben
 * credenciales en logs.</p>
 */
public class PacStubAdapter implements PacPort {

    /**
     * RFC generico de "publico en general" del SAT que este stub usa como
     * centinela para simular un rechazo del PAC en pruebas (Req 35.2).
     */
    public static final String RFC_SIMULA_RECHAZO = "XAXX010101000";

    /** Motivo de rechazo simulado cuando el receptor usa el RFC centinela. */
    static final String MOTIVO_RECHAZO_SIMULADO =
            "El PAC rechazo el timbrado: RFC del receptor no valido para CFDI de ingreso.";

    private final Clock clock;

    /**
     * @param clock reloj para fijar la fecha de Timbrado (inyectable en pruebas
     *              para resultados deterministas).
     */
    public PacStubAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ResultadoTimbrado timbrar(SolicitudTimbrado solicitud) {
        if (solicitud == null) {
            return ResultadoTimbrado.rechazado("Solicitud de timbrado nula.");
        }
        if (rfcSimulaRechazo(solicitud.receptorRfc())) {
            return ResultadoTimbrado.rechazado(MOTIVO_RECHAZO_SIMULADO);
        }
        UUID folioFiscal = UUID.randomUUID();
        String sello = "SELLO-STUB-" + folioFiscal;
        return ResultadoTimbrado.exitoso(folioFiscal, sello, clock.instant());
    }

    @Override
    public ResultadoCancelacion cancelar(SolicitudCancelacion solicitud) {
        if (solicitud == null || solicitud.folioFiscal() == null) {
            return ResultadoCancelacion.rechazada("Solicitud de cancelacion sin Folio_Fiscal.");
        }
        if (solicitud.motivoSat() == null || solicitud.motivoSat().isBlank()) {
            return ResultadoCancelacion.rechazada("Se requiere un motivo de cancelacion del SAT.");
        }
        return ResultadoCancelacion.aceptada("ACUSE-STUB-" + solicitud.folioFiscal());
    }

    private static boolean rfcSimulaRechazo(String rfc) {
        return rfc != null && RFC_SIMULA_RECHAZO.equalsIgnoreCase(rfc.strip());
    }
}
