package com.dessti.crm.contabilidad.polizas.adapter.in.rest;

import com.dessti.crm.contabilidad.polizas.domain.NaturalezaCuenta;
import com.dessti.crm.contabilidad.polizas.domain.TipoCuentaContable;
import com.dessti.crm.platform.error.ReglaNegocioException;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para crear una Cuenta_Contable (Req 38.1). DTO de entrada
 * del contrato REST, distinto del comando de aplicacion
 * {@link com.dessti.crm.contabilidad.polizas.application.CrearCuentaContableCommand}
 * (Req 12.2). El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * @param codigo     codigo de la cuenta; obligatorio y no vacio (unico por tenant).
 * @param nombre     nombre descriptivo; obligatorio y no vacio.
 * @param tipo       tipo contable (activo, pasivo, capital, ingreso, gasto); obligatorio.
 * @param naturaleza naturaleza del saldo (deudora, acreedora); obligatoria.
 */
public record CrearCuentaContableRequest(
        @NotBlank @Size(max = 30) String codigo,
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank String tipo,
        @NotBlank String naturaleza) {

    /**
     * Interpreta la etiqueta del tipo contable, rechazando con 422 si es desconocida.
     *
     * @return el tipo contable correspondiente.
     * @throws ReglaNegocioException si la etiqueta es desconocida (422).
     */
    public TipoCuentaContable tipoInterpretado() {
        try {
            return TipoCuentaContable.desdeValorBd(tipo);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Tipo de Cuenta_Contable desconocido: " + tipo);
        }
    }

    /**
     * Interpreta la etiqueta de la naturaleza, rechazando con 422 si es desconocida.
     *
     * @return la naturaleza correspondiente.
     * @throws ReglaNegocioException si la etiqueta es desconocida (422).
     */
    public NaturalezaCuenta naturalezaInterpretada() {
        try {
            return NaturalezaCuenta.desdeValorBd(naturaleza);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Naturaleza de Cuenta_Contable desconocida: " + naturaleza);
        }
    }
}
