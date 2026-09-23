package com.dessti.crm.contabilidad.reportes.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Proyeccion Spring Data de solo lectura con el saldo agregado de una
 * Cuenta_Contable en un periodo: los totales de cargos y abonos de sus renglones
 * ({@code movimiento_poliza}), junto con su clasificacion contable (tipo,
 * naturaleza, codigo, nombre) (Req 47.1). La capa de aplicacion la traduce a un
 * {@link com.dessti.crm.contabilidad.reportes.domain.SaldoCuenta} de dominio.
 *
 * <p>Es una <strong>agregacion de solo lectura</strong> sobre las Polizas_Contables:
 * no modifica ninguna dato de origen (Req 47.2).</p>
 */
public interface SaldoCuentaProjection {

    /**
     * @return identificador de la Cuenta_Contable.
     */
    UUID getCuentaId();

    /**
     * @return codigo de la Cuenta_Contable.
     */
    String getCodigo();

    /**
     * @return nombre de la Cuenta_Contable.
     */
    String getNombre();

    /**
     * @return etiqueta ASCII del tipo contable (activo, pasivo, capital, ingreso, gasto).
     */
    String getTipo();

    /**
     * @return etiqueta ASCII de la naturaleza (deudora, acreedora).
     */
    String getNaturaleza();

    /**
     * @return suma de los cargos del periodo a la cuenta.
     */
    BigDecimal getCargos();

    /**
     * @return suma de los abonos del periodo a la cuenta.
     */
    BigDecimal getAbonos();
}
