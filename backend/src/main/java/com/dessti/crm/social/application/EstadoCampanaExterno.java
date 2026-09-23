package com.dessti.crm.social.application;

/**
 * Instantanea <strong>de SOLO LECTURA</strong> del estado de una
 * Campaña_Publicitaria obtenida de la Marketing API de Meta a traves del
 * {@link PublicacionSocialPort} (Req 65.9). Es un record de frontera hexagonal,
 * inmutable, sin tipos de persistencia ni de framework.
 *
 * <p>La autoridad del estado operativo de la campaña reside en la API externa; el
 * CRM lo presenta como consulta y <strong>no</strong> lo persiste como
 * autoritativo. {@link #disponible()} indica si la consulta pudo resolverse; cuando
 * es {@code false}, {@link #estado()} y {@link #detalle()} pueden venir vacios y
 * {@link #mensaje()} explica el motivo.</p>
 *
 * @param externoId  id de la campaña en el proveedor; obligatorio.
 * @param estado     etiqueta del estado externo (por ejemplo activa/pausada/finalizada).
 * @param detalle    descripcion adicional del estado; opcional.
 * @param disponible {@code true} si la consulta a la API externa se resolvio.
 * @param mensaje    mensaje informativo (motivo de indisponibilidad); opcional.
 */
public record EstadoCampanaExterno(
        String externoId,
        String estado,
        String detalle,
        boolean disponible,
        String mensaje) {

    /**
     * Construye una instantanea <strong>disponible</strong> del estado externo
     * (Req 65.9).
     *
     * @param externoId id de la campaña en el proveedor; obligatorio.
     * @param estado    etiqueta del estado externo; obligatoria.
     * @param detalle   descripcion adicional; opcional.
     * @return la instantanea disponible.
     */
    public static EstadoCampanaExterno disponible(String externoId, String estado, String detalle) {
        return new EstadoCampanaExterno(externoId, estado, detalle, true, null);
    }

    /**
     * Construye una instantanea <strong>no disponible</strong> (la consulta a la API
     * externa no pudo resolverse) (Req 65.9).
     *
     * @param externoId id de la campaña en el proveedor; puede ser {@code null}.
     * @param mensaje   motivo de la indisponibilidad; obligatorio.
     * @return la instantanea no disponible.
     */
    public static EstadoCampanaExterno noDisponible(String externoId, String mensaje) {
        return new EstadoCampanaExterno(externoId, null, null, false, mensaje);
    }
}
