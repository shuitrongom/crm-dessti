package com.dessti.crm.platform.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resuelve la direccion IP de origen de una peticion teniendo en cuenta que el
 * backend se despliega detras de un Proxy_Inverso (IIS) que reenvia el trafico
 * (Req 2.4, contexto tecnico de despliegue).
 *
 * <p>Estrategia:</p>
 * <ul>
 *   <li>Si existe la cabecera {@code X-Forwarded-For}, se toma el
 *       <b>primer</b> salto (el cliente original), que es el mas a la izquierda
 *       de la lista {@code cliente, proxy1, proxy2}.</li>
 *   <li>En su defecto, se usa {@link HttpServletRequest#getRemoteAddr()}.</li>
 * </ul>
 *
 * <p><strong>Nota de seguridad:</strong> {@code X-Forwarded-For} es una cabecera
 * que el cliente podria falsificar si el Proxy_Inverso no la sanea. En este
 * despliegue on-premise IIS controla y reescribe la cabecera antes de alcanzar
 * el backend, por lo que se considera confiable. Si en el futuro el backend se
 * expone directamente a Internet, el limitador debera basarse solo en la
 * direccion de la conexion TCP.</p>
 */
public final class ResolvedorIpCliente {

    /** Cabecera estandar de reenvio de IP del cliente por proxies. */
    public static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    private ResolvedorIpCliente() {
        // Utilidad estatica.
    }

    /**
     * Resuelve la IP de origen de la peticion.
     *
     * @param request peticion HTTP entrante; obligatorio.
     * @return la IP del cliente (primer salto de {@code X-Forwarded-For} o la
     *         direccion remota); nunca {@code null} salvo que el contenedor no
     *         provea direccion remota.
     */
    public static String resolver(HttpServletRequest request) {
        String forwarded = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            int coma = forwarded.indexOf(',');
            String primerSalto = (coma >= 0) ? forwarded.substring(0, coma) : forwarded;
            String limpio = primerSalto.trim();
            if (!limpio.isEmpty()) {
                return limpio;
            }
        }
        return request.getRemoteAddr();
    }
}
