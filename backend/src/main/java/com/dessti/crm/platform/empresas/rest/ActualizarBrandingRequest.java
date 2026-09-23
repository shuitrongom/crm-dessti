package com.dessti.crm.platform.empresas.rest;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para actualizar la personalizacion de marca (branding)
 * de la Empresa del Usuario autenticado (Req 26.1, tarea 14.3).
 *
 * <p>Ambos campos son <strong>opcionales</strong>: enviar {@code null} o una
 * cadena en blanco limpia el dato correspondiente (permite establecer o quitar
 * el nombre visible y el logotipo de forma independiente). El {@code tenant_id}
 * NO se acepta en la peticion: la Empresa a personalizar se deriva del contexto
 * autenticado (Req 23.4).</p>
 *
 * <p>El logotipo se admite como una referencia (URL) o un pequeno {@code data
 * URI} en linea; la columna es {@code TEXT}. La cota de longitud definitiva la
 * aplica el dominio ({@code Empresa.actualizarBranding}) devolviendo 422 si se
 * excede; la anotacion {@link Size} aqui ofrece una validacion temprana con un
 * limite alineado.</p>
 *
 * <p>El color primario de marca es igualmente <strong>opcional</strong>:
 * enviar {@code null} lo limpia (restaura el tema corporativo). La anotacion
 * {@link Pattern} solo valida el <em>formato</em> {@code #RRGGBB} cuando llega
 * un valor; por contrato de Bean Validation {@code @Pattern} acepta {@code null}
 * (no lo valida), de modo que un color ausente pasa la validacion y significa
 * "limpiar".</p>
 *
 * @param nombreVisible nombre visible (max. 200); {@code null}/blanco lo limpia.
 * @param logo          logotipo como URL o {@code data URI}
 *                      (max. 1.048.576 caracteres); {@code null}/blanco lo limpia.
 * @param colorPrimario color primario de marca en formato {@code #RRGGBB};
 *                      {@code null} lo limpia. Solo se valida el formato cuando
 *                      viene un valor.
 */
public record ActualizarBrandingRequest(
        @Size(max = 200) String nombreVisible,
        @Size(max = 1_048_576) String logo,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$",
                message = "El color de marca debe tener el formato #RRGGBB.") String colorPrimario) {
}
