package com.dessti.crm.comercial.cotizacion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.dessti.crm.comercial.cotizacion.adapter.out.persistence.CotizacionRepository;
import com.dessti.crm.comercial.cotizacion.application.DatosClientePort.DatosCliente;
import com.dessti.crm.comercial.cotizacion.domain.Cotizacion;
import com.dessti.crm.comercial.cotizacion.domain.EstadoCotizacion;
import com.dessti.crm.comercial.producto.application.SugerenciaPrecioPort;
import com.dessti.crm.comercial.producto.application.SugerenciaPrecioPort.ConsultaSugerenciaPrecio;
import com.dessti.crm.notificaciones.application.MensajeNotificacion;
import com.dessti.crm.notificaciones.application.NotificadorCorreoPort;
import com.dessti.crm.notificaciones.application.ResultadoEnvio;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pruebas unitarias de {@link ServicioCotizaciones} (Req 6). Usan dobles de
 * Mockito; no arrancan Spring ni base de datos. Cubren alta con verificacion del
 * Cliente y calculo de totales (6.1, 6.3, 6.5), rechazo por Cliente inexistente
 * (404 + auditoria, 23.3), sugerencia de precio via {@link SugerenciaPrecioPort}
 * (59.4), agregado de partida con recalculo, cambio de estado con auditoria del
 * estado anterior/nuevo (6.6, 6.10), rechazo de transicion invalida (6.7 -> 409),
 * el cascaron de conversion (14.5) y el listado con filtros (6.8, 6.9).
 */
class ServicioCotizacionesTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PRODUCTO = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** Clock fijo: 15 de enero de 2026 (para folios COT-2026-nnnn). */
    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-01-15T12:00:00Z"), ZoneId.of("UTC"));

    private CotizacionRepository repositorio;
    private ClienteExistentePort clienteExistente;
    private CanalVentaExistentePort canalVentaExistente;
    private SugerenciaPrecioPort sugerenciaPrecio;
    private DatosClientePort datosCliente;
    private FolioCotizacionPort folioCotizacion;
    private CotizacionPdfService pdfService;
    private NotificadorCorreoPort notificadorCorreo;
    private EmpresaEmisorPort empresaEmisor;
    private AuditoriaPort auditoria;
    private ServicioCotizaciones servicio;

    @BeforeEach
    void setUp() {
        repositorio = mock(CotizacionRepository.class);
        clienteExistente = mock(ClienteExistentePort.class);
        canalVentaExistente = mock(CanalVentaExistentePort.class);
        sugerenciaPrecio = mock(SugerenciaPrecioPort.class);
        datosCliente = mock(DatosClientePort.class);
        folioCotizacion = mock(FolioCotizacionPort.class);
        pdfService = mock(CotizacionPdfService.class);
        notificadorCorreo = mock(NotificadorCorreoPort.class);
        empresaEmisor = mock(EmpresaEmisorPort.class);
        auditoria = mock(AuditoriaPort.class);
        // Emisor por defecto: la Empresa del tenant con datos fiscales completos.
        org.mockito.Mockito.lenient().when(empresaEmisor.emisorDeTenant(any()))
                .thenReturn(Optional.of(new DatosEmisor("Anuncios del Norte", "Anuncios Norte",
                        "ANO120101AB1", "Av. Constitucion 100, Monterrey", "contacto@an.mx",
                        "https://an.mx")));
        // Contador de folio por defecto: consecutivo creciente 1, 2, 3, ...
        AtomicInteger contador = new AtomicInteger(0);
        org.mockito.Mockito.lenient().when(folioCotizacion.siguienteConsecutivo(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(inv -> contador.incrementAndGet());
        servicio = new ServicioCotizaciones(repositorio, clienteExistente, canalVentaExistente,
                sugerenciaPrecio, datosCliente, folioCotizacion, pdfService, notificadorCorreo,
                empresaEmisor, auditoria, RELOJ);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CrearCotizacionCommand comandoConPrecio() {
        return new CrearCotizacionCommand(CLIENTE,
                List.of(new CrearPartidaCommand(null, "Anuncio", 2, new BigDecimal("100.00"))));
    }

    @Test
    @DisplayName("crearCotizacion verifica el Cliente, fija estado 'borrador', calcula total y audita (Req 6.1, 6.5, 6.10)")
    void crearExitoso() {
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(true);
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));

        CotizacionDto dto = servicio.crearCotizacion(comandoConPrecio());

        assertThat(dto.estado()).isEqualTo("borrador");
        assertThat(dto.clienteId()).isEqualTo(CLIENTE);
        assertThat(dto.total()).isEqualByComparingTo("200.00");
        assertThat(dto.partidas()).hasSize(1);
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("crear");
        assertThat(ev.getValue().recurso()).isEqualTo("cotizacion");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
    }

    @Test
    @DisplayName("crearCotizacion con Cliente inexistente devuelve 404 y audita el acceso cruzado (Req 6.1, 23.3)")
    void crearClienteInexistente() {
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crearCotizacion(comandoConPrecio()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repositorio, never()).save(any());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("cliente");
    }

    @Test
    @DisplayName("crearCotizacion usa SugerenciaPrecioPort cuando la partida refiere un Producto sin precio (Req 59.4)")
    void crearUsaSugerenciaDePrecio() {
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(true);
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sugerenciaPrecio.sugerirPrecioUnitario(any(ConsultaSugerenciaPrecio.class)))
                .thenReturn(Optional.of(new BigDecimal("250.00")));

        CrearCotizacionCommand comando = new CrearCotizacionCommand(CLIENTE,
                List.of(new CrearPartidaCommand(PRODUCTO, "Producto A", 2, null)));

        CotizacionDto dto = servicio.crearCotizacion(comando);

        assertThat(dto.partidas().get(0).precioUnitario()).isEqualByComparingTo("250.00");
        assertThat(dto.total()).isEqualByComparingTo("500.00");
        verify(sugerenciaPrecio).sugerirPrecioUnitario(any(ConsultaSugerenciaPrecio.class));
    }

    @Test
    @DisplayName("crearCotizacion respeta el precio del Usuario aunque haya Producto (la sugerencia es un default, Req 59.4)")
    void crearRespetaPrecioDelUsuario() {
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(true);
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));

        CrearCotizacionCommand comando = new CrearCotizacionCommand(CLIENTE,
                List.of(new CrearPartidaCommand(PRODUCTO, "Producto A", 1, new BigDecimal("99.00"))));

        CotizacionDto dto = servicio.crearCotizacion(comando);

        assertThat(dto.partidas().get(0).precioUnitario()).isEqualByComparingTo("99.00");
        verify(sugerenciaPrecio, never()).sugerirPrecioUnitario(any());
    }

    @Test
    @DisplayName("crearCotizacion rechaza si no hay precio y la sugerencia esta vacia (Req 6.4)")
    void crearSinPrecioNiSugerencia() {
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(true);
        when(sugerenciaPrecio.sugerirPrecioUnitario(any(ConsultaSugerenciaPrecio.class)))
                .thenReturn(Optional.empty());

        CrearCotizacionCommand comando = new CrearCotizacionCommand(CLIENTE,
                List.of(new CrearPartidaCommand(PRODUCTO, "Producto A", 1, null)));

        assertThatThrownBy(() -> servicio.crearCotizacion(comando))
                .isInstanceOf(ReglaNegocioException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("agregarPartida recalcula el total y audita (Req 6.3, 6.5)")
    void agregarPartida() {
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(true);
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE,
                List.of(com.dessti.crm.comercial.cotizacion.domain.PartidaCotizacion.crear(
                        null, "Base", 1, new BigDecimal("100.00"), "ventas")),
                "ventas");
        when(repositorio.findById(cotizacion.getId())).thenReturn(Optional.of(cotizacion));
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));

        CotizacionDto dto = servicio.agregarPartida(cotizacion.getId(),
                new CrearPartidaCommand(null, "Extra", 3, new BigDecimal("10.00")));

        assertThat(dto.partidas()).hasSize(2);
        assertThat(dto.total()).isEqualByComparingTo("130.00");
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("agregar_partida");
    }

    @Test
    @DisplayName("cambiarEstado aplica la transicion y audita estado anterior -> nuevo (Req 6.6, 6.10)")
    void cambiarEstadoAudita() {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE,
                List.of(com.dessti.crm.comercial.cotizacion.domain.PartidaCotizacion.crear(
                        null, "Base", 1, new BigDecimal("100.00"), "ventas")),
                "ventas");
        when(repositorio.findById(cotizacion.getId())).thenReturn(Optional.of(cotizacion));
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));

        CotizacionDto dto = servicio.cambiarEstado(cotizacion.getId(), "enviada");

        assertThat(dto.estado()).isEqualTo("enviada");
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("cambiar_estado");
        assertThat(ev.getValue().valorAnterior()).isEqualTo("borrador");
        assertThat(ev.getValue().valorNuevo()).isEqualTo("enviada");
    }

    @Test
    @DisplayName("cambiarEstado invalida propaga 409 (Req 6.7)")
    void cambiarEstadoInvalida() {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE,
                List.of(com.dessti.crm.comercial.cotizacion.domain.PartidaCotizacion.crear(
                        null, "Base", 1, new BigDecimal("100.00"), "ventas")),
                "ventas");
        when(repositorio.findById(cotizacion.getId())).thenReturn(Optional.of(cotizacion));

        assertThatThrownBy(() -> servicio.cambiarEstado(cotizacion.getId(), "aprobada"))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    @DisplayName("crearDesdeOportunidad crea un cascaron 'borrador' vinculado a la Oportunidad (Req 14.5)")
    void crearDesdeOportunidad() {
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID oportunidad = UUID.randomUUID();

        UUID id = servicio.crearDesdeOportunidad(oportunidad, CLIENTE, "ventas");

        assertThat(id).isNotNull();
        ArgumentCaptor<Cotizacion> cot = ArgumentCaptor.forClass(Cotizacion.class);
        verify(repositorio).save(cot.capture());
        assertThat(cot.getValue().getEstado()).isEqualTo(EstadoCotizacion.BORRADOR);
        assertThat(cot.getValue().getOportunidadId()).isEqualTo(oportunidad);
        assertThat(cot.getValue().getPartidas()).isEmpty();
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("crear_por_conversion");
    }

    @Test
    @DisplayName("consultarCotizacion de otro tenant devuelve 404 y audita el acceso cruzado (Req 23.3)")
    void consultarAccesoCruzado() {
        UUID ajeno = UUID.randomUUID();
        when(repositorio.findById(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultarCotizacion(ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class);

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("cotizacion");
    }

    @Test
    @DisplayName("listarCotizaciones traduce la etiqueta de estado y proyecta a DTO (Req 6.8, 6.9)")
    void listarConFiltros() {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE,
                List.of(com.dessti.crm.comercial.cotizacion.domain.PartidaCotizacion.crear(
                        null, "Base", 1, new BigDecimal("100.00"), "ventas")),
                "ventas");
        Pageable pageable = PageRequest.of(0, 20);
        Page<Cotizacion> pagina = new PageImpl<>(List.of(cotizacion), pageable, 1);
        when(repositorio.buscarConFiltros(eq(CLIENTE), eq(EstadoCotizacion.BORRADOR), any(), any(Pageable.class)))
                .thenReturn(pagina);

        Page<CotizacionDto> resultado = servicio.listarCotizaciones(CLIENTE, "borrador", null, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        verify(repositorio).buscarConFiltros(eq(CLIENTE), eq(EstadoCotizacion.BORRADOR), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("listarCotizaciones con etiqueta de estado desconocida devuelve 422 (Req 6.9)")
    void listarEstadoDesconocido() {
        assertThatThrownBy(() -> servicio.listarCotizaciones(null, "inexistente", null, PageRequest.of(0, 20)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("asignarCanalVenta verifica el canal, lo clasifica y audita anterior -> nuevo (Req 63.1, 63.3)")
    void asignarCanalVentaExitoso() {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE,
                List.of(com.dessti.crm.comercial.cotizacion.domain.PartidaCotizacion.crear(
                        null, "Base", 1, new BigDecimal("100.00"), "ventas")),
                "ventas");
        UUID canal = UUID.randomUUID();
        when(repositorio.findById(cotizacion.getId())).thenReturn(Optional.of(cotizacion));
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(canalVentaExistente.existeCanalVentaActivo(canal)).thenReturn(true);

        CotizacionDto dto = servicio.asignarCanalVenta(cotizacion.getId(), canal);

        assertThat(dto.canalVentaId()).isEqualTo(canal);
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("asignar_canal");
        assertThat(ev.getValue().recurso()).isEqualTo("cotizacion");
        assertThat(ev.getValue().valorNuevo()).isEqualTo(canal.toString());
    }

    @Test
    @DisplayName("asignarCanalVenta con canal inexistente devuelve 404 y audita el acceso cruzado (Req 63.1, 23.3)")
    void asignarCanalVentaInexistente() {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE,
                List.of(com.dessti.crm.comercial.cotizacion.domain.PartidaCotizacion.crear(
                        null, "Base", 1, new BigDecimal("100.00"), "ventas")),
                "ventas");
        UUID canal = UUID.randomUUID();
        when(repositorio.findById(cotizacion.getId())).thenReturn(Optional.of(cotizacion));
        when(canalVentaExistente.existeCanalVentaActivo(canal)).thenReturn(false);

        assertThatThrownBy(() -> servicio.asignarCanalVenta(cotizacion.getId(), canal))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repositorio, never()).save(any());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("canal_venta");
    }

    @Test
    @DisplayName("asignarCanalVenta con Cotizacion de otro tenant devuelve 404 y audita (Req 23.3)")
    void asignarCanalVentaCotizacionInexistente() {
        UUID ajeno = UUID.randomUUID();
        when(repositorio.findById(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignarCanalVenta(ajeno, UUID.randomUUID()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repositorio, never()).save(any());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("cotizacion");
    }

    // ------------------------------------------------------------------
    // Folio, datos descriptivos y DTO con datos del Cliente (V60)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("crearCotizacion asigna folio COT-2026-0001 y luego COT-2026-0002 en el mismo tenant/anio (V60)")
    void crearAsignaFolioSecuencial() {
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(true);
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));

        CotizacionDto primera = servicio.crearCotizacion(comandoConPrecio());
        CotizacionDto segunda = servicio.crearCotizacion(comandoConPrecio());

        assertThat(primera.folio()).isEqualTo("COT-2026-0001");
        assertThat(segunda.folio()).isEqualTo("COT-2026-0002");
        assertThat(primera.fechaEmision()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(primera.moneda()).isEqualTo("MXN");
    }

    @Test
    @DisplayName("crearCotizacion con valido_hasta anterior a la emision devuelve 422 (V60)")
    void crearValidoHastaAnteriorFalla() {
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(true);

        CrearCotizacionCommand comando = new CrearCotizacionCommand(CLIENTE,
                List.of(new CrearPartidaCommand(null, "Anuncio", 1, new BigDecimal("100.00"))),
                LocalDate.of(2026, 1, 14), null, null, null); // 14 ene < 15 ene (emision)

        assertThatThrownBy(() -> servicio.crearCotizacion(comando))
                .isInstanceOf(ReglaNegocioException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crearCotizacion persiste los campos descriptivos opcionales y la moneda (V60)")
    void crearPersisteCamposDescriptivos() {
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(true);
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));

        CrearCotizacionCommand comando = new CrearCotizacionCommand(CLIENTE,
                List.of(new CrearPartidaCommand(null, "Anuncio", 1, new BigDecimal("100.00"))),
                LocalDate.of(2026, 2, 15), "Pago a 30 dias", "Entrega en 2 semanas", "usd");

        CotizacionDto dto = servicio.crearCotizacion(comando);

        assertThat(dto.validoHasta()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(dto.condiciones()).isEqualTo("Pago a 30 dias");
        assertThat(dto.notas()).isEqualTo("Entrega en 2 semanas");
        assertThat(dto.moneda()).isEqualTo("USD");
    }

    @Test
    @DisplayName("crearCotizacion incluye el nombre y el correo del Cliente en el DTO (V60)")
    void crearIncluyeDatosCliente() {
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(true);
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(datosCliente.buscarPorId(CLIENTE)).thenReturn(Optional.of(
                new DatosCliente(CLIENTE, "Anuncios ACME", "AAA010101AAA", "ventas@acme.mx")));

        CotizacionDto dto = servicio.crearCotizacion(comandoConPrecio());

        assertThat(dto.clienteNombre()).isEqualTo("Anuncios ACME");
        assertThat(dto.clienteEmail()).isEqualTo("ventas@acme.mx");
        assertThat(dto.clienteRfc()).isEqualTo("AAA010101AAA");
    }

    // ------------------------------------------------------------------
    // Envio por correo (V60)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("enviarPorCorreo con correo del Cliente: 200, invoca el puerto, fija enviadaEn y pasa a 'enviada' (V60)")
    void enviarPorCorreoConCorreoCliente() {
        Cotizacion cotizacion = cotizacionConFolio();
        when(repositorio.findById(cotizacion.getId())).thenReturn(Optional.of(cotizacion));
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(datosCliente.buscarPorId(CLIENTE)).thenReturn(Optional.of(
                new DatosCliente(CLIENTE, "Anuncios ACME", "AAA010101AAA", "ventas@acme.mx")));
        when(pdfService.generar(any(), any(), any())).thenReturn(new byte[] {'%', 'P', 'D', 'F'});
        when(notificadorCorreo.enviarCorreo(any(MensajeNotificacion.class)))
                .thenReturn(ResultadoEnvio.exitoso());

        CotizacionDto dto = servicio.enviarPorCorreo(cotizacion.getId(), null);

        assertThat(dto.enviadaEn()).isNotNull();
        assertThat(dto.estado()).isEqualTo("enviada");
        ArgumentCaptor<MensajeNotificacion> msg = ArgumentCaptor.forClass(MensajeNotificacion.class);
        verify(notificadorCorreo).enviarCorreo(msg.capture());
        assertThat(msg.getValue().destinatario()).isEqualTo("ventas@acme.mx");
    }

    @Test
    @DisplayName("enviarPorCorreo usa el correo indicado en el cuerpo cuando se aporta (V60)")
    void enviarPorCorreoConCorreoExplicito() {
        Cotizacion cotizacion = cotizacionConFolio();
        when(repositorio.findById(cotizacion.getId())).thenReturn(Optional.of(cotizacion));
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(datosCliente.buscarPorId(CLIENTE)).thenReturn(Optional.of(
                new DatosCliente(CLIENTE, "Anuncios ACME", "AAA010101AAA", "ventas@acme.mx")));
        when(pdfService.generar(any(), any(), any())).thenReturn(new byte[] {'%', 'P', 'D', 'F'});
        when(notificadorCorreo.enviarCorreo(any(MensajeNotificacion.class)))
                .thenReturn(ResultadoEnvio.exitoso());

        servicio.enviarPorCorreo(cotizacion.getId(), "otro@correo.mx");

        ArgumentCaptor<MensajeNotificacion> msg = ArgumentCaptor.forClass(MensajeNotificacion.class);
        verify(notificadorCorreo).enviarCorreo(msg.capture());
        assertThat(msg.getValue().destinatario()).isEqualTo("otro@correo.mx");
    }

    @Test
    @DisplayName("enviarPorCorreo sin correo del Cliente ni indicado devuelve 422 (V60)")
    void enviarPorCorreoSinCorreoFalla() {
        Cotizacion cotizacion = cotizacionConFolio();
        when(repositorio.findById(cotizacion.getId())).thenReturn(Optional.of(cotizacion));
        when(datosCliente.buscarPorId(CLIENTE)).thenReturn(Optional.of(
                new DatosCliente(CLIENTE, "Anuncios ACME", "AAA010101AAA", null)));

        assertThatThrownBy(() -> servicio.enviarPorCorreo(cotizacion.getId(), null))
                .isInstanceOf(ReglaNegocioException.class);
        verify(notificadorCorreo, never()).enviarCorreo(any());
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("generarPdf resuelve el emisor via EmpresaEmisorPort (la Empresa del tenant, no la plataforma) (Req 1)")
    void generarPdfUsaEmisorDeLaEmpresa() {
        Cotizacion cotizacion = cotizacionConFolio();
        when(repositorio.findById(cotizacion.getId())).thenReturn(Optional.of(cotizacion));
        when(pdfService.generar(any(), any(), any())).thenReturn(new byte[] {'%', 'P', 'D', 'F'});

        servicio.generarPdf(cotizacion.getId());

        // El emisor pasado al generador es el DatosEmisor de la Empresa del tenant.
        ArgumentCaptor<DatosEmisor> emisorCap = ArgumentCaptor.forClass(DatosEmisor.class);
        verify(pdfService).generar(any(), any(), emisorCap.capture());
        assertThat(emisorCap.getValue().nombre()).isEqualTo("Anuncios del Norte");
        verify(empresaEmisor).emisorDeTenant(TENANT);
    }

    @Test
    @DisplayName("enviarPorCorreo NO marca emisorIncompleto cuando la Empresa tiene datos fiscales completos (Req 3)")
    void enviarPorCorreoEmisorCompleto() {
        Cotizacion cotizacion = cotizacionConFolio();
        when(repositorio.findById(cotizacion.getId())).thenReturn(Optional.of(cotizacion));
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(datosCliente.buscarPorId(CLIENTE)).thenReturn(Optional.of(
                new DatosCliente(CLIENTE, "Anuncios ACME", "AAA010101AAA", "ventas@acme.mx")));
        when(pdfService.generar(any(), any(), any())).thenReturn(new byte[] {'%', 'P', 'D', 'F'});
        when(notificadorCorreo.enviarCorreo(any(MensajeNotificacion.class)))
                .thenReturn(ResultadoEnvio.exitoso());

        CotizacionDto dto = servicio.enviarPorCorreo(cotizacion.getId(), null);

        assertThat(dto.emisorIncompleto()).isFalse();
    }

    @Test
    @DisplayName("enviarPorCorreo propaga emisorIncompleto cuando la Empresa carece de RFC/direccion (Req 3)")
    void enviarPorCorreoEmisorIncompleto() {
        // Empresa con datos fiscales incompletos (solo nombre).
        when(empresaEmisor.emisorDeTenant(any()))
                .thenReturn(Optional.of(DatosEmisor.minimo("Anuncios del Sur")));
        Cotizacion cotizacion = cotizacionConFolio();
        when(repositorio.findById(cotizacion.getId())).thenReturn(Optional.of(cotizacion));
        when(repositorio.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(datosCliente.buscarPorId(CLIENTE)).thenReturn(Optional.of(
                new DatosCliente(CLIENTE, "Anuncios ACME", "AAA010101AAA", "ventas@acme.mx")));
        when(pdfService.generar(any(), any(), any())).thenReturn(new byte[] {'%', 'P', 'D', 'F'});
        when(notificadorCorreo.enviarCorreo(any(MensajeNotificacion.class)))
                .thenReturn(ResultadoEnvio.exitoso());

        CotizacionDto dto = servicio.enviarPorCorreo(cotizacion.getId(), null);

        assertThat(dto.emisorIncompleto()).isTrue();
    }

    @Test
    @DisplayName("emisorIncompleto() refleja datosFiscalesIncompletos del emisor del tenant (Req 3)")
    void emisorIncompletoRefleja() {
        // Completo por defecto (setUp) -> false.
        assertThat(servicio.emisorIncompleto()).isFalse();

        // Empresa no resuelta -> fallback minimo neutro -> incompleto.
        when(empresaEmisor.emisorDeTenant(any())).thenReturn(Optional.empty());
        assertThat(servicio.emisorIncompleto()).isTrue();
    }

    /** Construye una Cotizacion en 'borrador' con folio y datos descriptivos (V60). */
    private Cotizacion cotizacionConFolio() {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE,
                List.of(com.dessti.crm.comercial.cotizacion.domain.PartidaCotizacion.crear(
                        null, "Base", 1, new BigDecimal("100.00"), "ventas")),
                "ventas");
        cotizacion.aplicarDatosDescriptivos(LocalDate.of(2026, 1, 15), null, null, null, "MXN", "ventas");
        cotizacion.asignarFolio("COT-2026-0001");
        return cotizacion;
    }
}
