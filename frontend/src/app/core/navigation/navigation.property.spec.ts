// =============================================================================
// Prueba de propiedad del NavigationService — filtrado de navegacion por Giro
// -----------------------------------------------------------------------------
// Feature: plataforma-multigiro, Property 7: La navegacion no expone verticales
// ajenos (frontend).
//
// Validates: Requirements 9.2, 9.3
//
// Enunciado (design.md, Property 7):
//   Para todo Giro g y todo conjunto de items de navegacion etiquetados por
//   Giro, los items visibles resueltos para una Empresa de Giro g NO incluyen
//   ningun item perteneciente a un vertical de Giro distinto de g.
//
// Enfoque property-based:
//   El proyecto usa Vitest y NO tiene `fast-check` en devDependencies (no se
//   agrega). Por ello la propiedad se verifica con un generador "hecho a mano":
//   se recorre el producto cartesiano de (giro del usuario) x (subconjuntos de
//   permisos del vertical y de nucleo), complementado con casos aleatorios
//   acotados y con una semilla determinista para reproducibilidad. En cada caso
//   se monta el NavigationService con un AuthService simulado y se afirma el
//   invariante sobre `NavigationService.items()`.
//
// Invariantes verificados en cada combinacion (usuario con giro g):
//   1. Ningun item del Vertical_Anuncios (rutas de operacion/mantenimiento con
//      recurso del vertical) aparece si g != 'anuncios-luminosos', AUN cuando el
//      usuario tenga el permiso correspondiente.
//   2. Si g == 'anuncios-luminosos' y el usuario tiene el permiso, el item del
//      vertical SI puede (y debe) aparecer.
//   3. Los items de Nucleo (incluido el inventario base: materiales e inventario
//      avanzado) dependen SOLO del permiso, nunca del giro: aparecen si y solo si
//      se concede el permiso, con cualquier valor de g.
// =============================================================================

import { TestBed } from '@angular/core/testing';

import { NavigationService } from './navigation';
import { AuthService } from '../auth/auth.service';
import { ModulosEmpresaService } from '../auth/modulos-empresa.service';
import { GIRO_ANUNCIOS } from '../auth/auth.models';

// -----------------------------------------------------------------------------
// AuthService simulado (mismo patron que navigation.spec.ts / auth.service.spec)
// -----------------------------------------------------------------------------

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
 * Construye un AuthService simulado del ambito empresa cuyo giro y conjunto de
 * permisos concedidos se fijan por caso. `permisosConcedidos` es el conjunto de
 * autoridades atomicas "recurso:operacion" que el usuario posee.
 */
function authFake(giro: string | null, permisosConcedidos: ReadonlySet<string>): AuthFake {
  const tienePermiso = (recurso: string, operacion: string) =>
    permisosConcedidos.has(`${recurso}:${operacion}`);
  return {
    ambito: () => 'empresa',
    giro: () => giro,
    tieneRol: () => false,
    tienePermiso,
    tieneAlgunPermiso: (...permisos: string[]) => permisos.some((p) => permisosConcedidos.has(p)),
    esGiro: (clave: string) => giro === clave,
    // Esta propiedad aisla el efecto del giro: se conceden todos los modulos para
    // que el gating por modulo no interfiera con la verificacion del vertical.
    tieneModulo: () => true,
  };
}

/** Crea el NavigationService con el AuthService simulado dado (TestBed aislado). */
function crearNav(auth: AuthFake): NavigationService {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: AuthService, useValue: auth },
      // Modulos vivos: se conceden todos (delega en el AuthFake) para aislar aqui
      // el efecto del giro sobre el vertical, sin interferencia del gating por
      // modulo (mismo criterio que `tieneModulo: () => true` del AuthFake).
      {
        provide: ModulosEmpresaService,
        useValue: { tieneModulo: (clave: string) => auth.tieneModulo(clave) },
      },
    ],
  });
  return TestBed.inject(NavigationService);
}

// -----------------------------------------------------------------------------
// Modelo del espacio de entrada (etiquetado de items por Giro)
// -----------------------------------------------------------------------------

/**
 * Items del Vertical_Anuncios: ruta -> permiso "recurso:operacion" que los
 * habilita (junto con esGiro(GIRO_ANUNCIOS)). Refleja las definiciones de
 * NavigationService para el vertical (Req 9.2, 9.3).
 */
const VERTICAL_ITEMS: ReadonlyArray<{ ruta: string; permiso: string }> = [
  { ruta: '/empresa/operacion/ordenes-fabricacion', permiso: 'orden_fabricacion:listar' },
  { ruta: '/empresa/operacion/levantamientos', permiso: 'levantamiento_sitio:listar' },
  { ruta: '/empresa/operacion/permisos', permiso: 'permiso_instalacion:listar' },
  { ruta: '/empresa/operacion/instalacion', permiso: 'orden_trabajo_instalacion:listar' },
  { ruta: '/empresa/operacion/proyectos', permiso: 'proyecto:listar' },
  { ruta: '/empresa/mantenimiento/contratos', permiso: 'contrato_mantenimiento:listar' },
  { ruta: '/empresa/mantenimiento/tickets', permiso: 'ticket_servicio:listar' },
];

/**
 * Muestra representativa de items de Nucleo: ruta -> permiso que los habilita.
 * Incluye el inventario base (materiales e inventario avanzado) que, pese a
 * colgar de /empresa/operacion, NO se filtra por giro.
 */
const NUCLEO_ITEMS: ReadonlyArray<{ ruta: string; permiso: string }> = [
  { ruta: '/empresa/comercial/clientes', permiso: 'cliente:listar' },
  { ruta: '/empresa/operacion/materiales', permiso: 'material:listar' },
  { ruta: '/empresa/operacion/inventario-avanzado', permiso: 'almacen:listar' },
  { ruta: '/empresa/compras/requisiciones', permiso: 'requisicion_compra:listar' },
  { ruta: '/empresa/facturacion/facturas', permiso: 'factura:listar' },
];

const RUTAS_VERTICAL = new Set(VERTICAL_ITEMS.map((i) => i.ruta));

/** Giros a explorar: anuncios, otro giro registrado, giro desconocido, vacio y nulo. */
const GIROS: ReadonlyArray<string | null> = [
  GIRO_ANUNCIOS,
  'manufactura',
  'giro-desconocido',
  '',
  null,
];

// -----------------------------------------------------------------------------
// PRNG determinista (mulberry32) para casos aleatorios reproducibles
// -----------------------------------------------------------------------------

/** Generador pseudoaleatorio determinista sembrado, para reproducibilidad. */
function mulberry32(semilla: number): () => number {
  let a = semilla >>> 0;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Verifica el invariante de Property 7 para un caso (giro, permisos concedidos).
 * Devuelve un mensaje de error si el invariante se viola, o null si se cumple.
 */
function verificarInvariante(
  giro: string | null,
  permisosConcedidos: ReadonlySet<string>,
): string | null {
  const nav = crearNav(authFake(giro, permisosConcedidos));
  const rutasVisibles = new Set(nav.items().map((i) => i.ruta));
  const esAnuncios = giro === GIRO_ANUNCIOS;

  // Invariantes 1 y 2: items del vertical.
  for (const { ruta, permiso } of VERTICAL_ITEMS) {
    const visible = rutasVisibles.has(ruta);
    const tienePermiso = permisosConcedidos.has(permiso);
    const esperado = esAnuncios && tienePermiso;
    if (visible !== esperado) {
      return (
        `Vertical: giro=${JSON.stringify(giro)} ruta=${ruta} permiso=${tienePermiso} ` +
        `-> visible=${visible}, esperado=${esperado}`
      );
    }
  }

  // Invariante extra de refuerzo: NINGUNA ruta de vertical visible con giro ajeno.
  if (!esAnuncios) {
    for (const ruta of rutasVisibles) {
      if (RUTAS_VERTICAL.has(ruta)) {
        return `Vertical ajeno expuesto: giro=${JSON.stringify(giro)} ruta=${ruta}`;
      }
    }
  }

  // Invariante 3: items de nucleo dependen solo del permiso (no del giro).
  for (const { ruta, permiso } of NUCLEO_ITEMS) {
    const visible = rutasVisibles.has(ruta);
    const esperado = permisosConcedidos.has(permiso);
    if (visible !== esperado) {
      return (
        `Nucleo: giro=${JSON.stringify(giro)} ruta=${ruta} permiso=${esperado} ` +
        `-> visible=${visible}`
      );
    }
  }

  return null;
}

describe('Feature: plataforma-multigiro, Property 7: La navegacion no expone verticales ajenos', () => {
  // Todos los permisos manejados en la propiedad (vertical + nucleo).
  const TODOS_PERMISOS = [
    ...VERTICAL_ITEMS.map((i) => i.permiso),
    ...NUCLEO_ITEMS.map((i) => i.permiso),
  ];

  it('cubre sistematicamente el producto cartesiano de giro x permisos del vertical', () => {
    // Recorrido exhaustivo: por cada giro, todos los subconjuntos del conjunto de
    // permisos del vertical (2^7 = 128). Los permisos de nucleo se conceden todos
    // para aislar aqui el efecto del giro sobre el vertical.
    const permisosNucleoTodos = new Set(NUCLEO_ITEMS.map((i) => i.permiso));
    let combinaciones = 0;

    for (const giro of GIROS) {
      const n = VERTICAL_ITEMS.length; // 7
      for (let mascara = 0; mascara < 1 << n; mascara++) {
        const concedidos = new Set<string>(permisosNucleoTodos);
        for (let i = 0; i < n; i++) {
          if (mascara & (1 << i)) {
            concedidos.add(VERTICAL_ITEMS[i].permiso);
          }
        }
        const error = verificarInvariante(giro, concedidos);
        expect(error, error ?? undefined).toBeNull();
        combinaciones++;
      }
    }

    // 5 giros x 128 subconjuntos = 640 combinaciones exhaustivas.
    expect(combinaciones).toBe(GIROS.length * (1 << VERTICAL_ITEMS.length));
  });

  it('cubre casos aleatorios acotados de (giro, permisos arbitrarios) con semilla determinista', () => {
    // Generador property-based ligero: para cada iteracion se elige un giro y un
    // subconjunto arbitrario de TODOS los permisos (vertical + nucleo), de modo
    // que tambien se ejercita el nucleo con permisos parciales bajo cualquier giro.
    const rnd = mulberry32(0x9e3779b9);
    const ITERACIONES = 200;

    for (let iter = 0; iter < ITERACIONES; iter++) {
      const giro = GIROS[Math.floor(rnd() * GIROS.length)];
      const concedidos = new Set<string>();
      for (const permiso of TODOS_PERMISOS) {
        if (rnd() < 0.5) {
          concedidos.add(permiso);
        }
      }
      const error = verificarInvariante(giro, concedidos);
      expect(error, error ?? undefined).toBeNull();
    }
  });
});
