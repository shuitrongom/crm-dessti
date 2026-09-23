// =============================================================================
// Contrato de respuesta paginada (Req 12)
// -----------------------------------------------------------------------------
// Refleja EXACTAMENTE el contrato del backend
// `com.empresa.crm.platform.web.pagination.PaginaResponse`: content + metadatos
// de paginacion (page/size/totalElements/totalPages). Reutilizable por todos los
// listados del servidor.
// =============================================================================

/** Respuesta paginada generica del backend. */
export interface PaginaResponse<T> {
  /** Elementos de la pagina actual. */
  content: T[];
  /** Numero de pagina 0-index. */
  page: number;
  /** Tamano de pagina solicitado. */
  size: number;
  /** Total de elementos en todas las paginas. */
  totalElements: number;
  /** Total de paginas. */
  totalPages: number;
}
