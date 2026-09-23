package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.comercial.cotizacion.application.DatosEmisor;

/**
 * Pruebas unitarias de {@link EmpresaEmisorAdapter} (V60, Req 1). Con un
 * {@link EmpresaRepository} simulado, verifican el mapeo de las columnas de la
 * Empresa a {@link DatosEmisor}, el armado de la direccion en una sola linea
 * omitiendo las partes vacias, la deteccion de datos fiscales incompletos y el
 * {@link Optional#empty()} cuando la Empresa no existe.
 */
class EmpresaEmisorAdapterTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GIRO = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private EmpresaRepository empresaRepository;
    private EmpresaEmisorAdapter adaptador;

    @BeforeEach
    void setUp() {
        empresaRepository = mock(EmpresaRepository.class);
        adaptador = new EmpresaEmisorAdapter(empresaRepository);
    }

    /** Empresa con toda la ficha descriptiva/de contacto capturada. */
    private static Empresa empresaCompleta() {
        Empresa empresa = Empresa.crear("Anuncios del Norte S.A. de C.V.", "ANO120101AB1",
                GIRO, "super_admin");
        empresa.asignarDatosDescriptivos(new DatosDescriptivosEmpresa(
                "Anuncios Norte", "contacto@anunciosnorte.mx", "81-1111-2222",
                "https://anunciosnorte.mx", "Av. Constitucion 100", "Monterrey", "Nuevo Leon",
                "64000", "Mexico", null, null), "super_admin");
        return empresa;
    }

    @Test
    @DisplayName("mapea las columnas de la Empresa a DatosEmisor y arma la direccion en una linea")
    void mapeaColumnasYArmaDireccion() {
        Empresa empresa = empresaCompleta();
        when(empresaRepository.findById(TENANT)).thenReturn(Optional.of(empresa));

        DatosEmisor emisor = adaptador.emisorDeTenant(TENANT).orElseThrow();

        assertThat(emisor.nombre()).isEqualTo("Anuncios del Norte S.A. de C.V.");
        assertThat(emisor.nombreComercial()).isEqualTo("Anuncios Norte");
        assertThat(emisor.rfc()).isEqualTo("ANO120101AB1");
        assertThat(emisor.email()).isEqualTo("contacto@anunciosnorte.mx");
        assertThat(emisor.sitioWeb()).isEqualTo("https://anunciosnorte.mx");
        assertThat(emisor.direccion())
                .isEqualTo("Av. Constitucion 100, Monterrey, Nuevo Leon, 64000, Mexico");
        assertThat(emisor.datosFiscalesIncompletos()).isFalse();
    }

    @Test
    @DisplayName("arma la direccion omitiendo las partes vacias (solo calle y ciudad)")
    void armaDireccionOmitiendoVacias() {
        Empresa empresa = Empresa.crear("Rotulos SA", "RSA010101AAA", GIRO, "super_admin");
        empresa.asignarDatosDescriptivos(new DatosDescriptivosEmpresa(
                null, null, null, null, "Calle 1", "Saltillo", null, null, null, null, null),
                "super_admin");
        when(empresaRepository.findById(TENANT)).thenReturn(Optional.of(empresa));

        DatosEmisor emisor = adaptador.emisorDeTenant(TENANT).orElseThrow();

        assertThat(emisor.direccion()).isEqualTo("Calle 1, Saltillo");
        assertThat(emisor.nombreComercial()).isNull();
        assertThat(emisor.email()).isNull();
    }

    @Test
    @DisplayName("detecta datos fiscales incompletos cuando falta la direccion (solo nombre y RFC)")
    void datosFiscalesIncompletosSinDireccion() {
        Empresa minima = Empresa.crear("Empresa Minima SA", "EMI900101AAA", GIRO, "super_admin");
        when(empresaRepository.findById(TENANT)).thenReturn(Optional.of(minima));

        DatosEmisor emisor = adaptador.emisorDeTenant(TENANT).orElseThrow();

        assertThat(emisor.rfc()).isEqualTo("EMI900101AAA");
        assertThat(emisor.direccion()).isNull();
        assertThat(emisor.datosFiscalesIncompletos()).isTrue();
    }

    @Test
    @DisplayName("devuelve Optional.empty cuando la Empresa no existe")
    void empresaInexistenteDevuelveVacio() {
        when(empresaRepository.findById(TENANT)).thenReturn(Optional.empty());

        assertThat(adaptador.emisorDeTenant(TENANT)).isEmpty();
    }

    @Test
    @DisplayName("devuelve Optional.empty cuando el tenantId es nulo (sin consultar el repositorio)")
    void tenantNuloDevuelveVacio() {
        assertThat(adaptador.emisorDeTenant(null)).isEmpty();
    }
}
