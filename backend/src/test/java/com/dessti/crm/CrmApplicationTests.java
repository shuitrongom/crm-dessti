package com.dessti.crm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de andamiaje.
 *
 * <p>En esta fase (tarea 1) NO se dispone de una base de datos ni de la
 * configuracion de secretos (Req 11), por lo que NO se realiza aun una carga
 * completa del contexto de Spring (que requeriria datasource/Flyway). La carga
 * de contexto con base de datos real se cubre en las tareas de integracion con
 * Testcontainers (por ejemplo, la tarea 2.2).</p>
 *
 * <p>Esta prueba se limita a verificar que la clase principal y su metodo de
 * arranque existen, manteniendo el build en verde sin dependencias externas.</p>
 */
class CrmApplicationTests {

    @Test
    void laClasePrincipalExisteYEsArrancable() throws Exception {
        assertThat(CrmApplication.class.getDeclaredMethod("main", String[].class)).isNotNull();
        assertThat(CrmApplication.class.getAnnotation(
                org.springframework.boot.autoconfigure.SpringBootApplication.class)).isNotNull();
    }
}
