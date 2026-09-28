package com.dessti.crm.operacion.proyecto.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code avance_sitio} (V78): materializa la {@link FaseSitioGenerica}
 * editable de un {@link Sitio} para Proyectos multi-sitio de giros genericos (Req 3.2,
 * 21.2). Mapea 1:1 (opcional) con un Sitio: a lo sumo una fila por Sitio.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignada al persistir, nunca desde la peticion, Req 23.4),
 * {@code version} (Req 49) y las marcas de auditoria. El mapeo coincide con V78.</p>
 */
@Entity
@Table(name = "avance_sitio")
public class AvanceSitio extends TenantScopedEntity {

    /** Longitud maxima de la nota (coincide con VARCHAR(500) de V78). */
    public static final int LONGITUD_MAXIMA_NOTA = 500;

    /** Longitud maxima de la referencia de evidencia (coincide con VARCHAR(1000) de V78). */
    public static final int LONGITUD_MAXIMA_EVIDENCIA = 1000;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Sitio al que pertenece el avance (Req 21.2). Inmutable; FK a {@code sitio}. */
    @Column(name = "sitio_id", nullable = false, updatable = false)
    private UUID sitioId;

    /** Fase operativa generica actual del Sitio (Req 3.2). */
    @Convert(converter = FaseSitioGenericaConverter.class)
    @Column(name = "fase", nullable = false)
    private FaseSitioGenerica fase;

    /** Nota opcional del avance; {@code null} si no se proporciono. */
    @Column(name = "nota")
    private String nota;

    /** Referencia opcional a la evidencia que respalda la fase; {@code null} si no hay. */
    @Column(name = "evidencia_url")
    private String evidenciaUrl;

    protected AvanceSitio() {
        // Requerido por JPA.
    }

    /**
     * Crea el avance inicial de un Sitio en fase {@link FaseSitioGenerica#PENDIENTE}
     * (Req 3.2). El {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir.
     *
     * @param sitioId identificador del Sitio; obligatorio.
     * @param actor   identificador de quien crea, para auditoria (Req 21.6).
     * @return el avance listo para persistir, en fase inicial.
     * @throws ReglaNegocioException si falta el Sitio.
     */
    public static AvanceSitio inicial(UUID sitioId, String actor) {
        if (sitioId == null) {
            throw new ReglaNegocioException("El avance debe asociarse a un Sitio existente.");
        }
        AvanceSitio avance = new AvanceSitio();
        avance.id = UUID.randomUUID();
        avance.sitioId = sitioId;
        avance.fase = FaseSitioGenerica.PENDIENTE;
        avance.setCreatedBy(actor);
        avance.setUpdatedBy(actor);
        return avance;
    }

    /**
     * Avanza la fase del Sitio validando la maquina de estados lineal (Pendiente
     * &rarr; Preparacion &rarr; Instalacion &rarr; Entrega, Req 3.2). Es la operacion
     * de uso comun (gobernada por {@code proyecto:actualizar}); NO permite retroceder.
     *
     * @param destino      fase destino; obligatoria.
     * @param nota         nota opcional del avance (hasta 500 caracteres).
     * @param evidenciaUrl referencia opcional a la evidencia que respalda la fase.
     * @param actor        identificador de quien actualiza, para auditoria.
     * @throws TransicionInvalidaException si la transicion no es valida (409).
     * @throws ReglaNegocioException si la nota o la evidencia exceden el maximo.
     */
    public void avanzarFase(FaseSitioGenerica destino, String nota, String evidenciaUrl,
                            String actor) {
        aplicarCambio(destino, nota, evidenciaUrl, actor, false);
    }

    /**
     * Corrige la fase del Sitio a cualquier fase, incluido un RETROCESO (Req 3.2). Es
     * una operacion administrativa sensible (gobernada por {@code proyecto:cambiar_estado})
     * para deshacer avances marcados por error; no aplica la restriccion lineal.
     *
     * @param destino      fase destino; obligatoria (puede ser anterior a la actual).
     * @param nota         nota opcional (motivo de la correccion).
     * @param evidenciaUrl referencia opcional a la evidencia.
     * @param actor        identificador de quien corrige, para auditoria.
     * @throws ReglaNegocioException si la nota o la evidencia exceden el maximo.
     */
    public void corregirFase(FaseSitioGenerica destino, String nota, String evidenciaUrl,
                             String actor) {
        aplicarCambio(destino, nota, evidenciaUrl, actor, true);
    }

    /**
     * Aplica el cambio de fase. Con {@code permitirRetroceso=false} exige que la
     * transicion respete la maquina lineal (avance); con {@code true} admite cualquier
     * fase (correccion administrativa). Un {@code destino} igual a la fase actual es
     * idempotente en ambos modos (solo actualiza nota/evidencia).
     */
    private void aplicarCambio(FaseSitioGenerica destino, String nota, String evidenciaUrl,
                               String actor, boolean permitirRetroceso) {
        if (destino == null) {
            throw new ReglaNegocioException("La fase destino es obligatoria.");
        }
        if (!permitirRetroceso && destino != this.fase && !this.fase.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "No se puede pasar la fase del Sitio de '" + this.fase.valorBd()
                            + "' a '" + destino.valorBd() + "'. Usa la correccion administrativa "
                            + "para retroceder o saltar fases.");
        }
        this.fase = destino;
        this.nota = normalizarNota(nota);
        this.evidenciaUrl = normalizarEvidencia(evidenciaUrl);
        setUpdatedBy(actor);
    }

    private static String normalizarNota(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOTA) {
            throw new ReglaNegocioException(
                    "La nota del avance no puede exceder " + LONGITUD_MAXIMA_NOTA + " caracteres.");
        }
        return normalizado;
    }

    private static String normalizarEvidencia(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_EVIDENCIA) {
            throw new ReglaNegocioException(
                    "La referencia de evidencia no puede exceder " + LONGITUD_MAXIMA_EVIDENCIA
                            + " caracteres.");
        }
        return normalizado;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSitioId() {
        return sitioId;
    }

    public FaseSitioGenerica getFase() {
        return fase;
    }

    public String getNota() {
        return nota;
    }

    public String getEvidenciaUrl() {
        return evidenciaUrl;
    }
}
