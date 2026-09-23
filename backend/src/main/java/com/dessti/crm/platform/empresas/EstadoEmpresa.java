package com.dessti.crm.platform.empresas;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Estado del ciclo de vida de una Empresa (Tenant), correspondiente a la
 * columna {@code empresa.estado} de la migracion V1, cuyo CHECK admite
 * exactamente los valores {@code 'activa'}, {@code 'suspendida'} y
 * {@code 'cancelada'} (Req 24).
 *
 * <p>El valor persistido en la base de datos es la etiqueta en minusculas
 * ({@link #valorBd()}), no el nombre de la constante Java. El
 * {@link EstadoEmpresaConverter} realiza la traduccion bidireccional para que el
 * mapeo coincida <em>exactamente</em> con el CHECK de V1 y un arranque con
 * {@code ddl-auto=validate} valide sin conflictos.</p>
 *
 * <ul>
 *   <li>{@link #ACTIVA}: Empresa operativa; sus Usuarios pueden iniciar sesion.</li>
 *   <li>{@link #SUSPENDIDA}: Empresa suspendida por el {@code super_admin}; se
 *       impide el inicio de sesion de sus Usuarios mientras dure (Req 24.4).</li>
 *   <li>{@link #CANCELADA}: Empresa cancelada (offboarding, tarea 14.4). Fuera
 *       del alcance de la tarea 14.1 salvo como valor de estado valido.</li>
 * </ul>
 */
public enum EstadoEmpresa {

    /** Empresa operativa; permite el inicio de sesion de sus Usuarios. */
    ACTIVA("activa"),

    /** Empresa suspendida; impide el inicio de sesion de sus Usuarios (Req 24.4). */
    SUSPENDIDA("suspendida"),

    /** Empresa cancelada (offboarding, tarea 14.4). */
    CANCELADA("cancelada");

    private final String valorBd;

    EstadoEmpresa(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta canonica en minusculas del estado. Es a la vez el valor persistido
     * en {@code empresa.estado} (tal como lo exige el CHECK de la migracion V1) y
     * el valor de representacion JSON hacia el frontend.
     *
     * <p>La anotacion {@link JsonValue} hace que Jackson serialice el enum como
     * esta etiqueta ({@code "activa"}/{@code "suspendida"}/{@code "cancelada"}) en
     * lugar del nombre de la constante Java ({@code ACTIVA}), de modo que el
     * contrato JSON coincida con la etiqueta de base de datos y con el contrato del
     * frontend (minusculas). No altera el {@link EstadoEmpresaConverter} ni la
     * semantica de persistencia.</p>
     *
     * @return la etiqueta canonica en minusculas del estado.
     */
    @JsonValue
    public String valorBd() {
        return valorBd;
    }

    /**
     * Fabrica de deserializacion JSON: resuelve el enum a partir de su etiqueta
     * canonica en minusculas, tolerando mayusculas/minusculas y espacios (delega
     * en {@link #desdeValorBd(String)}). Permite aceptar tanto {@code "activa"}
     * como {@code "ACTIVA"} en las peticiones entrantes por robustez.
     *
     * @param valor etiqueta del estado recibida en el JSON.
     * @return el {@link EstadoEmpresa} correspondiente.
     */
    @JsonCreator
    public static EstadoEmpresa desdeJson(String valor) {
        return desdeValorBd(valor);
    }

    /**
     * Resuelve el enum a partir de la etiqueta persistida en la base de datos.
     *
     * @param valor etiqueta persistida (por ejemplo {@code "activa"}); admite
     *              espacios y mayusculas/minusculas por robustez.
     * @return el {@link EstadoEmpresa} correspondiente.
     * @throws IllegalArgumentException si el valor no corresponde a ningun estado.
     */
    public static EstadoEmpresa desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Empresa no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoEmpresa estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Empresa desconocido: '" + valor + "'");
    }
}
