// =============================================================================
// Pruebas del dialogo NuevaOportunidadDialog (Req 14, 57)
// -----------------------------------------------------------------------------
// Verifican, sin red real (proyecto zoneless, temporizadores falsos para el
// debounce del autocompletado):
//   - Al elegir un Cliente del selector (por nombre) + titulo + valor, el POST
//     /oportunidades viaja con { clienteId:<UUID>, titulo, valorEstimado } y el
//     dialogo cierra con la oportunidad creada.
//   - Si no se eligio cliente, el formulario es invalido y NO se emite el POST.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { NuevaOportunidadDialog } from './nueva-oportunidad-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { Cliente, Oportunidad } from '../models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

/** Overlay de prueba: ejecuta el observable de origen tal cual (sin UI). */
class OverlayStub {
  ejecutar<T>(origen: Observable<T>): Observable<T> {
    return origen;
  }
}

const CLIENTE = {
  id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  nombre: 'Acme',
  rfc: 'ABCD901231XYZ',
} as unknown as Cliente;

interface EntitySelectProbe {
  alEscribir(v: string): void;
  alSeleccionar(evento: { option: { value: Cliente } }): void;
}

interface DialogTest {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
}

describe('NuevaOportunidadDialog', () => {
  let fixture: ComponentFixture<NuevaOportunidadDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  beforeEach(async () => {
    vi.useFakeTimers();
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [NuevaOportunidadDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(NuevaOportunidadDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  function selectorCliente(): EntitySelectProbe {
    const debug = fixture.debugElement.query((n) => n.name === 'app-entity-select');
    return debug.componentInstance as unknown as EntitySelectProbe;
  }

  function comp(): DialogTest {
    return fixture.componentInstance as unknown as DialogTest;
  }

  it('crea la oportunidad con el UUID del cliente elegido y cierra con la creada', () => {
    const selector = selectorCliente();
    selector.alEscribir('acm');
    fixture.detectChanges();
    vi.advanceTimersByTime(300);
    http
      .expectOne((r) => r.url === '/api/v1/clientes')
      .flush({ content: [CLIENTE], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    fixture.detectChanges();

    selector.alSeleccionar({ option: { value: CLIENTE } });
    fixture.detectChanges();

    comp().form.patchValue({ titulo: 'Proyecto rotulos', valorEstimado: 1500 });
    fixture.detectChanges();
    comp().guardar();

    const post = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/oportunidades');
    expect(post.request.body).toEqual({
      clienteId: CLIENTE.id,
      titulo: 'Proyecto rotulos',
      valorEstimado: 1500,
    });
    const creada = oportunidadCreada();
    post.flush(creada);
    expect((dialogRef.cerradoCon as Oportunidad).id).toBe('op-1');
  });

  it('no emite el POST si no se eligio un cliente', () => {
    comp().form.patchValue({ titulo: 'Sin cliente', valorEstimado: 500 });
    fixture.detectChanges();
    comp().guardar();
    http.expectNone((r) => r.method === 'POST' && r.url === '/api/v1/oportunidades');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    vi.useRealTimers();
    await esperarSinViolaciones(fixture);
  });
});

/** Oportunidad devuelta por el backend al crear. */
function oportunidadCreada(): Oportunidad {
  return {
    id: 'op-1',
    clienteId: CLIENTE.id,
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
  } as Oportunidad;
}
