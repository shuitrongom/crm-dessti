package com.dessti.crm.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;

/**
 * Pruebas unitarias acotadas del {@link ManejadorGlobalErrores}: verifican el
 * mapeo de excepcion a Problem Details (RFC 7807) sin arrancar el contexto de
 * Spring, para una ejecucion rapida y determinista.
 */
class ManejadorGlobalErroresTest {

    private final ManejadorGlobalErrores manejador = new ManejadorGlobalErrores();

    /** Objeto y metodo dummy usados para construir MethodParameter en el test. */
    static class Dummy {
        void metodo(Object cuerpo) { /* no-op */ }
    }

    @Test
    void validacion_devuelve400_conArregloErrores() throws Exception {
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "cliente");
        binding.addError(new FieldError("cliente", "rfc", "Formato de RFC invalido"));

        MethodParameter parametro = new MethodParameter(
                Dummy.class.getDeclaredMethod("metodo", Object.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parametro, binding);

        ProblemDetail pd = manejador.manejarArgumentoNoValido(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getTitle()).isEqualTo("Datos invalidos");
        Object errores = pd.getProperties().get("errors");
        assertThat(errores).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<ErrorCampo> lista = (List<ErrorCampo>) errores;
        assertThat(lista).extracting(ErrorCampo::field).contains("rfc");
        assertThat(lista).extracting(ErrorCampo::message).contains("Formato de RFC invalido");
    }

    @Test
    void recursoNoEncontrado_devuelve404() {
        ProblemDetail pd = manejador.manejarNoEncontrado(
                new RecursoNoEncontradoException("El cliente no existe"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getTitle()).isEqualTo("Recurso no encontrado");
        assertThat(pd.getDetail()).isEqualTo("El cliente no existe");
    }

    @Test
    void conflictoUnicidad_devuelve409() {
        ProblemDetail pd = manejador.manejarConflictoUnicidad(
                new ConflictoUnicidadException("RFC duplicado"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getTitle()).isEqualTo("Conflicto de unicidad");
    }

    @Test
    void excepcionGenerica_devuelve500_sinFugaDeDetalleInterno() {
        RuntimeException interna = new RuntimeException("NullPointer en DAO SQL interno");

        ProblemDetail pd = manejador.manejarGenerico(interna);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getTitle()).isEqualTo("Error interno");
        // No debe filtrar el mensaje interno de la excepcion al cliente.
        assertThat(pd.getDetail()).doesNotContain("NullPointer", "DAO", "SQL");
    }
}
