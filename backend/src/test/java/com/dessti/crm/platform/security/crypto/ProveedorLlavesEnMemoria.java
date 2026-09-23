package com.dessti.crm.platform.security.crypto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;

/**
 * Proveedor de llaves en memoria para PRUEBAS (Tarea 6.3). Permite controlar de
 * forma determinista qué versión es la activa y qué versiones están disponibles
 * para descifrar, habilitando escenarios de <b>rotación</b> y de <b>llave no
 * disponible</b> sin depender de variables de entorno.
 *
 * <p>El material de llave usado aquí es de PRUEBA (AES-256, 32 bytes), nunca el
 * material real de producción.</p>
 */
final class ProveedorLlavesEnMemoria implements ProveedorLlaves {

    private final String aliasActivo;
    private final Map<String, SecretKey> llaves;

    ProveedorLlavesEnMemoria(String aliasActivo, Map<String, SecretKey> llaves) {
        this.aliasActivo = aliasActivo;
        this.llaves = new LinkedHashMap<>(llaves);
    }

    @Override
    public String aliasActivo() {
        return aliasActivo;
    }

    @Override
    public SecretKey llavePara(String alias) {
        SecretKey llave = llaves.get(alias);
        if (llave == null) {
            // Mismo comportamiento que el proveedor de producción: bloqueo
            // controlado sin exponer el material de la llave (Req 67.6).
            throw new LlaveCifradoNoDisponibleException(alias);
        }
        return llave;
    }

    @Override
    public Set<String> versionesDisponibles() {
        return Set.copyOf(llaves.keySet());
    }
}
