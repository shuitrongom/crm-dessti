// =============================================================================
// Modelos del modulo Facturacion CFDI (Req 34, 35, 37)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (com.empresa.crm.facturacion.*). Los importes llegan como decimales (BigDecimal
// escala 2) y se formatean con pipes; el Folio_Fiscal es el UUID del SAT.
// =============================================================================

/** Estados de una Factura CFDI (etiquetas valorBd del backend). */
export type EstadoFactura =
  | 'borrador'
  | 'timbrada'
  | 'cancelacion_en_proceso'
  | 'cancelada';

/** Factura CFDI (FacturaDto del backend). */
export interface Factura {
  id: string;
  cotizacionId: string | null;
  ordenFabricacionId: string | null;
  clienteId: string;
  receptorRfc: string;
  receptorNombre: string;
  receptorCp: string;
  receptorRegimenFiscal: string;
  usoCfdi: string;
  subtotal: number;
  iva: number;
  retenciones: number;
  total: number;
  estado: EstadoFactura;
  /** Folio_Fiscal (UUID del SAT); null en borrador. */
  folioFiscal: string | null;
  fechaTimbrado: string | null;
  motivoCancelacion: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /facturacion/facturas (emitir CFDI). */
export interface EmitirFacturaRequest {
  cotizacionId: string | null;
  ordenFabricacionId: string | null;
  receptorRfc: string;
  receptorNombre: string;
  receptorCp: string;
  receptorRegimenFiscal: string;
  usoCfdi: string;
  tasaRetencion: number | null;
}

/** Cuerpo de POST /facturacion/facturas/{id}/cancelacion. */
export interface CancelarFacturaRequest {
  motivoSat: string;
}

/** Estados de una Nota de Credito (etiquetas valorBd del backend). */
export type EstadoNotaCredito = 'borrador' | 'timbrada' | 'cancelada';

/** Nota de Credito / CFDI de egreso (NotaCreditoDto del backend). */
export interface NotaCredito {
  id: string;
  facturaId: string;
  clienteId: string;
  monto: number;
  estado: EstadoNotaCredito;
  folioFiscal: string | null;
  fechaTimbrado: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /facturacion/notas-credito (emitir). */
export interface EmitirNotaCreditoRequest {
  facturaId: string;
  monto: number;
}

/** Catalogo minimo de motivos de cancelacion del SAT (Req 35.4). */
export const MOTIVOS_CANCELACION_SAT: { clave: string; etiqueta: string }[] = [
  { clave: '01', etiqueta: '01 - Comprobante emitido con errores con relacion' },
  { clave: '02', etiqueta: '02 - Comprobante emitido con errores sin relacion' },
  { clave: '03', etiqueta: '03 - No se llevo a cabo la operacion' },
  { clave: '04', etiqueta: '04 - Operacion nominativa en factura global' },
];
