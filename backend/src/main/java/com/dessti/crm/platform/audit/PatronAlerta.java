package com.dessti.crm.platform.audit;

/**
 * Patrones sensibles configurables que disparan una alerta de auditoria
 * (Alerta_Auditoria, Req 10.11).
 *
 * <p>Cada patron se asocia a una <em>accion</em> de la bitacora
 * ({@link RegistroAuditoria#getAccion()}): cuando el numero de eventos con esa
 * accion supera el {@code umbral} configurado dentro de la {@code ventana}
 * temporal, se emite una Notificacion al destinatario configurado.</p>
 */
public enum PatronAlerta {

    /** Multiples accesos denegados (posible intento de fuerza bruta o abuso). */
    ACCESOS_DENEGADOS("acceso_denegado"),

    /** Exportaciones masivas de datos sensibles en una ventana corta. */
    EXPORTACION_MASIVA("acceso_datos_sensibles"),

    /** Intentos de acceso a datos de otra Empresa (violacion de aislamiento). */
    ACCESO_OTRA_EMPRESA("acceso_otra_empresa");

    private final String accionAsociada;

    PatronAlerta(String accionAsociada) {
        this.accionAsociada = accionAsociada;
    }

    /**
     * Accion de la bitacora que este patron contabiliza para su deteccion.
     *
     * @return la accion asociada (coincide con {@link RegistroAuditoria#getAccion()}).
     */
    public String accionAsociada() {
        return accionAsociada;
    }
}
