package com.dessti.crm.operacion.produccion.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.produccion.application.SitioOrdenFabricacionTerminadaPort;

/**
 * Adaptador de salida <strong>del Nucleo</strong> que implementa
 * {@link SitioOrdenFabricacionTerminadaPort} para giros genericos (Decision D5-b,
 * &sect;A3).
 *
 * <p>En el Nucleo la {@code Orden_Fabricacion} se vincula a Cotizacion/Cliente y
 * <strong>no</strong> tiene columna {@code sitio_id} (ver
 * {@code OrdenFabricacion}); no existe, por ahora, un vinculo OF&rarr;Sitio que
 * permita afirmar por si solo que un Sitio tiene una OF terminada. Por ello este
 * adaptador devuelve {@code false} de forma <em>deny-safe</em>: para un giro
 * generico, mientras no exista tal vinculo, el Sitio se considera sin produccion
 * confirmada (lo que consolida el Proyecto en {@code EN_PRODUCCION}).</p>
 *
 * <p>Es el bean por defecto (unico) del Nucleo para este puerto: se registra como
 * {@link Component} siempre disponible. Si en el futuro se necesita una
 * implementacion mas especifica (p. ej. una que resuelva el vinculo OF&rarr;Sitio
 * real), debera marcarse {@code @Primary} para reemplazarlo. No depende de ningun
 * puerto de anuncios, preservando que el Nucleo no dependa de los verticales.</p>
 */
@Component("sitioOrdenFabricacionTerminadaAdapter")
public class SitioOrdenFabricacionTerminadaAdapter implements SitioOrdenFabricacionTerminadaPort {

    @Override
    public boolean sitioTieneOrdenFabricacionTerminada(UUID sitioId) {
        // Sin vinculo OF->Sitio en el Nucleo: deny-safe (Sitio sin produccion
        // confirmada). Ver la justificacion de modelado en el puerto.
        return false;
    }
}
