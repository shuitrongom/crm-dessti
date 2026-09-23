package com.dessti.crm.platform.empresas;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Factoria de apoyo para las pruebas que necesitan construir un {@link Plan} con
 * el modelo rediseñado ({@code plataforma-multigiro}: Giro + moneda + precio por
 * modulo) pero cuyo foco NO es el precio ni el Giro, sino la lista de modulos
 * habilitados (gating, limites de usuarios, herencia de modulos en facturacion).
 *
 * <p>Construye un Plan valido con un Giro ficticio, moneda {@code MXN} y precio
 * {@code 0.00} para cada modulo indicado, de modo que
 * {@link Plan#getModulosHabilitados()} coincida con el conjunto dado.</p>
 */
final class PlanTestFactory {

    /** Giro ficticio comun para las pruebas que no dependen del Giro. */
    static final UUID GIRO_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private PlanTestFactory() {
    }

    /**
     * Crea un Plan con los modulos indicados (precio {@code 0.00} cada uno).
     *
     * @param nombre      nombre del Plan.
     * @param maxUsuarios limite de Usuarios.
     * @param modulos     claves de modulo habilitadas; {@code null} = ninguno.
     * @return el Plan valido listo para usarse en pruebas.
     */
    static Plan conModulos(String nombre, int maxUsuarios, Set<String> modulos) {
        Map<String, BigDecimal> precios = new LinkedHashMap<>();
        if (modulos != null) {
            for (String modulo : modulos) {
                if (modulo != null) {
                    precios.put(modulo, new BigDecimal("0.00"));
                }
            }
        }
        return Plan.crear(nombre, maxUsuarios, 730, GIRO_ID, "MXN", precios, "super");
    }
}
