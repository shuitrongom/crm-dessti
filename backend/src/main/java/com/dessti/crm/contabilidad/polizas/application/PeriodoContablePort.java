package com.dessti.crm.contabilidad.polizas.application;

/**
 * Puerto de consulta del candado contable, usado por {@link ServicioContabilidad}
 * para decidir si una Poliza_Contable (o su reverso) puede afectar un periodo.
 *
 * <p>Se declara en el subpaquete {@code polizas.application} (donde vive el
 * consumidor) y lo implementa el servicio de cierre de periodo, evitando que las
 * polizas dependan de los detalles del cierre: solo dependen de este contrato
 * minimo. La direccion de dependencias es {@code polizas -> puerto <- cierre}.</p>
 */
public interface PeriodoContablePort {

    /**
     * Indica si el periodo mensual {@code (anio, mes)} del tenant vigente esta
     * cerrado (candado activo). Un periodo sin registro explicito se considera
     * ABIERTO, por lo que devuelve {@code false}.
     *
     * @param anio anio del periodo.
     * @param mes  mes del periodo (1..12).
     * @return {@code true} si el periodo esta cerrado y no admite polizas/reversos.
     */
    boolean estaCerrado(int anio, int mes);
}
