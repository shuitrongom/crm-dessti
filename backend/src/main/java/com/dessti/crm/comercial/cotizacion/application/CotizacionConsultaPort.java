package com.dessti.crm.comercial.cotizacion.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de consulta publicado por el Nucleo Comun (submodulo de Cotizaciones,
 * {@code comercial.cotizacion}) para que los Modulos-Vertical accedan a la
 * informacion de una Cotizacion <strong>exclusivamente por puerto</strong>, sin
 * acoplarse a las clases internas de persistencia del Nucleo (Req 4.5, 5.4, 10.3).
 *
 * <p>Con la conversion de anuncios luminosos en el primer vertical enchufable, los
 * flujos del vertical que hoy consultan la Cotizacion accediendo directamente al
 * {@code CotizacionRepository} y a la entidad {@code Cotizacion} (los adaptadores
 * {@code CotizacionExistenteAdapter} de Prueba_Diseno,
 * {@code CotizacionParaFabricacionAdapter} de Orden_Fabricacion y
 * {@code EnlacesLevantamientoAdapter} de Levantamiento) deben pasar a consumir
 * <strong>este puerto del Nucleo</strong>, invirtiendo asi la dependencia
 * vertical&rarr;persistencia-del-nucleo (Req 10.5). Este puerto reune las tres
 * capacidades que esos adaptadores necesitan hoy:</p>
 * <ul>
 *   <li><strong>Existencia</strong> de una Cotizacion accesible en el tenant
 *       ({@link #existe(UUID)}), para las guardas de enlace de Prueba_Diseno y
 *       Levantamiento (Req 15.1, 16.2).</li>
 *   <li><strong>Vista de consulta</strong> minima ({@link #buscar(UUID)}) con el
 *       identificador, la etiqueta de estado y el Cliente de la Cotizacion, que
 *       Orden_Fabricacion usa para validar que la Cotizacion este aprobada y
 *       heredar su Cliente (Req 7.1, 7.2), y su {@code total} como importe base.</li>
 * </ul>
 *
 * <p>Todas las consultas quedan acotadas al tenant vigente por el filtro global de
 * Hibernate y por la RLS (Req 23): una Cotizacion de otro tenant no se considera
 * accesible. El adaptador del puerto vive <strong>dentro del Nucleo</strong>
 * ({@code CotizacionConsultaAdapter}), respaldado por el {@code CotizacionRepository}.</p>
 */
public interface CotizacionConsultaPort {

    /**
     * Indica si existe una Cotizacion accesible en el tenant vigente con el
     * identificador dado.
     *
     * @param cotizacionId identificador de la Cotizacion; puede ser nulo.
     * @return {@code true} si la Cotizacion existe y es accesible en el tenant;
     *         {@code false} si es nulo, no existe o pertenece a otro tenant.
     */
    boolean existe(UUID cotizacionId);

    /**
     * Recupera la vista de consulta de una Cotizacion del tenant vigente
     * (identificador, etiqueta de estado, Cliente e importe total), o
     * {@link Optional#empty()} si el identificador es nulo, no existe o pertenece a
     * otro tenant (Req 23.3).
     *
     * @param cotizacionId identificador de la Cotizacion; puede ser nulo.
     * @return la vista de consulta de la Cotizacion, o vacio si no es accesible.
     */
    Optional<CotizacionConsulta> buscar(UUID cotizacionId);
}
