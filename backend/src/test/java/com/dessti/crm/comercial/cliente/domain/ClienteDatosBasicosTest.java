package com.dessti.crm.comercial.cliente.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias de los DATOS BASICOS de negocio OPCIONALES del
 * {@link Cliente} (Req 5, V59): nombre comercial, tipo de persona, telefono
 * adicional, direccion desglosada y notas. Verifican que son opcionales
 * (null OK), que se normalizan (recorte), que respetan sus cotas de longitud y
 * que el tipo de persona solo admite 'fisica'/'moral'. No arrancan Spring ni BD.
 */
class ClienteDatosBasicosTest {

    private static final String ACTOR = "ventas@acme.com";
    private static final String RFC = "ABC120101AB1";

    private static DatosBasicosCliente basicos(String nombreComercial, String tipoPersona,
                                               String telefonoAdicional, String direccionCalle,
                                               String notas) {
        return new DatosBasicosCliente(nombreComercial, tipoPersona, telefonoAdicional,
                direccionCalle, null, null, null, null, notas);
    }

    @Test
    @DisplayName("crear acepta y normaliza todos los datos basicos opcionales (Req 5, V59)")
    void crearConDatosBasicos() {
        DatosBasicosCliente datos = new DatosBasicosCliente(
                "  Acme Marca  ", "MORAL", "5598765432",
                "  Av. Reforma 100 ", "Monterrey", "Nuevo Leon", "64000", "Mexico",
                "  Cliente preferente  ");

        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "a@b.com", null, datos, ACTOR);

        assertThat(cliente.getNombreComercial()).isEqualTo("Acme Marca");
        assertThat(cliente.getTipoPersona()).isEqualTo(TipoPersona.MORAL);
        assertThat(cliente.getTelefonoAdicional()).isEqualTo("5598765432");
        assertThat(cliente.getDireccionCalle()).isEqualTo("Av. Reforma 100");
        assertThat(cliente.getDireccionCiudad()).isEqualTo("Monterrey");
        assertThat(cliente.getDireccionEstado()).isEqualTo("Nuevo Leon");
        assertThat(cliente.getDireccionCp()).isEqualTo("64000");
        assertThat(cliente.getDireccionPais()).isEqualTo("Mexico");
        assertThat(cliente.getNotas()).isEqualTo("Cliente preferente");
    }

    @Test
    @DisplayName("crear deja todos los datos basicos en null cuando no se proporcionan (opcionales)")
    void crearSinDatosBasicos() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                DatosBasicosCliente.VACIO, ACTOR);

        assertThat(cliente.getNombreComercial()).isNull();
        assertThat(cliente.getTipoPersona()).isNull();
        assertThat(cliente.getTelefonoAdicional()).isNull();
        assertThat(cliente.getDireccionCalle()).isNull();
        assertThat(cliente.getNotas()).isNull();
    }

    @Test
    @DisplayName("crear con datosBasicos nulo equivale a todos ausentes")
    void crearConDatosBasicosNulo() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "a@b.com", null, null, ACTOR);

        assertThat(cliente.getTipoPersona()).isNull();
        assertThat(cliente.getNombreComercial()).isNull();
    }

    @Test
    @DisplayName("tipo de persona 'fisica' se acepta y normaliza")
    void tipoPersonaFisica() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                basicos(null, "  Fisica ", null, null, null), ACTOR);

        assertThat(cliente.getTipoPersona()).isEqualTo(TipoPersona.FISICA);
    }

    @Test
    @DisplayName("tipo de persona invalido se rechaza con 422")
    void tipoPersonaInvalido() {
        assertThatThrownBy(() -> Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                basicos(null, "juridica", null, null, null), ACTOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("tipo de persona");
    }

    @Test
    @DisplayName("nombre comercial que excede 200 caracteres se rechaza con 422")
    void nombreComercialDemasiadoLargo() {
        String largo = "N".repeat(201);
        assertThatThrownBy(() -> Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                basicos(largo, null, null, null, null), ACTOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("direccion calle que excede 200 caracteres se rechaza con 422")
    void direccionCalleDemasiadoLarga() {
        String largo = "C".repeat(201);
        assertThatThrownBy(() -> Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                basicos(null, null, null, largo, null), ACTOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("notas que exceden 1000 caracteres se rechazan con 422")
    void notasDemasiadoLargas() {
        String largo = "x".repeat(1001);
        assertThatThrownBy(() -> Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                basicos(null, null, null, null, largo), ACTOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("1000");
    }

    @Test
    @DisplayName("telefono adicional aplica la misma regla que el telefono (10..15 digitos)")
    void telefonoAdicionalCortoRechazado() {
        assertThatThrownBy(() -> Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                basicos(null, null, "123", null, null), ACTOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("telefono");
    }

    @Test
    @DisplayName("telefono adicional de 15 digitos se acepta (misma cota backend que telefono)")
    void telefonoAdicional15DigitosAceptado() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                basicos(null, null, "123456789012345", null, null), ACTOR);

        assertThat(cliente.getTelefonoAdicional()).isEqualTo("123456789012345");
    }

    @Test
    @DisplayName("un dato basico invalido no muta la entidad al actualizar (validacion previa, 422)")
    void actualizarConDatoBasicoInvalidoNoMuta() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                basicos("Marca Original", "moral", null, null, null), ACTOR);

        assertThatThrownBy(() -> cliente.actualizar("Acme Nueva", RFC, "a@b.com", null,
                basicos("Marca Nueva", "invalido", null, null, null), ACTOR))
                .isInstanceOf(ReglaNegocioException.class);

        // El nombre obligatorio y el nombre comercial conservan su valor previo.
        assertThat(cliente.getNombre()).isEqualTo("Acme S.A.");
        assertThat(cliente.getNombreComercial()).isEqualTo("Marca Original");
        assertThat(cliente.getTipoPersona()).isEqualTo(TipoPersona.MORAL);
    }

    @Test
    @DisplayName("actualizar aplica los nuevos datos basicos validos")
    void actualizarAplicaDatosBasicos() {
        Cliente cliente = Cliente.crear("Acme S.A.", RFC, "a@b.com", null,
                DatosBasicosCliente.VACIO, ACTOR);

        cliente.actualizar("Acme S.A.", RFC, "a@b.com", null,
                basicos("Nueva Marca", "fisica", "5512345678", "Calle 1", "nota"), ACTOR);

        assertThat(cliente.getNombreComercial()).isEqualTo("Nueva Marca");
        assertThat(cliente.getTipoPersona()).isEqualTo(TipoPersona.FISICA);
        assertThat(cliente.getTelefonoAdicional()).isEqualTo("5512345678");
        assertThat(cliente.getDireccionCalle()).isEqualTo("Calle 1");
        assertThat(cliente.getNotas()).isEqualTo("nota");
    }
}
