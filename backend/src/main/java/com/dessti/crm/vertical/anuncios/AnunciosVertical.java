package com.dessti.crm.vertical.anuncios;

import com.dessti.crm.platform.vertical.ContratoVertical;
import com.dessti.crm.platform.vertical.ItemNavegacionVertical;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Implementacion del {@link ContratoVertical} para el giro
 * <strong>{@code anuncios-luminosos}</strong> (Req 10.2, 4.1).
 *
 * <p>Registra ante el Nucleo, mediante el puerto de plugin de giro, todo lo que
 * aporta el Vertical_Anuncios: su Giro, las claves de modulo con las que se
 * compone el gating por Plan (Req 25.4 del spec base) y por Giro (Req 6), los
 * recursos RBAC atomicos especificos del vertical (Req 7.1) y su metadato de
 * navegacion de frontend (Req 9.2). El {@code RegistroVerticales} descubre este
 * bean en el arranque y lo indexa por su {@link #giro() Giro}.</p>
 *
 * <p><strong>Claves de modulo ({@link #modulos()}).</strong> Se usan las claves
 * reales del catalogo de modulos del Nucleo (migracion {@code V22}, tabla
 * {@code catalogo_modulo}) que amparan los flujos del vertical:
 * <ul>
 *   <li>{@code operacion} — "Ordenes de fabricacion, levantamiento, permisos,
 *       proyectos e inventario": cubre Prueba_Diseno, Orden_Fabricacion,
 *       Levantamiento_Sitio, Permiso_Instalacion, Orden_Trabajo_Instalacion,
 *       Cuadrilla y Proyecto-Sitio.</li>
 *   <li>{@code mantenimiento} — "Contratos de mantenimiento, tickets de servicio
 *       y SLA": cubre Contrato_Mantenimiento y Ticket_Servicio.</li>
 * </ul>
 * Los controladores concretos aun no invocan
 * {@code @autorizador.moduloHabilitado(...)} para estos flujos (ese gating por
 * Giro se cablea al migrar los flujos en las tareas 8.2/8.3); estas claves son
 * las canonicas del catalogo con las que se compondra dicho gating.</p>
 *
 * <p><strong>Recursos RBAC ({@link #recursos()}).</strong> Son los recursos
 * atomicos especificos de anuncios sembrados en la migracion {@code V5}
 * ({@code V5__roles_predefinidos_permisos.sql}): {@code prueba_diseno},
 * {@code levantamiento_sitio}, {@code permiso_instalacion},
 * {@code orden_trabajo_instalacion}, {@code cuadrilla},
 * {@code contrato_mantenimiento} y {@code ticket_servicio}.
 * <strong>Nota (Decision D2, tarea 1.4):</strong> {@code orden_fabricacion} y
 * {@code proyecto} <em>ya no</em> se declaran como recursos del vertical; pasan a
 * ser recursos de <strong>Nucleo</strong> (transversales a todo Giro), por lo que
 * {@code giroDeRecurso(...)} queda vacio para ambos.</p>
 *
 * <p><strong>Navegacion ({@link #navegacion()}).</strong> Las rutas provienen de
 * la navegacion real del ambito {@code /empresa} del frontend
 * ({@code operacion.routes.ts} y {@code mantenimiento.routes.ts}); cada item
 * declara el permiso de lectura ({@code listar}) que lo hace visible. La entrada
 * de Prueba_Diseno usa una ruta coherente ({@code /empresa/operacion/pruebas-diseno})
 * pues este flujo aun no expone ruta propia en el ambito {@code /empresa}; es
 * metadato y se ajustara al migrar el flujo (tareas 8.2/8.3).</p>
 *
 * <p>Trazabilidad: Req 10.2 (declaracion del Giro, modulos, permisos y
 * navegacion), Req 4.1 (Contrato de Vertical).</p>
 */
@Component
public class AnunciosVertical implements ContratoVertical {

    /** Clave canonica del Giro, coherente con la sembrada en {@code V50}. */
    private static final String GIRO = "anuncios-luminosos";

    @Override
    public String giro() {
        return GIRO;
    }

    @Override
    public Set<String> modulos() {
        return Set.of(
                "operacion",
                "mantenimiento");
    }

    @Override
    public Set<String> recursos() {
        // Reclasificacion (spec operacion-produccion-enterprise, tarea 1.4,
        // Decision D2; Req 2.1, 3.1, 16.6): se RETIRAN 'orden_fabricacion' y
        // 'proyecto' del conjunto de recursos del Vertical_Anuncios. Al dejar de
        // declararlos ningun vertical, el RegistroVerticales.giroDeRecurso(...)
        // devuelve vacio para ambos, por lo que el Nucleo los trata como recursos
        // TRANSVERSALES (de Nucleo Comun), aplicables a cualquier Giro. El amarre
        // por giro de esos flujos ya no vive aqui, sino que se retira de la
        // clausula giroCorresponde('operacion') de sus @PreAuthorize (tarea 1.3).
        // Se conservan los demas recursos especificos de anuncios sembrados en V5.
        return Set.of(
                "prueba_diseno",
                "levantamiento_sitio",
                "permiso_instalacion",
                "orden_trabajo_instalacion",
                "cuadrilla",
                "contrato_mantenimiento",
                "ticket_servicio");
    }

    @Override
    public List<ItemNavegacionVertical> navegacion() {
        return List.of(
                new ItemNavegacionVertical(
                        "Pruebas de Diseno",
                        "/empresa/operacion/pruebas-diseno",
                        "palette",
                        "prueba_diseno",
                        "listar"),
                new ItemNavegacionVertical(
                        "Ordenes de Fabricacion",
                        "/empresa/operacion/ordenes-fabricacion",
                        "precision_manufacturing",
                        "orden_fabricacion",
                        "listar"),
                new ItemNavegacionVertical(
                        "Levantamientos de Sitio",
                        "/empresa/operacion/levantamientos",
                        "straighten",
                        "levantamiento_sitio",
                        "listar"),
                new ItemNavegacionVertical(
                        "Permisos de Instalacion",
                        "/empresa/operacion/permisos",
                        "assignment_turned_in",
                        "permiso_instalacion",
                        "listar"),
                new ItemNavegacionVertical(
                        "Ordenes de Trabajo de Instalacion",
                        "/empresa/operacion/instalacion",
                        "construction",
                        "orden_trabajo_instalacion",
                        "listar"),
                new ItemNavegacionVertical(
                        "Proyectos y Sitios",
                        "/empresa/operacion/proyectos",
                        "location_city",
                        "proyecto",
                        "listar"),
                new ItemNavegacionVertical(
                        "Contratos de Mantenimiento",
                        "/empresa/mantenimiento/contratos",
                        "handshake",
                        "contrato_mantenimiento",
                        "listar"),
                new ItemNavegacionVertical(
                        "Tickets de Servicio",
                        "/empresa/mantenimiento/tickets",
                        "support_agent",
                        "ticket_servicio",
                        "listar"));
    }
}
