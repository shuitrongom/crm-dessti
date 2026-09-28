package com.dessti.crm.operacion.proyecto.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Fase operativa <strong>generica</strong> de un Sitio para Proyectos multi-sitio de
 * giros que no son anuncios luminosos (Req 3.2, 21.2). A diferencia del avance de
 * anuncios —que se DERIVA de otros modulos (levantamiento/permiso/OF/instalacion)—,
 * esta fase se MATERIALIZA y se avanza manualmente por el usuario sobre la tabla
 * {@code avance_sitio} (V78), y sirve a clientes con muchas sucursales (cadenas
 * bancarias, grupos comerciales) para seguir el despliegue sitio por sitio.
 *
 * <p>Secuencia lineal e irreversible salvo correccion administrativa:
 * {@link #PENDIENTE} &rarr; {@link #EN_PREPARACION} &rarr; {@link #EN_INSTALACION}
 * &rarr; {@link #ENTREGADO}. {@link #ENTREGADO} es el estado final.</p>
 *
 * <p>Es un enum de dominio puro (sin Spring ni JPA): la persistencia usa su
 * {@link #valorBd()} y la reconstruccion {@link #desdeValorBd(String)}.</p>
 */
public enum FaseSitioGenerica {

    /** El Sitio aun no inicia trabajos (estado inicial por defecto). */
    PENDIENTE("pendiente"),

    /** Preparacion/acopio previos a la instalacion en el Sitio. */
    EN_PREPARACION("en_preparacion"),

    /** Instalacion/despliegue en curso en el Sitio. */
    EN_INSTALACION("en_instalacion"),

    /** Sitio entregado y aceptado (estado final). */
    ENTREGADO("entregado");

    /**
     * Maquina de estados pura de la fase generica del Sitio. Avance lineal; el
     * estado final {@link #ENTREGADO} no declara transiciones salientes.
     */
    private static final MaquinaEstados<FaseSitioGenerica> MAQUINA =
            MaquinaEstados.<FaseSitioGenerica>builder(FaseSitioGenerica.class)
                    .permitir(PENDIENTE, EN_PREPARACION)
                    .permitir(EN_PREPARACION, EN_INSTALACION)
                    .permitir(EN_INSTALACION, ENTREGADO)
                    .construir();

    private final String valorBd;

    FaseSitioGenerica(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code avance_sitio.fase}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos de la fase.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye la fase a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'en_instalacion'}).
     * @return la fase correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static FaseSitioGenerica desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("La fase del Sitio no puede ser nula.");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (FaseSitioGenerica fase : values()) {
            if (fase.valorBd.equals(normalizado)) {
                return fase;
            }
        }
        throw new IllegalArgumentException("Fase de Sitio desconocida: " + valor);
    }

    /**
     * Indica si desde esta fase se puede transitar a {@code destino} segun la
     * maquina de estados lineal.
     *
     * @param destino fase destino pretendida; obligatoria.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(FaseSitioGenerica destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }

    /**
     * Indica si esta fase es final ({@link #ENTREGADO}).
     *
     * @return {@code true} si es final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }
}
