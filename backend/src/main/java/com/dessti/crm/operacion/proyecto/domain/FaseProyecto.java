package com.dessti.crm.operacion.proyecto.domain;

/**
 * Fases del ciclo de vida de un Sitio dentro de un Proyecto, en el <strong>orden
 * operativo</strong> en que se recorren para derivar el estado consolidado
 * (Req 3.2, 3.5): Levantamiento -&gt; Permiso -&gt; Produccion -&gt; Instalacion.
 *
 * <p>No todas las fases aplican a todos los giros. El {@link PerfilFasesGiro}
 * indica que subconjunto de fases es aplicable a un giro concreto; por ejemplo, el
 * giro de anuncios luminosos recorre las cuatro fases, mientras que un giro
 * generico solo considera {@link #PRODUCCION} (Req 3.2, 3.4).</p>
 *
 * <p>El orden de declaracion de las constantes <strong>es significativo</strong>:
 * {@link DerivacionEstadoProyecto} lo usa para recorrer las fases de la mas
 * temprana a la mas tardia.</p>
 */
public enum FaseProyecto {

    /** Levantamiento_Sitio completado (Req 16.4). Solo aplica a giros que lo habilitan. */
    LEVANTAMIENTO,

    /** Permiso_Instalacion aprobado (Req 17.4). Solo aplica a giros que lo habilitan. */
    PERMISO,

    /** Orden_Fabricacion terminada que respalda al Sitio (Req 19.1/19.2). Aplica a cualquier giro. */
    PRODUCCION,

    /** Orden_Trabajo_Instalacion completada (Req 19.5). Solo aplica a giros que lo habilitan. */
    INSTALACION
}
