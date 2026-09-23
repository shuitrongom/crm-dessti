package com.dessti.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del backend del CRM de Anuncios Luminosos.
 *
 * <p>Aplicacion Spring Boot 3.x sobre Java 21, empaquetada como JAR ejecutable
 * con Tomcat embebido y organizada como monolito modular con arquitectura
 * hexagonal pragmatica (Puertos y Adaptadores). Ver design.md.</p>
 */
@SpringBootApplication
public class CrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
