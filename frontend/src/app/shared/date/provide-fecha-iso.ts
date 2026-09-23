import { Provider } from '@angular/core';
import {
  DateAdapter,
  MAT_DATE_FORMATS,
  MAT_DATE_LOCALE,
  MatDateFormats,
  NativeDateAdapter,
} from '@angular/material/core';

import { FechaIsoDateAdapter } from './fecha-iso-date-adapter';

/**
 * Formatos de fecha de Material para es-MX. El calendario muestra y captura las
 * fechas como DD/MM/YYYY (dia y mes de 2 digitos, anio de 4). El
 * FechaIsoDateAdapter mantiene el valor del control como cadena ISO YYYY-MM-DD.
 */
export const FORMATOS_FECHA_ES_MX: MatDateFormats = {
  parse: {
    dateInput: { day: '2-digit', month: '2-digit', year: 'numeric' },
  },
  display: {
    dateInput: { day: '2-digit', month: '2-digit', year: 'numeric' },
    monthYearLabel: { month: 'short', year: 'numeric' },
    dateA11yLabel: { day: 'numeric', month: 'long', year: 'numeric' },
    monthYearA11yLabel: { month: 'long', year: 'numeric' },
  },
};

/**
 * Proveedores del datepicker de Material para toda la app (y reutilizables en
 * los TestBed de componentes con fechas):
 *  - Locale de fechas es-MX (nombres de meses/dias, orden DD/MM/YYYY).
 *  - Formatos de entrada/visualizacion DD/MM/YYYY.
 *  - NativeDateAdapter como token concreto (sin dependencias extra) para la
 *    aritmetica de calendario; el FechaIsoDateAdapter lo inyecta y delega.
 *  - DateAdapter = FechaIsoDateAdapter, cuyo tipo de modelo es la cadena ISO
 *    YYYY-MM-DD, de modo que el control reactivo conserva el mismo formato que
 *    espera el backend, sin desfase de zona horaria.
 */
export function provideFechaIsoDatepicker(): Provider[] {
  return [
    { provide: MAT_DATE_LOCALE, useValue: 'es-MX' },
    { provide: MAT_DATE_FORMATS, useValue: FORMATOS_FECHA_ES_MX },
    NativeDateAdapter,
    { provide: DateAdapter, useClass: FechaIsoDateAdapter },
  ];
}
