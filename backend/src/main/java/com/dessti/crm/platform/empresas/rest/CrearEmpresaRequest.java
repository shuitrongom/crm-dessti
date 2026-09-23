package com.dessti.crm.platform.empresas.rest;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta una Empresa (Req 24.2, tarea 14.1).
 *
 * <p>Contiene los datos validos exigidos por el Req 24.2 (nombre, identificador
 * fiscal e instrumento comercial inicial) y los datos del primer Usuario
 * {@code admin_empresa}. El {@code tenant_id} NO se acepta en la peticion: lo
 * genera la propia Empresa creada (su PK es el tenant_id, Req 23.4).</p>
 *
 * <p><strong>Eleccion excluyente del instrumento (Req 4).</strong> El alta
 * asocia el Contrato vigente de la Empresa a <strong>exactamente uno</strong> de
 * los dos instrumentos: un Plan ({@code planId}) <strong>o</strong> un Paquete de
 * Suscripcion ({@code paqueteSuscripcionId}), nunca ambos. Esta regla es un XOR y
 * <strong>no</strong> se expresa con {@link NotNull} sobre un solo campo, por lo
 * que ninguno de los dos lleva anotacion de obligatoriedad; la validacion la
 * impone el servicio {@code ServicioEmpresas.crearEmpresa}, que responde
 * <strong>422</strong> si se indican ambos (Req 4.4) o ninguno (Req 4.3).
 * El indicador {@code otorgarPrueba} solo aplica cuando el instrumento es un
 * Paquete de Suscripcion que {@code admitePrueba}: en ese caso el Contrato nace
 * en estado {@code EN_PRUEBA} con la vigencia calculada por el servicio
 * (Req 4.5); es irrelevante para un Plan.</p>
 *
 * <p>La contrasena del administrador es <strong>opcional</strong>: si se omite,
 * el Sistema genera una temporal y la devuelve una unica vez en la respuesta del
 * alta (Req 11.3). Se valida su longitud solo cuando se proporciona (la anotacion
 * {@link Size} no aplica a valores {@code null}).</p>
 *
 * <p>Los datos DESCRIPTIVOS y de CONTACTO (nombre comercial, telefono, sitio
 * web, direccion desglosada, notas y logo, Req 24) son <strong>opcionales</strong>,
 * con la <strong>excepcion del correo de contacto</strong> ({@code emailContacto}),
 * que es <strong>obligatorio</strong> por ser el destino del envio de la factura
 * por correo. Las anotaciones {@link Size}/{@link Email}/{@link NotBlank} ofrecen
 * validacion temprana alineada con las columnas de la migracion V54; la
 * normalizacion (recorte, correo a minusculas) y la cota definitiva las aplica el
 * dominio ({@code Empresa.asignarDatosDescriptivos}). El logo se admite como URL
 * o {@code data URI} y comparte cota con el branding.</p>
 *
 * @param nombre             nombre de la Empresa; obligatorio.
 * @param rfc                identificador fiscal (RFC); obligatorio (12..13).
 * @param giroId             Giro (vertical de negocio) al que pertenece la
 *                           Empresa; obligatorio (Req 2.1). El servicio valida
 *                           ademas que el Giro exista y este activo (422 si no,
 *                           Req 2.2).
 * @param planId             Plan inicial a asociar; <strong>opcional</strong>,
 *                           excluyente con {@code paqueteSuscripcionId}. Debe
 *                           indicarse exactamente uno de los dos (Req 4.1); el
 *                           servicio responde 422 si se indican ambos o ninguno
 *                           (Req 4.3/4.4).
 * @param paqueteSuscripcionId Paquete de Suscripcion inicial a asociar;
 *                           <strong>opcional</strong>, excluyente con
 *                           {@code planId}. Debe indicarse exactamente uno de los
 *                           dos (Req 4.2); el servicio responde 422 si se indican
 *                           ambos o ninguno (Req 4.3/4.4).
 * @param otorgarPrueba      indica si se otorga el periodo de prueba; solo aplica
 *                           cuando el instrumento es un Paquete de Suscripcion que
 *                           {@code admitePrueba}. Si es {@code true} en ese caso,
 *                           el Contrato nace en estado {@code EN_PRUEBA} con
 *                           Vigencia_Fin = inicio + duracion de la prueba
 *                           (Req 4.5). Se ignora para un Plan.
 * @param adminIdentificador identificador de acceso del primer {@code admin_empresa};
 *                           obligatorio.
 * @param adminPassword      contrasena inicial del {@code admin_empresa};
 *                           opcional (8..255 si se proporciona).
 * @param modulosHabilitados subconjunto de modulos del Plan a habilitar para esta
 *                           Empresa (Req 25.4); <strong>opcional</strong>:
 *                           {@code null} (omitido) = la Empresa hereda todos los
 *                           modulos del Plan; un conjunto (posiblemente vacio) =
 *                           subconjunto especifico (vacio = cero modulos). Debe
 *                           ser subconjunto de {@code plan.modulos_habilitados}
 *                           (si no, 422).
 * @param emailContacto      correo de contacto de la Empresa;
 *                           <strong>obligatorio</strong> (formato de correo,
 *                           1..255). Es el destino al que se envia la factura por
 *                           correo, por lo que el alta lo exige (422 si falta o
 *                           tiene formato invalido). La normalizacion (recorte,
 *                           minusculas) la aplica el dominio
 *                           ({@code Empresa.asignarDatosDescriptivos}).
 */
public record CrearEmpresaRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(min = 12, max = 13) String rfc,
        @NotNull UUID giroId,
        UUID planId,
        UUID paqueteSuscripcionId,
        boolean otorgarPrueba,
        @NotBlank @Size(max = 255) String adminIdentificador,
        @Size(min = 8, max = 255) String adminPassword,
        Set<String> modulosHabilitados,
        @Size(max = 200) String nombreComercial,
        @NotBlank @Email @Size(max = 255) String emailContacto,
        @Size(max = 40) String telefono,
        @Size(max = 255) String sitioWeb,
        @Size(max = 255) String direccionCalle,
        @Size(max = 120) String direccionCiudad,
        @Size(max = 120) String direccionEstado,
        @Size(max = 12) String direccionCp,
        @Size(max = 80) String direccionPais,
        @Size(max = 5000) String notas,
        @Size(max = 1_048_576) String logo) {
}
