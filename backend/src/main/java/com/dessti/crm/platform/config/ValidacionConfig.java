package com.dessti.crm.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/**
 * Configuracion transversal de <b>Bean Validation</b> ({@code jakarta.validation})
 * para toda la aplicacion (Req 8).
 *
 * <h2>Politica de validacion de entrada</h2>
 * <ul>
 *   <li><b>DTOs de cuerpo:</b> los controladores anotan los DTOs de entrada con
 *   {@code @Valid} (o {@code @Validated}); las violaciones producen
 *   {@code MethodArgumentNotValidException}, que el manejador global traduce a
 *   HTTP 400 con el arreglo {@code errors[]} por campo (Req 8.2). No se duplica
 *   aqui esa traduccion (ya la realiza
 *   {@code com.dessti.crm.platform.web.ManejadorGlobalErrores}).</li>
 *   <li><b>Parametros de metodo:</b> este {@link MethodValidationPostProcessor}
 *   habilita la validacion de {@code @RequestParam}/{@code @PathVariable} en
 *   controladores anotados con {@code @Validated}; las violaciones producen
 *   {@code ConstraintViolationException}, tambien mapeada a 400 por el
 *   manejador global.</li>
 * </ul>
 *
 * <h2>Anti-inyeccion y XSS (Req 8)</h2>
 * <ul>
 *   <li><b>Consultas parametrizadas:</b> el acceso a datos se realiza siempre
 *   mediante JPA / Spring Data (repositorios derivados, {@code @Query} con
 *   parametros nombrados o {@code Criteria}), nunca concatenando entrada del
 *   Usuario en cadenas SQL/JPQL. Esto evita la inyeccion SQL por diseno.</li>
 *   <li><b>Codificacion de salida:</b> las respuestas se serializan como JSON
 *   con Jackson, que escapa el contenido; el frontend Angular aplica ademas
 *   escape contextual, evitando XSS reflejado/almacenado.</li>
 * </ul>
 *
 * <p>Bean Validation ya se auto-configura con
 * {@code spring-boot-starter-validation}; esta clase explicita la politica
 * transversal y garantiza la validacion a nivel de metodo de forma
 * documentada y centralizada.</p>
 */
@Configuration
public class ValidacionConfig {

    /**
     * Habilita la validacion de parametros de metodo en beans anotados con
     * {@code @Validated} (p. ej. controladores con {@code @RequestParam}
     * restringidos por {@code @Min}/{@code @Max}).
     *
     * @return el post-procesador de validacion de metodos
     */
    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor() {
        return new MethodValidationPostProcessor();
    }
}
