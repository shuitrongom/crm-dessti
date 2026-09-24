package com.dessti.crm.contabilidad.electronica.application;

import java.util.List;

/**
 * Vista previa del Catalogo_XML antes de exportar (Req 5.1, 5.2): cuantas cuentas se
 * incluiran (activas con codigo agrupador amarrado) y las cuentas activas SIN
 * amarrar (advertencia de amarre pendiente).
 *
 * @param cuentasAmarradas numero de cuentas activas amarradas que se exportaran.
 * @param cuentasSinAmarrar codigos de cuentas activas SIN codigo agrupador (advertencia).
 */
public record VistaPreviaCatalogoDto(
        int cuentasAmarradas,
        List<String> cuentasSinAmarrar) {

    /**
     * @return {@code true} si hay al menos una cuenta activa sin amarrar.
     */
    public boolean tieneAdvertencias() {
        return cuentasSinAmarrar != null && !cuentasSinAmarrar.isEmpty();
    }
}
