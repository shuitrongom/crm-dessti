package com.dessti.crm.platform.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Configuracion de la <strong>capa de autorizacion</strong> (RBAC) del CRM:
 * habilita la seguridad a nivel de metodo con {@code @PreAuthorize} y
 * denegacion por defecto (Req 3).
 *
 * <p>Esta clase es <em>intencionalmente independiente</em> de la configuracion
 * de la cadena de filtros ({@code SecurityFilterChain}) y del filtro JWT, que
 * corresponden a la <strong>tarea 9.1</strong>. Aqui solo se activa la
 * evaluacion de anotaciones de metodo; los permisos atomicos se comprueban con
 * el bean {@code autorizador}
 * ({@link com.dessti.crm.platform.security.rbac.Autorizador}) desde expresiones
 * SpEL, p. ej.:</p>
 *
 * <pre>{@code
 * @PreAuthorize("@autorizador.tiene('cliente', 'crear')")
 * }</pre>
 *
 * <h2>Denegacion por defecto</h2>
 * <p>Con {@code @EnableMethodSecurity} (prePostEnabled por defecto), un metodo de
 * negocio sin anotacion de permiso no queda autorizado por esta capa; combinado
 * con la politica {@code authenticated()}/deny-all de la cadena de filtros de la
 * tarea 9.1, el resultado es una denegacion por defecto coherente: sin permiso o
 * sin autenticacion se responde 403 (Req 3.2, 3.5, 3.6). El
 * {@code AccessDeniedException} resultante lo mapea a 403 el manejador global de
 * errores (tarea 5.1).</p>
 *
 * <h2>Punto de union con 9.1</h2>
 * <p>Esta configuracion NO declara beans de {@code SecurityFilterChain},
 * {@code PasswordEncoder} ni filtros. Si la tarea 9.1 ya habilitara la seguridad
 * de metodo, debe conservarse una unica anotacion {@code @EnableMethodSecurity}
 * para no duplicar la infraestructura de {@code AuthorizationManager}; en ese
 * caso, dejar esta clase como punto unico o retirar la anotacion duplicada.</p>
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
    // Sin beans adicionales: la evaluacion de permisos la provee el bean
    // "autorizador" (Autorizador). La cadena de filtros y el filtro JWT son
    // responsabilidad de la tarea 9.1.
}
