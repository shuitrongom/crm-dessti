// =============================================================================
// Pruebas del FechaIsoDateAdapter y sus utilidades de fecha
// -----------------------------------------------------------------------------
// Garantizan la invariante clave del datepicker de la app: el valor del modelo
// (y por tanto del control reactivo) SIEMPRE es la cadena ISO `YYYY-MM-DD`, sin
// desfase de zona horaria. Cubren:
//   - parse cadena ISO -> Date local (sin retroceder un dia)
//   - format Date local -> cadena ISO (sin corrimiento de UTC)
//   - manejo de valores vacios / invalidos
//   - deserialize / parse / today del adaptador conservan el formato ISO
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { MAT_DATE_LOCALE, NativeDateAdapter } from '@angular/material/core';

import {
  FechaIsoDateAdapter,
  fechaIsoALocal,
  localAFechaIso,
} from './fecha-iso-date-adapter';

describe('utilidades de fecha ISO', () => {
  describe('fechaIsoALocal (cadena ISO -> Date local)', () => {
    it('parsea YYYY-MM-DD a un Date en medianoche local (mismo dia)', () => {
      const fecha = fechaIsoALocal('2026-02-05');
      expect(fecha).not.toBeNull();
      expect(fecha!.getFullYear()).toBe(2026);
      expect(fecha!.getMonth()).toBe(1); // febrero = indice 1
      expect(fecha!.getDate()).toBe(5); // NO debe ser 4 por zona horaria
      expect(fecha!.getHours()).toBe(0);
    });

    it('devuelve null para cadena vacia, null o undefined', () => {
      expect(fechaIsoALocal('')).toBeNull();
      expect(fechaIsoALocal(null)).toBeNull();
      expect(fechaIsoALocal(undefined)).toBeNull();
    });

    it('rechaza formatos no ISO y fechas imposibles', () => {
      expect(fechaIsoALocal('05/02/2026')).toBeNull();
      expect(fechaIsoALocal('2026-2-5')).toBeNull();
      expect(fechaIsoALocal('2026-02-31')).toBeNull(); // 31 de feb no existe
      expect(fechaIsoALocal('2026-13-01')).toBeNull(); // mes 13 no existe
    });
  });

  describe('localAFechaIso (Date local -> cadena ISO)', () => {
    it('formatea usando componentes locales, sin corrimiento a UTC', () => {
      // 5 de febrero de 2026, medianoche local. toISOString() podria dar
      // "2026-02-04" en husos negativos; el formateo local debe dar el 5.
      const fecha = new Date(2026, 1, 5);
      expect(localAFechaIso(fecha)).toBe('2026-02-05');
    });

    it('rellena con ceros mes y dia de un solo digito', () => {
      expect(localAFechaIso(new Date(2026, 0, 9))).toBe('2026-01-09');
    });

    it('devuelve cadena vacia para null, undefined o Date invalido', () => {
      expect(localAFechaIso(null)).toBe('');
      expect(localAFechaIso(undefined)).toBe('');
      expect(localAFechaIso(new Date(NaN))).toBe('');
    });

    it('es la inversa de fechaIsoALocal para una fecha valida (ida y vuelta)', () => {
      const iso = '2026-02-05';
      expect(localAFechaIso(fechaIsoALocal(iso))).toBe(iso);
    });
  });
});

describe('FechaIsoDateAdapter', () => {
  let adapter: FechaIsoDateAdapter;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DATE_LOCALE, useValue: 'es-MX' },
        NativeDateAdapter,
        FechaIsoDateAdapter,
      ],
    });
    adapter = TestBed.inject(FechaIsoDateAdapter);
  });

  it('deserialize conserva una cadena ISO YYYY-MM-DD tal cual', () => {
    expect(adapter.deserialize('2026-02-05')).toBe('2026-02-05');
  });

  it('deserialize devuelve null para vacio o null', () => {
    expect(adapter.deserialize('')).toBeNull();
    expect(adapter.deserialize(null)).toBeNull();
  });

  it('isValid distingue fechas ISO validas de invalidas', () => {
    expect(adapter.isValid('2026-02-05')).toBe(true);
    expect(adapter.isValid('2026-02-31')).toBe(false);
    expect(adapter.isValid(adapter.invalid())).toBe(false);
  });

  it('isDateInstance reconoce solo cadenas ISO como instancias de fecha', () => {
    expect(adapter.isDateInstance('2026-02-05')).toBe(true);
    expect(adapter.isDateInstance('05/02/2026')).toBe(false);
    expect(adapter.isDateInstance(new Date())).toBe(false);
  });

  it('createDate produce una cadena ISO (mes 0-indexado)', () => {
    // month = 1 (febrero), day = 5 -> "2026-02-05"
    expect(adapter.createDate(2026, 1, 5)).toBe('2026-02-05');
  });

  it('today devuelve una cadena ISO valida de 10 caracteres', () => {
    const hoy = adapter.today();
    expect(hoy).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(adapter.isValid(hoy)).toBe(true);
  });

  it('extrae anio, mes y dia locales desde la cadena ISO', () => {
    const iso = '2026-02-05';
    expect(adapter.getYear(iso)).toBe(2026);
    expect(adapter.getMonth(iso)).toBe(1);
    expect(adapter.getDate(iso)).toBe(5);
  });

  it('addCalendarDays opera sobre la cadena y devuelve cadena (sin TZ shift)', () => {
    expect(adapter.addCalendarDays('2026-02-05', 1)).toBe('2026-02-06');
    expect(adapter.addCalendarDays('2026-02-28', 1)).toBe('2026-03-01');
  });

  it('addCalendarMonths respeta el fin de mes', () => {
    expect(adapter.addCalendarMonths('2026-01-31', 1)).toBe('2026-02-28');
  });
});
