package com.dessti.crm.compras.factura.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.dessti.crm.compras.factura.domain.ConciliacionTresVias.RenglonConciliacion;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 12: Conciliacion de
 * tres vias nunca autoriza fuera de tolerancia</strong> (Req 33.3, 33.4, 33.5).
 *
 * <p>Ejercita directamente la logica de dominio PURA
 * {@link ConciliacionTresVias#esConciliable(List, BigDecimal)} y
 * {@link ConciliacionTresVias#renglonConciliable(RenglonConciliacion, BigDecimal)},
 * sin base de datos ni contexto de Spring: son funciones estaticas, sin estado y
 * deterministas, por lo que la invariante "nunca concilia fuera de tolerancia" se
 * comprueba universalmente sobre renglones arbitrarios.</p>
 *
 * <h2>Invariantes verificados (Property 12)</h2>
 * <ol>
 *   <li><strong>Nunca autoriza fuera de tolerancia:</strong> si algun renglon tiene
 *       {@code cantidadFacturada > cantidadRecibida} o
 *       {@code |precioFacturado - precioOrden| > precioOrden * tolerancia}, el
 *       conjunto NO es conciliable (no autoriza el pago, Req 33.4).</li>
 *   <li><strong>Dentro de tolerancia y cantidades correctas concilia:</strong> si
 *       todos los renglones cumplen {@code facturada <= recibida} y precio dentro de
 *       tolerancia, el conjunto es conciliable (Req 33.5).</li>
 *   <li><strong>Equivalencia AND por renglon:</strong> el conjunto concilia si y
 *       solo si cada renglon concilia individualmente.</li>
 * </ol>
 *
 * <h2>Convenciones numericas (espejo de produccion)</h2>
 * <p>Dinero a escala 2 (coherente con {@code NUMERIC(18,2)}) con redondeo
 * {@code HALF_UP}; cantidades a escala 3. Los generadores construyen valores a
 * partir de enteros escalados para mantener comparaciones exactas por
 * {@code compareTo}. La tolerancia se genera como fraccion decimal en [0, 0.5].</p>
 */
class ConciliacionTresViasPropertyTest {

    /** Escala monetaria espejo de produccion (NUMERIC(18,2)). */
    private static final int ESCALA_MONEDA = 2;

    /** Escala de cantidades espejo de produccion (NUMERIC(18,3)). */
    private static final int ESCALA_CANTIDAD = 3;

    private static BigDecimal aMoneda(long centavos) {
        return new BigDecimal(centavos).movePointLeft(ESCALA_MONEDA);
    }

    private static BigDecimal aCantidad(long milesimas) {
        return new BigDecimal(milesimas).movePointLeft(ESCALA_CANTIDAD);
    }

    /** Precio de OC estrictamente positivo a escala 2 (centavos de 1 a 100_000_000). */
    @Provide
    Arbitrary<Long> preciosOrdenCentavos() {
        return Arbitraries.longs().between(1L, 100_000_000L);
    }

    /** Cantidad &gt;= 0 a escala 3 (milesimas de 0 a 10_000_000). */
    @Provide
    Arbitrary<Long> cantidadesMilesimas() {
        return Arbitraries.longs().between(0L, 10_000_000L);
    }

    /** Tolerancia como fraccion en [0, 0.5] con 4 decimales. */
    @Provide
    Arbitrary<BigDecimal> tolerancias() {
        return Arbitraries.longs().between(0L, 5_000L)
                .map(diez -> new BigDecimal(diez).movePointLeft(4));
    }

    // ----------------------------------------------------------------------
    // Property 12 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 12: Conciliación de tres vías nunca autoriza fuera de tolerancia
    @Property(tries = 1000)
    void precioFueraDeToleranciaNuncaConcilia(
            @ForAll("preciosOrdenCentavos") long precioOrdenCentavos,
            @ForAll("cantidadesMilesimas") long recibidaMilesimas,
            @ForAll("tolerancias") BigDecimal tolerancia) {

        BigDecimal precioOrden = aMoneda(precioOrdenCentavos);
        BigDecimal recibida = aCantidad(recibidaMilesimas);
        // cantidad facturada <= recibida para AISLAR la causa de rechazo al precio.
        BigDecimal facturada = recibida;

        // Desviacion estrictamente mayor a la maxima permitida (precioOrden * tolerancia):
        // se suma una desviacion = maximaDesviacion + 0.01, redondeada a escala 2.
        BigDecimal maximaDesviacion = precioOrden.multiply(tolerancia)
                .setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
        BigDecimal precioFacturado = precioOrden.add(maximaDesviacion).add(aMoneda(1L));

        RenglonConciliacion renglon =
                new RenglonConciliacion(facturada, recibida, precioFacturado, precioOrden);

        assertThat(ConciliacionTresVias.renglonConciliable(renglon, tolerancia))
                .as("precio fuera de tolerancia (desviacion > %s * %s) no debe conciliar",
                        precioOrden, tolerancia)
                .isFalse();
        assertThat(ConciliacionTresVias.esConciliable(List.of(renglon), tolerancia))
                .as("un renglon fuera de tolerancia hace no conciliable el conjunto")
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 12: Conciliación de tres vías nunca autoriza fuera de tolerancia
    @Property(tries = 1000)
    void cantidadFacturadaMayorQueRecibidaNuncaConcilia(
            @ForAll("preciosOrdenCentavos") long precioOrdenCentavos,
            @ForAll("cantidadesMilesimas") long recibidaMilesimas,
            @ForAll("tolerancias") BigDecimal tolerancia) {

        BigDecimal precioOrden = aMoneda(precioOrdenCentavos);
        BigDecimal recibida = aCantidad(recibidaMilesimas);
        // facturada estrictamente mayor que recibida (una milesima mas).
        BigDecimal facturada = recibida.add(aCantidad(1L));
        // precio exacto (dentro de cualquier tolerancia) para AISLAR la causa de rechazo.
        BigDecimal precioFacturado = precioOrden;

        RenglonConciliacion renglon =
                new RenglonConciliacion(facturada, recibida, precioFacturado, precioOrden);

        assertThat(ConciliacionTresVias.renglonConciliable(renglon, tolerancia))
                .as("cantidad facturada(%s) > recibida(%s) no debe conciliar", facturada, recibida)
                .isFalse();
        assertThat(ConciliacionTresVias.esConciliable(List.of(renglon), tolerancia))
                .as("un renglon con exceso de cantidad hace no conciliable el conjunto")
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 12: Conciliación de tres vías nunca autoriza fuera de tolerancia
    @Property(tries = 1000)
    void dentroDeToleranciaYCantidadCorrectaConcilia(
            @ForAll("preciosOrdenCentavos") long precioOrdenCentavos,
            @ForAll("cantidadesMilesimas") long recibidaMilesimas,
            @ForAll("tolerancias") BigDecimal tolerancia) {

        BigDecimal precioOrden = aMoneda(precioOrdenCentavos);
        BigDecimal recibida = aCantidad(recibidaMilesimas);
        // cantidad facturada <= recibida (aqui igual): correcta.
        BigDecimal facturada = recibida;

        // Desviacion DENTRO de la tolerancia: usamos el maximo permitido redondeado
        // hacia abajo para no rebasarlo por redondeo (<= precioOrden * tolerancia).
        BigDecimal maximaDesviacion = precioOrden.multiply(tolerancia)
                .setScale(ESCALA_MONEDA, RoundingMode.DOWN);
        BigDecimal precioFacturado = precioOrden.add(maximaDesviacion);

        RenglonConciliacion renglon =
                new RenglonConciliacion(facturada, recibida, precioFacturado, precioOrden);

        assertThat(ConciliacionTresVias.renglonConciliable(renglon, tolerancia))
                .as("cantidad correcta y precio dentro de tolerancia debe conciliar")
                .isTrue();
        assertThat(ConciliacionTresVias.esConciliable(List.of(renglon), tolerancia))
                .as("todos los renglones dentro de tolerancia => conjunto conciliable")
                .isTrue();
    }

    // Feature: crm-anuncios-luminosos, Property 12: Conciliación de tres vías nunca autoriza fuera de tolerancia
    @Property(tries = 1000)
    void unRenglonFueraDeToleranciaHaceNoConciliableElConjunto(
            @ForAll @IntRange(min = 1, max = 10) int numConformes,
            @ForAll("preciosOrdenCentavos") long precioOrdenCentavos,
            @ForAll("cantidadesMilesimas") long recibidaMilesimas,
            @ForAll("tolerancias") BigDecimal tolerancia) {

        BigDecimal precioOrden = aMoneda(precioOrdenCentavos);
        BigDecimal recibida = aCantidad(recibidaMilesimas);

        // Renglones conformes (cantidad correcta, precio exacto).
        List<RenglonConciliacion> renglones = new ArrayList<>();
        for (int i = 0; i < numConformes; i++) {
            renglones.add(new RenglonConciliacion(recibida, recibida, precioOrden, precioOrden));
        }
        // Un renglon fuera de tolerancia por precio.
        BigDecimal maximaDesviacion = precioOrden.multiply(tolerancia)
                .setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
        BigDecimal precioFuera = precioOrden.add(maximaDesviacion).add(aMoneda(1L));
        renglones.add(new RenglonConciliacion(recibida, recibida, precioFuera, precioOrden));

        assertThat(ConciliacionTresVias.esConciliable(renglones, tolerancia))
                .as("basta un renglon fuera de tolerancia para no autorizar el pago")
                .isFalse();
    }
}
