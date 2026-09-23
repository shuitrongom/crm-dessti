package com.dessti.crm.platform.vertical;

/**
 * Metadato de un elemento de navegacion aportado por un Modulo-Vertical.
 *
 * <p>Cada item describe una entrada de menu/ruta especifica del vertical que el
 * Nucleo expone al frontend dentro del contexto de sesion de los Usuarios cuya
 * Empresa pertenece al Giro correspondiente (Req 9.2). Es un value object
 * inmutable de solo metadato: no contiene logica de negocio ni referencia a
 * clases de persistencia, coherente con el caracter hexagonal-puro del
 * {@link ContratoVertical}.</p>
 *
 * <p>El par {@code (recurso, operacion)} identifica el permiso atomico RBAC
 * requerido para que el item sea visible/accesible, con el mismo formato
 * {@code recurso:operacion} del Nucleo (Req 7.1), permitiendo al frontend
 * filtrar la navegacion por permiso ademas de por Giro (Req 9.3).</p>
 *
 * @param etiqueta  texto visible del item de navegacion (p. ej.
 *                  {@code "Ordenes de Fabricacion"}).
 * @param ruta      ruta de frontend asociada al item (p. ej.
 *                  {@code "/empresa/operacion/ordenes-fabricacion"}).
 * @param icono     identificador del icono a mostrar junto a la etiqueta.
 * @param recurso   recurso RBAC requerido para acceder al item (p. ej.
 *                  {@code "orden_fabricacion"}).
 * @param operacion operacion RBAC requerida sobre el recurso (p. ej.
 *                  {@code "listar"}).
 */
public record ItemNavegacionVertical(
        String etiqueta,
        String ruta,
        String icono,
        String recurso,
        String operacion) {
}
