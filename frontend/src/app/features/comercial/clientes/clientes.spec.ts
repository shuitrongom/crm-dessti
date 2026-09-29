// =============================================================================
// Pruebas de la vista de Clientes (Req 5) — listado + acciones (modal/nav/baja)
// -----------------------------------------------------------------------------
// La vista de Clientes es ahora un LISTADO: el alta/edicion ocurre en un modal
// (ClienteFormDialog, probado aparte). Aqui se verifica, sin zona ni red real:
//   - Carga inicial del listado (GET /clientes) y render de la tabla.
//   - nuevo()/editar() abren el modal (MatDialog.open) con los datos correctos y
//     recargan el listado cuando el dialogo confirma.
//   - ver() navega a la Ficha 360 por id (sin teclear el identificador).
//   - eliminar() confirma, hace DELETE y recarga.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { ComercialClientes } from './clientes';
import { ClienteFormDialog } from './cliente-form-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** AuthService de prueba: concede todos los permisos de cliente. */
class AuthServiceStub {
  tienePermiso(): boolean {
    return true;
  }
  tieneRol(): boolean {
    return false;
  }
}

/** Espia del servicio de notificaciones. */
class ToastSpy {
  exitos: string[] = [];
  errores: string[] = [];
  exito(m: string): void {
    this.exitos.push(m);
  }
  error(m: string): void {
    this.errores.push(m);
  }
  info(): void {}
}

/** Doble de ConfirmDialogService (la baja confirma siempre). */
class ConfirmStub {
  async confirmar(): Promise<boolean> {
    return true;
  }
}

/**
 * Doble de MatDialog: registra la ultima apertura y devuelve un ref cuyo
 * afterClosed() emite un resultado configurable (para simular guardado/cancel).
 */
class MatDialogStub {
  ultimoComponente: unknown = null;
  ultimaConfig: { data?: { cliente?: unknown } } | undefined;
  resultado: unknown = undefined;
  aperturas = 0;

  open(componente: unknown, config?: { data?: { cliente?: unknown } }) {
    this.aperturas++;
    this.ultimoComponente = componente;
    this.ultimaConfig = config;
    return { afterClosed: () => of(this.resultado) };
  }
}

/** Cliente minimo aceptado por ver()/editar(). */
interface ClienteVista {
  id: string;
  nombre: string;
  rfc: string;
  [k: string]: unknown;
}

/** Forma minima del componente accedida por las pruebas. */
interface ClientesTest {
  nuevo(): void;
  ver(cliente: ClienteVista): void;
  editar(cliente: ClienteVista): void;
  eliminar(cliente: ClienteVista): Promise<void>;
}

/** ClienteDto minimo devuelto por el listado. */
function clienteDto(over: Record<string, unknown> = {}) {
  return {
    id: 'c1',
    nombre: 'Acme',
    rfc: 'ABC010101AB1',
    email: 'ventas@acme.test',
    telefono: '5551234567',
    nombreComercial: null,
    tipoPersona: null,
    telefonoAdicional: null,
    direccionCalle: null,
    direccionCiudad: null,
    direccionEstado: null,
    direccionCp: null,
    direccionPais: null,
    notas: null,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  };
}

describe('ComercialClientes', () => {
  let fixture: ComponentFixture<ComercialClientes>;
  let http: HttpTestingController;
  let toast: ToastSpy;
  let dialog: MatDialogStub;

  beforeEach(async () => {
    toast = new ToastSpy();
    dialog = new MatDialogStub();
    await TestBed.configureTestingModule({
      imports: [ComercialClientes, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: toast },
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: ConfirmDialogService, useClass: ConfirmStub },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Crea el componente y resuelve la carga inicial del listado. */
  function crear(contenido: Record<string, unknown>[] = [clienteDto()]): void {
    fixture = TestBed.createComponent(ComercialClientes);
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url.startsWith('/api/v1/clientes') && r.method === 'GET');
    req.flush({ content: contenido, totalElements: contenido.length, totalPages: 1, number: 0, size: 20 });
    fixture.detectChanges();
  }

  /** Resuelve una recarga del listado (usada tras guardar/eliminar). */
  function resolverRecarga(): void {
    http.expectOne((r) => r.url.startsWith('/api/v1/clientes') && r.method === 'GET').flush({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    });
  }

  function comp(): ClientesTest {
    return fixture.componentInstance as unknown as ClientesTest;
  }

  it('carga el listado al iniciar y renderiza la tabla', () => {
    crear();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Acme');
  });

  it('nuevo() abre el modal en modo alta (sin cliente en los datos)', () => {
    crear();
    comp().nuevo();
    expect(dialog.aperturas).toBe(1);
    expect(dialog.ultimoComponente).toBe(ClienteFormDialog);
    expect(dialog.ultimaConfig?.data?.cliente).toBeUndefined();
  });

  it('editar() abre el modal en modo edicion con el cliente en los datos', () => {
    crear();
    const dto = clienteDto() as unknown as ClienteVista;
    dialog.resultado = undefined; // cancela: no recarga
    comp().editar(dto);
    expect(dialog.aperturas).toBe(1);
    expect(dialog.ultimoComponente).toBe(ClienteFormDialog);
    expect((dialog.ultimaConfig?.data?.cliente as ClienteVista).id).toBe('c1');
  });

  it('cuando el modal confirma (devuelve cliente), notifica exito y recarga el listado', () => {
    crear();
    dialog.resultado = clienteDto(); // simula guardado
    comp().nuevo();
    resolverRecarga();
    expect(toast.exitos).toContain('Cliente creado.');
  });

  it('cuando el modal se cancela (undefined), no recarga ni notifica', () => {
    crear();
    dialog.resultado = undefined;
    comp().editar(clienteDto() as unknown as ClienteVista);
    // No debe haber una segunda peticion GET (solo la carga inicial ya resuelta).
    http.expectNone((r) => r.url.startsWith('/api/v1/clientes') && r.method === 'GET');
    expect(toast.exitos.length).toBe(0);
  });

  it('ver() navega a la Ficha 360 del cliente por su id (sin teclear el identificador)', () => {
    crear();
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    comp().ver(clienteDto() as unknown as ClienteVista);
    expect(navegar).toHaveBeenCalledWith(['/empresa/comercial/clientes', 'c1']);
  });

  it('eliminar() confirma, hace DELETE y recarga el listado', async () => {
    crear();
    const dto = clienteDto() as unknown as ClienteVista;
    await comp().eliminar(dto);
    http.expectOne((r) => r.url === `/api/v1/clientes/${dto.id}` && r.method === 'DELETE').flush(null);
    resolverRecarga();
    expect(toast.exitos).toContain('Cliente dado de baja.');
  });

  it('eliminar() con pipeline abierto (422) muestra el mensaje del backend y no recarga (Req 5.10)', async () => {
    crear();
    const dto = clienteDto() as unknown as ClienteVista;
    const mensaje =
      'No se puede dar de baja el Cliente porque tiene Oportunidades abiertas (en una etapa no final). ' +
      'Cierra o reasigna esa actividad antes de darlo de baja.';
    await comp().eliminar(dto);
    http
      .expectOne((r) => r.url === `/api/v1/clientes/${dto.id}` && r.method === 'DELETE')
      .flush({ detail: mensaje, status: 422 }, { status: 422, statusText: 'Unprocessable Entity' });
    // Muestra el mensaje de negocio del backend y NO recarga el listado ni notifica exito.
    expect(toast.errores).toContain(mensaje);
    expect(toast.exitos).not.toContain('Cliente dado de baja.');
    http.expectNone((r) => r.url.startsWith('/api/v1/clientes') && r.method === 'GET');
  });

  it('la accion de ver es un boton que navega (no un enlace directo en la fila)', () => {
    crear();
    const host = fixture.nativeElement as HTMLElement;
    const botonVer = host.querySelector('button[aria-label="Ver detalle del cliente"]');
    expect(botonVer).not.toBeNull();
    expect(host.querySelector('.comercial-acciones-fila a[href]')).toBeNull();
    expect(host.querySelector('a[aria-label="Ver detalle del cliente"]')).toBeNull();
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    crear();
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
