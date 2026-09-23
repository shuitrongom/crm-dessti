package com.dessti.crm.vertical.manufactura;

import com.dessti.crm.platform.vertical.ContratoVertical;
import com.dessti.crm.platform.vertical.ItemNavegacionVertical;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Implementacion de <strong>demostracion</strong> del {@link ContratoVertical}
 * para el giro <strong>{@code manufactura}</strong> (Req 12.1, 12.2, 12.3).
 *
 * <p>Es la <strong>prueba del modelo enchufable</strong>: un segundo Giro,
 * hermano del Vertical_Anuncios, que encaja en el mismo {@code ContratoVertical}
 * <strong>sin tocar el Vertical_Anuncios ni el Nucleo_Comun</strong> (Req 12.2).
 * El {@code RegistroVerticales} descubre este bean en el arranque y lo indexa por
 * su {@link #giro() Giro}, exactamente igual que a {@code AnunciosVertical}. Como
 * el Registro rechaza el arranque ante Giros o modulos duplicados (Req 4.4), todas
 * las claves aqui declaradas son <strong>unicas</strong> y disjuntas de las de
 * anuncios.</p>
 *
 * <p><strong>Alcance: esqueleto de demostracion.</strong> Este vertical evidencia
 * la ESTRUCTURA del encaje (Giro + modulos + recursos + navegacion) y sus
 * entidades de dominio ({@code Bom}, {@code OrdenProduccion}) son dominio puro sin
 * persistencia; no es un vertical funcional completo (ver
 * {@code package-info.java}).</p>
 *
 * <p><strong>Clave de modulo ({@link #modulos()}).</strong> Se declara la clave
 * <strong>{@code produccion-industrial}</strong>, propia de manufactura y
 * <strong>distinta</strong> de las de anuncios ({@code operacion},
 * {@code mantenimiento}), para no colisionar en el {@code RegistroVerticales}
 * (que fallaria el arranque por modulo duplicado). Es la clave canonica con la que
 * se compondria el gating por Plan/Giro de los flujos de manufactura cuando el
 * vertical se implemente de forma completa.</p>
 *
 * <p><strong>Recursos RBAC ({@link #recursos()}).</strong> Recursos atomicos de
 * <strong>demostracion</strong> propios de manufactura: {@code bom},
 * {@code orden_produccion} y {@code planeacion_produccion}. Son nombres NUEVOS que
 * NO existen en el Vertical_Anuncios ni en el Nucleo, de modo que el
 * {@code ClasificadorRecursosVertical} los resuelve inequivocamente al Giro
 * {@code manufactura} (Req 7.2-7.4). No estan sembrados en BD por ser un esqueleto
 * de prueba del modelo.</p>
 *
 * <p><strong>Navegacion ({@link #navegacion()}).</strong> Items de demostracion con
 * rutas coherentes bajo {@code /empresa/manufactura/...}; cada item declara el
 * permiso de lectura ({@code listar}) que lo hace visible (Req 9.3).</p>
 *
 * <p>Trazabilidad: Req 12.1 (Contrato de Vertical con entidades propias de
 * demostracion), Req 12.2 (registro por el Giro {@code manufactura} sin modificar
 * anuncios ni el Nucleo), Req 12.3 (consumo del Nucleo solo por puertos).</p>
 */
@Component
public class ManufacturaVertical implements ContratoVertical {

    /**
     * Clave canonica del Giro, coherente con la sembrada en la migracion de
     * catalogo del giro {@code manufactura}.
     */
    private static final String GIRO = "manufactura";

    @Override
    public String giro() {
        return GIRO;
    }

    @Override
    public Set<String> modulos() {
        // Clave de modulo UNICA de manufactura, disjunta de anuncios
        // ('operacion', 'mantenimiento') para no romper el arranque por
        // duplicado en RegistroVerticales (Req 4.4).
        return Set.of(
                "produccion-industrial");
    }

    @Override
    public Set<String> recursos() {
        // Recursos RBAC de demostracion, nombres NUEVOS que no colisionan con
        // anuncios ni con el Nucleo (Req 7.2-7.4).
        return Set.of(
                "bom",
                "orden_produccion",
                "planeacion_produccion");
    }

    @Override
    public List<ItemNavegacionVertical> navegacion() {
        // Items de navegacion de demostracion bajo /empresa/manufactura/...
        return List.of(
                new ItemNavegacionVertical(
                        "Listas de Materiales (BOM)",
                        "/empresa/manufactura/bom",
                        "account_tree",
                        "bom",
                        "listar"),
                new ItemNavegacionVertical(
                        "Ordenes de Produccion",
                        "/empresa/manufactura/ordenes-produccion",
                        "precision_manufacturing",
                        "orden_produccion",
                        "listar"),
                new ItemNavegacionVertical(
                        "Planeacion de Produccion",
                        "/empresa/manufactura/planeacion",
                        "calendar_month",
                        "planeacion_produccion",
                        "listar"));
    }
}
