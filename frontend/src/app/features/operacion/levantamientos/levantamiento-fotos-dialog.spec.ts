// =============================================================================
// Pruebas del dialogo LevantamientoFotosDialog (Req 12)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Al montar hace GET /levantamientos/{id}/fotos y muestra la galeria.
//   - Agregar: POST /levantamientos/{id}/fotos con las referencias no vacias.
//   - Cerrar tras adjuntar devuelve `true`.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';
import { LOCALE_ID } from '@angular/core';

import { LevantamientoFotosDialog } from './levantamiento-fotos-dialog';
import { LevantamientoSitio } from '../models/operacion.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

const LEV_ID = 'lev-1';

class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

function levantamientoDe(): LevantamientoSitio {
  return {
    id: LEV_ID,
    sitioId: null,
    cotizacionId: null,
    ordenFabricacionId: null,
    mediciones: '3x2 metros',
    tipoSuperficie: 'Muro de concreto',
    condicionesElectricas: '110V',
    estado: 'en_proceso',
    completadoPor: null,
    completadoEn: null,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  } as LevantamientoSitio;
}

interface Probe {
  referencias: { at(i: number): { setValue(v: string): void } };
  agregarFotos(): void;
  cerrar(): void;
}

describe('LevantamientoFotosDialog', () => {
  let fixture: ComponentFixture<LevantamientoFotosDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  const URL_FOTOS = `/api/v1/levantamientos/${LEV_ID}/fotos`;

  async function montar(): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [LevantamientoFotosDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { levantamiento: levantamientoDe(), puedeAgregar: true } },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LevantamientoFotosDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  /** Resuelve el GET de galeria inicial que dispara el constructor. */
  function resolverGaleriaInicial(fotos: Record<string, unknown>[] = []): void {
    http.expectOne((r) => r.url === URL_FOTOS && r.method === 'GET').flush(fotos);
    fixture.detectChanges();
  }

  it('al montar carga la galeria con GET /levantamientos/{id}/fotos', async () => {
    await montar();
    resolverGaleriaInicial([
      { id: 'f-1', levantamientoId: LEV_ID, referencia: 'https://x/y.jpg', createdAt: '2026-01-01T00:00:00Z' },
    ]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('https://x/y.jpg');
  });

  it('agregar: POST con las referencias y cerrar devuelve true', async () => {
    await montar();
    resolverGaleriaInicial([]);
    comp().referencias.at(0).setValue('https://foto/1.jpg');
    comp().agregarFotos();
    const req = http.expectOne((r) => r.url === URL_FOTOS && r.method === 'POST');
    expect(req.request.body.referencias).toEqual(['https://foto/1.jpg']);
    req.flush([
      { id: 'f-9', levantamientoId: LEV_ID, referencia: 'https://foto/1.jpg', createdAt: '2026-01-01T00:00:00Z' },
    ]);
    comp().cerrar();
    expect(dialogRef.cerradoCon).toBe(true);
  });

  it('sin referencias validas no hace POST', async () => {
    await montar();
    resolverGaleriaInicial([]);
    comp().agregarFotos();
    http.expectNone((r) => r.url === URL_FOTOS && r.method === 'POST');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    resolverGaleriaInicial([]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
