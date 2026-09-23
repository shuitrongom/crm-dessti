package com.dessti.crm.platform.security;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.dessti.crm.platform.config.SecretosProperties;
import com.dessti.crm.platform.security.jwt.JwtProperties;
import com.dessti.crm.platform.security.jwt.ServicioTokensJwt;

/**
 * Configuracion central de Spring Security (Req 1, 9, tarea 9.1).
 *
 * <p>Establece una cadena de filtros <b>stateless</b> (sin sesion de servlet):
 * la identidad se transporta exclusivamente en el {@code Token_Acceso} JWT. Se
 * registra el {@link JwtAuthenticationFilter} antes del filtro estandar de
 * usuario/contrasena para autenticar cada peticion a partir del token.</p>
 *
 * <p><strong>Rutas publicas</strong> (recordar que el context-path es
 * {@code /api/v1}, por lo que estas rutas son relativas a el):</p>
 * <ul>
 *   <li>{@code POST /auth/login}, {@code /auth/refresh}, {@code /auth/logout}</li>
 *   <li>Swagger UI y OpenAPI ({@code /swagger-ui/**}, {@code /v3/api-docs/**})</li>
 *   <li>Health de Actuator ({@code /actuator/health/**})</li>
 * </ul>
 * <p>El resto de endpoints requiere autenticacion.</p>
 *
 * <p><strong>Orden respecto a multi-tenant (tarea 4.1):</strong> la cadena de
 * Spring Security se registra en orden {@code -100} y el
 * {@code TenantResolutionFilter} en orden {@code 0}; asi, el principal
 * autenticado ({@link UsuarioAutenticado}, {@code TenantAware}) ya existe cuando
 * se resuelve el tenant.</p>
 *
 * <p><strong>Cabeceras de seguridad (Req 9.3, tarea 12):</strong> cada
 * respuesta incluye {@code Content-Security-Policy}, {@code Strict-Transport-Security}
 * (HSTS), {@code X-Frame-Options}, {@code Referrer-Policy} y
 * {@code X-Content-Type-Options}. Ver {@link #securityFilterChain} para el
 * detalle de cada valor y su justificacion.</p>
 *
 * <p><strong>CSRF (Req 9.4, tarea 12):</strong> se mantiene deshabilitado para
 * la API REST porque la autenticacion es <b>stateless</b> por token Bearer en
 * la cabecera {@code Authorization} y no por cookie de sesion. Al no existir
 * <i>credenciales ambientales</i> (el navegador no adjunta automaticamente el
 * token en peticiones entre sitios como si hace con las cookies), no hay
 * superficie de ataque CSRF clasico: una pagina maliciosa no puede forzar el
 * envio del {@code Token_Acceso}. Por ello deshabilitar CSRF aqui es la
 * decision correcta y ademas evita romper los endpoints de token
 * ({@code /auth/**}).</p>
 *
 * <p><strong>Importante:</strong> si en el futuro se introdujera cualquier
 * sesion basada en <b>cookie</b> para navegador (por ejemplo, una cookie de
 * sesion o un token en cookie {@code HttpOnly}), habria que <em>habilitar</em>
 * la proteccion CSRF con {@code CookieCsrfTokenRepository} (patron de doble
 * envio de token) para las operaciones que modifican estado, tal como exige el
 * Req 9.4 para el contexto de navegador. La terminacion TLS y la redireccion
 * HTTP&rarr;HTTPS (Req 9.1, 9.2) se resuelven en el Proxy_Inverso (IIS); ver
 * {@code docs/tls-y-proxy-inverso-iis.md}.</p>
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /** Rutas publicas relativas al context-path {@code /api/v1}. */
    private static final String[] RUTAS_PUBLICAS = {
            "/auth/login",
            "/auth/refresh",
            "/auth/logout",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            // Webhooks entrantes de Meta (Req 64.3): no portan token JWT; su
            // autenticidad se valida por firma HMAC-SHA256 (X-Hub-Signature-256)
            // en el propio controlador (FirmaWebhookMeta), y TLS termina en IIS
            // (Req 9). Por ello la ruta es publica a nivel de autenticacion JWT.
            "/social/webhooks/**"
    };

    /**
     * Politica de seguridad de contenido (CSP) para el backend (Req 9.3).
     *
     * <p>El backend expone <b>exclusivamente JSON</b> bajo {@code /api/v1} y no
     * sirve HTML ni scripts: la SPA de Angular se sirve por separado como
     * archivos estaticos desde IIS (ver design.md y
     * {@code docs/tls-y-proxy-inverso-iis.md}). Por ello se aplica una CSP
     * <b>restrictiva por defecto</b> que no permite cargar ningun recurso
     * ({@code default-src 'none'}), impide que las respuestas se enmarquen
     * ({@code frame-ancestors 'none'}, refuerza a {@code X-Frame-Options: DENY})
     * y bloquea URIs base ({@code base-uri 'none'}).</p>
     *
     * <p>La SPA servida por IIS <b>puede y debe</b> definir/afinar su propia CSP
     * en su capa (por ejemplo, permitiendo {@code script-src}/{@code style-src}
     * de su propio origen), sin verse condicionada por esta politica de la API.</p>
     */
    static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

    /**
     * Valor de {@code Referrer-Policy} (Req 9.3). {@code no-referrer} evita que
     * el navegador filtre la URL de origen (que podria contener identificadores)
     * hacia terceros al navegar o al cargar recursos.
     */
    static final String REFERRER_POLICY = "no-referrer";

    /**
     * Vigencia de HSTS en segundos: 1 anio (365 dias). Un valor alto es la
     * practica recomendada para {@code Strict-Transport-Security} (Req 9.3).
     */
    static final long HSTS_MAX_AGE_SEGUNDOS = 31_536_000L;

    /**
     * Reloj del sistema (UTC) reutilizable para la emision/validacion de tokens.
     * Se declara como bean para poder sustituirlo por un reloj fijo en pruebas.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Servicio de tokens JWT. La clave de firma proviene de
     * {@link SecretosProperties} (Req 11) y nunca se registra en logs.
     */
    @Bean
    public ServicioTokensJwt servicioTokensJwt(SecretosProperties secretos,
                                               JwtProperties jwtProperties,
                                               Clock clock) {
        return new ServicioTokensJwt(secretos.jwtSigningKey(), jwtProperties, clock);
    }

    /**
     * Codificador de contrasenas BCrypt (Req 1.2). Alternativa considerada:
     * Argon2 ({@code Argon2PasswordEncoder}), mas resistente a hardware
     * especializado pero con mayor coste de dependencias/afinacion; BCrypt es
     * suficiente y ampliamente soportado para este entregable.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(ServicioTokensJwt servicioTokensJwt) {
        return new JwtAuthenticationFilter(servicioTokensJwt);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http
                // API stateless: sin sesion de servlet, identidad via JWT.
                // CSRF deshabilitado de forma justificada (ver Javadoc de la
                // clase): auth por token Bearer, sin credenciales ambientales de
                // cookie -> sin superficie CSRF clasica (Req 9.4).
                .csrf(AbstractHttpConfigurer::disable)
                // Cabeceras de seguridad del navegador (Req 9.3).
                .headers(headers -> headers
                        // Content-Security-Policy restrictiva: la API solo emite
                        // JSON; la SPA (Angular) se sirve aparte por IIS y define
                        // su propia CSP.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        // X-Frame-Options: DENY. Impide el enmarcado (clickjacking);
                        // reforzado por frame-ancestors 'none' en la CSP.
                        .frameOptions(frame -> frame.deny())
                        // HSTS: fuerza HTTPS en visitas posteriores. TLS termina en
                        // IIS (Req 9.1/9.2), pero la app emite igualmente la cabecera
                        // para toda respuesta, incluyendo subdominios.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_MAX_AGE_SEGUNDOS))
                        // Referrer-Policy: no-referrer (definido explicitamente).
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.NO_REFERRER))
                        // X-Content-Type-Options: nosniff lo agrega Spring Security
                        // por defecto; se mantiene habilitado.
                )
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                // Token de acceso ausente/invalido -> 401 (Req 1.6). Sin cuerpo
                // que filtre detalles internos.
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
