package com.dessti.crm.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de la especificacion <b>OpenAPI</b> publicada por springdoc y
 * navegable mediante <b>Swagger UI</b> (Req 13).
 *
 * <h2>Que aporta esta clase</h2>
 * <p>springdoc genera automaticamente el documento OpenAPI a partir de los
 * controladores REST y los esquemas de los DTO (Bean Validation incluida). Este
 * bean personaliza los metadatos globales del documento sin necesidad de anotar
 * cada controlador:</p>
 * <ul>
 *   <li><b>Info:</b> titulo, version, descripcion, contacto y licencia del
 *       servicio.</li>
 *   <li><b>Servidor base:</b> se declara el prefijo {@code /api/v1}, coherente
 *       con {@code server.servlet.context-path} de {@code application.yml}, de
 *       modo que la interfaz de Swagger emita las peticiones a la ruta correcta
 *       (Req 12.4).</li>
 *   <li><b>Esquema de seguridad JWT:</b> se registra un esquema HTTP
 *       <em>bearer</em> con formato {@code JWT} y se aplica como requisito
 *       global, documentando el {@code Token_Acceso} exigido por la mayoria de
 *       las operaciones (Req 13, autenticacion por JWT). Esto habilita el boton
 *       <em>Authorize</em> de Swagger UI.</li>
 * </ul>
 *
 * <h2>Sobre la seguridad global</h2>
 * <p>El requisito de seguridad global documenta la <em>norma</em> del API. Los
 * extremos publicos de autenticacion ({@code /auth/login}, refresco de token,
 * etc.) siguen siendo accesibles sin token: la lista blanca de rutas publicas
 * la gobierna {@code SecurityConfig}, no este documento. Declarar el requisito
 * global es meramente descriptivo y no altera la autorizacion efectiva.</p>
 *
 * <p>Se apoya exclusivamente en las clases {@code io.swagger.v3.oas.models.*}
 * disponibles transitivamente a traves de
 * {@code springdoc-openapi-starter-webmvc-ui}; no introduce dependencias
 * nuevas.</p>
 */
@Configuration
public class ConfiguracionOpenApi {

    /** Version del API publicada en el documento OpenAPI. */
    private static final String VERSION_API = "0.0.1-SNAPSHOT";

    /** Ruta base del API, coherente con {@code server.servlet.context-path}. */
    private static final String RUTA_BASE = "/api/v1";

    /** Nombre del esquema de seguridad JWT registrado en los componentes. */
    private static final String ESQUEMA_JWT = "bearer-jwt";

    /**
     * Construye el documento OpenAPI con los metadatos globales del servicio, el
     * servidor base {@code /api/v1} y el esquema de seguridad JWT aplicado como
     * requisito global.
     *
     * @return la especificacion OpenAPI personalizada.
     */
    @Bean
    public OpenAPI openApiCrmAnunciosLuminosos() {
        return new OpenAPI()
                .info(new Info()
                        .title("CRM Anuncios Luminosos API")
                        .version(VERSION_API)
                        .description("API del CRM/ERP multi-tenant para fabricantes de anuncios "
                                + "luminosos: comercial, operacion, compras, contabilidad, "
                                + "facturacion CFDI, tesoreria, activos fijos, RH/nomina, "
                                + "mantenimiento, redes sociales, notificaciones, estrategia, "
                                + "presupuestos, reportes/BI y portal del cliente. Autenticacion "
                                + "por JWT (Token_Acceso); todas las peticiones se sirven bajo "
                                + RUTA_BASE + ".")
                        .contact(new Contact()
                                .name("Equipo CRM Anuncios Luminosos")
                                .email("soporte@empresa.com"))
                        .license(new License()
                                .name("Propietaria")))
                .addServersItem(new Server()
                        .url(RUTA_BASE)
                        .description("Servidor del API bajo el prefijo de contexto"))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token_Acceso JWT emitido por /auth/login. "
                                        + "Formato: Authorization: Bearer <token>.")))
                .security(List.of(new SecurityRequirement().addList(ESQUEMA_JWT)));
    }
}
