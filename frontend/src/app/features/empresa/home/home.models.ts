// =============================================================================
// Modelos de la pagina principal por empresa (Req 26)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan los DTOs del backend integrados por la
// pagina principal de empresa. Los modelos de Estrategia (esencia/objetivos) y de
// Tablero se reutilizan de sus modulos canonicos (estrategia-vistas y reportes)
// para evitar duplicados; aqui solo permanece el modelo propio de Branding:
//   - Branding -> com.empresa.crm.platform.empresas.BrandingDto
// =============================================================================

/**
 * Branding de la empresa (GET /empresa/branding). Req 26.2, 4.1, 6.2.
 * `colorPrimario` es el color primario de marca en formato `#RRGGBB` (o `null`
 * si la empresa no ha configurado color); alimenta la tematizacion por tenant.
 */
export interface Branding {
  nombreVisible: string | null;
  logo: string | null;
  colorPrimario: string | null;
}
