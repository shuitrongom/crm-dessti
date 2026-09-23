package com.dessti.crm.calidad.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.calidad.adapter.out.persistence.AccionCorrectivaRepository;
import com.dessti.crm.calidad.adapter.out.persistence.NoConformidadRepository;
import com.dessti.crm.calidad.adapter.out.persistence.QuejaClienteRepository;
import com.dessti.crm.calidad.domain.EstadoAccionCorrectiva;
import com.dessti.crm.calidad.domain.EstadoNoConformidad;
import com.dessti.crm.calidad.domain.EstadoQuejaCliente;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion de <strong>solo lectura</strong> que produce los indicadores de
 * cultura de calidad y percepcion del cliente del tenant (Req 70.6, 70.7, 70.8, clausulas
 * 5.1.1 y 7.3). Todas son <strong>agregaciones que no modifican los datos de origen</strong>
 * (Req 22): No_Conformidad abiertas/cerradas, tiempo medio de cierre de Accion_Correctiva,
 * tasa de reincidencia de una misma No_Conformidad y quejas atendidas en tiempo.
 *
 * <h2>Sin colision con reportes-bi (coordinacion del bloque 56)</h2>
 * <p>Este servicio es <strong>propio del modulo {@code calidad}</strong> y se expone por su
 * propio endpoint REST ({@code GET /calidad/indicadores}); <em>no</em> modifica
 * {@code ReportesBiConfig} ni los puertos de area existentes de reportes-bi, ni crea puertos
 * nuevos en ese paquete (que otro agente edita en paralelo). Los indicadores aqui calculados
 * pueden surfacearse luego en el Tablero (Req 22, 48) sin acoplar {@code calidad} a
 * reportes-bi.</p>
 *
 * <h2>Percepcion del cliente por redes sociales (Req 70.8)</h2>
 * <p>La nota a la clausula 9.1.2 integra las metricas sociales del Req 66 como fuente de
 * percepcion. Para <strong>no crear un ciclo</strong> {@code calidad} &harr; {@code social},
 * este servicio calcula unicamente los indicadores nativos de calidad (incluidas las quejas
 * de origen social ya registradas como Queja_Cliente); la analitica social se consulta por su
 * propio servicio ({@code ServicioAnaliticaSocial}, Req 66) y ambos pueden yuxtaponerse en el
 * Tablero. Este es el punto de integracion documentado.</p>
 *
 * <h2>Autorizacion, aislamiento y auditoria</h2>
 * <ul>
 *   <li><strong>403 sin permiso:</strong> el controlador exige {@code calidad:leer}.</li>
 *   <li><strong>Multi-tenant (Req 23):</strong> las agregaciones solo incluyen datos del
 *       tenant vigente (filtro de Hibernate + RLS de V47).</li>
 *   <li><strong>Auditoria (Req 70.9):</strong> cada consulta se audita.</li>
 * </ul>
 */
@Service
public class ServicioIndicadoresCalidad {

    /** Tipo de recurso de auditoria/RBAC de los indicadores de calidad. */
    static final String RECURSO = "calidad";

    /** Segundos por dia, para expresar el tiempo medio de cierre en dias. */
    private static final double SEGUNDOS_POR_DIA = 86_400d;

    private final NoConformidadRepository noConformidadRepository;
    private final AccionCorrectivaRepository accionCorrectivaRepository;
    private final QuejaClienteRepository quejaRepository;
    private final AuditoriaPort auditoria;

    public ServicioIndicadoresCalidad(NoConformidadRepository noConformidadRepository,
                                      AccionCorrectivaRepository accionCorrectivaRepository,
                                      QuejaClienteRepository quejaRepository,
                                      AuditoriaPort auditoria) {
        this.noConformidadRepository = noConformidadRepository;
        this.accionCorrectivaRepository = accionCorrectivaRepository;
        this.quejaRepository = quejaRepository;
        this.auditoria = auditoria;
    }

    /**
     * Calcula los indicadores de cultura de calidad del tenant vigente (Req 70.6, 70.7). Es
     * una operacion de solo lectura que no modifica los datos de origen (Req 22) y se audita
     * (Req 70.9).
     *
     * @return el DTO con las agregaciones de calidad.
     */
    @Transactional(readOnly = true)
    public IndicadoresCalidadDto consultar() {
        long ncAbiertas = noConformidadRepository.countByEstado(EstadoNoConformidad.ABIERTA);
        long ncEnTratamiento = noConformidadRepository.countByEstado(EstadoNoConformidad.EN_TRATAMIENTO);
        long ncCerradas = noConformidadRepository.countByEstado(EstadoNoConformidad.CERRADA);

        long acCerradas = accionCorrectivaRepository.countByEstado(EstadoAccionCorrectiva.CERRADA);
        long acAbiertas = accionCorrectivaRepository.countByEstado(EstadoAccionCorrectiva.ABIERTA)
                + accionCorrectivaRepository.countByEstado(EstadoAccionCorrectiva.EN_ANALISIS)
                + accionCorrectivaRepository.countByEstado(EstadoAccionCorrectiva.EN_EJECUCION)
                + accionCorrectivaRepository.countByEstado(EstadoAccionCorrectiva.VERIFICACION);

        Double tiempoMedioCierreDias = tiempoMedioCierreDias();
        Double tasaReincidencia = tasaReincidencia();

        long quejasRegistradas = quejaRepository.countByEstado(EstadoQuejaCliente.REGISTRADA);
        long quejasVinculadas = quejaRepository.countByEstado(EstadoQuejaCliente.VINCULADA);
        long quejasAtendidas = quejaRepository.countByEstado(EstadoQuejaCliente.ATENDIDA);

        auditarConsulta();

        return new IndicadoresCalidadDto(
                ncAbiertas, ncEnTratamiento, ncCerradas,
                acAbiertas, acCerradas,
                tiempoMedioCierreDias, tasaReincidencia,
                quejasRegistradas, quejasVinculadas, quejasAtendidas);
    }

    // ------------------------------------------------------------------
    // Reglas internas (agregaciones puras)
    // ------------------------------------------------------------------

    private Double tiempoMedioCierreDias() {
        List<Double> segundos = accionCorrectivaRepository.segundosDeCierreDeCerradas();
        if (segundos == null || segundos.isEmpty()) {
            return null;
        }
        double promedioSegundos = segundos.stream()
                .filter(s -> s != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0d);
        return redondear(promedioSegundos / SEGUNDOS_POR_DIA);
    }

    private Double tasaReincidencia() {
        long conAccion = accionCorrectivaRepository.contarNoConformidadesConAccion();
        if (conAccion == 0) {
            return null;
        }
        long reincidentes = accionCorrectivaRepository.noConformidadesReincidentes().size();
        return redondear((double) reincidentes / (double) conAccion);
    }

    private static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private void auditarConsulta() {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actorActual(), "consultar", RECURSO,
                "consulta de indicadores de cultura de calidad", null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
