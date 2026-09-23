package com.dessti.crm.reportesbi.application.indicadores;

/**
 * Puerto de indicadores del area <strong>redes sociales/mensajeria omnicanal</strong>
 * (Req 22.1, 48.1). El adaptador concreto (modulo social) agregara de solo lectura los
 * mensajes por canal, el tiempo de respuesta y los leads generados. Adaptador por
 * defecto: indicadores vacios.
 *
 * <p><strong>Independencia (bloque 44):</strong> este modulo declara el puerto pero NO
 * importa ni referencia nada del paquete {@code com.dessti.crm.social}; el adaptador
 * concreto lo aportara ese modulo por separado. Asi reportes-bi compila sin acoplarse a
 * social ni colisionar con su desarrollo en paralelo.</p>
 */
public interface IndicadorSocialPort extends IndicadorAreaPort {

    @Override
    default AreaIndicador area() {
        return AreaIndicador.REDES_SOCIALES;
    }
}
