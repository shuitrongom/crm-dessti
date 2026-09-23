package com.dessti.crm.platform.giros;

import java.util.UUID;

import com.dessti.crm.platform.giros.domain.Giro;

/**
 * DTO de salida de un Giro (Req 1.1, 1.6), distinto de la entidad de
 * persistencia {@link Giro}.
 *
 * <p>Replica el patron de {@code platform.empresas.EmpresaDto}: es un
 * {@code record} inmutable con proyeccion estatica desde la entidad y expone
 * unicamente los atributos de plataforma del Giro (identidad, clave canonica,
 * nombre visible, descripcion, estado y {@code version} para el control
 * optimista), nunca la entidad JPA.</p>
 *
 * <h2>Completitud del Giro (Base vs Completo)</h2>
 * <p>Ademas de los atributos de persistencia, el DTO comunica al
 * {@code super_admin} el <strong>grado de completitud</strong> del Giro, un dato
 * derivado del {@code RegistroVerticales} (no persistido) que no vive en la
 * entidad:</p>
 * <ul>
 *   <li>{@link #tieneReglasNegocio()} — {@code true} si la clave del Giro esta
 *       registrada por un {@code ContratoVertical} programado en codigo, es
 *       decir, el Giro es <strong>"Completo"</strong> (aporta modulos y reglas de
 *       negocio propios ademas de los modulos base). {@code false} si el Giro es
 *       <strong>"Base"</strong>: solo hereda los modulos base compartidos, pues
 *       sus reglas especificas aun no estan programadas.</li>
 *   <li>{@link #modulosEspecificos()} — numero de modulos atribuidos a este Giro
 *       por su vertical ({@code 0} si no tiene vertical registrado). Es un conteo
 *       barato; el detalle de los modulos se consulta en {@code /plataforma/modulos}.</li>
 * </ul>
 *
 * <p>Como {@link #de(Giro)} es una proyeccion pura sin acceso al
 * {@code RegistroVerticales}, la enriquecedora {@link #de(Giro, boolean, int)}
 * recibe los dos valores ya calculados por {@code ServicioGiros}, que si depende
 * del registro. La proyeccion base {@link #de(Giro)} se conserva para
 * compatibilidad y usos internos, dejando los campos de completitud en su valor
 * conservador ("Base", {@code 0}).</p>
 *
 * @param id                identificador del Giro.
 * @param clave             clave canonica normalizada (minusculas, kebab); unica
 *                          e inmutable.
 * @param nombreVisible     nombre visible del Giro.
 * @param descripcion       descripcion del vertical; puede ser {@code null}.
 * @param activo            estado del Giro (activo/inactivo).
 * @param version           version para el control de concurrencia optimista.
 * @param tieneReglasNegocio {@code true} si el Giro tiene un vertical programado
 *                          (Giro "Completo"); {@code false} si es "Base".
 * @param modulosEspecificos numero de modulos que aporta el vertical de este Giro
 *                          ({@code 0} si no tiene vertical registrado).
 */
public record GiroDto(
        UUID id,
        String clave,
        String nombreVisible,
        String descripcion,
        boolean activo,
        long version,
        boolean tieneReglasNegocio,
        int modulosEspecificos) {

    /**
     * Proyecta una entidad {@link Giro} a su DTO de salida sin informacion de
     * completitud (Giro tratado como "Base": sin reglas de negocio y con cero
     * modulos especificos).
     *
     * <p>Se conserva para usos internos y compatibilidad: cuando quien proyecta
     * no dispone del {@code RegistroVerticales} (proyeccion pura). Para exponer la
     * completitud real usar {@link #de(Giro, boolean, int)}.</p>
     *
     * @param giro entidad a proyectar.
     * @return el DTO correspondiente con completitud conservadora ("Base", 0).
     */
    public static GiroDto de(Giro giro) {
        return de(giro, false, 0);
    }

    /**
     * Proyecta una entidad {@link Giro} a su DTO de salida enriquecido con la
     * informacion de completitud derivada del {@code RegistroVerticales}.
     *
     * @param giro               entidad a proyectar.
     * @param tieneReglasNegocio {@code true} si el Giro tiene un vertical
     *                           programado (Giro "Completo").
     * @param modulosEspecificos numero de modulos que aporta el vertical del Giro.
     * @return el DTO correspondiente con la completitud calculada.
     */
    public static GiroDto de(Giro giro, boolean tieneReglasNegocio, int modulosEspecificos) {
        return new GiroDto(
                giro.getId(),
                giro.getClave(),
                giro.getNombreVisible(),
                giro.getDescripcion(),
                giro.isActivo(),
                giro.getVersion(),
                tieneReglasNegocio,
                modulosEspecificos);
    }
}
