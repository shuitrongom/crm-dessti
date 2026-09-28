// =============================================================================
// Pruebas del dialogo AsignarCanalDialog (Req 2.1, 63.1, 57)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Guardar con un canal elegido hace PUT /oportunidades/{id}/canal-venta con
//     { canalVentaId } y cierra con la oportunidad actualizada.
//   - "Sin canal" envia canalVentaId null (retira el canal).
//   - Sin canales disponibles se muestra la guia y no hay boton de guardar.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { AsignarCanalDialog, AsignarCanalDialogData } from './asignar-canal-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { CanalVenta, Oportunidad } from '../models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

class OverlayStub {
  ejecutar<T>(origen: Observable<T>): Observable<T> {
    return origen;
  }
}

const CANAL = { id: 'cccccccc-1111-2222-3333-444444444444', nombre: 'Redes sociales' } as unknown as CanalVenta;

function oportunidadDto(over: Partial<Oportunidad> = {}): Oportunidad {
  return {
    id: 'op-1',
    clienteId: 'cli-1',
    titulo: 'Proyecto rotulos',
    valorEstimado: 1500,
    etapa: 'nuevo',
    responsableUsuarioId: null,
    cotizacionId: null,
    canalVentaId: null,
    probabilidad: 10,
    fechaCierreEsperada: null,
    motivoPerdida: null,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as Oportunidad;
}

interface DialogTest {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
}

describe('AsignarCanalDialog', () => {
  let fixture: ComponentFixture<AsignarCanalDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(data: AsignarCanalDialogData): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [AsignarCanalDialog, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AsignarCanalDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): DialogTest {
    return fixture.componentInstance as unknown as DialogTest;
  }

  it('guarda el canal elegido con PUT canal-venta y cierra con la oportunidad actualizada', async () => {
    await montar({ oportunidad: oportunidadDto(), canales: [CANAL], puedeCrearCanal: true });
    comp().form.patchValue({ canalId: CANAL.id });
    comp().guardar();
    const put = http.expectOne(
      (r) => r.method === 'PUT' && r.url === '/api/v1/oportunidades/op-1/canal-venta',
    );
    expect(put.request.body).toEqual({ canalVentaId: CANAL.id });
    put.flush(oportunidadDto({ canalVentaId: CANAL.id }));
    expect((dialogRef.cerradoCon as Oportunidad).canalVentaId).toBe(CANAL.id);
  });

  it('"Sin canal" envia canalVentaId null (retira el canal)', async () => {
    await montar({
      oportunidad: oportunidadDto({ canalVentaId: CANAL.id }),
      canales: [CANAL],
      puedeCrearCanal: true,
    });
    comp().form.patchValue({ canalId: '' });
    comp().guardar();
    const put = http.expectOne(
      (r) => r.method === 'PUT' && r.url === '/api/v1/oportunidades/op-1/canal-venta',
    );
    expect(put.request.body).toEqual({ canalVentaId: null });
    put.flush(oportunidadDto({ canalVentaId: null }));
    expect((dialogRef.cerradoCon as Oportunidad).canalVentaId).toBeNull();
  });

  it('sin canales disponibles muestra la guia y no ofrece guardar', async () => {
    await montar({ oportunidad: oportunidadDto(), canales: [], puedeCrearCanal: true });
    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Aún no hay canales de venta');
    // No hay boton "Guardar canal" cuando no hay canales.
    const botones = Array.from(host.querySelectorAll('button')).map((b) => b.textContent ?? '');
    expect(botones.some((t) => t.includes('Guardar canal'))).toBe(false);
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar({ oportunidad: oportunidadDto(), canales: [CANAL], puedeCrearCanal: true });
    await esperarSinViolaciones(fixture);
  });
});
