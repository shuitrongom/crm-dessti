package com.dessti.crm.comercial.producto.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Pruebas unitarias de la entidad de dominio {@link Producto} (Req 59.1, 59.2,
 * 59.5, 59.6). No arrancan Spring ni base de datos.
 */
class ProductoTest {

    @Test
    @DisplayName("crear persiste los datos obligatorios y la informacion de apoyo (Req 59.1, 59.5)")
    void crearConDatosCompletos() {
        Producto producto = Producto.crear("Pantalla LED P4", "pieza",
                "Modulo de anuncio luminoso full color", "Comercios", "Proveedor X", "Competidor Y",
                "https://cdn.example.com/pantalla.png", "ventas");

        assertThat(producto.getNombre()).isEqualTo("Pantalla LED P4");
        assertThat(producto.getUnidad()).isEqualTo("pieza");
        assertThat(producto.getDescripcion()).isEqualTo("Modulo de anuncio luminoso full color");
        assertThat(producto.getClienteMeta()).isEqualTo("Comercios");
        assertThat(producto.getAlianzas()).isEqualTo("Proveedor X");
        assertThat(producto.getCompetencia()).isEqualTo("Competidor Y");
        assertThat(producto.getFoto()).isEqualTo("https://cdn.example.com/pantalla.png");
        assertThat(producto.estaActivo()).isTrue();
    }

    @Test
    @DisplayName("crear sin nombre se rechaza con 422 (Req 59.2)")
    void crearSinNombre() {
        assertThatThrownBy(() -> Producto.crear("  ", "pieza", "desc", null, null, null, null, "ventas"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear sin unidad se rechaza con 422 (Req 59.2)")
    void crearSinUnidad() {
        assertThatThrownBy(() -> Producto.crear("Producto", null, "desc", null, null, null, null, "ventas"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crear sin descripcion se rechaza con 422 (Req 59.2)")
    void crearSinDescripcion() {
        assertThatThrownBy(() -> Producto.crear("Producto", "pieza", "", null, null, null, null, "ventas"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("desactivar realiza el borrado logico conservando los datos (Req 59.6)")
    void desactivarBorradoLogico() {
        Producto producto = Producto.crear("Producto", "pieza", "desc", null, null, null, null, "ventas");

        producto.desactivar("ventas");

        assertThat(producto.estaActivo()).isFalse();
        assertThat(producto.getNombre()).isEqualTo("Producto");
    }

    @Test
    @DisplayName("la foto es opcional: crear sin foto la deja en null (Req 59)")
    void crearSinFotoDejaNull() {
        Producto producto = Producto.crear("Producto", "pieza", "desc", null, null, null, null, "ventas");

        assertThat(producto.getFoto()).isNull();
    }

    @Test
    @DisplayName("crear con foto en blanco la normaliza a null (Req 59)")
    void crearConFotoEnBlancoNormalizaANull() {
        Producto producto = Producto.crear("Producto", "pieza", "desc", null, null, null, "   ", "ventas");

        assertThat(producto.getFoto()).isNull();
    }

    @Test
    @DisplayName("crear con data URI valido recorta y conserva la foto (Req 59)")
    void crearConDataUriValido() {
        String dataUri = "  data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42m\n" ;
        Producto producto = Producto.crear("Producto", "pieza", "desc", null, null, null, dataUri, "ventas");

        assertThat(producto.getFoto()).isEqualTo(dataUri.strip());
    }

    @Test
    @DisplayName("crear con foto que excede el maximo se rechaza con 422 (Req 59)")
    void crearConFotoDesmesuradaSeRechaza() {
        String fotoEnorme = "a".repeat(Producto.LONGITUD_MAXIMA_FOTO + 1);

        assertThatThrownBy(() -> Producto.crear("Producto", "pieza", "desc", null, null, null, fotoEnorme, "ventas"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("actualizar cambia la foto y limpiarla con blanco la deja en null (Req 59)")
    void actualizarCambiaYLimpiaFoto() {
        Producto producto = Producto.crear("Producto", "pieza", "desc", null, null, null,
                "https://cdn.example.com/uno.png", "ventas");

        producto.actualizar("Producto", "pieza", "desc", null, null, null,
                "https://cdn.example.com/dos.png", "ventas");
        assertThat(producto.getFoto()).isEqualTo("https://cdn.example.com/dos.png");

        producto.actualizar("Producto", "pieza", "desc", null, null, null, "  ", "ventas");
        assertThat(producto.getFoto()).isNull();
    }

    @Test
    @DisplayName("actualizar con foto que excede el maximo se rechaza con 422 (Req 59)")
    void actualizarConFotoDesmesuradaSeRechaza() {
        Producto producto = Producto.crear("Producto", "pieza", "desc", null, null, null, null, "ventas");
        String fotoEnorme = "a".repeat(Producto.LONGITUD_MAXIMA_FOTO + 1);

        assertThatThrownBy(() -> producto.actualizar("Producto", "pieza", "desc", null, null, null,
                fotoEnorme, "ventas"))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
