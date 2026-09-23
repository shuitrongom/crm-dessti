package com.dessti.crm.platform.respaldo.application;

import java.nio.file.Path;

/**
 * Puerto del <strong>motor de respaldo</strong> (Req 50): abstrae la produccion
 * de un volcado logico de la base de datos y su restauracion, sin acoplar el
 * nucleo a una tecnologia concreta ni a la invocacion de procesos del sistema.
 *
 * <p>El adaptador por defecto orquesta {@code pg_dump}/{@code pg_restore} de
 * PostgreSQL a traves de una frontera de invocacion de procesos sustituible, de
 * modo que las pruebas puedan reemplazar el motor por un doble que NO ejecute
 * procesos externos.</p>
 *
 * <p>El motor trabaja con artefactos <em>en claro</em> (el volcado). El cifrado
 * y descifrado del artefacto son responsabilidad de {@link CifradorRespaldoPort},
 * de modo que en reposo el respaldo quede siempre cifrado (Req 50.3, 67).</p>
 */
public interface MotorRespaldoPort {

    /**
     * Produce un volcado logico de la base de datos en la ruta indicada.
     *
     * @param destinoVolcadoEnClaro ruta del archivo de volcado a generar (en
     *                              claro, antes de cifrar); no nula.
     * @throws RespaldoException si el volcado falla.
     */
    void volcar(Path destinoVolcadoEnClaro);

    /**
     * Restaura la base de datos a partir de un volcado logico previamente
     * descifrado (Req 50.2).
     *
     * @param volcadoEnClaro ruta del archivo de volcado ya descifrado; no nula.
     * @throws RespaldoException si la restauracion falla.
     */
    void restaurar(Path volcadoEnClaro);
}
