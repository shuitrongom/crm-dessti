// =============================================================================
// Modelos del modulo Calidad / SGC ISO 9001:2026 (Req 70)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (com.dessti.crm.calidad.*). Los instantes llegan como ISO string; los estados
// son las etiquetas ASCII (valorBd) del dominio. El nivel de un Riesgo lo deriva
// el servidor (probabilidad x impacto) y es de solo lectura.
// =============================================================================

// ------------------------------- Queja de cliente --------------------------

/** Estados de una Queja_Cliente (Req 70.1). */
export type EstadoQueja = 'registrada' | 'vinculada' | 'atendida';

/** Origenes de una Queja_Cliente (Req 70.1). */
export type OrigenQueja = 'portal' | 'social' | 'correo' | 'telefono' | 'otro';

/** Queja de cliente / reclamacion (QuejaClienteDto del backend). */
export interface Queja {
  id: string;
  clienteId: string;
  origen: OrigenQueja;
  canalSocialId: string | null;
  descripcion: string;
  estado: EstadoQueja;
  accionCorrectivaId: string | null;
  registradaEn: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /calidad/quejas. */
export interface RegistrarQuejaRequest {
  clienteId: string;
  origen: OrigenQueja;
  canalSocialId: string | null;
  descripcion: string;
}

/** Cuerpo de PUT /calidad/quejas/{id}/vinculo. */
export interface VincularAccionRequest {
  accionCorrectivaId: string;
}

// ------------------------------- Riesgo -------------------------------------

/** Estados de un Riesgo del SGC (Req 70.3). */
export type EstadoRiesgo = 'identificado' | 'en_tratamiento' | 'mitigado' | 'aceptado';

/** Probabilidad de un Riesgo (Req 70.3). */
export type Probabilidad = 'baja' | 'media' | 'alta';

/** Impacto de un Riesgo (Req 70.3). */
export type Impacto = 'bajo' | 'medio' | 'alto';

/** Nivel derivado de un Riesgo (matriz probabilidad x impacto), solo lectura. */
export type NivelRiesgo = 'bajo' | 'medio' | 'alto' | 'critico';

/** Riesgo del SGC (RiesgoDto del backend). */
export interface Riesgo {
  id: string;
  descripcion: string;
  probabilidad: Probabilidad;
  impacto: Impacto;
  nivelDerivado: NivelRiesgo;
  acciones: string | null;
  estado: EstadoRiesgo;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /calidad/riesgos. */
export interface IdentificarRiesgoRequest {
  descripcion: string;
  probabilidad: Probabilidad;
  impacto: Impacto;
  acciones: string | null;
}

// --------------------------- Accion correctiva ------------------------------

/** Estados de una Accion_Correctiva (Req 70.2). */
export type EstadoAccionCorrectiva =
  | 'abierta'
  | 'en_analisis'
  | 'en_ejecucion'
  | 'verificacion'
  | 'cerrada';

/** Accion correctiva del SGC (AccionCorrectivaDto del backend). */
export interface AccionCorrectiva {
  id: string;
  noConformidadId: string | null;
  responsableId: string;
  causaRaiz: string;
  accionesPlanificadas: string;
  evidenciaCierre: string | null;
  eficaciaVerificada: boolean;
  estado: EstadoAccionCorrectiva;
  cerradaEn: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /calidad/acciones-correctivas. */
export interface AbrirAccionCorrectivaRequest {
  noConformidadId: string | null;
  responsableId: string;
  causaRaiz: string;
  accionesPlanificadas: string;
}

/** Cuerpo de PUT /calidad/acciones-correctivas/{id}/verificacion-eficacia. */
export interface VerificarEficaciaRequest {
  evidencia: string | null;
}

// --------------------------- No conformidad ---------------------------------

/** Estados de una No_Conformidad (Req 70.2). */
export type EstadoNoConformidad = 'abierta' | 'en_tratamiento' | 'cerrada';

/** Origenes de una No_Conformidad (Req 70.2). */
export type OrigenNoConformidad =
  | 'queja'
  | 'auditoria_interna'
  | 'proceso'
  | 'proveedor'
  | 'otro';

/** No conformidad del SGC (NoConformidadDto del backend). */
export interface NoConformidad {
  id: string;
  origen: OrigenNoConformidad;
  descripcion: string;
  procesoAfectado: string;
  detectadaEn: string;
  estado: EstadoNoConformidad;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /calidad/no-conformidades. */
export interface RegistrarNoConformidadRequest {
  origen: OrigenNoConformidad;
  descripcion: string;
  procesoAfectado: string;
}

// --------------------------- Oportunidad de calidad -------------------------

/** Estados de una Oportunidad_Calidad (Req 70.3). */
export type EstadoOportunidad =
  | 'identificada'
  | 'en_evaluacion'
  | 'en_ejecucion'
  | 'realizada'
  | 'descartada';

/** Oportunidad de calidad (OportunidadCalidadDto del backend). */
export interface OportunidadCalidad {
  id: string;
  descripcion: string;
  beneficioEsperado: string;
  acciones: string | null;
  estado: EstadoOportunidad;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /calidad/oportunidades. */
export interface IdentificarOportunidadRequest {
  descripcion: string;
  beneficioEsperado: string;
  acciones: string | null;
}

// --------------------------- Cambio del SGC ---------------------------------

/** Estados de un Cambio_SGC (Req 70.4). */
export type EstadoCambioSgc = 'propuesto' | 'aprobado' | 'implementado' | 'rechazado';

/** Cambio del sistema de gestion de calidad (CambioSgcDto del backend). */
export interface CambioSgc {
  id: string;
  titulo: string;
  proposito: string;
  consecuenciasPotenciales: string;
  recursosNecesarios: string;
  responsableId: string;
  estado: EstadoCambioSgc;
  aprobadoPor: string | null;
  aprobadoEn: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /calidad/cambios-sgc. */
export interface ProponerCambioSgcRequest {
  titulo: string;
  proposito: string;
  consecuenciasPotenciales: string;
  recursosNecesarios: string;
  responsableId: string;
}

// --------------------------- Contexto de la organizacion --------------------

/** Tipo de una cuestion del contexto (Req 70.5). */
export type TipoContexto = 'interna' | 'externa';

/** Cuestion del contexto de la organizacion (ContextoOrganizacionDto del backend). */
export interface ContextoOrganizacion {
  id: string;
  cuestion: string;
  tipo: TipoContexto;
  climaPertinente: boolean;
  justificacion: string;
  parteInteresada: string | null;
  expectativa: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /calidad/contexto. */
export interface DeterminarContextoRequest {
  cuestion: string;
  tipo: TipoContexto;
  climaPertinente: boolean;
  justificacion: string;
  parteInteresada: string | null;
  expectativa: string | null;
}

// --------------------------- Indicadores y trazabilidad ---------------------

/** Indicadores de cultura de calidad (IndicadoresCalidadDto del backend). */
export interface IndicadoresCalidad {
  noConformidadesAbiertas: number;
  noConformidadesEnTratamiento: number;
  noConformidadesCerradas: number;
  accionesCorrectivasAbiertas: number;
  accionesCorrectivasCerradas: number;
  tiempoMedioCierreDias: number | null;
  tasaReincidencia: number | null;
  quejasRegistradas: number;
  quejasVinculadas: number;
  quejasAtendidas: number;
}

/** Entrada de la trazabilidad de clausulas ISO 9001 (TrazabilidadClausulaDto). */
export interface TrazabilidadClausula {
  clausula: string;
  tema: string;
  recursoEvidencia: string;
  requisito: string;
}

// --------------------------- Cuerpo generico de cambio de estado ------------

/** Cuerpo de los PUT /{id}/estado (riesgo, no-conformidad, oportunidad, accion). */
export interface CambiarEstadoRequest {
  estado: string;
}

// --------------------------- Catalogos de UI --------------------------------

/** Etiquetas de probabilidad para los selectores. */
export const PROBABILIDADES: { valor: Probabilidad; etiqueta: string }[] = [
  { valor: 'baja', etiqueta: 'Baja' },
  { valor: 'media', etiqueta: 'Media' },
  { valor: 'alta', etiqueta: 'Alta' },
];

/** Etiquetas de impacto para los selectores. */
export const IMPACTOS: { valor: Impacto; etiqueta: string }[] = [
  { valor: 'bajo', etiqueta: 'Bajo' },
  { valor: 'medio', etiqueta: 'Medio' },
  { valor: 'alto', etiqueta: 'Alto' },
];

/** Origenes de queja para los selectores. */
export const ORIGENES_QUEJA: { valor: OrigenQueja; etiqueta: string }[] = [
  { valor: 'portal', etiqueta: 'Portal' },
  { valor: 'social', etiqueta: 'Redes sociales' },
  { valor: 'correo', etiqueta: 'Correo' },
  { valor: 'telefono', etiqueta: 'Teléfono' },
  { valor: 'otro', etiqueta: 'Otro' },
];

/** Origenes de no conformidad para los selectores. */
export const ORIGENES_NO_CONFORMIDAD: { valor: OrigenNoConformidad; etiqueta: string }[] = [
  { valor: 'queja', etiqueta: 'Queja de cliente' },
  { valor: 'auditoria_interna', etiqueta: 'Auditoría interna' },
  { valor: 'proceso', etiqueta: 'Proceso' },
  { valor: 'proveedor', etiqueta: 'Proveedor' },
  { valor: 'otro', etiqueta: 'Otro' },
];
