// =============================================================================
// Modelos del modulo RH / Nomina y Organizacion (Req 40, 41, 61)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (com.empresa.crm.rhnomina.*). Los importes llegan como decimales (escala 2).
// =============================================================================

// --- Empleados, contratos e incidencias (Req 40) ----------------------------

/** Empleado (EmpleadoDto). */
export interface Empleado {
  id: string;
  nombre: string;
  rfc: string;
  curp: string;
  nss: string;
  fechaIngreso: string;
  activo: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /rh-nomina/empleados (AltaEmpleadoRequest). */
export interface AltaEmpleadoRequest {
  nombre: string;
  rfc: string;
  curp: string;
  nss: string;
  fechaIngreso: string;
  tipoContrato: string;
  salarioDiario: number;
  periodicidad: string;
  fechaInicio: string;
}

/** Contrato laboral (ContratoLaboralDto). */
export interface ContratoLaboral {
  id: string;
  empleadoId: string;
  tipo: string;
  salarioDiario: number;
  periodicidad: string;
  fechaInicio: string;
  fechaFin: string | null;
  activo: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Incidencia (IncidenciaDto). */
export interface Incidencia {
  id: string;
  empleadoId: string;
  periodoNomina: string;
  tipo: string;
  cantidad: number | null;
  descripcion: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /rh-nomina/empleados/{id}/incidencias (RegistrarIncidenciaRequest). */
export interface RegistrarIncidenciaRequest {
  periodoNomina: string;
  tipo: string;
  cantidad: number | null;
  descripcion: string | null;
}

// --- Nomina y recibos (Req 41) ----------------------------------------------

/** Estados de una Nomina (etiquetas valorBd del backend). */
export type EstadoNomina = 'borrador' | 'calculada' | 'autorizada' | 'timbrada' | 'pagada';

/** Nomina de un periodo (NominaDto). */
export interface Nomina {
  id: string;
  periodoNomina: string;
  estado: EstadoNomina;
  totalPercepciones: number;
  totalDeducciones: number;
  totalNeto: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /rh-nomina/nominas (CrearNominaRequest). */
export interface CrearNominaRequest {
  periodoNomina: string;
}

/** Cuerpo (opcional) de POST /rh-nomina/nominas/{id}/calculo (CalcularNominaRequest). */
export interface CalcularNominaRequest {
  aguinaldo: number | null;
  ptu: number | null;
  tasaInfonavit: number | null;
}

/** Estados de un recibo de nomina. */
export type EstadoRecibo = 'calculado' | 'timbrado' | 'cancelado' | string;

/** Recibo de nomina (ReciboNominaDto). */
export interface ReciboNomina {
  id: string;
  nominaId: string;
  empleadoId: string;
  percepciones: number;
  deducciones: number;
  subsidio: number;
  neto: number;
  estado: EstadoRecibo;
  folioFiscal: string | null;
  fechaTimbrado: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

// --- Organizacion de personal (Req 61) --------------------------------------

/** Puesto (PuestoDto). */
export interface Puesto {
  id: string;
  nombre: string;
  descripcion: string | null;
  puestoSuperiorId: string | null;
  activo: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /rh-nomina/puestos (CrearPuestoRequest). */
export interface CrearPuestoRequest {
  nombre: string;
  descripcion: string | null;
  puestoSuperiorId: string | null;
}

/** Cuerpo de POST /rh-nomina/asignaciones-puesto (AsignarEmpleadoRequest). */
export interface AsignarEmpleadoRequest {
  empleadoId: string;
  puestoId: string;
  fechaInicio: string;
}

/** Asignacion de puesto (AsignacionPuestoDto). */
export interface AsignacionPuesto {
  id: string;
  empleadoId: string;
  puestoId: string;
  fechaInicio: string;
  fechaFin: string | null;
  activa: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Evaluacion de desempeno (EvaluacionDesempenoDto). */
export interface EvaluacionDesempeno {
  id: string;
  empleadoId: string;
  periodo: string;
  calificacion: number;
  comentarios: string | null;
  evaluadaEn: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /rh-nomina/evaluaciones (RegistrarEvaluacionRequest). */
export interface RegistrarEvaluacionRequest {
  empleadoId: string;
  periodo: string;
  calificacion: number;
  comentarios: string | null;
}

/** Nodo del organigrama (OrganigramaNodoDto), recursivo. */
export interface OrganigramaNodo {
  puestoId: string;
  nombre: string;
  superiorId: string | null;
  subordinados: OrganigramaNodo[];
}
