package com.dessti.crm.comercial.cotizacion.application;

/**
 * Objeto de valor con los <strong>datos fiscales del EMISOR</strong> de una
 * Cotizacion (V60, Req 1). El emisor de una Cotizacion es SIEMPRE la Empresa
 * (tenant) del contexto autenticado, NUNCA la plataforma (Dess-TI): por eso este
 * objeto se deriva de la Empresa via {@link EmpresaEmisorPort} y jamas contiene
 * datos por defecto de plataforma.
 *
 * <p>Se separa deliberadamente de {@code EmisorProperties} (el emisor de
 * plataforma que solo usa el comprobante de renta, Req 2): aquel es configuracion
 * global; este es el reflejo fiscal de una Empresa concreta.</p>
 *
 * <p>Salvo {@code nombre} (la razon social, obligatoria), todos los campos son
 * opcionales: cuando quedan en blanco se normalizan a {@code null} para que el
 * generador del PDF omita esa linea sin romper el layout (degradacion elegante,
 * Req 1.5). El {@code nombre} en blanco cae a un nombre neutro generico
 * ({@value #NOMBRE_NEUTRO}); nunca a un nombre de plataforma.</p>
 *
 * @param nombre          razon social de la Empresa; obligatorio (si viene en
 *                        blanco se sustituye por {@value #NOMBRE_NEUTRO}).
 * @param nombreComercial nombre comercial/marca; opcional (subtitulo).
 * @param rfc             identificador fiscal (RFC); opcional.
 * @param direccion       direccion fiscal en una sola linea; opcional.
 * @param email           correo de contacto; opcional.
 * @param sitioWeb        sitio web; opcional.
 */
public record DatosEmisor(String nombre, String nombreComercial, String rfc,
                          String direccion, String email, String sitioWeb) {

    /** Nombre neutro usado cuando la Empresa no aporta una razon social. */
    public static final String NOMBRE_NEUTRO = "Empresa";

    /**
     * Normaliza los valores: recorta espacios envolventes, convierte los campos
     * opcionales en blanco/{@code null} a {@code null}, y fuerza un {@code nombre}
     * no vacio (cae a {@value #NOMBRE_NEUTRO} si viene en blanco). Nunca introduce
     * datos de plataforma.
     */
    public DatosEmisor {
        nombre = (nombre == null || nombre.isBlank()) ? NOMBRE_NEUTRO : nombre.strip();
        nombreComercial = normalizarOpcional(nombreComercial);
        rfc = normalizarOpcional(rfc);
        direccion = normalizarOpcional(direccion);
        email = normalizarOpcional(email);
        sitioWeb = normalizarOpcional(sitioWeb);
    }

    /**
     * Construye un emisor MINIMO con solo la razon social (el resto de campos
     * quedan vacios). Sirve como fallback defensivo cuando la Empresa del tenant
     * no se puede resolver: el PDF se genera igual con un emisor neutro, nunca con
     * datos de plataforma (Req 3.1).
     *
     * @param nombre razon social; si viene en blanco se usa {@value #NOMBRE_NEUTRO}.
     * @return un {@link DatosEmisor} con solo el nombre poblado.
     */
    public static DatosEmisor minimo(String nombre) {
        return new DatosEmisor(nombre, null, null, null, null, null);
    }

    /**
     * Indica si los datos fiscales del emisor estan INCOMPLETOS para efectos del
     * aviso al administrador (Req 3): se consideran incompletos cuando falta el
     * RFC o la direccion fiscal.
     *
     * @return {@code true} si el RFC o la direccion estan vacios.
     */
    public boolean datosFiscalesIncompletos() {
        return rfc == null || direccion == null;
    }

    private static String normalizarOpcional(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.strip();
    }
}
