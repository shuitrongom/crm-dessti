package com.dessti.crm.platform.respaldo.adapter.out;

import java.nio.file.Path;

import com.dessti.crm.platform.respaldo.application.MotorRespaldoPort;
import com.dessti.crm.platform.respaldo.application.RespaldoException;

/**
 * Motor de respaldo <strong>inactivo</strong>, usado cuando la capacidad
 * operativa esta deshabilitada ({@code crm.respaldo.habilitado=false}, valor por
 * defecto).
 *
 * <p>Permite que el contexto arranque (el {@code ServicioRespaldo} siempre tiene
 * un {@link MotorRespaldoPort} inyectable) sin ejecutar procesos externos: si se
 * intenta volcar o restaurar, rechaza la operacion de forma controlada. Asi, en
 * pruebas y en entornos sin configurar, nunca se invoca {@code pg_dump}/
 * {@code pg_restore}.</p>
 */
public class MotorRespaldoDeshabilitado implements MotorRespaldoPort {

    private static final String MENSAJE =
            "La capacidad de respaldo esta deshabilitada (crm.respaldo.habilitado=false). "
                    + "Habilitela y configure el motor de PostgreSQL para operar (Req 50).";

    @Override
    public void volcar(Path destinoVolcadoEnClaro) {
        throw new RespaldoException(MENSAJE);
    }

    @Override
    public void restaurar(Path volcadoEnClaro) {
        throw new RespaldoException(MENSAJE);
    }
}
