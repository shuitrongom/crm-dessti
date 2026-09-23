package com.dessti.crm.platform.security.auth;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Fabrica de {@link UsuarioAuth} para pruebas. La entidad solo tiene
 * constructor protegido (requerido por JPA) y campos privados; esta utilidad
 * los rellena por reflexion para construir instancias en estado conocido sin
 * exponer setters de produccion.
 */
final class UsuarioAuthTestFactory {

    private UsuarioAuthTestFactory() {
    }

    /**
     * Crea un Usuario activo con hash de contrasena dado y contador de fallos en 0.
     */
    static UsuarioAuth activo(UUID id, UUID tenantId, String identificador, String hash) {
        return conEstado(id, tenantId, identificador, hash, true);
    }

    /**
     * Crea un Usuario INACTIVO con hash de contrasena dado y contador de fallos
     * en 0. Util para verificar que una cuenta inactiva recibe el mismo error
     * generico que credenciales invalidas (Req 1.3).
     */
    static UsuarioAuth inactivo(UUID id, UUID tenantId, String identificador, String hash) {
        return conEstado(id, tenantId, identificador, hash, false);
    }

    private static UsuarioAuth conEstado(UUID id, UUID tenantId, String identificador,
                                         String hash, boolean activo) {
        UsuarioAuth u = nuevo();
        set(u, "id", id);
        set(u, "tenantId", tenantId);
        set(u, "identificadorAcceso", identificador);
        set(u, "hashPassword", hash);
        set(u, "activo", activo);
        set(u, "intentosFallidos", 0);
        set(u, "bloqueadoHasta", null);
        set(u, "primerIntentoFallido", null);
        set(u, "version", 0L);
        return u;
    }

    private static UsuarioAuth nuevo() {
        try {
            var ctor = UsuarioAuth.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo instanciar UsuarioAuth para pruebas", e);
        }
    }

    private static void set(UsuarioAuth u, String campo, Object valor) {
        try {
            Field f = UsuarioAuth.class.getDeclaredField(campo);
            f.setAccessible(true);
            f.set(u, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo asignar el campo '" + campo + "'", e);
        }
    }
}
