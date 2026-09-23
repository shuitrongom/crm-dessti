package com.dessti.crm.platform.respaldo.adapter.out;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dessti.crm.platform.respaldo.application.MotorRespaldoPort;
import com.dessti.crm.platform.respaldo.application.RespaldoException;
import com.dessti.crm.platform.respaldo.application.RespaldoProperties;

/**
 * Adaptador del {@link MotorRespaldoPort} que produce un volcado logico de
 * PostgreSQL con {@code pg_dump} y lo restaura con {@code pg_restore},
 * invocandolos a traves de un {@link EjecutorProceso} (frontera de invocacion
 * de procesos, sustituible en pruebas).
 *
 * <p>Las rutas de los ejecutables, host, puerto, base y credenciales se toman
 * de {@link RespaldoProperties}, resueltas desde configuracion/secretos y
 * <strong>nunca embebidas</strong> (Req 11). La contrasena se pasa al proceso
 * hijo por la variable de entorno {@code PGPASSWORD} y no se registra en logs
 * (Req 11.3).</p>
 *
 * <p>Este adaptador realiza I/O de proceso real; se instancia UNICAMENTE cuando
 * {@code crm.respaldo.habilitado=true} (ver la configuracion del modulo), de
 * modo que las pruebas nunca ejecuten {@code pg_dump}/{@code pg_restore}.</p>
 */
public class MotorRespaldoPgDump implements MotorRespaldoPort {

    /** Formato personalizado de pg_dump ({@code -Fc}), apto para pg_restore. */
    private static final String FORMATO_CUSTOM = "-Fc";

    private final RespaldoProperties propiedades;
    private final EjecutorProceso ejecutorProceso;

    public MotorRespaldoPgDump(RespaldoProperties propiedades, EjecutorProceso ejecutorProceso) {
        this.propiedades = propiedades;
        this.ejecutorProceso = ejecutorProceso;
    }

    @Override
    public void volcar(Path destinoVolcadoEnClaro) {
        List<String> comando = new ArrayList<>();
        comando.add(propiedades.pgDumpPath());
        comando.add("-h");
        comando.add(propiedades.host());
        comando.add("-p");
        comando.add(String.valueOf(propiedades.puerto()));
        comando.add("-U");
        comando.add(nvl(propiedades.usuario()));
        comando.add(FORMATO_CUSTOM);
        comando.add("-f");
        comando.add(destinoVolcadoEnClaro.toString());
        comando.add(propiedades.baseDatos());

        int codigo = ejecutar(comando);
        if (codigo != 0) {
            throw new RespaldoException("pg_dump termino con codigo distinto de cero: " + codigo);
        }
    }

    @Override
    public void restaurar(Path volcadoEnClaro) {
        List<String> comando = new ArrayList<>();
        comando.add(propiedades.pgRestorePath());
        comando.add("-h");
        comando.add(propiedades.host());
        comando.add("-p");
        comando.add(String.valueOf(propiedades.puerto()));
        comando.add("-U");
        comando.add(nvl(propiedades.usuario()));
        comando.add("-d");
        comando.add(propiedades.baseDatos());
        // Limpia objetos existentes antes de recrearlos, para una restauracion
        // idempotente ante desastre (Req 50.2).
        comando.add("--clean");
        comando.add("--if-exists");
        comando.add(volcadoEnClaro.toString());

        int codigo = ejecutar(comando);
        if (codigo != 0) {
            throw new RespaldoException("pg_restore termino con codigo distinto de cero: " + codigo);
        }
    }

    private int ejecutar(List<String> comando) {
        try {
            return ejecutorProceso.ejecutar(comando, entorno(), propiedades.tiempoLimite());
        } catch (EjecutorProceso.ProcesoException e) {
            throw new RespaldoException("Fallo la invocacion del motor de respaldo de PostgreSQL.", e);
        }
    }

    private Map<String, String> entorno() {
        Map<String, String> env = new HashMap<>();
        String password = propiedades.password();
        if (password != null && !password.isBlank()) {
            // PGPASSWORD: unica via de pasar la credencial sin exponerla en logs.
            env.put("PGPASSWORD", password);
        }
        return env;
    }

    private static String nvl(String valor) {
        return (valor == null) ? "" : valor;
    }
}
