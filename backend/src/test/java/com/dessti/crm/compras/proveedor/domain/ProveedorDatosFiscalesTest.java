package com.dessti.crm.compras.proveedor.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias del dominio {@link Proveedor}/{@link ProveedorValidaciones} para los
 * datos fiscales y comerciales anadidos en V89 (persona de contacto, regimen fiscal, dias
 * de credito y domicilio con codigo postal). No arrancan Spring ni base de datos.
 */
class ProveedorDatosFiscalesTest {

    private static final String ACTOR = "sistema";
    private static final String RFC = "ABC010101AB1";

    private static DatosProveedor datos(Integer diasCredito, String codigoPostal, String regimen) {
        return new DatosProveedor("Aceros del Norte", RFC, "compras@aceros.mx", null,
                "  Juan Perez  ", regimen, diasCredito, "Av. Reforma 100", "Monterrey",
                "Nuevo Leon", codigoPostal);
    }

    @Test
    @DisplayName("crear normaliza y conserva los datos fiscales/comerciales (V89)")
    void crearConservaDatosFiscales() {
        Proveedor p = Proveedor.crear(datos(30, "64000", "601"), ACTOR);

        assertThat(p.getPersonaContacto()).isEqualTo("Juan Perez");
        assertThat(p.getRegimenFiscal()).isEqualTo("601");
        assertThat(p.getDiasCredito()).isEqualTo(30);
        assertThat(p.getDomicilioCalle()).isEqualTo("Av. Reforma 100");
        assertThat(p.getDomicilioCiudad()).isEqualTo("Monterrey");
        assertThat(p.getDomicilioEstado()).isEqualTo("Nuevo Leon");
        assertThat(p.getCodigoPostal()).isEqualTo("64000");
    }

    @Test
    @DisplayName("dias de credito null se interpreta como pago de contado")
    void diasCreditoNullEsContado() {
        Proveedor p = Proveedor.crear(datos(null, null, null), ACTOR);

        assertThat(p.getDiasCredito()).isNull();
        assertThat(p.getCodigoPostal()).isNull();
        assertThat(p.getRegimenFiscal()).isNull();
    }

    @Test
    @DisplayName("rechaza (422) dias de credito negativos")
    void rechazaDiasCreditoNegativos() {
        assertThatThrownBy(() -> Proveedor.crear(datos(-1, null, null), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("rechaza (422) dias de credito por encima del tope")
    void rechazaDiasCreditoExcesivos() {
        assertThatThrownBy(() -> Proveedor.crear(datos(400, null, null), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("rechaza (422) un codigo postal que no tenga 5 digitos")
    void rechazaCodigoPostalInvalido() {
        assertThatThrownBy(() -> Proveedor.crear(datos(null, "123", null), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("normaliza el regimen fiscal a mayusculas")
    void normalizaRegimenFiscal() {
        Proveedor p = Proveedor.crear(datos(null, null, "abc"), ACTOR);

        assertThat(p.getRegimenFiscal()).isEqualTo("ABC");
    }

    @Test
    @DisplayName("actualizar aplica los nuevos datos fiscales sobre un proveedor existente")
    void actualizarAplicaDatos() {
        Proveedor p = Proveedor.crear(datos(null, null, null), ACTOR);

        p.actualizar(datos(15, "06000", "612"), ACTOR);

        assertThat(p.getDiasCredito()).isEqualTo(15);
        assertThat(p.getCodigoPostal()).isEqualTo("06000");
        assertThat(p.getRegimenFiscal()).isEqualTo("612");
    }
}
