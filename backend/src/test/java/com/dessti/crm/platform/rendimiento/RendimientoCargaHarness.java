package com.dessti.crm.platform.rendimiento;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Esqueleto <strong>documentado y deshabilitado</strong> del arnes de pruebas de
 * carga/rendimiento (Tarea 49.3, Req 51.1).
 *
 * <p><strong>Fuera del alcance de la suite automatica.</strong> El diseño
 * (design.md, seccion "Pruebas de rendimiento") establece explicitamente que la
 * verificacion de percentiles bajo carga concurrente queda <em>fuera</em> de las
 * pruebas unitarias/PBT: son pruebas de carga que dependen del hardware, del
 * dimensionamiento y de la concurrencia real, y sus asertos por tiempo serian
 * inestables (flaky) en CI. Por ello esta clase esta anotada con
 * {@link Disabled} y {@link Tag}("performance") y NO se ejecuta en la suite por
 * defecto.</p>
 *
 * <h2>Objetivos de rendimiento (Req 51.1)</h2>
 * <ul>
 *   <li><b>Lectura:</b> al menos el 95% de las peticiones de lectura en
 *       &le; 2 segundos (p95 &le; 2 s), medido en el servidor.</li>
 *   <li><b>Escritura:</b> al menos el 95% de las peticiones de escritura en
 *       &le; 4 segundos (p95 &le; 4 s), medido en el servidor.</li>
 *   <li><b>Concurrencia:</b> soportar la cantidad configurable de Usuarios
 *       concurrentes sin degradacion funcional (Req 51.2).</li>
 * </ul>
 *
 * <h2>Como ejecutar la medicion de p95</h2>
 * <p>La medicion se realiza con una herramienta de carga externa contra un
 * despliegue representativo (no en esta suite):</p>
 * <ol>
 *   <li>Desplegar el backend con datos de prueba representativos y el perfil de
 *       produccion (cache y ejecutor intensivo activos; ver
 *       {@code ConfiguracionCache} y {@code ConfiguracionAsync}).</li>
 *   <li>Ejecutar un plan de carga (por ejemplo, k6, JMeter o Gatling) con la
 *       concurrencia esperada, cubriendo endpoints de lectura (listados
 *       paginados, catalogos cacheados) y de escritura (altas y transiciones de
 *       estado).</li>
 *   <li>Recolectar los percentiles del lado servidor y comparar contra los
 *       objetivos: p95 lectura &le; 2 s y p95 escritura &le; 4 s.</li>
 *   <li>Las operaciones intensivas (nomina, estados financieros, BI, timbrado
 *       masivo) se ejecutan en el ejecutor dedicado (Req 51.3) y no deben contar
 *       en la latencia interactiva.</li>
 * </ol>
 *
 * <p>Para habilitar temporalmente este arnes de forma manual, retire la
 * anotacion {@link Disabled} e implemente el escenario; nunca deje asertos por
 * tiempo en la suite por defecto.</p>
 */
@Tag("performance")
@Disabled("Prueba de carga fuera del alcance de la suite automatica (Req 51.1); ver Javadoc para su ejecucion.")
@DisplayName("Tarea 49.3 - Arnes de carga/rendimiento p95 (Req 51.1, deshabilitado)")
class RendimientoCargaHarness {

    /**
     * Marcador del escenario de carga. Intencionalmente sin implementacion ni
     * asertos por tiempo: la medicion de p95 se realiza con una herramienta de
     * carga externa (ver Javadoc de la clase), no en esta suite.
     */
    @Test
    @DisplayName("Escenario de carga p95 (lectura <= 2 s / escritura <= 4 s) - ejecutar con herramienta externa")
    void escenarioDeCargaP95() {
        // Esqueleto documentado: la ejecucion real es externa (k6/JMeter/Gatling).
        // No se incluyen asertos por tiempo para no introducir inestabilidad en CI.
    }
}
