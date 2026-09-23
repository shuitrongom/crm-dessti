// =============================================================================
// Pruebas del shell de navegacion: marca sensible al ambito (Req 26, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real, que la marca del
// drawer y el titulo de la barra superior respetan el ambito del Usuario:
//   - plataforma (super_admin) -> muestra "Dess-TI" y NO consulta el branding.
//   - empresa -> consulta GET /empresa/branding UNA vez y muestra el
//     nombreVisible (o el logo con alt), nunca "Dess-TI".
//   - empresa sin branding configurado -> etiqueta neutral, nunca "Dess-TI".
//   - portal -> no consulta branding ni muestra "Dess-TI".
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { type Event as RouterEvent, NavigationEnd, Router, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { vi } from 'vitest';

import { ShellLayout } from './shell-layout';
import { AuthService } from '../../auth/auth.service';
import { ModulosEmpresaService } from '../../auth/modulos-empresa.service';
import { NavigationService } from '../../navigation/navigation';
import { TematizacionService } from '../../services/tematizacion.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

type Ambito = 'plataforma' | 'empresa' | 'portal';

/** AuthService de prueba: solo lo que consulta el shell. Ambito reactivo. */
class AuthServiceStub {
  readonly ambitoSignal = signal<Ambito>('empresa');
  readonly ambito = this.ambitoSignal.asReadonly();
  readonly nombreMostrado = signal<string | null>('admin@acme');
}

/**
 * NavigationService de prueba: expone tres secciones colapsables ("Cuenta",
 * "Comercial (CRM)" y "Compras"), suficiente para ejercitar que TODAS las
 * secciones (incluida "Cuenta") son acordeones que arrancan cerrados y no se
 * auto-abren al navegar.
 */
class NavigationServiceStub {
  readonly grupos = signal([
    {
      titulo: 'Cuenta',
      items: [
        { etiqueta: 'Inicio', ruta: '/empresa/inicio', icono: 'dashboard' },
        { etiqueta: 'Usuarios', ruta: '/empresa/administracion/usuarios', icono: 'group' },
      ],
    },
    {
      titulo: 'Comercial (CRM)',
      items: [
        { etiqueta: 'Clientes', ruta: '/empresa/comercial/clientes', icono: 'contacts' },
        { etiqueta: 'Oportunidades', ruta: '/empresa/comercial/oportunidades', icono: 'trending_up' },
      ],
    },
    {
      titulo: 'Compras',
      items: [{ etiqueta: 'Compras', ruta: '/empresa/compras/requisiciones', icono: 'shopping_cart' }],
    },
  ]);
}

/** ModulosEmpresaService de prueba: cuenta las invocaciones de refrescar(). */
class ModulosEmpresaServiceStub {
  refrescos = 0;
  refrescar(): void {
    this.refrescos += 1;
  }
  limpiar(): void {
    /* sin efecto en pruebas */
  }
  tieneModulo(): boolean {
    return true;
  }
}

/**
 * TematizacionService de prueba: espia las llamadas que el shell hace al entrar
 * al ambito empresa (aplicar/limpiar) sin tocar el DOM real ni derivar paletas.
 * `colorActivo` se expone para respetar la superficie publica del servicio.
 */
class TematizacionServiceStub {
  aplicar = vi.fn<(colorPrimario: string, modo?: 'light' | 'dark') => void>();
  limpiar = vi.fn<() => void>();
  colorActivo = vi.fn<() => string | null>(() => null);
}

const URL_BRANDING = '/api/v1/empresa/branding';

describe('ShellLayout (marca sensible al ambito)', () => {
  let fixture: ComponentFixture<ShellLayout>;
  let http: HttpTestingController;
  let auth: AuthServiceStub;
  let modulos: ModulosEmpresaServiceStub;
  let tematizacion: TematizacionServiceStub;

  function crear(ambito: Ambito): void {
    auth.ambitoSignal.set(ambito);
    fixture = TestBed.createComponent(ShellLayout);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    auth = new AuthServiceStub();
    modulos = new ModulosEmpresaServiceStub();
    tematizacion = new TematizacionServiceStub();
    await TestBed.configureTestingModule({
      imports: [ShellLayout, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ModulosEmpresaService, useValue: modulos },
        { provide: NavigationService, useClass: NavigationServiceStub },
        { provide: TematizacionService, useValue: tematizacion },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  function textoMarca(): string {
    const marca = fixture.nativeElement.querySelector('.shell__marca') as HTMLElement;
    return marca?.textContent ?? '';
  }

  it('plataforma: muestra Dess-TI y NO consulta el branding de empresa', () => {
    crear('plataforma');
    http.expectNone(URL_BRANDING);
    expect(textoMarca()).toContain('Dess');
    expect(textoMarca()).toContain('-TI');
  });

  it('portal: NO consulta el branding de empresa y conserva su titulo', () => {
    crear('portal');
    // Portal (cliente) no es una empresa-tenant: no debe consultar GET /empresa/branding.
    http.expectNone(URL_BRANDING);
    const titulo = fixture.nativeElement.querySelector('.shell__titulo') as HTMLElement;
    expect(titulo.textContent?.trim()).toBe('Portal del cliente');
  });

  it('empresa: consulta el branding y muestra el nombreVisible (no Dess-TI)', () => {
    crear('empresa');
    const req = http.expectOne(URL_BRANDING);
    expect(req.request.method).toBe('GET');
    req.flush({ nombreVisible: 'Rotulos del Norte', logo: null });
    fixture.detectChanges();

    const texto = textoMarca();
    expect(texto).toContain('Rotulos del Norte');
    expect(texto).not.toContain('Dess-TI');
  });

  it('empresa con logo: renderiza <img> con alt = nombre de la empresa', () => {
    crear('empresa');
    const req = http.expectOne(URL_BRANDING);
    req.flush({ nombreVisible: 'Rotulos del Norte', logo: 'data:image/png;base64,AAAA' });
    fixture.detectChanges();

    const img = fixture.nativeElement.querySelector('.shell__marca-imagen') as HTMLImageElement;
    expect(img).toBeTruthy();
    expect(img.getAttribute('alt')).toBe('Rotulos del Norte');
    expect(textoMarca()).not.toContain('Dess-TI');
  });

  it('empresa sin branding: usa etiqueta neutral y nunca Dess-TI', () => {
    crear('empresa');
    const req = http.expectOne(URL_BRANDING);
    req.flush({ nombreVisible: null, logo: null });
    fixture.detectChanges();

    const texto = textoMarca();
    expect(texto).toContain('Mi empresa');
    expect(texto).not.toContain('Dess-TI');
  });

  it('empresa con error de red: recae en etiqueta neutral, sin romper el shell', () => {
    crear('empresa');
    const req = http.expectOne(URL_BRANDING);
    req.flush('fallo', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const texto = textoMarca();
    expect(texto).toContain('Mi empresa');
    expect(texto).not.toContain('Dess-TI');
  });

  it('no presenta violaciones de accesibilidad WCAG 2.1 A/AA (empresa)', async () => {
    crear('empresa');
    const req = http.expectOne(URL_BRANDING);
    req.flush({ nombreVisible: 'Rotulos del Norte', logo: null });
    fixture.detectChanges();
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });

  // ---------------------------------------------------------------------------
  // Modulos vivos: el shell dispara refrescar() al montarse para una empresa y
  // en cada NavigationEnd. El servicio ignora internamente el refresco fuera de
  // empresa; el shell delega esa guarda de ambito en el servicio.
  // ---------------------------------------------------------------------------
  describe('refresco de Modulos vivos (sin re-login)', () => {
    it('empresa: refresca los Modulos al montarse el shell', () => {
      crear('empresa');
      http.expectOne(URL_BRANDING).flush({ nombreVisible: null, logo: null });
      expect(modulos.refrescos).toBeGreaterThanOrEqual(1);
    });

    it('empresa: refresca los Modulos en cada NavigationEnd', () => {
      crear('empresa');
      http.expectOne(URL_BRANDING).flush({ nombreVisible: null, logo: null });
      const inicial = modulos.refrescos;

      const router = TestBed.inject(Router);
      const eventos = router.events as unknown as Subject<RouterEvent>;
      eventos.next(new NavigationEnd(1, '/empresa/inicio', '/empresa/inicio'));
      expect(modulos.refrescos).toBe(inicial + 1);

      eventos.next(new NavigationEnd(2, '/empresa/comercial/clientes', '/empresa/comercial/clientes'));
      expect(modulos.refrescos).toBe(inicial + 2);
    });

    it('plataforma: el shell NO consulta branding y delega en el servicio la guarda de ambito', () => {
      // El shell invoca refrescar() en NavigationEnd sin filtrar por ambito; es
      // el servicio quien ignora el refresco en plataforma. Aqui verificamos que
      // el shell de plataforma no monta el efecto de carga inicial de empresa.
      crear('plataforma');
      http.expectNone(URL_BRANDING);
      // Sin carga inicial de empresa: el contador permanece en 0 tras el montaje.
      expect(modulos.refrescos).toBe(0);
    });
  });

  // ---------------------------------------------------------------------------
  // Acordeon de navegacion: TODAS las secciones (incluida "Cuenta") son
  // colapsables y arrancan CERRADAS. La navegacion NUNCA auto-abre una seccion;
  // solo el clic/teclado del Usuario en un encabezado alterna su expansion.
  // ---------------------------------------------------------------------------
  describe('acordeon de navegacion (secciones colapsables)', () => {
    /** Emite un NavigationEnd hacia la URL indicada y refresca la vista. */
    function navegarA(url: string): void {
      const router = TestBed.inject(Router);
      const eventos = router.events as unknown as Subject<RouterEvent>;
      eventos.next(new NavigationEnd(1, url, url));
      fixture.detectChanges();
    }

    /** Localiza el <button> de encabezado de una seccion por su texto de titulo. */
    function botonSeccion(titulo: string): HTMLButtonElement | null {
      const botones = Array.from(
        fixture.nativeElement.querySelectorAll('.shell__seccion-boton'),
      ) as HTMLButtonElement[];
      return botones.find((b) => b.textContent?.includes(titulo)) ?? null;
    }

    /** Prepara el shell de empresa (resuelve la peticion de branding). */
    function crearEmpresa(): void {
      crear('empresa');
      http.expectOne(URL_BRANDING).flush({ nombreVisible: null, logo: null });
      fixture.detectChanges();
    }

    it('TODAS las secciones (incluida "Cuenta") se pintan como encabezados colapsables (button con aria-controls)', () => {
      crearEmpresa();
      const cuenta = botonSeccion('Cuenta');
      const comercial = botonSeccion('Comercial (CRM)');
      const compras = botonSeccion('Compras');
      expect(cuenta).toBeTruthy();
      expect(comercial).toBeTruthy();
      expect(compras).toBeTruthy();
      expect(cuenta!.hasAttribute('aria-controls')).toBe(true);
      expect(comercial!.hasAttribute('aria-controls')).toBe(true);
      expect(compras!.hasAttribute('aria-controls')).toBe(true);
    });

    it('al cargar, NINGUNA seccion esta expandida (todas cerradas, incluida "Cuenta")', () => {
      crearEmpresa();
      for (const titulo of ['Cuenta', 'Comercial (CRM)', 'Compras']) {
        const boton = botonSeccion(titulo)!;
        expect(boton.getAttribute('aria-expanded')).toBe('false');
        // Cerrada: la lista de items NO existe en el DOM (removida por @if).
        const lista = fixture.nativeElement.querySelector(
          `#${boton.getAttribute('aria-controls')}`,
        ) as HTMLElement | null;
        expect(lista).toBeNull();
      }
    });

    it('al cargar en una ruta profunda, su seccion NO se auto-abre', () => {
      auth.ambitoSignal.set('empresa');
      const router = TestBed.inject(Router);
      // La ruta vigente del Router apunta a una seccion de negocio antes de crear
      // el componente, simulando una recarga en ruta profunda.
      vi.spyOn(router, 'url', 'get').mockReturnValue('/empresa/comercial/clientes');
      crearEmpresa();

      const comercial = botonSeccion('Comercial (CRM)')!;
      expect(comercial.getAttribute('aria-expanded')).toBe('false');
      // Cerrada: la lista de items NO existe en el DOM (removida por @if).
      const lista = fixture.nativeElement.querySelector(
        `#${comercial.getAttribute('aria-controls')}`,
      ) as HTMLElement | null;
      expect(lista).toBeNull();
    });

    it('al navegar (NavigationEnd) a una seccion, esa seccion NO se auto-abre', () => {
      crearEmpresa();
      navegarA('/empresa/comercial/clientes');

      const comercial = botonSeccion('Comercial (CRM)')!;
      const compras = botonSeccion('Compras')!;
      expect(comercial.getAttribute('aria-expanded')).toBe('false');
      expect(compras.getAttribute('aria-expanded')).toBe('false');

      // Cerrada: la lista de items NO existe en el DOM (removida por @if).
      const listaComercial = fixture.nativeElement.querySelector(
        `#${comercial.getAttribute('aria-controls')}`,
      ) as HTMLElement | null;
      expect(listaComercial).toBeNull();
    });

    it('al pulsar un encabezado cambia aria-expanded y muestra/oculta sus items', () => {
      crearEmpresa();

      const compras = botonSeccion('Compras')!;
      const idLista = compras.getAttribute('aria-controls')!;
      const listaDe = () =>
        fixture.nativeElement.querySelector(`#${idLista}`) as HTMLElement | null;

      // Cerrada: sin lista en el DOM.
      expect(compras.getAttribute('aria-expanded')).toBe('false');
      expect(listaDe()).toBeNull();

      // Abierta: la lista aparece en el DOM.
      compras.click();
      fixture.detectChanges();
      expect(compras.getAttribute('aria-expanded')).toBe('true');
      expect(listaDe()).not.toBeNull();

      // Cerrada de nuevo: la lista se vuelve a remover del DOM.
      compras.click();
      fixture.detectChanges();
      expect(compras.getAttribute('aria-expanded')).toBe('false');
      expect(listaDe()).toBeNull();
    });

    it('"Cuenta" tambien se abre y cierra por clic en su encabezado', () => {
      crearEmpresa();

      const cuenta = botonSeccion('Cuenta')!;
      const idLista = cuenta.getAttribute('aria-controls')!;
      const listaDe = () =>
        fixture.nativeElement.querySelector(`#${idLista}`) as HTMLElement | null;

      // Cerrada: sin lista en el DOM.
      expect(cuenta.getAttribute('aria-expanded')).toBe('false');
      expect(listaDe()).toBeNull();

      // Abierta: la lista aparece con sus enlaces.
      cuenta.click();
      fixture.detectChanges();
      expect(cuenta.getAttribute('aria-expanded')).toBe('true');
      const lista = listaDe();
      expect(lista).not.toBeNull();
      const textos = Array.from(lista!.querySelectorAll('.shell__enlace')).map(
        (e) => e.textContent ?? '',
      );
      expect(textos.some((t) => t.includes('Inicio'))).toBe(true);
      expect(textos.some((t) => t.includes('Usuarios'))).toBe(true);

      // Cerrada de nuevo: la lista se vuelve a remover del DOM.
      cuenta.click();
      fixture.detectChanges();
      expect(cuenta.getAttribute('aria-expanded')).toBe('false');
      expect(listaDe()).toBeNull();
    });

    it('el estado abierto que el Usuario deja se conserva al navegar (no se recolapsa)', () => {
      crearEmpresa();

      const compras = botonSeccion('Compras')!;
      compras.click();
      fixture.detectChanges();
      expect(compras.getAttribute('aria-expanded')).toBe('true');

      // Una navegacion a otra seccion no cierra la que el Usuario abrio.
      navegarA('/empresa/comercial/clientes');
      expect(botonSeccion('Compras')!.getAttribute('aria-expanded')).toBe('true');
      // Y tampoco auto-abre la seccion de la nueva ruta.
      expect(botonSeccion('Comercial (CRM)')!.getAttribute('aria-expanded')).toBe('false');
    });

    it('no presenta violaciones de accesibilidad WCAG con secciones colapsables', async () => {
      crearEmpresa();
      // Con una seccion expandida por el Usuario para cubrir el estado abierto.
      botonSeccion('Comercial (CRM)')!.click();
      fixture.detectChanges();
      await fixture.whenStable();
      await esperarSinViolaciones(fixture);
    });
  });

  // ---------------------------------------------------------------------------
  // Tematizacion por tenant (Req 4.1-4.4, 5.1): al entrar al ambito empresa el
  // shell consulta el branding y delega la tematizacion en TematizacionService.
  //   - Con Color_Primario_Marca -> aplicar(color) (Req 4.2).
  //   - Sin color -> limpiar() y NUNCA aplicar (mantiene Tema_Corporativo, 4.3).
  //   - Carga fallida -> limpiar() y el shell no rompe (Req 4.4).
  //   - Plataforma -> no consulta branding ni tematiza por carga (Req 5.1).
  // ---------------------------------------------------------------------------
  describe('tematización por tenant (Req 4.x)', () => {
    it('empresa con color: aplica la Paleta_Derivada del Color_Primario_Marca (Req 4.2)', () => {
      crear('empresa');
      const req = http.expectOne(URL_BRANDING);
      req.flush({ nombreVisible: 'X', logo: null, colorPrimario: '#1a2b3c' });
      fixture.detectChanges();

      expect(tematizacion.aplicar).toHaveBeenCalledWith('#1a2b3c');
      expect(tematizacion.limpiar).not.toHaveBeenCalled();
    });

    it('empresa sin color: limpia y mantiene el Tema_Corporativo, sin aplicar (Req 4.3)', () => {
      crear('empresa');
      const req = http.expectOne(URL_BRANDING);
      req.flush({ nombreVisible: 'X', logo: null, colorPrimario: null });
      fixture.detectChanges();

      expect(tematizacion.limpiar).toHaveBeenCalledTimes(1);
      expect(tematizacion.aplicar).not.toHaveBeenCalled();
    });

    it('empresa con carga fallida: limpia, mantiene el tema y el shell no rompe (Req 4.4)', () => {
      crear('empresa');
      const req = http.expectOne(URL_BRANDING);
      // El GET falla: el shell debe seguir operando (etiqueta neutral) sin lanzar.
      expect(() => {
        req.flush('fallo', { status: 500, statusText: 'Server Error' });
        fixture.detectChanges();
      }).not.toThrow();

      expect(tematizacion.limpiar).toHaveBeenCalledTimes(1);
      expect(tematizacion.aplicar).not.toHaveBeenCalled();
      // El shell recae en la etiqueta neutral (sigue mostrando marca legible).
      expect(textoMarca()).toContain('Mi empresa');
    });

    it('plataforma: NO consulta branding y LIMPIA el color de marca (Req 5.1)', () => {
      crear('plataforma');
      // La plataforma no es tenant: no consulta el branding de empresa.
      http.expectNone(URL_BRANDING);
      // Nunca aplica color de empresa en plataforma.
      expect(tematizacion.aplicar).not.toHaveBeenCalled();
      // Pero SI limpia cualquier color que hubiera quedado de una sesion de
      // empresa previa en la misma pestana: el branding queda aislado al ambito
      // empresa y nunca se filtra a la plataforma (super_admin).
      expect(tematizacion.limpiar).toHaveBeenCalled();
    });
  });
});
