package com.dessti.crm.operacion.proyecto.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Objeto de valor <strong>puro</strong> del dominio que describe que
 * {@link FaseProyecto fases} son aplicables al giro de un tenant, para parametrizar
 * la derivacion del estado consolidado del Proyecto (Req 3.2). Al depender de un
 * perfil de fases en lugar de asumir siempre las cuatro fases de anuncios, el
 * mismo nucleo sirve a cualquier giro sin ramas por giro embebidas (Decision D5).
 *
 * <h2>Perfiles predefinidos</h2>
 * <ul>
 *   <li>{@link #ANUNCIOS}: las cuatro fases (Levantamiento -&gt; Permiso -&gt;
 *       Produccion -&gt; Instalacion), equivalente a la derivacion clasica de
 *       anuncios (Req 3.5).</li>
 *   <li>{@link #GENERICO}: solo {@link FaseProyecto#PRODUCCION}, para giros que no
 *       habilitan levantamiento, permiso ni instalacion (Req 3.2).</li>
 * </ul>
 *
 * <p>El conjunto de fases se copia de forma defensiva a un {@link EnumSet}
 * inmutable en el constructor, de modo que el perfil sea verdaderamente inmutable y
 * la derivacion sea determinista.</p>
 *
 * @param fasesAplicables conjunto de fases aplicables al giro; nunca {@code null}
 *                        ni con elementos {@code null}.
 */
public record PerfilFasesGiro(Set<FaseProyecto> fasesAplicables) {

    /**
     * Perfil de anuncios luminosos: recorre las cuatro fases en la secuencia
     * completa (Levantamiento -&gt; Permiso -&gt; Produccion -&gt; Instalacion),
     * equivalente a la derivacion clasica (Req 3.5).
     */
    public static final PerfilFasesGiro ANUNCIOS =
            new PerfilFasesGiro(EnumSet.allOf(FaseProyecto.class));

    /**
     * Perfil generico para cualquier giro que no habilita levantamiento, permiso ni
     * instalacion: solo considera la fase de {@link FaseProyecto#PRODUCCION}
     * (Req 3.2).
     */
    public static final PerfilFasesGiro GENERICO =
            new PerfilFasesGiro(EnumSet.of(FaseProyecto.PRODUCCION));

    /**
     * Compacta: valida y copia de forma defensiva el conjunto de fases a un
     * {@link EnumSet} inmutable, garantizando la inmutabilidad del perfil.
     *
     * @throws NullPointerException si {@code fasesAplicables} es {@code null} o
     *         contiene algun elemento {@code null}.
     */
    public PerfilFasesGiro {
        Objects.requireNonNull(fasesAplicables, "El conjunto de fases aplicables es obligatorio.");
        // Copia defensiva inmutable; EnumSet.copyOf rechaza elementos nulos.
        fasesAplicables = Set.copyOf(EnumSet.copyOf(fasesAplicables));
    }

    /**
     * Indica si la fase dada es aplicable a este perfil de giro.
     *
     * @param fase la fase a consultar; nunca {@code null}.
     * @return {@code true} si la fase pertenece a las fases aplicables.
     */
    public boolean aplica(FaseProyecto fase) {
        return fasesAplicables.contains(fase);
    }
}
