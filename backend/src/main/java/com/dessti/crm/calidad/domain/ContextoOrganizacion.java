package com.dessti.crm.calidad.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code contexto_organizacion}: una cuestion del
 * contexto de la organizacion pertinente al Sistema de Gestion de Calidad, mapeada
 * sobre la tabla {@code contexto_organizacion} de la migracion V47 (Req 70.5,
 * clausulas 4.1 y 4.2, Req 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}. El
 * mapeo de columnas coincide <em>exactamente</em> con V47.</p>
 *
 * <h2>Cambio climatico (Req 70.5, clausula 4.2)</h2>
 * <p>{@link #climaPertinente} indica si el cambio climatico es una cuestion pertinente.
 * La {@link #justificacion} es <strong>obligatoria en ambos sentidos</strong>: la
 * determinacion de "no pertinente" tambien debe justificarse y su justificacion se
 * conserva.</p>
 */
@Entity
@Table(name = "contexto_organizacion")
public class ContextoOrganizacion extends TenantScopedEntity {

    /** Longitud maxima de la parte interesada (coincide con VARCHAR(200) de V47). */
    static final int MAX_PARTE_INTERESADA = 200;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cuestion interna/externa determinada (Req 70.5, clausula 4.1). */
    @Column(name = "cuestion", nullable = false)
    private String cuestion;

    /** Tipo de la cuestion (interna/externa) (Req 70.5). */
    @Convert(converter = TipoContextoConverter.class)
    @Column(name = "tipo", nullable = false, length = 8)
    private TipoContexto tipo;

    /** Indica si el cambio climatico es pertinente (Req 70.5, clausula 4.2). */
    @Column(name = "clima_pertinente", nullable = false)
    private boolean climaPertinente;

    /** Justificacion de la determinacion; obligatoria aun cuando no sea pertinente. */
    @Column(name = "justificacion", nullable = false)
    private String justificacion;

    /** Parte interesada relacionada; opcional (Req 70.5). */
    @Column(name = "parte_interesada", length = MAX_PARTE_INTERESADA)
    private String parteInteresada;

    /** Expectativa de la parte interesada; opcional (Req 70.5). */
    @Column(name = "expectativa")
    private String expectativa;

    protected ContextoOrganizacion() {
        // Requerido por JPA.
    }

    /**
     * Determina y documenta una cuestion del contexto de la organizacion (Req 70.5). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4). La
     * justificacion es obligatoria con independencia del valor de
     * {@code climaPertinente}, de modo que se conserva incluso al concluir "no
     * pertinente" (clausula 4.2).
     *
     * @param cuestion        cuestion determinada; obligatoria.
     * @param tipo            tipo (interna/externa); obligatorio.
     * @param climaPertinente si el cambio climatico es pertinente.
     * @param justificacion   justificacion de la determinacion; obligatoria.
     * @param parteInteresada parte interesada; opcional.
     * @param expectativa     expectativa de la parte interesada; opcional.
     * @param actor           identificador de origen (auditoria).
     * @return el Contexto_Organizacion listo para persistir.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static ContextoOrganizacion determinar(String cuestion, TipoContexto tipo,
                                                  boolean climaPertinente, String justificacion,
                                                  String parteInteresada, String expectativa,
                                                  String actor) {
        if (cuestion == null || cuestion.isBlank()) {
            throw new ReglaNegocioException("El Contexto_Organizacion debe indicar la cuestion.");
        }
        if (tipo == null) {
            throw new ReglaNegocioException("El Contexto_Organizacion debe indicar el tipo (interna/externa).");
        }
        if (justificacion == null || justificacion.isBlank()) {
            throw new ReglaNegocioException(
                    "El Contexto_Organizacion debe indicar la justificacion, aun cuando la conclusion "
                            + "sea 'no pertinente'.");
        }
        String parte = (parteInteresada == null || parteInteresada.isBlank())
                ? null : parteInteresada.strip();
        if (parte != null && parte.length() > MAX_PARTE_INTERESADA) {
            throw new ReglaNegocioException(
                    "La parte interesada no puede exceder " + MAX_PARTE_INTERESADA + " caracteres.");
        }

        ContextoOrganizacion contexto = new ContextoOrganizacion();
        contexto.id = UUID.randomUUID();
        contexto.cuestion = cuestion.strip();
        contexto.tipo = tipo;
        contexto.climaPertinente = climaPertinente;
        contexto.justificacion = justificacion.strip();
        contexto.parteInteresada = parte;
        contexto.expectativa = (expectativa == null || expectativa.isBlank()) ? null : expectativa.strip();
        contexto.setCreatedBy(actor);
        contexto.setUpdatedBy(actor);
        return contexto;
    }

    public UUID getId() {
        return id;
    }

    public String getCuestion() {
        return cuestion;
    }

    public TipoContexto getTipo() {
        return tipo;
    }

    public boolean isClimaPertinente() {
        return climaPertinente;
    }

    public String getJustificacion() {
        return justificacion;
    }

    public String getParteInteresada() {
        return parteInteresada;
    }

    public String getExpectativa() {
        return expectativa;
    }
}
