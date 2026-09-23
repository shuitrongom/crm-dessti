package com.dessti.crm.operacion.produccion.application;

import java.util.UUID;

/**
 * Puerto de consulta (del Nucleo) que expone la mera <strong>existencia</strong> de
 * una Orden_Fabricacion por identificador dentro del tenant vigente. Lo
 * <strong>consumen</strong> los verticales que enlazan opcionalmente con una OF
 * —p. ej. el Levantamiento_Sitio de anuncios (Req 16.2)— para validar el vinculo
 * sin acoplarse a la persistencia interna del Nucleo.
 *
 * <p>Se distingue de {@link OrdenFabricacionTerminadaPort} (que responde por el
 * <em>estado</em> terminada, Req 19.2): aqui solo interesa que la OF exista y sea
 * accesible en el tenant. Publicarlo como puerto permite que el vertical dependa de
 * una interfaz estable de la capa {@code application} del Nucleo, nunca de su
 * {@code adapter.out.persistence} (Req 4.5, 10.3). El adaptador
 * {@code OrdenFabricacionExistenteAdapter} del Nucleo lo implementa delegando en el
 * {@code OrdenFabricacionRepository}, acotado al tenant vigente (Req 23).</p>
 */
public interface OrdenFabricacionExistentePort {

    /**
     * Indica si existe una Orden_Fabricacion con el identificador dado en el tenant
     * vigente (Req 16.2). Una OF inexistente o de otro tenant devuelve {@code false}.
     *
     * @param ordenFabricacionId identificador de la Orden_Fabricacion.
     * @return {@code true} si la Orden_Fabricacion existe y es accesible en el tenant.
     */
    boolean existePorId(UUID ordenFabricacionId);
}