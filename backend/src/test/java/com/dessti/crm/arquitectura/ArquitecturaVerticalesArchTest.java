package com.dessti.crm.arquitectura;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Pruebas de arquitectura (ArchUnit) que blindan la direccion de las
 * dependencias entre el <strong>Núcleo</strong> y los <strong>Verticales</strong>
 * de la plataforma multigiro.
 *
 * <p>El Núcleo reside bajo {@code com.dessti.crm.platform..} (junto a los
 * módulos de negocio del núcleo: comercial, operacion, compras, facturacion,
 * contabilidad, rhnomina, tesoreria, activosfijos, mantenimiento, portalcliente,
 * estrategia, presupuestos, notificaciones, reportesbi, calidad, social). Los
 * Verticales enchufables residen bajo {@code com.dessti.crm.vertical..}.</p>
 *
 * <p><strong>Ojo con el contrato:</strong> el puerto que define el contrato de
 * vertical vive en {@code com.dessti.crm.platform.vertical} (NÚCLEO). No debe
 * confundirse con {@code com.dessti.crm.vertical..} (implementaciones concretas
 * de cada vertical). La regla de esta clase apunta a este último paquete, de
 * modo que el contrato (parte del núcleo) queda del lado permitido.</p>
 *
 * <h2>Reglas verificadas</h2>
 * <ul>
 *   <li><strong>(6.6)</strong> Ninguna clase de {@code com.dessti.crm.platform..}
 *   debe depender de clases de {@code com.dessti.crm.vertical..}. El núcleo es
 *   autónomo y los verticales se enchufan a él a través del puerto
 *   {@code ContratoVertical}, nunca al revés (Req 4.2, 5.4).</li>
 *   <li><strong>(8.6)</strong> Aislamiento <em>entre</em> verticales: ninguna
 *   clase de {@code com.dessti.crm.vertical.anuncios..} depende de
 *   {@code com.dessti.crm.vertical.manufactura..} ni viceversa. Cada giro es un
 *   plugin independiente que solo conoce el contrato del núcleo (Req 4.6).</li>
 *   <li><strong>(8.6)</strong> Aislamiento de la <em>persistencia interna</em>
 *   del núcleo: ninguna clase de {@code com.dessti.crm.vertical..} depende de la
 *   persistencia interna ({@code ..adapter.out.persistence..}) de otro módulo.
 *   El acoplamiento legítimo del vertical hacia el núcleo es por <em>puertos</em>
 *   ({@code ..application..}), nunca por repositorios/entidades internas
 *   (Req 4.5, 10.3).</li>
 *   <li><strong>(8.6)</strong> Generalización de núcleo→vertical: <em>ninguna</em>
 *   clase fuera de {@code com.dessti.crm.vertical..} debe depender de clases del
 *   vertical (no solo {@code platform..}). El contrato/registro vive en
 *   {@code platform.vertical} y recibe las implementaciones por inyección de
 *   {@code List<ContratoVertical>}, sin importar {@code vertical..} (Req 10.5).</li>
 * </ul>
 *
 * <h2>Alcance: solo código de producción ({@code DoNotIncludeTests})</h2>
 * <p>La regla se evalúa <strong>únicamente sobre clases de producción</strong>
 * ({@code @AnalyzeClasses(importOptions = DoNotIncludeTests.class)}). Las reglas
 * de arquitectura protegen la direccionalidad de las dependencias del
 * <em>sistema</em>, no de las pruebas: un test del núcleo (p. ej. el de la
 * máquina de estados transversal) puede usar legítimamente los enum de estado de
 * un vertical como datos/oráculo de prueba sin que ello constituya un
 * acoplamiento arquitectónico real del núcleo hacia el vertical. Excluir los
 * tests evita ese falso positivo y mantiene la regla centrada en el código que
 * se despliega.</p>
 *
 * <h2>Sobre {@code allowEmptyShould(true)}</h2>
 * <p>Se conserva {@code allowEmptyShould(true)} por robustez: si en algún
 * momento no hubiera clases de producción en {@code com.dessti.crm.vertical..}
 * (p. ej. antes de enchufar cualquier vertical), la regla pasa trivialmente en
 * lugar de fallar por "empty should". Con los verticales ya presentes (bloque 8),
 * la regla protege de verdad la dependencia núcleo→vertical en producción.</p>
 *
 * <h2>Nota sobre el aislamiento entre verticales (Req 4.6)</h2>
 * <p>Hoy solo existe el vertical de anuncios; el de manufactura llegará en el
 * bloque 12. La regla entre-verticales se expresa como la pareja explícita
 * {@code anuncios ↔ manufactura} (la más clara y directa dado el catálogo de
 * giros del diseño) y se marca con {@code allowEmptyShould(true)}: mientras
 * {@code vertical.manufactura..} no exista, la regla pasa trivialmente y, en
 * cuanto el bloque 12 lo cree, blindará el aislamiento sin tocar este test.</p>
 *
 * @see com.dessti.crm.platform.vertical.ContratoVertical
 */
@AnalyzeClasses(packages = "com.dessti.crm", importOptions = DoNotIncludeTests.class)
class ArquitecturaVerticalesArchTest {

    /**
     * Núcleo → Vertical prohibido: ninguna clase del núcleo
     * ({@code com.dessti.crm.platform..}) puede depender de clases de los
     * verticales ({@code com.dessti.crm.vertical..}).
     *
     * <p>Se permite regla vacía ({@code allowEmptyShould(true)}) porque los
     * verticales aún no existen; la regla pasa trivialmente hasta que el bloque 8
     * los cree, y a partir de entonces protege la direccionalidad de la
     * dependencia.</p>
     *
     * <p>Validates: Requirements 4.2, 5.4</p>
     */
    @ArchTest
    static final ArchRule el_nucleo_no_depende_de_los_verticales =
            noClasses()
                    .that().resideInAPackage("com.dessti.crm.platform..")
                    .should().dependOnClassesThat().resideInAPackage("com.dessti.crm.vertical..")
                    .because("El Núcleo es autónomo: los Verticales se enchufan al Núcleo "
                            + "a través del puerto ContratoVertical, nunca al revés (Req 4.2, 5.4)")
                    .allowEmptyShould(true);

    /**
     * Aislamiento <strong>entre verticales</strong>: ninguna clase del vertical
     * de anuncios ({@code com.dessti.crm.vertical.anuncios..}) puede depender de
     * clases del vertical de manufactura ({@code com.dessti.crm.vertical.manufactura..}).
     *
     * <p>Cada giro es un plugin autónomo: comparte con los demás únicamente el
     * contrato del núcleo ({@code platform.vertical.ContratoVertical}) y los
     * puertos del núcleo, jamás las implementaciones de otro vertical. Se expresa
     * como la pareja explícita {@code anuncios → manufactura} (con su recíproca
     * más abajo) por claridad frente al catálogo de giros del diseño.</p>
     *
     * <p>El objetivo puede estar vacío hoy ({@code vertical.manufactura..} aún no
     * existe; llega en el bloque 12), por eso {@code allowEmptyShould(true)}: la
     * regla pasa trivialmente ahora y blindará el aislamiento en cuanto el
     * esqueleto de manufactura se cree, sin necesidad de tocar este test.</p>
     *
     * <p>Validates: Requirements 4.6</p>
     */
    @ArchTest
    static final ArchRule anuncios_no_depende_de_manufactura =
            noClasses()
                    .that().resideInAPackage("com.dessti.crm.vertical.anuncios..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.dessti.crm.vertical.manufactura..")
                    .because("Cada Vertical es un plugin independiente: anuncios no debe "
                            + "conocer las implementaciones de manufactura; solo comparten el "
                            + "Contrato_Vertical y los puertos del Núcleo (Req 4.6)")
                    .allowEmptyShould(true);

    /**
     * Aislamiento <strong>entre verticales</strong> (recíproca): ninguna clase
     * del vertical de manufactura ({@code com.dessti.crm.vertical.manufactura..})
     * puede depender de clases del vertical de anuncios
     * ({@code com.dessti.crm.vertical.anuncios..}).
     *
     * <p>Complementa {@link #anuncios_no_depende_de_manufactura} para prohibir el
     * acoplamiento en ambos sentidos. Hoy el paquete origen ({@code manufactura..})
     * está vacío, por lo que no hay clases que evaluar y
     * {@code allowEmptyShould(true)} evita el fallo por "empty should"; la regla
     * cobra vigencia con el bloque 12.</p>
     *
     * <p>Validates: Requirements 4.6</p>
     */
    @ArchTest
    static final ArchRule manufactura_no_depende_de_anuncios =
            noClasses()
                    .that().resideInAPackage("com.dessti.crm.vertical.manufactura..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.dessti.crm.vertical.anuncios..")
                    .because("Cada Vertical es un plugin independiente: manufactura no debe "
                            + "conocer las implementaciones de anuncios; solo comparten el "
                            + "Contrato_Vertical y los puertos del Núcleo (Req 4.6)")
                    .allowEmptyShould(true);

    /**
     * Predicado: clases de <strong>persistencia interna de OTRO módulo</strong>,
     * es decir, que residen en algún paquete {@code ..adapter.out.persistence..}
     * pero <em>fuera</em> de {@code com.dessti.crm.vertical..}.
     *
     * <p>Se construye combinando dos predicados de ArchUnit: "reside en un paquete
     * {@code ..adapter.out.persistence..}" Y "no reside en {@code vertical..}".
     * Así la regla que lo usa prohíbe al vertical tocar la persistencia interna de
     * cualquier módulo del Núcleo (comercial, facturacion, etc.) sin tener que
     * enumerarlos uno a uno, y sin castigar la persistencia <em>propia</em> del
     * vertical (sus adapters {@code vertical.anuncios..adapter.out.persistence..}).</p>
     */
    private static final DescribedPredicate<JavaClass> persistencia_interna_de_otro_modulo =
            resideInAPackage("..adapter.out.persistence..")
                    .and(not(resideInAPackage("com.dessti.crm.vertical..")))
                    .as("persistencia interna (..adapter.out.persistence..) de otro módulo "
                            + "fuera de com.dessti.crm.vertical..");

    /**
     * El <strong>Vertical no accede a la persistencia interna del Núcleo</strong>:
     * ninguna clase de {@code com.dessti.crm.vertical..} debe depender de clases
     * que residan en la persistencia interna ({@code ..adapter.out.persistence..})
     * de otro módulo.
     *
     * <p><strong>Enfoque elegido</strong>: en lugar de enumerar cada módulo del
     * Núcleo (frágil y siempre incompleto), la regla usa un predicado genérico
     * ({@link #persistencia_interna_de_otro_modulo}) que captura cualquier paquete
     * {@code ..adapter.out.persistence..} que no pertenezca al propio vertical.
     * Es robusto ante nuevos módulos del Núcleo y ante nuevos verticales.</p>
     *
     * <p>El acoplamiento legítimo del vertical hacia el Núcleo es por
     * <strong>puertos de aplicación</strong> ({@code ..application..}); p. ej. los
     * adapters del vertical de anuncios sí pueden depender de
     * {@code com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort}.
     * Esta regla apunta <em>exclusivamente</em> a {@code ..adapter.out.persistence..},
     * de modo que esos puertos en {@code ..application..} quedan permitidos.</p>
     *
     * <p>No se marca {@code allowEmptyShould(true)}: el vertical de anuncios ya
     * tiene clases de producción, así que la regla evalúa sujetos reales y su
     * cumplimiento es una garantía efectiva, no trivial.</p>
     *
     * <p>Validates: Requirements 4.5, 10.3</p>
     */
    @ArchTest
    static final ArchRule el_vertical_no_accede_a_la_persistencia_interna_del_nucleo =
            noClasses()
                    .that().resideInAPackage("com.dessti.crm.vertical..")
                    .should().dependOnClassesThat(persistencia_interna_de_otro_modulo)
                    .because("El Vertical se acopla al Núcleo por PUERTOS (..application..), "
                            + "nunca por la persistencia interna (repositorios/entidades en "
                            + "..adapter.out.persistence..) de otro módulo (Req 4.5, 10.3)");

    /**
     * Generalización de <strong>Núcleo → Vertical prohibido</strong>: ninguna
     * clase que resida <em>fuera</em> de {@code com.dessti.crm.vertical..} debe
     * depender de clases del vertical ({@code com.dessti.crm.vertical..}).
     *
     * <p>La regla {@link #el_nucleo_no_depende_de_los_verticales} solo cubre
     * {@code platform..}; esta la extiende a <em>todo</em> lo que no sea vertical
     * (comercial, facturacion, operacion, compras, contabilidad, tesoreria,
     * activosfijos, portalcliente, estrategia, presupuestos, notificaciones,
     * reportesbi, calidad, social y el propio {@code platform..}). El Núcleo no
     * referencia entidades ni clases del Vertical (Req 10.5).</p>
     *
     * <p><strong>Sobre el contrato/registro</strong>: {@code ContratoVertical} y
     * {@code RegistroVerticales} viven en {@code platform.vertical} (Núcleo) y
     * <em>no importan</em> {@code vertical..}; reciben las implementaciones por
     * inyección de {@code List<ContratoVertical>} que resuelve Spring. Por eso no
     * hay dependencia de compilación núcleo→vertical y la regla pasa. Si en el
     * futuro apareciera un falso positivo por inyección/component-scan, debe
     * ajustarse la <em>expresión</em> de la regla (no relajarse) para seguir
     * detectando acoplamientos reales.</p>
     *
     * <p>No se usa {@code allowEmptyShould(true)}: el vertical de anuncios existe,
     * por lo que el objetivo no está vacío y la garantía es efectiva.</p>
     *
     * <p>Validates: Requirements 10.5</p>
     */
    @ArchTest
    static final ArchRule ningun_modulo_del_nucleo_depende_del_vertical =
            noClasses()
                    .that().resideOutsideOfPackage("com.dessti.crm.vertical..")
                    .should().dependOnClassesThat().resideInAPackage("com.dessti.crm.vertical..")
                    .because("El Núcleo (cualquier módulo fuera de vertical.., incluido "
                            + "platform.vertical que solo define el contrato) no referencia "
                            + "entidades ni clases del Vertical; el enchufe es por inyección "
                            + "de List<ContratoVertical>, no por dependencia de compilación (Req 10.5)");
}
