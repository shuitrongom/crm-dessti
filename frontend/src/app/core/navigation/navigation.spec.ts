// =============================================================================
// Pruebas unitarias del NavigationService (deny-by-default — Req 3, 9.2, 9.3)
// -----------------------------------------------------------------------------
// Verifican que la navegacion visible se compone por ambito, permiso y, para el
// Vertical_Anuncios, tambien por Giro:
//   - Un Usuario con permiso de operacion pero giro != anuncios-luminosos NO ve
//     los items del vertical (ordenes de fabricacion, levantamientos, permisos,
//     instalacion, proyectos, contratos y tickets de mantenimiento).
//   - Con giro anuncios-luminosos Y el permiso, SI los ve.
//   - Los items del Nucleo (clientes, materiales, inventario, etc.) no se ven
//     afectados por el giro.
//
// Se inyecta un AuthService simulado (solo los metodos que consulta el servicio).
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';

import { NavigationService } from './navigation';
import { AuthService } from '../auth/auth.service';
import { ModulosEmpresaService } from '../auth/modulos-empresa.service';

/** AuthService simulado: solo lo que consulta NavigationService. */
interface AuthFake {
  ambito: () => 'plataforma' | 'empresa' | 'portal';
  giro: () => string | null;
  tieneRol: (rol: string) => boolean;
  tienePermiso: (recurso: string, operacion: string) => boolean;
  tieneAlgunPermiso: (...permisos: string[]) => boolean;
  esGiro: (clave: string) => boolean;
  tieneModulo: (clave: string) => boolean;
}

/**
 * Construye un AuthService simulado para el ambito empresa. Por defecto concede
 * TODOS los permisos Y TODOS los modulos (para aislar el efecto del giro sobre
 * la visibilidad). Los casos de gating por modulo sobrescriben `tieneModulo`.
 */
function authFake(overrides: Partial<AuthFake> = {}): AuthFake {
  const giro = overrides.giro ?? (() => null);
  return {
    ambito: () => 'empresa',
    giro,
    tieneRol: () => false,
    tienePermiso: () => true,
    tieneAlgunPermiso: () => true,
    esGiro: (clave) => giro() === clave,
    tieneModulo: () => true,
    ...overrides,
  };
}

/**
 * ModulosEmpresaService simulado: delega `tieneModulo` en el AuthFake, de modo
 * que los casos existentes que ajustan `authFake({ tieneModulo })` siguen siendo
 * validos ahora que NavigationService consulta el servicio de Modulos vivos.
 */
function modulosFake(auth: AuthFake): Pick<ModulosEmpresaService, 'tieneModulo'> {
  return { tieneModulo: (clave: string) => auth.tieneModulo(clave) };
}

/** Crea el NavigationService con el AuthService y ModulosEmpresaService dados. */
function crearNav(
  auth: AuthFake,
  modulos: Pick<ModulosEmpresaService, 'tieneModulo'> = modulosFake(auth),
): NavigationService {
  TestBed.configureTestingModule({
    providers: [
      { provide: AuthService, useValue: auth },
      { provide: ModulosEmpresaService, useValue: modulos },
    ],
  });
  return TestBed.inject(NavigationService);
}

/** Rutas de los items del Vertical_Anuncios (permiso Y giro). */
const RUTAS_VERTICAL = [
  '/empresa/operacion/ordenes-fabricacion',
  '/empresa/operacion/levantamientos',
  '/empresa/operacion/permisos',
  '/empresa/operacion/instalacion',
  '/empresa/operacion/proyectos',
  '/empresa/mantenimiento/contratos',
  '/empresa/mantenimiento/tickets',
];

/** Rutas de Nucleo que NO deben depender del giro (incluye inventario base). */
const RUTAS_NUCLEO = [
  '/empresa/comercial/clientes',
  '/empresa/operacion/materiales',
  '/empresa/operacion/inventario-avanzado',
  '/empresa/compras/requisiciones',
  '/empresa/facturacion/facturas',
];

describe('NavigationService (filtrado por giro — Req 9.2, 9.3)', () => {
  it('con giro != anuncios-luminosos NO expone los items del vertical, aun con permiso', () => {
    const nav = crearNav(authFake({ giro: () => 'manufactura' }));
    const rutas = nav.items().map((i) => i.ruta);
    for (const ruta of RUTAS_VERTICAL) {
      expect(rutas).not.toContain(ruta);
    }
  });

  it('sin giro (super_admin sin giro) NO expone los items del vertical', () => {
    const nav = crearNav(authFake({ giro: () => null }));
    const rutas = nav.items().map((i) => i.ruta);
    for (const ruta of RUTAS_VERTICAL) {
      expect(rutas).not.toContain(ruta);
    }
  });

  it('con giro anuncios-luminosos Y permiso SI expone todos los items del vertical', () => {
    const nav = crearNav(authFake({ giro: () => 'anuncios-luminosos' }));
    const rutas = nav.items().map((i) => i.ruta);
    for (const ruta of RUTAS_VERTICAL) {
      expect(rutas).toContain(ruta);
    }
  });

  it('con giro anuncios-luminosos pero SIN el permiso, no expone el item del vertical', () => {
    const nav = crearNav(
      authFake({
        giro: () => 'anuncios-luminosos',
        // Deniega solo orden_fabricacion; el resto de permisos siguen concedidos.
        tienePermiso: (r) => r !== 'orden_fabricacion',
      }),
    );
    const rutas = nav.items().map((i) => i.ruta);
    expect(rutas).not.toContain('/empresa/operacion/ordenes-fabricacion');
    // Otro item del vertical (con permiso y giro) si aparece.
    expect(rutas).toContain('/empresa/operacion/levantamientos');
  });

  it('los items del Nucleo aparecen con giro de anuncios-luminosos', () => {
    const nav = crearNav(authFake({ giro: () => 'anuncios-luminosos' }));
    const rutas = nav.items().map((i) => i.ruta);
    for (const ruta of RUTAS_NUCLEO) {
      expect(rutas).toContain(ruta);
    }
  });

  it('los items del Nucleo aparecen igualmente con un giro distinto (no dependen del giro)', () => {
    const nav = crearNav(authFake({ giro: () => 'manufactura' }));
    const rutas = nav.items().map((i) => i.ruta);
    for (const ruta of RUTAS_NUCLEO) {
      expect(rutas).toContain(ruta);
    }
  });
});

// -----------------------------------------------------------------------------
// Gating por MODULO (ambito empresa): el item exige el modulo contratado ademas
// del permiso (y giro para el vertical). Los items de administracion base no
// llevan modulo y siempre estan disponibles para el admin.
// -----------------------------------------------------------------------------

/** Rutas de administracion base: NO llevan modulo (siempre disponibles). */
const RUTAS_ADMIN_BASE = [
  '/empresa/inicio',
  '/empresa/administracion/usuarios',
  '/empresa/administracion/roles',
  '/empresa/administracion/sesiones',
  '/empresa/administracion/branding',
];

describe('NavigationService (filtrado por modulo — gating por Suscripcion)', () => {
  it('con modulos=[estrategia] solo expone items de estrategia (+admin base), oculta el resto', () => {
    const nav = crearNav(
      authFake({ tieneModulo: (clave) => clave === 'estrategia' }),
    );
    const rutas = nav.items().map((i) => i.ruta);

    // Admin base siempre visible.
    for (const ruta of RUTAS_ADMIN_BASE) {
      expect(rutas).toContain(ruta);
    }
    // Unica entrada de estrategia (la vista completa) visible.
    expect(rutas).toContain('/empresa/estrategia-vistas');
    // Items de otros modulos ocultos.
    expect(rutas).not.toContain('/empresa/comercial/clientes');
    expect(rutas).not.toContain('/empresa/facturacion/facturas');
    expect(rutas).not.toContain('/empresa/presupuestos-vistas');
  });

  it('con modulos=[comercial] expone los items comerciales (dado el permiso)', () => {
    const nav = crearNav(
      authFake({ tieneModulo: (clave) => clave === 'comercial' }),
    );
    const rutas = nav.items().map((i) => i.ruta);
    expect(rutas).toContain('/empresa/comercial/clientes');
    expect(rutas).toContain('/empresa/comercial/oportunidades');
    expect(rutas).toContain('/empresa/comercial/cotizaciones');
    // Estrategia oculta (modulo no contratado).
    expect(rutas).not.toContain('/empresa/estrategia-vistas');
  });

  it('con modulos=[] (Empresa sin modulos) solo queda la administracion base', () => {
    const nav = crearNav(authFake({ tieneModulo: () => false }));
    const rutas = nav.items().map((i) => i.ruta);
    for (const ruta of RUTAS_ADMIN_BASE) {
      expect(rutas).toContain(ruta);
    }
    // Ningun item gestionado por modulo aparece.
    expect(rutas).not.toContain('/empresa/comercial/clientes');
    expect(rutas).not.toContain('/empresa/estrategia-vistas');
    expect(rutas).not.toContain('/empresa/facturacion/facturas');
  });

  it('estrategia y presupuestos son modulos independientes', () => {
    const nav = crearNav(authFake({ tieneModulo: (clave) => clave === 'estrategia' }));
    const rutas = nav.items().map((i) => i.ruta);
    expect(rutas).toContain('/empresa/estrategia-vistas');
    // presupuestos es su propia clave: no aparece con solo estrategia.
    expect(rutas).not.toContain('/empresa/presupuestos-vistas');
  });

  it('con solo modulo operacion: Materiales visible, Inventario avanzado oculto', () => {
    const nav = crearNav(
      authFake({ giro: () => 'anuncios-luminosos', tieneModulo: (c) => c === 'operacion' }),
    );
    const rutas = nav.items().map((i) => i.ruta);
    expect(rutas).toContain('/empresa/operacion/materiales');
    expect(rutas).not.toContain('/empresa/operacion/inventario-avanzado');
  });

  it('con solo modulo inventario-avanzado: Inventario avanzado visible, Materiales oculto', () => {
    const nav = crearNav(
      authFake({ giro: () => 'anuncios-luminosos', tieneModulo: (c) => c === 'inventario-avanzado' }),
    );
    const rutas = nav.items().map((i) => i.ruta);
    expect(rutas).toContain('/empresa/operacion/inventario-avanzado');
    expect(rutas).not.toContain('/empresa/operacion/materiales');
  });

  it('el gating por modulo NO se aplica al ambito plataforma (super_admin)', () => {
    // super_admin: sin modulos, pero el menu de plataforma no usa gating por modulo.
    const nav = crearNav(
      authFake({
        ambito: () => 'plataforma',
        tieneRol: (r) => r === 'super_admin',
        tieneModulo: () => false,
      }),
    );
    const rutas = nav.items().map((i) => i.ruta);
    expect(rutas).toContain('/plataforma/giros');
    expect(rutas).toContain('/plataforma/empresas');
  });

  it('omite el encabezado de una seccion cuyos items quedan todos filtrados por modulo', () => {
    // Solo comercial contratado: la seccion Catalogo no aplica (empresa), pero
    // verificamos que ningun grupo quede con items de modulos no contratados.
    const nav = crearNav(authFake({ tieneModulo: (c) => c === 'comercial' }));
    const rutasGrupos = nav.grupos().flatMap((g) => g.items.map((i) => i.ruta));
    expect(rutasGrupos).not.toContain('/empresa/facturacion/facturas');
    expect(rutasGrupos).toContain('/empresa/comercial/clientes');
  });
});

// -----------------------------------------------------------------------------
// Secciones del menu de empresa (Task 1): cada item pertenece a una seccion con
// titulo en espanol; las secciones sin items visibles no producen encabezado.
// -----------------------------------------------------------------------------
describe('NavigationService (secciones del menu de empresa)', () => {
  it('con modulos=[estrategia] muestra solo las secciones Cuenta y Estrategia', () => {
    const nav = crearNav(authFake({ tieneModulo: (c) => c === 'estrategia' }));
    const grupos = nav.grupos();
    const titulos = grupos.map((g) => g.titulo);

    // Exactamente las dos secciones esperadas, en orden (Cuenta antes de Estrategia).
    expect(titulos).toEqual(['Cuenta', 'Estrategia']);

    // La seccion Estrategia contiene la unica entrada, etiquetada "Estrategia".
    const estrategia = grupos.find((g) => g.titulo === 'Estrategia');
    expect(estrategia?.items).toEqual([
      { etiqueta: 'Estrategia', ruta: '/empresa/estrategia-vistas', icono: 'flag_circle' },
    ]);

    // No aparecen encabezados de otros modulos.
    expect(titulos).not.toContain('Comercial (CRM)');
    expect(titulos).not.toContain('Facturacion (CFDI)');
    expect(titulos).not.toContain('Presupuestos');
  });

  it('respeta el orden de secciones definido para varios modulos contratados', () => {
    const nav = crearNav(
      authFake({ tieneModulo: (c) => c === 'comercial' || c === 'facturacion' }),
    );
    const titulos = nav.grupos().map((g) => g.titulo);
    expect(titulos).toEqual(['Cuenta', 'Comercial (CRM)', 'Facturación (CFDI)']);
  });

  it('todos los items del ambito empresa llevan seccion (ningun grupo sin titulo)', () => {
    const nav = crearNav(authFake());
    const grupos = nav.grupos();
    expect(grupos.length).toBeGreaterThan(0);
    for (const grupo of grupos) {
      expect(grupo.titulo).not.toBeNull();
    }
  });

  it('la seccion Cuenta ya no incluye "Configuracion" (dead-end eliminado)', () => {
    const nav = crearNav(authFake());
    const cuenta = nav.grupos().find((g) => g.titulo === 'Cuenta');
    expect(cuenta).toBeDefined();
    const etiquetas = cuenta!.items.map((i) => i.etiqueta);
    expect(etiquetas).toEqual(['Inicio', 'Usuarios', 'Roles y permisos', 'Sesiones', 'Branding']);
    expect(etiquetas).not.toContain('Configuracion');
    // Ninguna ruta apunta ya a la configuracion eliminada.
    const rutas = nav.items().map((i) => i.ruta);
    expect(rutas).not.toContain('/empresa/administracion/configuracion');
  });
});

// -----------------------------------------------------------------------------
// Gating por MODULOS VIVOS (sin re-login): el menu se repinta cuando la lista
// viva de Modulos cambia. Se conduce con un ModulosEmpresaService simulado cuyo
// `tieneModulo` lee un signal, de modo que los computed `items`/`grupos` de la
// navegacion se reevaluan al actualizarse la lista viva.
// -----------------------------------------------------------------------------
describe('NavigationService (modulos vivos conducen el menu sin re-login)', () => {
  it('cuando la lista viva anade "comercial", la seccion Comercial aparece (antes oculta)', () => {
    // Lista viva inicial: solo estrategia (el claim del token estaba congelado).
    const modulosVivos = signal<string[]>(['estrategia']);
    const auth = authFake();
    const nav = crearNav(auth, {
      tieneModulo: (clave: string) => modulosVivos().includes(clave),
    });

    // Estado inicial: Comercial oculto.
    expect(nav.items().map((i) => i.ruta)).not.toContain('/empresa/comercial/clientes');
    expect(nav.grupos().map((g) => g.titulo)).not.toContain('Comercial (CRM)');

    // El super_admin anadio "comercial" al plan -> la lista viva se actualiza.
    modulosVivos.set(['estrategia', 'comercial']);

    // El menu se repinta: ahora Comercial esta visible, sin re-login.
    expect(nav.items().map((i) => i.ruta)).toContain('/empresa/comercial/clientes');
    expect(nav.grupos().map((g) => g.titulo)).toContain('Comercial (CRM)');
  });

  it('cuando la lista viva quita un modulo (baja de plan), la seccion se oculta al repintar', () => {
    const modulosVivos = signal<string[]>(['estrategia', 'comercial']);
    const auth = authFake();
    const nav = crearNav(auth, {
      tieneModulo: (clave: string) => modulosVivos().includes(clave),
    });

    expect(nav.items().map((i) => i.ruta)).toContain('/empresa/comercial/clientes');

    // Baja de plan: la lista viva ya no incluye comercial.
    modulosVivos.set(['estrategia']);

    expect(nav.items().map((i) => i.ruta)).not.toContain('/empresa/comercial/clientes');
    expect(nav.grupos().map((g) => g.titulo)).not.toContain('Comercial (CRM)');
  });
});
