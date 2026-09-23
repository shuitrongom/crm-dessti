package com.dessti.crm.platform.empresas.rest;

import java.util.Set;

/**
 * Cuerpo de la peticion para editar el subconjunto de modulos habilitados de una
 * Empresa sobre su Suscripcion activa (Req 25.4).
 *
 * <p><strong>Semantica null-vs-vacio</strong> (coherente con el resto del
 * flujo):</p>
 * <ul>
 *   <li>{@code modulos == null} (campo omitido o {@code "modulos": null}) = la
 *       Empresa vuelve a HEREDAR todos los modulos del Plan.</li>
 *   <li>{@code modulos} presente (incluido {@code []}) = subconjunto EXACTO; el
 *       arreglo vacio significa cero modulos habilitados. Debe ser subconjunto de
 *       {@code plan.modulos_habilitados} (si no, 422).</li>
 * </ul>
 *
 * @param modulos subconjunto de modulos a habilitar, o {@code null} para heredar
 *                del Plan.
 */
public record ActualizarModulosEmpresaRequest(Set<String> modulos) {
}
