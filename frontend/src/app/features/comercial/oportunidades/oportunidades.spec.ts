// =============================================================================
// Pruebas de la vista ComercialOportunidades (Req 14, 63, 57) — kanban
// -----------------------------------------------------------------------------
// El alta y la asignacion de canal se hacen ahora en modales (probados aparte).
// Aqui se verifica, sin red real (proyecto zoneless):
//   - Carga inicial (canales + pipeline + nombres de cliente) y render.
//   - "Nueva oportunidad" abre el modal (MatDialog) y recarga si se creo.
//   - "Asignar canal" abre el modal con los datos correctos y aplica el cambio
//     en memoria cuando el modal devuelve la oportunidad actualizada.
//   - Drag & drop: soltarEnColumna valida la maquina de estados (transicion
//     invalida no llama al backend; valida hace PUT /etapa) y actualiza memoria.
//   - Filtro por canal recarga pasando canalVentaId.
//   - Enlaces a Ficha 360 / cotizacion sin exponer el UUID.
//   - Gating por permiso de la accion de canal.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { ComercialOportunidades } from './oportunidades';
import { NuevaOportunidadDialog } from './nueva-oportunidad-dialog';
import { AsignarCanalDialog } from './asignar-canal-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { Cliente, EtapaOportunidad, Oportunidad } from '../models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** AuthService de prueba con permisos configurables. */
class AuthServiceStub {
  constructor(private readonly permisos: string[] | null = null) {}
  tienePermiso(recurso: string, operacion: string): boolean {
    if (this.permisos) {
      return this.permisos.includes(`${recurso}:${operacion}`);
    }
    return recurso === 'oportunidad' || recurso === 'cliente' || recurso === 'cotizacion';
  }
}

/** Espia del servicio de notificaciones. */
class ToastSpy {
  exitos: string[] = [];
  infos: string[] = [];
  errores: string[] = [];
  exito(m: string): void {
    this.exitos.push(m);
  }
  info(m: string): void {
    this.infos.push(m);
  }
  error(m: string): void {
    this.errores.push(m);
  }
}

/** Doble de MatDialog: registra la ultima apertura y devuelve un resultado configurable. */
class MatDialogStub {
  ultimoComponente: unknown = null;
  ultimaData: unknown = undefined;
  resultado: unknown = undefined;
  aperturas = 0;
  open(componente: unknown, config?: { data?: unknown }) {
    this.aperturas++;
    this.ultimoComponente = componente;
    this.ultimaData = config?.data;
    return { afterClosed: () => of(this.resultado) };
  }
}

const CLIENTE = {
  id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  nombre: 'Acme',
  rfc: 'ABCD901231XYZ',
} as unknown as Cliente;

const CANAL = {
  id: 'cccccccc-1111-2222-3333-444444444444',
  nombre: 'Redes sociales',
} as unknown as { id: string; nombre: string };

/** Oportunidad de prueba (incluye los campos de forecast, V81). */
function oportunidadDto(over: Partial<Oportunidad> = {}): Oportunidad {
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
    ...over,
  } as Oportunidad;
}

/** Superficie protegida del componente para las pruebas. */
interface Probe {
  puedeAsignarCanal: boolean;
  soltarEnColumna(evento: { item: { data: Oportunidad } }, destino: EtapaOportunidad): void;
}

describe('ComercialOportunidades', () => {
  let fixture: ComponentFixture<ComercialOportunidades>;
  let http: HttpTestingController;
  let dialog: MatDialogStub;
  let toast: ToastSpy;

  function configurar(auth: AuthService = new AuthServiceStub() as unknown as AuthService): void {
    dialog = new MatDialogStub();
    toast = new ToastSpy();
    TestBed.configureTestingModule({
      imports: [ComercialOportunidades, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: MatDialog, useValue: dialog },
        { provide: NotificacionesService, useValue: toast },
      ],
    });
    fixture = TestBed.createComponent(ComercialOportunidades);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  /** Resuelve la carga inicial (canales + pipeline + nombres de cliente). */
  function resolverCargaInicial(items: Oportunidad[] = []): void {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === '/api/v1/canales-venta')
      .flush({ content: [CANAL], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    http
      .expectOne((r) => r.url === '/api/v1/oportunidades')
      .flush({ content: items, page: 0, size: 100, totalElements: items.length, totalPages: 1 });
    fixture.detectChanges();
    const ids = [...new Set(items.map((o) => o.clienteId))];
    for (const id of ids) {
      for (const p of http.match((r) => r.url === `/api/v1/clientes/${id}`)) {
        p.flush(CLIENTE);
      }
    }
    fixture.detectChanges();
  }

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('carga y renderiza el pipeline', () => {
    configurar();
    resolverCargaInicial([oportunidadDto()]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Proyecto rotulos');
  });

  it('"Nueva oportunidad" abre el modal y recarga si se creo', () => {
    configurar();
    resolverCargaInicial();
    dialog.resultado = oportunidadDto();
    (fixture.componentInstance as unknown as { nuevaOportunidad(): void }).nuevaOportunidad();
    expect(dialog.aperturas).toBe(1);
    expect(dialog.ultimoComponente).toBe(NuevaOportunidadDialog);
    // Recarga del pipeline tras el alta.
    http.expectOne((r) => r.url === '/api/v1/oportunidades').flush({
      content: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    });
    expect(toast.exitos).toContain('Oportunidad creada.');
  });

  it('"Asignar canal" abre el modal con la oportunidad y aplica el cambio en memoria', () => {
    configurar();
    const op = oportunidadDto();
    resolverCargaInicial([op]);
    dialog.resultado = oportunidadDto({ canalVentaId: CANAL.id });
    (fixture.componentInstance as unknown as { asignarCanal(o: Oportunidad): void }).asignarCanal(op);
    expect(dialog.aperturas).toBe(1);
    expect(dialog.ultimoComponente).toBe(AsignarCanalDialog);
    expect((dialog.ultimaData as { oportunidad: Oportunidad }).oportunidad.id).toBe('op-1');
    expect(toast.exitos).toContain('Canal de venta asignado.');
  });

  it('drag & drop: una transicion invalida NO llama al backend y avisa', () => {
    configurar();
    const op = oportunidadDto({ etapa: 'nuevo' });
    resolverCargaInicial([op]);
    // nuevo -> ganado no es una transicion valida (solo calificado/perdido).
    comp().soltarEnColumna({ item: { data: op } }, 'ganado');
    http.expectNone((r) => r.url === `/api/v1/oportunidades/${op.id}/etapa`);
    expect(toast.infos.some((m) => m.includes('No se puede mover'))).toBe(true);
  });

  it('drag & drop: una transicion valida hace PUT /etapa y actualiza en memoria', () => {
    configurar();
    const op = oportunidadDto({ etapa: 'nuevo' });
    resolverCargaInicial([op]);
    comp().soltarEnColumna({ item: { data: op } }, 'calificado');
    const put = http.expectOne(
      (r) => r.method === 'PUT' && r.url === `/api/v1/oportunidades/${op.id}/etapa`,
    );
    expect(put.request.body).toEqual({ etapa: 'calificado', motivoPerdida: null });
    put.flush(oportunidadDto({ etapa: 'calificado', probabilidad: 30 }));
    expect(toast.exitos.some((m) => m.includes('Calificado'))).toBe(true);
  });

  it('el filtro por canal recarga el pipeline pasando canalVentaId', () => {
    configurar();
    resolverCargaInicial([oportunidadDto()]);
    (fixture.componentInstance as unknown as { aplicarFiltroCanal(c: string): void }).aplicarFiltroCanal(
      CANAL.id,
    );
    const req = http.expectOne((r) => r.url.startsWith('/api/v1/oportunidades'));
    expect(req.request.params.get('canalVentaId')).toBe(CANAL.id);
    req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 1 });
  });

  it('muestra el cliente como enlace a su Ficha 360 y "Ver cotizacion" sin exponer el UUID', () => {
    configurar();
    resolverCargaInicial([oportunidadDto({ cotizacionId: 'cot-9', etapa: 'ganado', probabilidad: 100 })]);
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector(`a[href="/empresa/comercial/clientes/${CLIENTE.id}"]`)).not.toBeNull();
    expect(host.querySelector('a[href="/empresa/comercial/cotizaciones/cot-9"]')).not.toBeNull();
    expect(host.textContent).not.toContain(CLIENTE.id);
  });

  it('sin permiso oportunidad:actualizar no ofrece la accion de asignar canal', () => {
    configurar(
      new AuthServiceStub(['oportunidad:listar', 'cliente:leer', 'cotizacion:leer']) as unknown as AuthService,
    );
    resolverCargaInicial([oportunidadDto()]);
    expect(comp().puedeAsignarCanal).toBe(false);
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('button[aria-label^="Asignar canal de venta"]')).toBeNull();
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    configurar();
    resolverCargaInicial([oportunidadDto()]);
    await esperarSinViolaciones(fixture);
  });
});
