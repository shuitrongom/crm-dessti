package com.dessti.crm.platform.respaldo.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades tipadas de la capacidad de respaldo y recuperacion (Req 50), bajo
 * el prefijo {@code crm.respaldo}.
 *
 * <p>Toda la configuracion sensible (host, base, credenciales de la conexion de
 * respaldo) se resuelve <strong>fuera del codigo</strong> (variables de entorno
 * / almacen externo, Req 11); aqui solo se declaran claves con sus placeholders,
 * SIN valores por defecto sensibles. Las credenciales NUNCA se escriben en logs
 * (Req 11.3).</p>
 *
 * <p><strong>Habilitacion (seguridad en pruebas):</strong> {@link #habilitado()}
 * controla si los adaptadores que invocan procesos del sistema y el programador
 * periodico se activan. Por defecto es {@code false}, de modo que el arranque
 * del contexto en pruebas nunca ejecute {@code pg_dump} ni dispare respaldos
 * programados.</p>
 *
 * @param habilitado         si la capacidad operativa (motor + programador) esta
 *                           activa. Por defecto {@code false}.
 * @param cron               expresion cron del respaldo periodico (Req 50.1);
 *                           por defecto diario a las 02:00.
 * @param directorio         directorio del almacenamiento restringido donde se
 *                           depositan los artefactos cifrados (Req 50.3).
 * @param rpo                objetivo de punto de recuperacion configurable
 *                           (Req 50.2), como metadato de politica.
 * @param rto                objetivo de tiempo de recuperacion configurable
 *                           (Req 50.2), como metadato de politica.
 * @param pgDumpPath         ruta al ejecutable {@code pg_dump} (configurable, no
 *                           embebida); resuelta desde el entorno.
 * @param pgRestorePath      ruta al ejecutable {@code pg_restore} (configurable).
 * @param host               host de PostgreSQL para el volcado/restauracion.
 * @param puerto             puerto de PostgreSQL.
 * @param baseDatos          nombre de la base de datos a respaldar/restaurar.
 * @param usuario            usuario de la conexion de respaldo (Req 11).
 * @param password           contrasena de la conexion de respaldo (Req 11); nunca
 *                           en logs.
 * @param tiempoLimite       tiempo maximo de ejecucion del proceso externo antes
 *                           de abortarlo.
 */
@ConfigurationProperties(prefix = "crm.respaldo")
public record RespaldoProperties(
        boolean habilitado,
        String cron,
        String directorio,
        Duration rpo,
        Duration rto,
        String pgDumpPath,
        String pgRestorePath,
        String host,
        Integer puerto,
        String baseDatos,
        String usuario,
        String password,
        Duration tiempoLimite
) {

    /** Cron por defecto: respaldo diario a las 02:00 (hora del servidor). */
    public static final String CRON_POR_DEFECTO = "0 0 2 * * *";

    /**
     * Constructor compacto que aplica valores por defecto NO sensibles.
     */
    public RespaldoProperties {
        cron = (cron == null || cron.isBlank()) ? CRON_POR_DEFECTO : cron;
        directorio = (directorio == null || directorio.isBlank()) ? "respaldos" : directorio;
        rpo = (rpo == null) ? Duration.ofHours(24) : rpo;
        rto = (rto == null) ? Duration.ofHours(4) : rto;
        pgDumpPath = (pgDumpPath == null || pgDumpPath.isBlank()) ? "pg_dump" : pgDumpPath;
        pgRestorePath = (pgRestorePath == null || pgRestorePath.isBlank()) ? "pg_restore" : pgRestorePath;
        host = (host == null || host.isBlank()) ? "localhost" : host;
        puerto = (puerto == null) ? 5432 : puerto;
        baseDatos = (baseDatos == null || baseDatos.isBlank()) ? "crm" : baseDatos;
        tiempoLimite = (tiempoLimite == null) ? Duration.ofHours(1) : tiempoLimite;
    }

    /**
     * Representacion segura que NUNCA expone la contrasena de la conexion de
     * respaldo (Req 11.3).
     *
     * @return descripcion sin credenciales.
     */
    @Override
    public String toString() {
        return "RespaldoProperties{"
                + "habilitado=" + habilitado
                + ", cron='" + cron + '\''
                + ", directorio='" + directorio + '\''
                + ", rpo=" + rpo
                + ", rto=" + rto
                + ", host='" + host + '\''
                + ", puerto=" + puerto
                + ", baseDatos='" + baseDatos + '\''
                + ", usuario=" + (usuario == null || usuario.isBlank() ? "<ausente>" : "****")
                + ", password=****"
                + ", tiempoLimite=" + tiempoLimite
                + '}';
    }
}
