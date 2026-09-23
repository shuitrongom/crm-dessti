package com.dessti.crm.platform.respaldo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.dessti.crm.platform.respaldo.adapter.out.CifradorRespaldoAesGcm;
import com.dessti.crm.platform.respaldo.adapter.out.EjecutorProceso;
import com.dessti.crm.platform.respaldo.adapter.out.EjecutorProcesoSistema;
import com.dessti.crm.platform.respaldo.adapter.out.MotorRespaldoDeshabilitado;
import com.dessti.crm.platform.respaldo.adapter.out.MotorRespaldoPgDump;
import com.dessti.crm.platform.respaldo.application.CifradorRespaldoPort;
import com.dessti.crm.platform.respaldo.application.MotorRespaldoPort;
import com.dessti.crm.platform.respaldo.application.RespaldoProperties;
import com.dessti.crm.platform.security.crypto.ProveedorLlaves;

/**
 * Configuracion del modulo de respaldo y recuperacion (Req 50).
 *
 * <p>Habilita {@link RespaldoProperties} (configuracion/secretos resueltos fuera
 * del codigo, Req 11) y la programacion de tareas ({@link EnableScheduling}),
 * necesaria para el respaldo periodico. Cablea:</p>
 * <ul>
 *   <li>El {@link CifradorRespaldoPort} (AES-256-GCM sobre la
 *       {@link ProveedorLlaves} existente): siempre disponible, es pura
 *       criptografia sin procesos externos.</li>
 *   <li>El {@link MotorRespaldoPort}: cuando {@code crm.respaldo.habilitado=true}
 *       se cablea {@link MotorRespaldoPgDump} sobre un
 *       {@link EjecutorProcesoSistema} real; en otro caso (por defecto, y en
 *       pruebas) se cablea {@link MotorRespaldoDeshabilitado}, que arranca el
 *       contexto sin invocar procesos externos y rechaza la operacion de forma
 *       controlada.</li>
 * </ul>
 *
 * <p>El programador periodico ({@code ProgramadorRespaldos}) es un
 * {@code @Component} condicional que solo existe cuando la capacidad esta
 * habilitada, por lo que en pruebas no se dispara ningun respaldo.</p>
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RespaldoProperties.class)
public class ConfiguracionRespaldo {

    /**
     * Cifrador de artefactos de respaldo (AES-256-GCM, Req 67). Siempre presente.
     *
     * @param proveedorLlaves proveedor de {@code Llave_Cifrado} existente.
     * @return el cifrador de respaldos.
     */
    @Bean
    public CifradorRespaldoPort cifradorRespaldo(ProveedorLlaves proveedorLlaves) {
        return new CifradorRespaldoAesGcm(proveedorLlaves);
    }

    /**
     * Ejecutor de procesos del sistema para el motor real. Solo cuando la
     * capacidad esta habilitada.
     *
     * @return el ejecutor de procesos del sistema.
     */
    @Bean
    @ConditionalOnProperty(name = "crm.respaldo.habilitado", havingValue = "true")
    public EjecutorProceso ejecutorProceso() {
        return new EjecutorProcesoSistema();
    }

    /**
     * Motor de respaldo real basado en {@code pg_dump}/{@code pg_restore}. Solo
     * cuando la capacidad esta habilitada.
     *
     * @param propiedades     configuracion del respaldo (rutas, host, credenciales).
     * @param ejecutorProceso frontera de invocacion de procesos.
     * @return el motor de respaldo de PostgreSQL.
     */
    @Bean
    @ConditionalOnProperty(name = "crm.respaldo.habilitado", havingValue = "true")
    public MotorRespaldoPort motorRespaldoPgDump(RespaldoProperties propiedades,
                                                 EjecutorProceso ejecutorProceso) {
        return new MotorRespaldoPgDump(propiedades, ejecutorProceso);
    }

    /**
     * Motor de respaldo inactivo por defecto (capacidad deshabilitada). Permite
     * arrancar el contexto sin procesos externos; se sustituye por el motor real
     * cuando {@code crm.respaldo.habilitado=true}.
     *
     * @return el motor de respaldo inactivo.
     */
    @Bean
    @ConditionalOnProperty(name = "crm.respaldo.habilitado", havingValue = "false", matchIfMissing = true)
    public MotorRespaldoPort motorRespaldoDeshabilitado() {
        return new MotorRespaldoDeshabilitado();
    }
}
