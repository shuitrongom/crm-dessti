package com.dessti.crm.platform.security.crypto;

import org.springframework.beans.factory.InitializingBean;

/**
 * Puente estático entre el contexto de Spring y los componentes que JPA
 * instancia <b>fuera</b> del contexto (como los {@code AttributeConverter}).
 *
 * <p>El proveedor de persistencia crea los converters con su propio ciclo de
 * vida y no participa de la inyección de dependencias de Spring. Para que
 * {@link CampoCifradoConverter} pueda usar el {@link ServicioCifrado}
 * (bean de Spring), este holder expone el servicio mediante un campo estático
 * inicializado por Spring durante el arranque.</p>
 *
 * <p><b>Enfoque elegido:</b> holder estático inicializado por un bean gestionado
 * (vía {@link InitializingBean}). Es simple, no depende de
 * {@code SpringBeanAutowiringSupport} (orientado a entornos con
 * {@code WebApplicationContext}) y funciona igual en producción y en pruebas
 * unitarias, donde el holder puede inicializarse manualmente con un servicio de
 * prueba mediante {@link #inicializar(ServicioCifrado)}.</p>
 *
 * <p>La referencia estática es {@code volatile} para publicar de forma segura el
 * servicio entre hilos.</p>
 */
public final class CifradoHolder implements InitializingBean {

    private static volatile ServicioCifrado servicioCifrado;

    private final ServicioCifrado servicioInyectado;

    /**
     * @param servicioCifrado servicio de cifrado gestionado por Spring; no nulo.
     */
    public CifradoHolder(ServicioCifrado servicioCifrado) {
        this.servicioInyectado = servicioCifrado;
    }

    /**
     * Publica el servicio inyectado en el campo estático una vez construido el bean.
     */
    @Override
    public void afterPropertiesSet() {
        inicializar(servicioInyectado);
    }

    /**
     * Inicializa el holder con un servicio de cifrado. Usado por Spring al
     * arranque y, en pruebas, para inyectar un servicio de prueba.
     *
     * @param servicio servicio de cifrado a exponer estáticamente; no nulo.
     */
    public static void inicializar(ServicioCifrado servicio) {
        servicioCifrado = servicio;
    }

    /**
     * @return el {@link ServicioCifrado} disponible.
     * @throws IllegalStateException si el holder no ha sido inicializado (indica
     *                               un problema de configuración del contexto).
     */
    public static ServicioCifrado servicio() {
        ServicioCifrado actual = servicioCifrado;
        if (actual == null) {
            throw new IllegalStateException(
                    "El servicio de cifrado no está inicializado: verifique la configuración "
                            + "del contexto (CifradoHolder) (Req 67).");
        }
        return actual;
    }
}
