// =============================================================================
// Pruebas de la vista ComercialCotizacionNueva: alta con selectores de Cliente y
// Producto (Req 6, 59, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless, con los
// temporizadores falsos de Vitest para el debounce del autocompletado):
//   - El selector de Cliente enlaza su id (UUID) a la cabecera y el selector de
//     Producto de una partida enlaza el productoId (UUID) — o null si no se elige.
//   - El POST /cotizaciones viaja con { clienteId: <UUID>, partidas: [...] } con
//     productoId como UUID o null, sin que el Usuario teclee identificadores.
//   - El total previsualizado suma los subtotales de las partidas.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';

import { ComercialCotizacionNueva } from './cotizacion-nueva';
import { Cliente, Cotizacion, Producto } from '../models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';
import { provideFechaIsoDatepicker } from '../../../shared/date/provide-fecha-iso';

/** Cliente y Producto de prueba (solo los campos que los selectores consumen). */
const CLIENTE = {
  id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  nombre: 'Acme',
  rfc: 'ABCD901231XYZ',
} as unknown as Cliente;

const PRODUCTO = {
  id: 'ffffffff-1111-2222-3333-444444444444',
  nombre: 'Rotulo LED',
  unidad: 'pieza',
} as unknown as Producto;

/** Superficie protegida de un EntitySelect. */
interface EntitySelectProbe {
  alEscribir(v: string): void;
  alSeleccionar(evento: { option: { value: unknown } }): void;
}

/** Superficie protegida del componente. */
interface CotizacionNuevaProbe {
  partidas: { at(i: number): { get(n: string): { setValue(v: unknown): void } | null } };
  total(): number;
  crear(): void;
}

describe('ComercialCotizacionNueva', () => {
  let fixture: ComponentFixture<ComercialCotizacionNueva>;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [ComercialCotizacionNueva, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        // provideRouter da ActivatedRoute (para routerLink) y Router; se espia
        // navigate para que la creacion no dispare el reconocedor de rutas.
        provideRouter([]),
        ...provideFechaIsoDatepicker(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ComercialCotizacionNueva);
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  /** Lista todos los EntitySelect renderizados (cabecera cliente + partidas). */
  function selectores(): EntitySelectProbe[] {
    return fixture.debugElement
      .queryAll((n) => n.name === 'app-entity-select')
      .map((d) => d.componentInstance as unknown as EntitySelectProbe);
  }

  /** Elige una entidad en un selector, resolviendo su busqueda por la URL dada. */
  function elegir(selector: EntitySelectProbe, url: string, valor: unknown): void {
    selector.alEscribir('bus');
    // toObservable emite el nuevo valor del signal a traves de un effect que
    // corre en la deteccion de cambios; hay que propagarlo antes del debounce.
    fixture.detectChanges();
    vi.advanceTimersByTime(300);
    const req = http.expectOne((r) => r.url === url);
    req.flush({ content: [valor], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    fixture.detectChanges();
    selector.alSeleccionar({ option: { value: valor } });
    fixture.detectChanges();
  }

  it('envia clienteId y productoId como UUID y calcula el total previsualizado', () => {
    const componente = fixture.componentInstance as unknown as CotizacionNuevaProbe;

    // Selector 0 = Cliente (cabecera), Selector 1 = Producto (partida 1).
    const [selCliente, selProducto] = selectores();
    elegir(selCliente, '/api/v1/clientes', CLIENTE);
    elegir(selProducto, '/api/v1/productos', PRODUCTO);

    // Completa la partida y verifica el total previsualizado (2 * 100 = 200).
    componente.partidas.at(0).get('descripcion')!.setValue('Letrero principal');
    componente.partidas.at(0).get('cantidad')!.setValue(2);
    componente.partidas.at(0).get('precioUnitario')!.setValue(100);
    fixture.detectChanges();
    expect(componente.total()).toBe(200);

    componente.crear();
    const post = http.expectOne(
      (r) => r.method === 'POST' && r.url === '/api/v1/cotizaciones',
    );
    expect(post.request.body).toEqual({
      clienteId: CLIENTE.id,
      moneda: 'MXN',
      partidas: [
        {
          productoId: PRODUCTO.id,
          descripcion: 'Letrero principal',
          cantidad: 2,
          precioUnitario: 100,
        },
      ],
    });
    post.flush({ id: 'c1' } as Cotizacion);
  });

  it('permite una partida sin producto (productoId null) eligiendo solo el cliente', () => {
    const componente = fixture.componentInstance as unknown as CotizacionNuevaProbe;

    const [selCliente] = selectores();
    elegir(selCliente, '/api/v1/clientes', CLIENTE);

    componente.partidas.at(0).get('descripcion')!.setValue('Servicio de diseno');
    componente.partidas.at(0).get('cantidad')!.setValue(1);
    fixture.detectChanges();

    componente.crear();
    const post = http.expectOne(
      (r) => r.method === 'POST' && r.url === '/api/v1/cotizaciones',
    );
    expect(post.request.body).toEqual({
      clienteId: CLIENTE.id,
      moneda: 'MXN',
      partidas: [
        {
          productoId: null,
          descripcion: 'Servicio de diseno',
          cantidad: 1,
          precioUnitario: null,
        },
      ],
    });
    post.flush({ id: 'c2' } as Cotizacion);
  });

  it('envia validoHasta, condiciones, notas y la moneda seleccionada', () => {
    const componente = fixture.componentInstance as unknown as CotizacionNuevaProbe & {
      form: { get(n: string): { setValue(v: unknown): void } | null };
    };

    const [selCliente] = selectores();
    elegir(selCliente, '/api/v1/clientes', CLIENTE);

    componente.form.get('validoHasta')!.setValue('2026-03-31');
    componente.form.get('moneda')!.setValue('USD');
    componente.form.get('condiciones')!.setValue('Precios sujetos a cambio.');
    componente.form.get('notas')!.setValue('Requiere anticipo del 50%.');
    componente.partidas.at(0).get('descripcion')!.setValue('Letrero');
    componente.partidas.at(0).get('cantidad')!.setValue(1);
    componente.partidas.at(0).get('precioUnitario')!.setValue(500);
    fixture.detectChanges();

    componente.crear();
    const post = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/cotizaciones');
    expect(post.request.body).toEqual({
      clienteId: CLIENTE.id,
      moneda: 'USD',
      validoHasta: '2026-03-31',
      condiciones: 'Precios sujetos a cambio.',
      notas: 'Requiere anticipo del 50%.',
      partidas: [
        {
          productoId: null,
          descripcion: 'Letrero',
          cantidad: 1,
          precioUnitario: 500,
        },
      ],
    });
    post.flush({ id: 'c3' } as Cotizacion);
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    // axe usa temporizadores internos; se ejecuta con los reales.
    vi.useRealTimers();
    await esperarSinViolaciones(fixture);
  });
});
