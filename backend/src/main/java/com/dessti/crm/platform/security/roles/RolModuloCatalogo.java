package com.dessti.crm.platform.security.roles;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Catalogo de dominio que mapea cada Rol predefinido de nivel EMPRESA con el (o
 * los) modulo(s) contratable(s) que habilitan su asignacion (plataforma-multigiro).
 *
 * <h2>Fuente unica de verdad</h2>
 * <p>Este enum es el UNICO lugar donde vive la relacion rol&rarr;modulo. Tanto la
 * OFERTA de roles asignables ({@code GET /roles/asignables}) como la
 * VALIDACION dura al crear un Usuario o reasignar sus roles
 * ({@code ServicioUsuarios}) consultan este catalogo, de modo que la interfaz y
 * la defensa en profundidad nunca divergen.</p>
 *
 * <h2>Reglas de asignabilidad</h2>
 * <ul>
 *   <li><strong>Roles transversales de administracion/direccion</strong>
 *       ({@code admin_empresa}, {@code gerente}, {@code supervisor}): SIEMPRE
 *       asignables. No estan atados a un modulo vendible concreto, por lo que su
 *       {@link #modulos()} es vacio y {@link #requiereModulo()} es {@code false}.</li>
 *   <li><strong>Roles de modulo</strong> (ventas, diseno, produccion, almacen,
 *       instalacion, mantenimiento, contabilidad, rh, marketing): asignables solo
 *       si el tenant contrato alguno de los modulos requeridos. Cuando hay varios
 *       modulos, basta con contratar UNO (semantica OR), reflejando que el rol
 *       opera si cualquiera de esas areas esta habilitada.</li>
 *   <li><strong>{@code super_admin}</strong> NO figura aqui: es un rol de
 *       plataforma que un admin de empresa jamas puede asignar (lo rechaza
 *       {@code ServicioUsuarios} con 422). No se incluye para no sugerir siquiera
 *       su asignabilidad.</li>
 * </ul>
 *
 * <p>Las claves de modulo coinciden EXACTAMENTE con las claves canonicas del
 * catalogo de modulos (V22 {@code catalogo_modulo}) y con las que resuelve
 * {@code ModulosHabilitadosPort} (mismas que el claim {@code modulos} del JWT):
 * {@code comercial}, {@code redes-sociales}, {@code operacion},
 * {@code inventario-avanzado}, {@code mantenimiento}, {@code compras},
 * {@code facturacion}, {@code contabilidad}, {@code tesoreria},
 * {@code activos-fijos}, {@code rh-nomina}, etc.</p>
 */
public enum RolModuloCatalogo {

    // --- Roles transversales: SIEMPRE asignables (sin modulo requerido) ---
    ADMIN_EMPRESA("admin_empresa"),
    DIRECTOR("director"),
    GERENTE("gerente"),
    SUPERVISOR("supervisor"),

    // --- Roles de modulo: requieren contratar al menos uno de los modulos ---
    VENTAS("ventas", "comercial"),
    DISENO("diseno", "operacion"),
    PRODUCCION("produccion", "operacion"),
    ALMACEN("almacen", "compras", "inventario-avanzado", "operacion"),
    INSTALACION("instalacion", "operacion"),
    MANTENIMIENTO("mantenimiento", "mantenimiento"),
    CONTABILIDAD("contabilidad", "facturacion", "contabilidad", "tesoreria", "activos-fijos"),
    RH("rh", "rh-nomina"),
    MARKETING("marketing", "redes-sociales"),
    CALIDAD("calidad", "operacion"),

    // --- Roles gerentes/aprobadores (segregacion de funciones, V62) ---
    GERENTE_COMERCIAL("gerente_comercial", "comercial"),
    GERENTE_COMPRAS("gerente_compras", "compras", "inventario-avanzado", "operacion"),
    CONTADOR_GENERAL("contador_general", "contabilidad", "facturacion"),
    TESORERO("tesorero", "tesoreria"),
    GERENTE_RH("gerente_rh", "rh-nomina"),
    GERENTE_OPERACIONES("gerente_operaciones", "operacion", "mantenimiento"),
    GERENTE_CALIDAD("gerente_calidad", "operacion");

    private final String nombreRol;
    private final Set<String> modulos;

    RolModuloCatalogo(String nombreRol, String... modulos) {
        this.nombreRol = nombreRol;
        this.modulos = Set.of(modulos);
    }

    /** @return el nombre del rol predefinido, tal como se sembro en V5. */
    public String nombreRol() {
        return nombreRol;
    }

    /**
     * @return las claves de modulo que habilitan este rol (semantica OR: basta
     *         contratar una). Vacio para los roles transversales.
     */
    public Set<String> modulos() {
        return modulos;
    }

    /** @return {@code true} si el rol exige contratar al menos un modulo. */
    public boolean requiereModulo() {
        return !modulos.isEmpty();
    }

    /**
     * Determina si este rol es asignable dado el conjunto de modulos contratados
     * por el tenant. Los roles transversales siempre lo son; los de modulo, si el
     * tenant contrato al menos uno de sus modulos requeridos.
     *
     * @param modulosContratados claves de modulo habilitadas para el tenant
     *                           (normalizadas o no; la comparacion es tolerante).
     * @return {@code true} si el rol puede asignarse en ese tenant.
     */
    public boolean esAsignableCon(Collection<String> modulosContratados) {
        if (!requiereModulo()) {
            return true;
        }
        if (modulosContratados == null || modulosContratados.isEmpty()) {
            return false;
        }
        for (String contratado : modulosContratados) {
            if (contratado != null && modulos.contains(normalizar(contratado))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Localiza la entrada del catalogo por nombre de rol (tolerante a
     * mayusculas/minusculas y espacios), si el rol figura como rol de empresa.
     *
     * @param nombreRol nombre del rol a buscar.
     * @return la entrada del catalogo, o {@link Optional#empty()} si el nombre no
     *         corresponde a un rol de empresa mapeado (p. ej. {@code super_admin}
     *         o un rol personalizado).
     */
    public static Optional<RolModuloCatalogo> porNombre(String nombreRol) {
        if (nombreRol == null || nombreRol.isBlank()) {
            return Optional.empty();
        }
        String normalizado = normalizar(nombreRol);
        for (RolModuloCatalogo entrada : values()) {
            if (entrada.nombreRol.equals(normalizado)) {
                return Optional.of(entrada);
            }
        }
        return Optional.empty();
    }

    /**
     * @return el catalogo completo en orden estable: primero los roles
     *         transversales (declaracion), luego el resto tal como se declaran.
     */
    public static List<RolModuloCatalogo> todos() {
        return List.of(values());
    }

    private static String normalizar(String valor) {
        return valor.strip().toLowerCase(Locale.ROOT);
    }
}
