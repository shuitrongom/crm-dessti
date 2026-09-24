package com.dessti.crm.contabilidad.electronica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del catalogo oficial de <strong>codigos agrupadores del SAT</strong>
 * (Apartado B del Anexo 24 de la RMF), mapeada sobre la tabla
 * {@code codigo_agrupador_sat_catalogo} de la migracion V71.
 *
 * <p>Es un <strong>dato de plataforma</strong> comun a todas las Empresas (como
 * {@code permiso} o {@code plan}): NO es tenant-scoped, por lo que no hereda de
 * {@code TenantScopedEntity} ni lleva RLS. Los tenants solo lo consultan (solo
 * lectura) para "amarrar" sus Cuentas_Contables al codigo correspondiente y para
 * generar la Contabilidad Electronica.</p>
 *
 * <p>La clave primaria es el propio {@code codigo} (p. ej. {@code 101.01}). El
 * {@code nivel} distingue cuenta de mayor (1) de subcuenta de primer nivel (2), y
 * la {@code naturaleza} es {@code D} (deudora) o {@code A} (acreedora), conforme al
 * Anexo 24.</p>
 */
@Entity
@Table(name = "codigo_agrupador_sat_catalogo")
public class CodigoAgrupadorSat {

    @Id
    @Column(name = "codigo", nullable = false, updatable = false, length = 10)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "nivel", nullable = false)
    private short nivel;

    @Column(name = "naturaleza", nullable = false, length = 1)
    private String naturaleza;

    @Column(name = "codigo_padre", length = 10)
    private String codigoPadre;

    protected CodigoAgrupadorSat() {
        // Requerido por JPA.
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public short getNivel() {
        return nivel;
    }

    public String getNaturaleza() {
        return naturaleza;
    }

    public String getCodigoPadre() {
        return codigoPadre;
    }
}
