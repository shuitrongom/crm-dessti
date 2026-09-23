package com.dessti.crm.calidad.application;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Servicio de aplicacion de <strong>solo lectura</strong> que publica la vista de
 * trazabilidad de clausulas ISO 9001:2026 -> capacidades del Sistema (Req 70.10,
 * clausula 7.5). Cada clausula soportada se mapea al recurso o evidencia del Sistema que
 * la habilita, con el requisito de referencia.
 *
 * <p>El contenido es un <strong>catalogo fijo e inmutable</strong> mantenido en codigo
 * (basado en la tabla de trazabilidad del diseno, seccion Data Models); no se persiste ni
 * depende del tenant. La evidencia documentada (clausula 7.5) se satisface reutilizando el
 * Servicio_Auditoria (Req 10, 70.6): cada operacion sobre recursos de calidad genera un
 * Registro_Auditoria encadenado; este servicio solo <em>mapea</em> clausulas a recursos y
 * no copia datos de auditoria.</p>
 */
@Service
public class ServicioTrazabilidadIso {

    /**
     * Catalogo inmutable de trazabilidad clausula -> capacidad del Sistema (Req 70.10),
     * basado en la tabla de trazabilidad del diseno. El orden refleja la secuencia de
     * clausulas de la norma.
     */
    private static final List<TrazabilidadClausulaDto> CATALOGO = List.of(
            new TrazabilidadClausulaDto("4.1, 4.2",
                    "Contexto de la organizacion; pertinencia del cambio climatico; partes interesadas",
                    "Contexto_Organizacion", "Req 70.5"),
            new TrazabilidadClausulaDto("5.1.1, 7.3",
                    "Liderazgo: cultura de calidad y comportamiento etico; toma de conciencia",
                    "Indicadores de cultura de calidad en el Tablero", "Req 70.7, 22"),
            new TrazabilidadClausulaDto("5.2.1",
                    "Politica de calidad alineada al contexto y a la direccion estrategica",
                    "Planeacion estrategica y Esencia_Empresa", "Req 58"),
            new TrazabilidadClausulaDto("6.1.2",
                    "Riesgos (determinar, analizar, evaluar)",
                    "Riesgo", "Req 70.3"),
            new TrazabilidadClausulaDto("6.1.3",
                    "Oportunidades (tratadas por separado)",
                    "Oportunidad_Calidad", "Req 70.3"),
            new TrazabilidadClausulaDto("6.3",
                    "Gestion del cambio del SGC",
                    "Cambio_SGC", "Req 70.4"),
            new TrazabilidadClausulaDto("7.5",
                    "Informacion documentada disponible como evidencia",
                    "Auditoria inmutable con integridad/no repudio", "Req 70.6, 10"),
            new TrazabilidadClausulaDto("8.2.1",
                    "Comunicacion con el cliente; contingencias",
                    "Mensajeria omnicanal y notificaciones", "Req 64, 46"),
            new TrazabilidadClausulaDto("9.1.2 (nota)",
                    "Redes sociales como fuente de percepcion del cliente",
                    "Analitica social integrada a la satisfaccion", "Req 70.8, 66"),
            new TrazabilidadClausulaDto("10.1 (Anexo A)",
                    "Mejora continua; digitalizacion, automatizacion y datos confiables",
                    "Tablero e Inteligencia de Negocio", "Req 22, 48"),
            new TrazabilidadClausulaDto("10.2",
                    "No conformidad y accion correctiva; la queja como entrada potencial",
                    "Queja_Cliente, No_Conformidad, Accion_Correctiva", "Req 70.1, 70.2"));

    /**
     * Devuelve la vista de trazabilidad de clausulas ISO 9001:2026 (Req 70.10). Es una
     * lista inmutable, estable e independiente del tenant.
     *
     * @return el catalogo de trazabilidad clausula -> recurso/evidencia.
     */
    public List<TrazabilidadClausulaDto> trazabilidad() {
        return CATALOGO;
    }
}
