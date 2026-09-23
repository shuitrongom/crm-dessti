package com.dessti.crm.platform.security.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del cifrado de datos sensibles en reposo (Requisito 67).
 *
 * <p>Habilita {@link CifradoProperties} (llaves resueltas desde el entorno,
 * Req 11) y expone los beans del subsistema de cifrado:</p>
 * <ul>
 *   <li>{@link ProveedorLlaves} &rarr; carga y valida el material de las
 *       {@code Llave_Cifrado}; su construcción falla el arranque si no hay
 *       llave activa disponible (bloqueo controlado, Req 67.6).</li>
 *   <li>{@link ServicioCifrado} &rarr; cifrado/descifrado AES-256-GCM con
 *       versión de llave.</li>
 *   <li>{@link CifradoHolder} &rarr; puente estático para el
 *       {@link CampoCifradoConverter} de JPA.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(CifradoProperties.class)
public class CifradoConfig {

    /**
     * Proveedor de llaves resuelto desde el entorno. Su construcción valida la
     * disponibilidad de la llave activa (Req 67.6).
     *
     * @param propiedades propiedades de cifrado.
     * @return proveedor de llaves listo para cifrar/descifrar.
     */
    @Bean
    public ProveedorLlaves proveedorLlaves(CifradoProperties propiedades) {
        return new ProveedorLlavesEnEntorno(propiedades);
    }

    /**
     * Servicio de cifrado AES-256-GCM.
     *
     * @param proveedorLlaves proveedor de llaves.
     * @return servicio de cifrado.
     */
    @Bean
    public ServicioCifrado servicioCifrado(ProveedorLlaves proveedorLlaves) {
        return new ServicioCifradoAesGcm(proveedorLlaves);
    }

    /**
     * Holder que publica el servicio de cifrado para el converter de JPA.
     *
     * @param servicioCifrado servicio de cifrado gestionado.
     * @return holder inicializado por Spring al arranque.
     */
    @Bean
    public CifradoHolder cifradoHolder(ServicioCifrado servicioCifrado) {
        return new CifradoHolder(servicioCifrado);
    }
}
