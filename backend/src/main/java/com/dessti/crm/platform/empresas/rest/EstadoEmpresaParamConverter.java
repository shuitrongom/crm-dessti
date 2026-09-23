package com.dessti.crm.platform.empresas.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.dessti.crm.platform.empresas.EstadoEmpresa;

/**
 * Registra un {@link Converter} de {@link String} a {@link EstadoEmpresa} para
 * los parametros de consulta REST del listado de Empresas (Req 24.5).
 *
 * <p>Permite que el cliente filtre usando la etiqueta persistida del estado
 * ({@code activa}, {@code suspendida}, {@code cancelada}) en lugar del nombre de
 * la constante Java. Un valor no reconocido produce una
 * {@code MethodArgumentTypeMismatchException} que el manejador global traduce a
 * HTTP 400 (contrato de validacion uniforme), en vez de un 500.</p>
 *
 * <p>Se restringe a aplicaciones web ({@link ConditionalOnWebApplication}) para
 * no interferir con pruebas unitarias sin contexto web.</p>
 */
@Configuration
@ConditionalOnWebApplication
public class EstadoEmpresaParamConverter implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringAEstadoEmpresa());
    }

    /**
     * Convierte la etiqueta persistida del estado a su enum. Delega en
     * {@link EstadoEmpresa#desdeValorBd(String)}, que lanza
     * {@link IllegalArgumentException} ante un valor desconocido; Spring MVC la
     * envuelve en {@code MethodArgumentTypeMismatchException} (HTTP 400).
     */
    static final class StringAEstadoEmpresa implements Converter<String, EstadoEmpresa> {
        @Override
        public EstadoEmpresa convert(String source) {
            return EstadoEmpresa.desdeValorBd(source);
        }
    }
}
