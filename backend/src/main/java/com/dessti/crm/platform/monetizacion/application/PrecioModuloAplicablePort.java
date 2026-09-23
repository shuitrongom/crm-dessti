package com.dessti.crm.platform.monetizacion.application;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto que resuelve el <strong>precio aplicable</strong> de un modulo para una
 * Empresa en una moneda (Req 24.3, ambito super_admin). Es el contrato que la
 * factura de renta (PARTE 2) consume para valorar cada modulo habilitado.
 *
 * <h2>Regla de resolucion</h2>
 * <ol>
 *   <li>Precio ESPECIAL negociado por la Empresa ({@code empresa_modulo_precio})
 *       para (empresa, modulo, moneda), si existe.</li>
 *   <li>en su defecto, el precio de LISTA del modulo ({@code precio_modulo}) en
 *       esa moneda, si existe.</li>
 *   <li>si no hay precio en esa moneda, se devuelve {@link Optional#empty()} (el
 *       modulo no tiene precio definido para esa moneda).</li>
 * </ol>
 */
public interface PrecioModuloAplicablePort {

    /**
     * Resuelve el precio aplicable de un modulo (por su clave canonica) para una
     * Empresa en una moneda.
     *
     * @param tenantId     Empresa (tenant).
     * @param claveModulo  clave canonica del modulo (coincide con modulos_habilitados).
     * @param monedaCodigo codigo de moneda ISO 4217.
     * @return el precio aplicable (especial &gt; lista), o vacio si no hay precio
     *         para esa moneda.
     */
    Optional<BigDecimal> precioAplicable(UUID tenantId, String claveModulo, String monedaCodigo);
}