package com.dessti.crm.platform.statemachine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import com.dessti.crm.comercial.cotizacion.domain.EstadoCotizacion;
import com.dessti.crm.comercial.oportunidad.domain.EtapaOportunidad;
import com.dessti.crm.compras.factura.domain.EstadoFacturaProveedor;
import com.dessti.crm.compras.ordencompra.domain.EstadoOrdenCompra;
import com.dessti.crm.compras.requisicion.domain.EstadoRequisicionCompra;
import com.dessti.crm.facturacion.factura.domain.EstadoFactura;
import com.dessti.crm.vertical.anuncios.mantenimiento.domain.EstadoTicketServicio;
import com.dessti.crm.vertical.anuncios.instalacion.domain.EstadoOrdenTrabajoInstalacion;
import com.dessti.crm.operacion.produccion.domain.EstadoOrdenFabricacion;
import com.dessti.crm.vertical.anuncios.permiso.domain.EstadoPermisoInstalacion;
import com.dessti.crm.rhnomina.nomina.domain.EstadoNomina;
import com.dessti.crm.social.domain.EstadoPublicacion;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 5: Transiciones
 * de estado validas (maquinas de estado)</strong> (design.md, seccion
 * <em>Correctness Properties</em>), transversal a las <strong>doce</strong>
 * maquinas de estado dirigidas por eventos del sistema.
 *
 * <p><strong>Enunciado.</strong> Para cualquier maquina de estado del sistema
 * (Cotizacion, Orden_Fabricacion, Oportunidad, Permiso_Instalacion,
 * Orden_Trabajo_Instalacion, Ticket_Servicio, Requisicion_Compra, Orden_Compra,
 * Factura_Proveedor, Factura CFDI, Nomina y Publicacion_Social) y para cualquier
 * par {@code (estadoActual, estadoDestino)} tomado de los valores de esa
 * maquina, la transicion se acepta <em>si y solo si</em> pertenece al conjunto
 * de transiciones definidas para esa maquina; toda transicion que parta de un
 * estado final se rechaza y el estado se conserva.</p>
 *
 * <p><strong>Oraculo independiente.</strong> La expectativa (que par
 * {@code (actual, destino)} es valido) se declara aqui como una tabla
 * <em>hardcoded</em> derivada directamente de las tablas de transicion de
 * design.md (seccion <em>State Machines</em>), <strong>sin</strong> consultar la
 * {@code MaquinaEstados} ni el enum bajo prueba. De este modo la prueba es un
 * oraculo genuino: contrasta la implementacion de produccion
 * ({@code puedeTransicionarA}, que delega en {@link MaquinaEstados}) contra una
 * especificacion redundante e independiente. Un desajuste entre ambas —tanto por
 * una transicion permitida de mas como de menos— hace fallar la propiedad.</p>
 *
 * <p><strong>Estados finales.</strong> Un estado es final cuando el oraculo no
 * declara ninguna transicion saliente para el. La propiedad verifica que
 * {@code esFinal()} coincide con esa definicion y que ninguna transicion que
 * parta de un estado final se acepta, para <em>todos</em> los destinos posibles
 * (Req x.6/x.7 correspondientes).</p>
 *
 * <p>La prueba es de dominio puro: no arranca contexto de Spring, es rapida y
 * determinista. Los generadores recorren el espacio completo de pares por
 * maquina, por lo que a lo largo de las iteraciones se cubren exhaustivamente
 * todas las combinaciones {@code (actual, destino)}.</p>
 */
class MaquinaEstadosProperty5Test {

    // ----------------------------------------------------------------------
    // Verificador generico reutilizable por las doce maquinas
    // ----------------------------------------------------------------------

    /**
     * Comprueba, para un par {@code (actual, destino)} de una maquina cualquiera,
     * las dos vertientes de la Property 5:
     * <ol>
     *   <li><strong>Aceptacion sii en la tabla:</strong> {@code puedeTransicionarA}
     *       devuelve {@code true} exactamente cuando el par pertenece al oraculo.</li>
     *   <li><strong>Estados finales:</strong> si {@code actual} es final segun el
     *       oraculo, la implementacion lo reporta como final y rechaza la
     *       transicion hacia cualquier destino.</li>
     * </ol>
     *
     * @param actual              estado de partida generado.
     * @param destino             estado destino generado.
     * @param puedeTransicionar   resultado real de {@code actual.puedeTransicionarA(destino)}.
     * @param esFinalActual       resultado real de {@code actual.esFinal()}.
     * @param oraculoPermitido    {@code true} si el oraculo declara valido el par.
     * @param oraculoActualEsFinal {@code true} si el oraculo declara final a {@code actual}.
     */
    private static <E extends Enum<E>> void verificar(
            E actual, E destino,
            boolean puedeTransicionar, boolean esFinalActual,
            boolean oraculoPermitido, boolean oraculoActualEsFinal) {

        // (1) Aceptacion si y solo si el par pertenece al conjunto declarado.
        assertThat(puedeTransicionar)
                .as("%s -> %s se acepta sii pertenece a la tabla de transiciones",
                        actual, destino)
                .isEqualTo(oraculoPermitido);

        // (2a) La nocion de estado final coincide con el oraculo.
        assertThat(esFinalActual)
                .as("%s es final sii no tiene transiciones salientes declaradas", actual)
                .isEqualTo(oraculoActualEsFinal);

        // (2b) Desde un estado final no se acepta ninguna transicion.
        if (oraculoActualEsFinal) {
            assertThat(puedeTransicionar)
                    .as("desde el estado final %s no se admite ninguna transicion (-> %s)",
                            actual, destino)
                    .isFalse();
        }
    }

    // ======================================================================
    // 1. Cotizacion (Req 6.6, 6.7)
    // ======================================================================

    private static boolean oraculoCotizacion(EstadoCotizacion a, EstadoCotizacion d) {
        return switch (a) {
            case BORRADOR -> d == EstadoCotizacion.ENVIADA;
            case ENVIADA -> d == EstadoCotizacion.APROBADA || d == EstadoCotizacion.RECHAZADA;
            case APROBADA, RECHAZADA -> false; // finales
        };
    }

    private static final Set<EstadoCotizacion> COTIZACION_FINALES =
            EnumSet.of(EstadoCotizacion.APROBADA, EstadoCotizacion.RECHAZADA);

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1000)
    void cotizacionAceptaTransicionSiiEnTabla(
            @ForAll("cotizacion") EstadoCotizacion actual,
            @ForAll("cotizacion") EstadoCotizacion destino) {

        verificar(actual, destino,
                actual.puedeTransicionarA(destino), actual.esFinal(),
                oraculoCotizacion(actual, destino), COTIZACION_FINALES.contains(actual));
    }

    @Provide
    Arbitrary<EstadoCotizacion> cotizacion() {
        return Arbitraries.of(EstadoCotizacion.class);
    }

    // ======================================================================
    // 2. Orden de Fabricacion (Req 7.5, 7.6)
    // ======================================================================

    private static boolean oraculoOrdenFabricacion(EstadoOrdenFabricacion a, EstadoOrdenFabricacion d) {
        return switch (a) {
            case PENDIENTE -> d == EstadoOrdenFabricacion.EN_PRODUCCION
                    || d == EstadoOrdenFabricacion.CANCELADA;
            case EN_PRODUCCION -> d == EstadoOrdenFabricacion.TERMINADA
                    || d == EstadoOrdenFabricacion.CANCELADA;
            case TERMINADA, CANCELADA -> false; // finales
        };
    }

    private static final Set<EstadoOrdenFabricacion> ORDEN_FABRICACION_FINALES =
            EnumSet.of(EstadoOrdenFabricacion.TERMINADA, EstadoOrdenFabricacion.CANCELADA);

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1000)
    void ordenFabricacionAceptaTransicionSiiEnTabla(
            @ForAll("ordenFabricacion") EstadoOrdenFabricacion actual,
            @ForAll("ordenFabricacion") EstadoOrdenFabricacion destino) {

        verificar(actual, destino,
                actual.puedeTransicionarA(destino), actual.esFinal(),
                oraculoOrdenFabricacion(actual, destino),
                ORDEN_FABRICACION_FINALES.contains(actual));
    }

    @Provide
    Arbitrary<EstadoOrdenFabricacion> ordenFabricacion() {
        return Arbitraries.of(EstadoOrdenFabricacion.class);
    }

    // ======================================================================
    // 3. Oportunidad / Pipeline (Req 14.3, 14.4)
    // ======================================================================

    private static boolean oraculoOportunidad(EtapaOportunidad a, EtapaOportunidad d) {
        return switch (a) {
            case NUEVO -> d == EtapaOportunidad.CALIFICADO || d == EtapaOportunidad.PERDIDO;
            case CALIFICADO -> d == EtapaOportunidad.PROPUESTA || d == EtapaOportunidad.PERDIDO;
            case PROPUESTA -> d == EtapaOportunidad.NEGOCIACION || d == EtapaOportunidad.PERDIDO;
            case NEGOCIACION -> d == EtapaOportunidad.GANADO || d == EtapaOportunidad.PERDIDO;
            case GANADO, PERDIDO -> false; // finales
        };
    }

    private static final Set<EtapaOportunidad> OPORTUNIDAD_FINALES =
            EnumSet.of(EtapaOportunidad.GANADO, EtapaOportunidad.PERDIDO);

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1000)
    void oportunidadAceptaTransicionSiiEnTabla(
            @ForAll("oportunidad") EtapaOportunidad actual,
            @ForAll("oportunidad") EtapaOportunidad destino) {

        verificar(actual, destino,
                actual.puedeTransicionarA(destino), actual.esFinal(),
                oraculoOportunidad(actual, destino), OPORTUNIDAD_FINALES.contains(actual));
    }

    @Provide
    Arbitrary<EtapaOportunidad> oportunidad() {
        return Arbitraries.of(EtapaOportunidad.class);
    }

    // ======================================================================
    // 4. Permiso de Instalacion (Req 17.2, 17.3)
    // ======================================================================

    private static boolean oraculoPermiso(EstadoPermisoInstalacion a, EstadoPermisoInstalacion d) {
        return switch (a) {
            case SOLICITADO -> d == EstadoPermisoInstalacion.APROBADO
                    || d == EstadoPermisoInstalacion.RECHAZADO;
            case APROBADO, RECHAZADO -> false; // finales
        };
    }

    private static final Set<EstadoPermisoInstalacion> PERMISO_FINALES =
            EnumSet.of(EstadoPermisoInstalacion.APROBADO, EstadoPermisoInstalacion.RECHAZADO);

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1000)
    void permisoAceptaTransicionSiiEnTabla(
            @ForAll("permiso") EstadoPermisoInstalacion actual,
            @ForAll("permiso") EstadoPermisoInstalacion destino) {

        verificar(actual, destino,
                actual.puedeTransicionarA(destino), actual.esFinal(),
                oraculoPermiso(actual, destino), PERMISO_FINALES.contains(actual));
    }

    @Provide
    Arbitrary<EstadoPermisoInstalacion> permiso() {
        return Arbitraries.of(EstadoPermisoInstalacion.class);
    }

    // ======================================================================
    // 5. Orden de Trabajo de Instalacion (Req 19.5)
    // ======================================================================

    private static boolean oraculoOrdenTrabajo(EstadoOrdenTrabajoInstalacion a,
                                               EstadoOrdenTrabajoInstalacion d) {
        return switch (a) {
            case PROGRAMADA -> d == EstadoOrdenTrabajoInstalacion.EN_CURSO
                    || d == EstadoOrdenTrabajoInstalacion.CANCELADA;
            case EN_CURSO -> d == EstadoOrdenTrabajoInstalacion.COMPLETADA
                    || d == EstadoOrdenTrabajoInstalacion.CANCELADA;
            case COMPLETADA, CANCELADA -> false; // finales
        };
    }

    private static final Set<EstadoOrdenTrabajoInstalacion> ORDEN_TRABAJO_FINALES =
            EnumSet.of(EstadoOrdenTrabajoInstalacion.COMPLETADA,
                    EstadoOrdenTrabajoInstalacion.CANCELADA);

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1000)
    void ordenTrabajoAceptaTransicionSiiEnTabla(
            @ForAll("ordenTrabajo") EstadoOrdenTrabajoInstalacion actual,
            @ForAll("ordenTrabajo") EstadoOrdenTrabajoInstalacion destino) {

        verificar(actual, destino,
                actual.puedeTransicionarA(destino), actual.esFinal(),
                oraculoOrdenTrabajo(actual, destino), ORDEN_TRABAJO_FINALES.contains(actual));
    }

    @Provide
    Arbitrary<EstadoOrdenTrabajoInstalacion> ordenTrabajo() {
        return Arbitraries.of(EstadoOrdenTrabajoInstalacion.class);
    }

    // ======================================================================
    // 6. Ticket de Servicio (Req 20.4, 20.5)
    // ======================================================================

    private static boolean oraculoTicket(EstadoTicketServicio a, EstadoTicketServicio d) {
        return switch (a) {
            case ABIERTO -> d == EstadoTicketServicio.ASIGNADO;
            case ASIGNADO -> d == EstadoTicketServicio.EN_PROCESO;
            case EN_PROCESO -> d == EstadoTicketServicio.RESUELTO;
            case RESUELTO -> d == EstadoTicketServicio.CERRADO;
            case CERRADO -> false; // final
        };
    }

    private static final Set<EstadoTicketServicio> TICKET_FINALES =
            EnumSet.of(EstadoTicketServicio.CERRADO);

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1000)
    void ticketAceptaTransicionSiiEnTabla(
            @ForAll("ticket") EstadoTicketServicio actual,
            @ForAll("ticket") EstadoTicketServicio destino) {

        verificar(actual, destino,
                actual.puedeTransicionarA(destino), actual.esFinal(),
                oraculoTicket(actual, destino), TICKET_FINALES.contains(actual));
    }

    @Provide
    Arbitrary<EstadoTicketServicio> ticket() {
        return Arbitraries.of(EstadoTicketServicio.class);
    }

    // ======================================================================
    // 7. Requisicion de Compra (Req 30.3, 30.4)
    // ======================================================================

    private static boolean oraculoRequisicion(EstadoRequisicionCompra a, EstadoRequisicionCompra d) {
        return switch (a) {
            case BORRADOR -> d == EstadoRequisicionCompra.ENVIADA;
            case ENVIADA -> d == EstadoRequisicionCompra.APROBADA
                    || d == EstadoRequisicionCompra.RECHAZADA;
            case APROBADA, RECHAZADA, CANCELADA -> false; // finales (CANCELADA sin transiciones)
        };
    }

    private static final Set<EstadoRequisicionCompra> REQUISICION_FINALES =
            EnumSet.of(EstadoRequisicionCompra.APROBADA, EstadoRequisicionCompra.RECHAZADA,
                    EstadoRequisicionCompra.CANCELADA);

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1000)
    void requisicionAceptaTransicionSiiEnTabla(
            @ForAll("requisicion") EstadoRequisicionCompra actual,
            @ForAll("requisicion") EstadoRequisicionCompra destino) {

        verificar(actual, destino,
                actual.puedeTransicionarA(destino), actual.esFinal(),
                oraculoRequisicion(actual, destino), REQUISICION_FINALES.contains(actual));
    }

    @Provide
    Arbitrary<EstadoRequisicionCompra> requisicion() {
        return Arbitraries.of(EstadoRequisicionCompra.class);
    }

    // ======================================================================
    // 8. Orden de Compra (Req 31.6, 31.7)
    // ======================================================================

    private static boolean oraculoOrdenCompra(EstadoOrdenCompra a, EstadoOrdenCompra d) {
        return switch (a) {
            case ABIERTA -> d == EstadoOrdenCompra.RECIBIDA_PARCIAL
                    || d == EstadoOrdenCompra.CANCELADA;
            case RECIBIDA_PARCIAL -> d == EstadoOrdenCompra.RECIBIDA_TOTAL
                    || d == EstadoOrdenCompra.CANCELADA;
            case RECIBIDA_TOTAL -> d == EstadoOrdenCompra.CERRADA;
            case CERRADA, CANCELADA -> false; // finales
        };
    }

    private static final Set<EstadoOrdenCompra> ORDEN_COMPRA_FINALES =
            EnumSet.of(EstadoOrdenCompra.CERRADA, EstadoOrdenCompra.CANCELADA);

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1000)
    void ordenCompraAceptaTransicionSiiEnTabla(
            @ForAll("ordenCompra") EstadoOrdenCompra actual,
            @ForAll("ordenCompra") EstadoOrdenCompra destino) {

        verificar(actual, destino,
                actual.puedeTransicionarA(destino), actual.esFinal(),
                oraculoOrdenCompra(actual, destino), ORDEN_COMPRA_FINALES.contains(actual));
    }

    @Provide
    Arbitrary<EstadoOrdenCompra> ordenCompra() {
        return Arbitraries.of(EstadoOrdenCompra.class);
    }

    // ======================================================================
    // 9. Factura de Proveedor (Req 33.6)
    // ======================================================================

    private static boolean oraculoFacturaProveedor(EstadoFacturaProveedor a, EstadoFacturaProveedor d) {
        return switch (a) {
            case REGISTRADA -> d == EstadoFacturaProveedor.CONCILIADA
                    || d == EstadoFacturaProveedor.DISCREPANCIA;
            case CONCILIADA -> d == EstadoFacturaProveedor.PAGADA;
            case DISCREPANCIA, PAGADA -> false; // finales
        };
    }

    private static final Set<EstadoFacturaProveedor> FACTURA_PROVEEDOR_FINALES =
            EnumSet.of(EstadoFacturaProveedor.DISCREPANCIA, EstadoFacturaProveedor.PAGADA);

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1000)
    void facturaProveedorAceptaTransicionSiiEnTabla(
            @ForAll("facturaProveedor") EstadoFacturaProveedor actual,
            @ForAll("facturaProveedor") EstadoFacturaProveedor destino) {

        verificar(actual, destino,
                actual.puedeTransicionarA(destino), actual.esFinal(),
                oraculoFacturaProveedor(actual, destino),
                FACTURA_PROVEEDOR_FINALES.contains(actual));
    }

    @Provide
    Arbitrary<EstadoFacturaProveedor> facturaProveedor() {
        return Arbitraries.of(EstadoFacturaProveedor.class);
    }

    // ======================================================================
    // 10. Factura CFDI (Req 35.7)
    // ======================================================================

    private static boolean oraculoFactura(EstadoFactura a, EstadoFactura d) {
        return switch (a) {
            case BORRADOR -> d == EstadoFactura.TIMBRADA;
            case TIMBRADA -> d == EstadoFactura.CANCELACION_EN_PROCESO;
            case CANCELACION_EN_PROCESO -> d == EstadoFactura.CANCELADA;
            case CANCELADA -> false; // final
        };
    }

    private static final Set<EstadoFactura> FACTURA_FINALES =
            EnumSet.of(EstadoFactura.CANCELADA);

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1000)
    void facturaAceptaTransicionSiiEnTabla(
            @ForAll("factura") EstadoFactura actual,
            @ForAll("factura") EstadoFactura destino) {

        verificar(actual, destino,
                actual.puedeTransicionarA(destino), actual.esFinal(),
                oraculoFactura(actual, destino), FACTURA_FINALES.contains(actual));
    }

    @Provide
    Arbitrary<EstadoFactura> factura() {
        return Arbitraries.of(EstadoFactura.class);
    }

    // ======================================================================
    // 11. Nomina (Req 41.5, 41.6)
    // ======================================================================

    private static boolean oraculoNomina(EstadoNomina a, EstadoNomina d) {
        return switch (a) {
            case BORRADOR -> d == EstadoNomina.CALCULADA;
            case CALCULADA -> d == EstadoNomina.AUTORIZADA;
            case AUTORIZADA -> d == EstadoNomina.TIMBRADA;
            case TIMBRADA -> d == EstadoNomina.PAGADA;
            case PAGADA -> false; // final
        };
    }

    private static final Set<EstadoNomina> NOMINA_FINALES =
            EnumSet.of(EstadoNomina.PAGADA);

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1000)
    void nominaAceptaTransicionSiiEnTabla(
            @ForAll("nomina") EstadoNomina actual,
            @ForAll("nomina") EstadoNomina destino) {

        verificar(actual, destino,
                actual.puedeTransicionarA(destino), actual.esFinal(),
                oraculoNomina(actual, destino), NOMINA_FINALES.contains(actual));
    }

    @Provide
    Arbitrary<EstadoNomina> nomina() {
        return Arbitraries.of(EstadoNomina.class);
    }

    // ======================================================================
    // 12. Publicacion Social (Req 65.3, 65.4)
    // ======================================================================

    private static boolean oraculoPublicacion(EstadoPublicacion a, EstadoPublicacion d) {
        return switch (a) {
            case BORRADOR -> d == EstadoPublicacion.PROGRAMADA;
            case PROGRAMADA -> d == EstadoPublicacion.PUBLICADA || d == EstadoPublicacion.FALLIDA;
            case PUBLICADA, FALLIDA -> false; // finales
        };
    }

    private static final Set<EstadoPublicacion> PUBLICACION_FINALES =
            EnumSet.of(EstadoPublicacion.PUBLICADA, EstadoPublicacion.FALLIDA);

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1000)
    void publicacionAceptaTransicionSiiEnTabla(
            @ForAll("publicacion") EstadoPublicacion actual,
            @ForAll("publicacion") EstadoPublicacion destino) {

        verificar(actual, destino,
                actual.puedeTransicionarA(destino), actual.esFinal(),
                oraculoPublicacion(actual, destino), PUBLICACION_FINALES.contains(actual));
    }

    @Provide
    Arbitrary<EstadoPublicacion> publicacion() {
        return Arbitraries.of(EstadoPublicacion.class);
    }

    // ----------------------------------------------------------------------
    // Coherencia estructural: el oraculo cubre exactamente los estados finales
    // ----------------------------------------------------------------------

    /**
     * Verifica, para una maquina, que la definicion de "final" del oraculo
     * (ninguna transicion saliente hacia ningun destino) coincide con el conjunto
     * de finales declarado explicitamente, cerrando el circulo entre ambas
     * vertientes del oraculo. No depende de la implementacion de produccion.
     */
    private static <E extends Enum<E>> void oraculoCoherente(
            E[] valores, BiPredicate<E, E> oraculo, Predicate<E> esFinal) {
        for (E actual : valores) {
            boolean tieneSalida = false;
            for (E destino : valores) {
                if (oraculo.test(actual, destino)) {
                    tieneSalida = true;
                    break;
                }
            }
            assertThat(!tieneSalida)
                    .as("el oraculo declara final a %s sii no tiene transiciones salientes",
                            actual)
                    .isEqualTo(esFinal.test(actual));
        }
    }

    // Feature: crm-anuncios-luminosos, Property 5: Para cualquier máquina de estado del sistema (Cotización, Orden_Fabricación, Oportunidad, Permiso_Instalación, Orden_Trabajo_Instalación, Ticket_Servicio, Requisición_Compra, Orden_Compra, Factura_Proveedor, Factura CFDI, Nómina y Publicación_Social) y para cualquier par (estado actual, evento), la transición se acepta si y solo si pertenece al conjunto de transiciones definidas para esa máquina; toda transición que parta de un estado final se rechaza y el estado se conserva sin cambios.
    @Property(tries = 1)
    void oraculosInternamenteCoherentes() {
        oraculoCoherente(EstadoCotizacion.values(),
                MaquinaEstadosProperty5Test::oraculoCotizacion, COTIZACION_FINALES::contains);
        oraculoCoherente(EstadoOrdenFabricacion.values(),
                MaquinaEstadosProperty5Test::oraculoOrdenFabricacion,
                ORDEN_FABRICACION_FINALES::contains);
        oraculoCoherente(EtapaOportunidad.values(),
                MaquinaEstadosProperty5Test::oraculoOportunidad, OPORTUNIDAD_FINALES::contains);
        oraculoCoherente(EstadoPermisoInstalacion.values(),
                MaquinaEstadosProperty5Test::oraculoPermiso, PERMISO_FINALES::contains);
        oraculoCoherente(EstadoOrdenTrabajoInstalacion.values(),
                MaquinaEstadosProperty5Test::oraculoOrdenTrabajo, ORDEN_TRABAJO_FINALES::contains);
        oraculoCoherente(EstadoTicketServicio.values(),
                MaquinaEstadosProperty5Test::oraculoTicket, TICKET_FINALES::contains);
        oraculoCoherente(EstadoRequisicionCompra.values(),
                MaquinaEstadosProperty5Test::oraculoRequisicion, REQUISICION_FINALES::contains);
        oraculoCoherente(EstadoOrdenCompra.values(),
                MaquinaEstadosProperty5Test::oraculoOrdenCompra, ORDEN_COMPRA_FINALES::contains);
        oraculoCoherente(EstadoFacturaProveedor.values(),
                MaquinaEstadosProperty5Test::oraculoFacturaProveedor,
                FACTURA_PROVEEDOR_FINALES::contains);
        oraculoCoherente(EstadoFactura.values(),
                MaquinaEstadosProperty5Test::oraculoFactura, FACTURA_FINALES::contains);
        oraculoCoherente(EstadoNomina.values(),
                MaquinaEstadosProperty5Test::oraculoNomina, NOMINA_FINALES::contains);
        oraculoCoherente(EstadoPublicacion.values(),
                MaquinaEstadosProperty5Test::oraculoPublicacion, PUBLICACION_FINALES::contains);
    }
}
