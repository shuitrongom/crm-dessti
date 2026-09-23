// =============================================================================
// Modelos del modulo Estrategia (vistas de negocio) (Req 58)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (com.empresa.crm.estrategia.*). Vista de negocio mas completa que la pantalla
// de administracion existente: esencia editable, objetivos con resultados clave
// ponderados y avance/estado derivado.
// =============================================================================

/** Esencia de la empresa (EsenciaEmpresaDto). */
export interface EsenciaEmpresa {
  id: string;
  mision: string | null;
  vision: string | null;
  valores: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de PUT /estrategia/esencia (GuardarEsenciaRequest). */
export interface GuardarEsenciaRequest {
  mision: string | null;
  vision: string | null;
  valores: string | null;
}

/** Resultado clave ponderado (ResultadoClaveDto). */
export interface ResultadoClave {
  id: string;
  descripcion: string;
  valorObjetivo: number;
  valorActual: number;
  peso: number;
}

/** Objetivo estrategico con avance y estado derivado (ObjetivoEstrategicoDto). */
export interface ObjetivoEstrategico {
  id: string;
  nombre: string;
  responsable: string;
  periodoInicio: string;
  periodoFin: string;
  meta: string;
  avance: number;
  /** en_riesgo | en_curso | cumplido. */
  estadoDerivado: string;
  resultadosClave: ResultadoClave[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /estrategia/objetivos (CrearObjetivoRequest). */
export interface CrearObjetivoRequest {
  nombre: string;
  responsable: string;
  periodoInicio: string;
  periodoFin: string;
  meta: string;
}

/** Cuerpo de POST /estrategia/objetivos/{id}/resultados-clave (AgregarResultadoClaveRequest). */
export interface AgregarResultadoClaveRequest {
  descripcion: string;
  valorObjetivo: number;
  valorActual: number;
  peso: number;
}
