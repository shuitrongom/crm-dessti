import { inject, Injectable } from '@angular/core';
import { DateAdapter, NativeDateAdapter } from '@angular/material/core';

/**
 * Formato interno de intercambio de la aplicacion para fechas: cadena ISO
 * `YYYY-MM-DD` (fecha "desnuda", sin hora ni zona horaria). Es el mismo
 * formato que el backend espera y devuelve, por lo que el control reactivo
 * conserva SIEMPRE una cadena `YYYY-MM-DD` (o '' / null cuando esta vacio).
 */

/** Expresion que valida una fecha ISO de solo dia: `YYYY-MM-DD`. */
const REGEX_FECHA_ISO = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Convierte una cadena ISO `YYYY-MM-DD` en un `Date` LOCAL (medianoche local),
 * sin desfase de zona horaria, o `null` si la cadena es vacia o invalida.
 *
 * Usa el constructor local `new Date(anio, mes - 1, dia)` en lugar de
 * `new Date('YYYY-MM-DD')`; este ultimo interpreta la cadena como UTC y, en
 * husos negativos, retrocede un dia (el clasico off-by-one).
 */
export function fechaIsoALocal(iso: string | null | undefined): Date | null {
  if (!iso) {
    return null;
  }
  const partes = REGEX_FECHA_ISO.exec(iso.trim());
  if (!partes) {
    return null;
  }
  const anio = Number(partes[1]);
  const mes = Number(partes[2]);
  const dia = Number(partes[3]);
  const fecha = new Date(anio, mes - 1, dia);
  // Rechaza fechas normalizadas por el motor (p. ej. 2026-02-31 -> marzo).
  if (
    fecha.getFullYear() !== anio ||
    fecha.getMonth() !== mes - 1 ||
    fecha.getDate() !== dia
  ) {
    return null;
  }
  return fecha;
}

/**
 * Formatea un `Date` local como cadena ISO `YYYY-MM-DD` usando los componentes
 * LOCALES (getFullYear/getMonth/getDate) con relleno de ceros.
 *
 * No usa `toISOString()`: ese metodo convierte a UTC y podria cambiar el dia
 * (elegir "5 feb" terminaria como "4 feb" en husos negativos).
 */
export function localAFechaIso(fecha: Date | null | undefined): string {
  if (!fecha || Number.isNaN(fecha.getTime())) {
    return '';
  }
  const anio = fecha.getFullYear().toString().padStart(4, '0');
  const mes = (fecha.getMonth() + 1).toString().padStart(2, '0');
  const dia = fecha.getDate().toString().padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

/**
 * Adaptador de fechas de Material cuyo tipo de modelo es la cadena ISO
 * `YYYY-MM-DD` (en vez de `Date`). Gracias a esto, el `MatDatepickerInput`
 * escribe y lee cadenas ISO directamente en el control reactivo, preservando
 * el formato de envio al backend y evitando errores de zona horaria.
 *
 * La aritmetica de calendario se delega en un `NativeDateAdapter` interno que
 * opera con `Date` locales; solo se convierte a/desde cadena en las fronteras.
 */
@Injectable()
export class FechaIsoDateAdapter extends DateAdapter<string> {
  // Se inyecta (no `new`) porque NativeDateAdapter usa inject() en su
  // constructor y debe crearse dentro de un contexto de inyeccion.
  private readonly nativo = inject(NativeDateAdapter);

  override setLocale(locale: unknown): void {
    super.setLocale(locale);
    this.nativo.setLocale(locale);
  }

  getYear(fecha: string): number {
    return this.nativo.getYear(this.aDate(fecha));
  }

  getMonth(fecha: string): number {
    return this.nativo.getMonth(this.aDate(fecha));
  }

  getDate(fecha: string): number {
    return this.nativo.getDate(this.aDate(fecha));
  }

  getDayOfWeek(fecha: string): number {
    return this.nativo.getDayOfWeek(this.aDate(fecha));
  }

  getMonthNames(estilo: 'long' | 'short' | 'narrow'): string[] {
    return this.nativo.getMonthNames(estilo);
  }

  getDateNames(): string[] {
    return this.nativo.getDateNames();
  }

  getDayOfWeekNames(estilo: 'long' | 'short' | 'narrow'): string[] {
    return this.nativo.getDayOfWeekNames(estilo);
  }

  getYearName(fecha: string): string {
    return this.nativo.getYearName(this.aDate(fecha));
  }

  getFirstDayOfWeek(): number {
    return this.nativo.getFirstDayOfWeek();
  }

  getNumDaysInMonth(fecha: string): number {
    return this.nativo.getNumDaysInMonth(this.aDate(fecha));
  }

  clone(fecha: string): string {
    return fecha;
  }

  createDate(anio: number, mes: number, dia: number): string {
    return this.desdeDate(this.nativo.createDate(anio, mes, dia));
  }

  today(): string {
    return this.desdeDate(this.nativo.today());
  }

  parse(valor: unknown, formato: unknown): string | null {
    // El usuario escribe en formato DD/MM/YYYY (segun MAT_DATE_FORMATS).
    const fecha = this.nativo.parse(valor, formato);
    return fecha ? this.desdeDate(fecha) : null;
  }

  format(fecha: string, formato: object): string {
    return this.nativo.format(this.aDate(fecha), formato);
  }

  addCalendarYears(fecha: string, anios: number): string {
    return this.desdeDate(this.nativo.addCalendarYears(this.aDate(fecha), anios));
  }

  addCalendarMonths(fecha: string, meses: number): string {
    return this.desdeDate(this.nativo.addCalendarMonths(this.aDate(fecha), meses));
  }

  addCalendarDays(fecha: string, dias: number): string {
    return this.desdeDate(this.nativo.addCalendarDays(this.aDate(fecha), dias));
  }

  toIso8601(fecha: string): string {
    return fecha;
  }

  override deserialize(valor: unknown): string | null {
    if (valor == null || valor === '') {
      return null;
    }
    if (typeof valor === 'string') {
      // Ya viene como fecha ISO de solo dia: se conserva tal cual.
      if (REGEX_FECHA_ISO.test(valor.trim())) {
        return valor.trim();
      }
    }
    const fecha = this.nativo.deserialize(valor);
    return fecha ? this.desdeDate(fecha) : null;
  }

  isDateInstance(obj: unknown): boolean {
    return typeof obj === 'string' && REGEX_FECHA_ISO.test(obj);
  }

  isValid(fecha: string): boolean {
    return fechaIsoALocal(fecha) !== null;
  }

  invalid(): string {
    return 'FECHA_INVALIDA';
  }

  /** Convierte el modelo (cadena ISO) al `Date` local que usa el nativo. */
  private aDate(fecha: string): Date {
    return fechaIsoALocal(fecha) ?? this.nativo.invalid();
  }

  /** Convierte un `Date` local del nativo a la cadena ISO del modelo. */
  private desdeDate(fecha: Date): string {
    return localAFechaIso(fecha) || 'FECHA_INVALIDA';
  }
}
