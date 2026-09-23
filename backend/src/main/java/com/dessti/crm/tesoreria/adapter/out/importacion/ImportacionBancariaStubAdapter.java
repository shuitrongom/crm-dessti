package com.dessti.crm.tesoreria.adapter.out.importacion;

import java.math.BigDecimal;
import java.util.List;

import com.dessti.crm.tesoreria.application.EstadoCuentaImportado;
import com.dessti.crm.tesoreria.application.ImportacionBancariaPort;
import com.dessti.crm.tesoreria.application.MovimientoImportado;
import com.dessti.crm.tesoreria.application.SolicitudImportacion;

/**
 * Adaptador <strong>stub</strong> del {@link ImportacionBancariaPort}: implementacion
 * determinista de la importacion de estados de cuenta para desarrollo y pruebas
 * (Req 43.2). No realiza ninguna lectura de archivo ni llamada de red.
 *
 * <h2>Comportamiento</h2>
 * <ul>
 *   <li>Si la {@link SolicitudImportacion} ya trae {@code movimientos}, el stub los
 *       <em>reproduce</em> tal cual y deriva los saldos: {@code saldoInicial = 0} y
 *       {@code saldoFinal = suma de los montos con signo}. Asi las pruebas controlan
 *       exactamente el estado de cuenta importado.</li>
 *   <li>Si la solicitud no trae movimientos, el stub devuelve un pequeno conjunto
 *       determinista (un deposito y un retiro) que permite ejercitar el flujo de
 *       importacion y conciliacion sin una fuente real.</li>
 * </ul>
 *
 * <h2>Sustituibilidad</h2>
 * <p>Se registra como bean {@link ImportacionBancariaPort} mediante el metodo
 * {@code @Bean} {@code @ConditionalOnMissingBean} de {@link ImportacionBancariaConfig}:
 * en cuanto exista otro bean {@link ImportacionBancariaPort} (el futuro adaptador de
 * archivo CSV/OFX o de la API del banco), este stub deja de registrarse, de modo que
 * la integracion real se intercambia sin tocar la aplicacion. La aplicacion depende
 * siempre de la interfaz {@link ImportacionBancariaPort}, no de esta clase.</p>
 *
 * <h2>Credenciales (Req 11)</h2>
 * <p>Este stub <strong>no</strong> usa credenciales. El adaptador real de API del
 * banco resolveria sus credenciales exclusivamente desde la gestion de secretos
 * (Req 11) y nunca las escribiria en logs.</p>
 */
public class ImportacionBancariaStubAdapter implements ImportacionBancariaPort {

    /** Escala monetaria coherente con NUMERIC(18,2) de V35. */
    private static final int ESCALA_MONETARIA = 2;

    @Override
    public EstadoCuentaImportado importar(SolicitudImportacion solicitud) {
        if (solicitud != null && solicitud.movimientos() != null
                && !solicitud.movimientos().isEmpty()) {
            List<MovimientoImportado> movimientos = List.copyOf(solicitud.movimientos());
            BigDecimal saldoFinal = movimientos.stream()
                    .map(MovimientoImportado::monto)
                    .filter(monto -> monto != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(ESCALA_MONETARIA, java.math.RoundingMode.HALF_UP);
            return new EstadoCuentaImportado(
                    BigDecimal.ZERO.setScale(ESCALA_MONETARIA, java.math.RoundingMode.HALF_UP),
                    saldoFinal,
                    movimientos);
        }
        return conjuntoDeterministaPorDefecto(solicitud);
    }

    /**
     * Conjunto determinista por defecto (un deposito y un retiro) para arranque y
     * pruebas cuando la solicitud no aporta movimientos.
     */
    private EstadoCuentaImportado conjuntoDeterministaPorDefecto(SolicitudImportacion solicitud) {
        java.time.LocalDate fecha = (solicitud != null && solicitud.periodoInicio() != null)
                ? solicitud.periodoInicio()
                : java.time.LocalDate.EPOCH;
        MovimientoImportado deposito = new MovimientoImportado(
                fecha, new BigDecimal("1000.00"), "DEP-STUB-001", "Deposito de ejemplo (stub)");
        MovimientoImportado retiro = new MovimientoImportado(
                fecha, new BigDecimal("-250.00"), "RET-STUB-001", "Retiro de ejemplo (stub)");
        List<MovimientoImportado> movimientos = List.of(deposito, retiro);
        BigDecimal saldoFinal = new BigDecimal("750.00");
        return new EstadoCuentaImportado(
                BigDecimal.ZERO.setScale(ESCALA_MONETARIA, java.math.RoundingMode.HALF_UP),
                saldoFinal,
                movimientos);
    }
}
