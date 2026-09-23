/**
 * Modulo transversal <strong>notificaciones</strong> (Req 46, tarea 43.1). Genera y
 * entrega Notificaciones ante eventos relevantes por correo o por Canal_Social,
 * mediante puertos/adaptadores desacoplados con politica de reintentos configurable.
 *
 * <h2>Puertos (arquitectura hexagonal)</h2>
 * <ul>
 *   <li><strong>Entrada:</strong> {@code NotificacionPort} — fachada que los modulos
 *       productores invocan para solicitar una Notificacion (Req 46.1).</li>
 *   <li><strong>Salida por canal:</strong> {@code NotificadorCorreoPort},
 *       {@code NotificadorWhatsappPort} y {@code NotificadorSocialPort}, cada uno con
 *       un adaptador por defecto de registro en log ({@code @ConditionalOnMissingBean})
 *       para arrancar/probar sin proveedores reales (Req 46.2, 46.6). Las credenciales
 *       se resuelven desde la gestion de secretos (Req 11).</li>
 *   <li><strong>Consentimiento:</strong> {@code ConsentimientoPort} — guarda de Opt_In
 *       para marketing en Canal_Social, con politica segura por defecto (Req 46.7).</li>
 * </ul>
 *
 * <h2>Independencia del modulo social</h2>
 * <p>El {@code NotificadorSocialPort} es la frontera que el modulo social
 * ({@code com.dessti.crm.social}) podra implementar mas adelante; este modulo NO
 * referencia ninguna clase de aquel (Req 46.6).</p>
 *
 * <p>Persistencia: tablas {@code notificacion} e {@code intento_envio_notificacion}
 * (migracion V42), tenant-scoped con RLS (Req 23). Ver design.md.</p>
 */
package com.dessti.crm.notificaciones;
