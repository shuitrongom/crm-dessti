// =============================================================================
// Etiquetas legibles de las areas de indicadores (Req 22, 48)
// -----------------------------------------------------------------------------
// El backend entrega la etiqueta ASCII estable del area (AreaIndicador.etiqueta()).
// Aqui se humaniza para presentacion en espanol. Al no conocer el catalogo
// completo de antemano, se aplica una humanizacion generica (snake_case -> Titulo)
// con sobrescrituras conocidas.
// =============================================================================

/** Sobrescrituras de etiquetas conocidas (ASCII estable -> texto en espanol). */
const ETIQUETAS_AREA: Record<string, string> = {
  comercial: 'Comercial',
  operacion: 'Operación',
  produccion: 'Producción',
  finanzas: 'Finanzas',
  facturacion: 'Facturación',
  compras: 'Compras',
  tesoreria: 'Tesorería',
  contabilidad: 'Contabilidad',
  rh: 'Recursos humanos',
  rh_nomina: 'RH y nómina',
  mantenimiento: 'Mantenimiento',
  calidad: 'Calidad',
  social: 'Redes sociales',
  estrategia: 'Estrategia',
  presupuestos: 'Presupuestos',
  activos: 'Activos fijos',
};

/** Humaniza la etiqueta ASCII de un area a texto legible en espanol. */
export function humanizarArea(area: string | null | undefined): string {
  if (!area) {
    return 'General';
  }
  const clave = area.toLowerCase();
  if (ETIQUETAS_AREA[clave]) {
    return ETIQUETAS_AREA[clave];
  }
  const texto = area.replace(/[_-]/g, ' ').trim();
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
