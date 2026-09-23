/**
 * Jerarquia de excepciones de dominio reutilizable por los modulos de negocio.
 *
 * <p>Estas excepciones expresan condiciones de negocio (recurso no encontrado,
 * conflicto de unicidad, regla de negocio incumplida, transicion de estado
 * invalida, acceso no autorizado) de forma independiente del framework. El
 * manejador global de errores ({@code com.dessti.crm.platform.web}) las
 * traduce a respuestas HTTP con formato Problem Details (RFC 7807).</p>
 */
package com.dessti.crm.platform.error;
