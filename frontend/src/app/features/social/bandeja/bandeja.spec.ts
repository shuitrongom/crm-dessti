// =============================================================================
// Pruebas de la vista Bandeja: vinculacion de una Conversacion a un Cliente
// (lead social, Req 5.1, 5.2, 5.5)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless, con los
// temporizadores falsos de Vitest para el debounce del autocompletado):
//   - Al abrir "Vincular a cliente", elegir un Cliente por nombre y confirmar,
//     el PUT /social/bandeja/{id}/vinculacion viaja con { clienteId: <UUID> } y
//     el Usuario nunca teclea el identificador.
//   - Cuando la conversacion ya tiene clienteId, se muestra un enlace a la Ficha
//     360 del Cliente (/empresa/comercial/clientes/{id}) sin exponer el UUID.
//   - Sin el permiso conversacion:actualizar, la accion de vincular no aparece.
//   - Ninguna vista expone el UUID como texto visible.
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { Bandeja } from './bandeja';
import { AuthService } from '../../../core/auth/auth.service';
import { Conversacion } from '../models/social.models';
import { Cliente } from '../../comercial/models/comercial.models';

/**
 * AuthService de prueba. Por defecto concede los permisos que la vista consulta
 * (conversacion:{enviar,actualizar} y cliente:leer para el enlace a la Ficha
 * 360). Los casos de gating instancian un stub con permisos acotados.
 */
class AuthServiceStub {
  constructor(private readonly permisos: string[] | null = null) {}
  tienePermiso(recurso: string, operacion: string): boolean {
    if (this.permisos) {
      return this.permisos.includes(`${recurso}:${operacion}`);
    }
    return recurso === 'conversacion' || recurso === 'cliente';
  }
  identificador(): string {
    return 'usuario-1';
  }
}

/** Cliente de prueba (solo los campos que el selector/enlace consumen). */
const CLIENTE = {
  id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  nombre: 'Acme',
  rfc: 'ABCD901231XYZ',
} as unknown as Cliente;

/** Superficie protegida del EntitySelect que las pruebas necesitan accionar. */
interface EntitySelectProbe {
  alEscribir(v: string): void;
  alSeleccionar(evento: { option: { value: Cliente } }): void;
}

/** Superficie protegida del componente que las pruebas necesitan accionar. */
interface BandejaProbe {
  seleccionar(c: Conversacion): void;
  abrirVinculacion(): void;
  vincular(): void;
  puedeActualizar: boolean;
}

/** Conversacion de prueba (solo los campos que la vista consume). */
function conversacionDto(over: Partial<Conversacion> = {}): Conversacion {
  return {
    id: 'conv-1',
    cuentaCanalSocialId: 'cta-1',
    canal: 'whatsapp',
    remitenteExterno: '5215500000000',
    clienteId: null,
    contactoId: null,
    estado: 'abierta',
    asignadoA: null,
    ultimoEntranteUtc: '2026-01-01T00:00:00Z',
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as Conversacion;
}

describe('Bandeja (lead social)', () => {
  let fixture: ComponentFixture<Bandeja>;
  let http: HttpTestingController;

  function configurar(auth: AuthService | (new () => object) = AuthServiceStub): void {
    TestBed.configureTestingModule({
      imports: [Bandeja, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        typeof auth === 'function'
          ? { provide: AuthService, useClass: auth }
          : { provide: AuthService, useValue: auth },
      ],
    });
    fixture = TestBed.createComponent(Bandeja);
    http = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  /** Resuelve la carga inicial de la bandeja (GET /social/bandeja). */
  function resolverCargaInicial(items: Conversacion[] = []): void {
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === '/api/v1/social/bandeja');
    req.flush({ content: items, page: 0, size: 50, totalElements: items.length, totalPages: 1 });
    fixture.detectChanges();
  }

  /** Resuelve las peticiones disparadas al seleccionar una conversacion. */
  function resolverSeleccion(c: Conversacion): void {
    // Historial de mensajes.
    http
      .expectOne((r) => r.url === `/api/v1/social/bandeja/${c.id}/mensajes`)
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 1 });
    // Plantillas aprobadas del canal.
    http
      .expectOne((r) => r.url === '/api/v1/social/plantillas')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 1 });
    // Si la conversacion ya tiene clienteId y hay permiso, se resuelve su nombre.
    if (c.clienteId) {
      const pendientes = http.match((r) => r.url === `/api/v1/clientes/${c.clienteId}`);
      for (const p of pendientes) {
        p.flush(CLIENTE);
      }
    }
    fixture.detectChanges();
  }

  /** Localiza la instancia del EntitySelect del selector de Cliente. */
  function selectorCliente(): EntitySelectProbe {
    const debug = fixture.debugElement.query((n) => n.name === 'app-entity-select');
    return debug.componentInstance as unknown as EntitySelectProbe;
  }

  it('vincula la conversacion con el UUID del cliente elegido en el selector', () => {
    configurar();
    const conv = conversacionDto();
    resolverCargaInicial([conv]);
    const componente = fixture.componentInstance as unknown as BandejaProbe;

    componente.seleccionar(conv);
    resolverSeleccion(conv);

    componente.abrirVinculacion();
    fixture.detectChanges();

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

    componente.vincular();

    const put = http.expectOne(
      (r) => r.method === 'PUT' && r.url === `/api/v1/social/bandeja/${conv.id}/vinculacion`,
    );
    expect(put.request.body).toEqual({ clienteId: CLIENTE.id });
    // Responde con la conversacion ya vinculada; se resuelve su nombre.
    put.flush(conversacionDto({ clienteId: CLIENTE.id }));
    fixture.detectChanges();
    const pendientes = http.match((r) => r.url === `/api/v1/clientes/${CLIENTE.id}`);
    for (const p of pendientes) {
      p.flush(CLIENTE);
    }
  });

  it('muestra el cliente vinculado como enlace a su Ficha 360 sin exponer el UUID', () => {
    configurar();
    const conv = conversacionDto({ clienteId: CLIENTE.id });
    resolverCargaInicial([conv]);
    const componente = fixture.componentInstance as unknown as BandejaProbe;

    componente.seleccionar(conv);
    resolverSeleccion(conv);

    const host = fixture.nativeElement as HTMLElement;
    const enlace = host.querySelector(`a[href="/empresa/comercial/clientes/${CLIENTE.id}"]`);
    expect(enlace).not.toBeNull();
    expect(enlace?.textContent).toContain('Acme');
    // Ningun texto visible expone el UUID.
    expect(host.textContent).not.toContain(CLIENTE.id);
  });

  it('sin permiso conversacion:actualizar no ofrece la accion de vincular', () => {
    configurar(
      new AuthServiceStub(['bandeja:leer', 'conversacion:leer', 'cliente:leer']) as unknown as AuthService,
    );
    const conv = conversacionDto();
    resolverCargaInicial([conv]);
    const componente = fixture.componentInstance as unknown as BandejaProbe;

    componente.seleccionar(conv);
    resolverSeleccion(conv);

    expect(componente.puedeActualizar).toBe(false);
    const host = fixture.nativeElement as HTMLElement;
    // El menu de acciones no se renderiza sin el permiso de actualizar.
    expect(host.querySelector('button[aria-label="Acciones de la conversacion"]')).toBeNull();
  });
});
