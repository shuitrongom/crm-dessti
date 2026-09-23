package com.dessti.crm.platform.empresas;

import java.util.Set;
import java.util.UUID;

/**
 * Comando de aplicacion para dar de alta una Empresa (Req 24.2), desacoplado de
 * las entidades JPA.
 *
 * <p>Incluye los datos validos exigidos por el Req 24.2 (nombre, identificador
 * fiscal y el instrumento de contratacion inicial) y los datos del primer
 * Usuario {@code admin_empresa} que se aprovisiona junto con la Empresa. El
 * {@code tenant_id} NO forma parte del comando: lo genera la propia Empresa
 * creada (su PK es el tenant_id).</p>
 *
 * <p><strong>Instrumento de contratacion excluyente (Req 4.1-4.5):</strong> el
 * alta contrata la Empresa con <strong>exactamente uno</strong> de los dos
 * instrumentos comerciales, nunca ambos ni ninguno:</p>
 * <ul>
 *   <li>un <strong>Plan</strong> ({@code planId} no nulo, {@code paqueteSuscripcionId}
 *       nulo): contrato de largo plazo, sin periodo de prueba; o</li>
 *   <li>una <strong>Suscripcion</strong> ({@code paqueteSuscripcionId} no nulo,
 *       {@code planId} nulo): contrato de corto plazo (Paquete de Suscripcion),
 *       con la opcion de otorgar periodo de prueba.</li>
 * </ul>
 * <p>Indicar ambos o ninguno viola la exclusividad y el servicio responde 422
 * ({@code ReglaNegocioException}). El indicador {@code otorgarPrueba} solo aplica
 * cuando se contrata una Suscripcion cuyo Paquete admite prueba.</p>
 *
 * <p><strong>Contrasena inicial (secreto, Req 11.3):</strong> {@code adminPassword}
 * viaja en claro solo en este comando de entrada; el servicio la cifra de
 * inmediato con {@code PasswordEncoder} y jamas se persiste, registra ni audita
 * en claro. Si se omite, el servicio genera una contrasena temporal aleatoria y
 * la devuelve <em>una unica vez</em> en la respuesta del alta.</p>
 *
 * <p><strong>Subconjunto de modulos (Req 25.4):</strong> {@code modulosHabilitados}
 * permite al {@code super_admin} habilitar para ESTA Empresa solo un subconjunto
 * de los modulos que ofrece el Plan inicial. Semantica null-vs-vacio:</p>
 * <ul>
 *   <li>{@code null} = la Empresa HEREDA todos los modulos del Plan
 *       (comportamiento historico).</li>
 *   <li>un conjunto (posiblemente vacio) = la Empresa recibe EXACTAMENTE esos
 *       modulos (el conjunto vacio = cero modulos habilitados). Debe ser
 *       subconjunto de {@code plan.modulos_habilitados}, si no el servicio
 *       responde 422.</li>
 * </ul>
 *
 * @param nombre                nombre de la Empresa; obligatorio.
 * @param rfc                   identificador fiscal (RFC); obligatorio.
 * @param giroId                Giro (vertical de negocio) al que pertenece la
 *                              Empresa; obligatorio (Req 2.1). La obligatoriedad
 *                              la refuerza el dominio ({@code Empresa.crear}); la
 *                              validacion de que el Giro EXISTE y esta ACTIVO
 *                              (422 si no, Req 2.2) la aplica el servicio en la
 *                              tarea 4.3.
 * @param planId                Plan inicial a asociar; <strong>opcional</strong>.
 *                              Debe indicarse EXACTAMENTE uno de
 *                              {@code planId}/{@code paqueteSuscripcionId} (Req 4.3):
 *                              cuando se contrata un Plan viaja {@code planId} y
 *                              {@code paqueteSuscripcionId} es {@code null}.
 * @param paqueteSuscripcionId  Paquete de Suscripcion inicial a asociar;
 *                              <strong>opcional</strong> y excluyente con
 *                              {@code planId} (Req 4.3): cuando se contrata una
 *                              Suscripcion viaja este identificador y
 *                              {@code planId} es {@code null}.
 * @param otorgarPrueba         cuando se contrata una Suscripcion, {@code true}
 *                              solicita crear el contrato en periodo de prueba
 *                              (estado {@code EN_PRUEBA}); solo es valido si el
 *                              Paquete elegido admite prueba. Se ignora cuando se
 *                              contrata un Plan (Req 4.2).
 * @param adminIdentificador    identificador de acceso del primer
 *                              {@code admin_empresa}; obligatorio.
 * @param adminPassword         contrasena inicial del {@code admin_empresa} en
 *                              claro; opcional (si es {@code null}/vacia se
 *                              genera una temporal).
 * @param modulosHabilitados    subconjunto de modulos del Plan a habilitar para
 *                              esta Empresa; opcional ({@code null} = heredar
 *                              todos los del Plan). Debe ser subconjunto del
 *                              Plan (Req 25.4).
 * @param datos                 datos descriptivos/de contacto opcionales de la
 *                              Empresa (nombre comercial, correo, telefono, sitio
 *                              web, direccion, notas y logo, Req 24); opcional
 *                              ({@code null} = no se registran). El servicio los
 *                              asigna a la Empresa antes de persistirla para que
 *                              queden en el mismo alta.
 */
public record CrearEmpresaCommand(
        String nombre,
        String rfc,
        UUID giroId,
        UUID planId,
        UUID paqueteSuscripcionId,
        boolean otorgarPrueba,
        String adminIdentificador,
        String adminPassword,
        Set<String> modulosHabilitados,
        DatosDescriptivosEmpresa datos) {
}
