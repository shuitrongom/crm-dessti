package com.dessti.crm.social.domain;

import java.util.Locale;

/**
 * Canal_Social soportado por la mensajeria omnicanal y las publicaciones sociales
 * de la plataforma (Req 64.1, 64.2; spec redes-sociales-conexiones Req 1): WhatsApp,
 * Facebook, Instagram, Messenger y TikTok. Sigue el patron de enum con etiqueta de
 * base de datos de {@code EstadoActivoFijo}.
 *
 * <p>WhatsApp, Facebook, Instagram y Messenger pertenecen al ecosistema de Meta
 * (Meta Graph API); <strong>TikTok NO es de Meta</strong> y se integra por su propia
 * API (TikTok Content Posting API). El dominio del enum es agnostico al proveedor:
 * cada canal se cablea tras su puerto/adaptador correspondiente.</p>
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'whatsapp'}, {@code 'facebook'}, {@code 'instagram'},
 * {@code 'messenger'} o {@code 'tiktok'}, tal como admiten los CHECK de las
 * migraciones V41/V43 relajados por V63. El {@link CanalSocialConverter} traduce
 * entre el enum y esta etiqueta.</p>
 */
public enum CanalSocial {

    /** WhatsApp Business (Req 64.1). */
    WHATSAPP("whatsapp"),

    /** Facebook (paginas de Facebook; spec redes-sociales-conexiones Req 1). */
    FACEBOOK("facebook"),

    /** Facebook Messenger (Req 64.1). */
    MESSENGER("messenger"),

    /** Instagram Direct (Req 64.1). */
    INSTAGRAM("instagram"),

    /** TikTok (NO pertenece a Meta; API propia; spec redes-sociales-conexiones Req 1). */
    TIKTOK("tiktok");

    private final String valorBd;

    CanalSocial(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en las columnas {@code canal}, en minusculas ASCII, tal
     * como la admiten los CHECK de V41/V43 relajados por V63.
     *
     * @return la etiqueta de base de datos del canal.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el canal a partir de su etiqueta de base de datos (inversa de
     * {@link #valorBd()}). La comparacion es insensible a mayusculas y recorta
     * espacios.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'whatsapp'}).
     * @return el canal correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static CanalSocial desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El Canal_Social no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (CanalSocial canal : values()) {
            if (canal.valorBd.equals(normalizado)) {
                return canal;
            }
        }
        throw new IllegalArgumentException("Canal_Social desconocido: " + valor);
    }
}
