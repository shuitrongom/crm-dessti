// =============================================================================
// Pruebas unitarias de EmpresaHome (gating por Modulo en la pagina de Inicio)
// -----------------------------------------------------------------------------
// Verifican que el Inicio degrada con elegancia segun los Modulos contratados:
//   - Con modulos=['estrategia']: carga la esencia/objetivos y NO consulta el
//     Tablero (Modulo 'reportes-bi' no contratado).
//   - Con modulos=[]: solo branding/bienvenida; no consulta estrategia ni tablero.
//   - En ningun caso se llama a un endpoint de un Modulo no contratado (evita el
//     403/500 "Ocurrio un error inesperado").
//
// Se inyectan servicios simulados (spies) y un AuthService simulado; no hay red.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { EmpresaHome } from './empresa-home';
import { AuthService } from '../../../core/auth/auth.service';
import { BrandingService } from '../services/branding.service';
import { MiEmpresaService } from '../services/mi-empresa.service';
import { EstrategiaVistasService } from '../../estrategia-vistas/services/estrategia-vistas.service';
import { TableroService } from '../../reportes/services/tablero.service';
import { OportunidadesService } from '../../comercial/services/oportunidades.service';
import { CotizacionesService } from '../../comercial/services/cotizaciones.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { ObjetivoEstrategico } from '../../estrategia-vistas/models/estrategia.models';
import { Cotizacion, Oportunidad } from '../../comercial/models/comercial.models';

/** AuthService simulado: solo lo que consulta EmpresaHome. */
function authFake(
  modulos: readonly string[],
  nombreMostrado: string | null = 'admin@demofactura.com',
): Partial<AuthService> {
  const tieneModulo = (clave: string) => modulos.includes(clave);
  return {
    // Claim legible para el saludo; NUNCA el UUID tecnico (`sub`).
    nombreMostrado: (() => nombreMostrado) as unknown as AuthService['nombreMostrado'],
    identificador: (() => '11111111-2222-3333-4444-555555555555') as unknown as AuthService['identificador'],
    tieneModulo,
    // Todos los permisos concedidos, para aislar el efecto del gating por modulo.
    tienePermiso: () => true,
    tieneAlgunPermiso: () => true,
  } as Partial<AuthService>;
}

/** Pagina vacia de objetivos. */
function paginaVacia(): PaginaResponse<ObjetivoEstrategico> {
  return { content: [], totalElements: 0, totalPages: 0, size: 5, page: 0 };
}

/** Oportunidad de prueba (solo los campos que consume el resumen comercial). */
function oportunidad(parcial: Partial<Oportunidad> = {}): Oportunidad {
  return {
    id: 'op1',
    clienteId: 'c1',
    titulo: 'Rótulo luminoso',
    valorEstimado: 15000,
    etapa: 'propuesta',
    responsableUsuarioId: null,
    cotizacionId: null,
    canalVentaId: null,
    version: 0,
    createdAt: '',
    updatedAt: '',
    ...parcial,
  };
}

/** Cotizacion de prueba (solo los campos que consume el resumen comercial). */
function cotizacion(parcial: Partial<Cotizacion> = {}): Cotizacion {
  return {
    id: 'q1',
    clienteId: 'c1',
    oportunidadId: null,
    estado: 'borrador',
    subtotal: 0,
    total: 5000,
    partidas: [],
    canalVentaId: null,
    folio: 'COT-001',
    fechaEmision: '2025-02-01',
    validoHasta: null,
    condiciones: null,
    notas: null,
    moneda: 'MXN',
    enviadaEn: null,
    ...parcial,
  } as Cotizacion;
}

/** Pagina comercial generica a partir de un contenido. */
function paginaComercial<T>(content: T[]): PaginaResponse<T> {
  return { content, totalElements: content.length, totalPages: 1, size: 100, page: 0 };
}

describe('EmpresaHome (gating por modulo en Inicio)', () => {
  function crear(
    modulos: readonly string[],
    miEmpresa: Observable<unknown> = of({
      brandingNombreVisible: 'ACME Rotulos',
      nombre: 'ACME S.A. de C.V.',
    }),
    nombreMostrado: string | null = 'admin@demofactura.com',
  ) {
    const brandingSpy = {
      consultar: vi.fn().mockReturnValue(of({ nombreVisible: 'ACME Rotulos', logo: null })),
    };
    const miEmpresaSpy = {
      consultarMiEmpresa: vi.fn().mockReturnValue(miEmpresa),
    };
    const estrategiaSpy = {
      consultarEsencia: vi.fn().mockReturnValue(
        of({ id: '1', mision: null, vision: null, valores: null, version: 0, createdAt: '', updatedAt: '' }),
      ),
      listarObjetivos: vi.fn().mockReturnValue(of(paginaVacia())),
    };
    const tableroSpy = {
      consultar: vi.fn().mockReturnValue(of({ generadoEn: '', desde: null, hasta: null, clienteId: null, areas: [] })),
    };
    const oportunidadesSpy = {
      listar: vi.fn().mockReturnValue(
        of(paginaComercial([oportunidad({ etapa: 'propuesta', valorEstimado: 15000 }), oportunidad({ id: 'op2', etapa: 'ganado', valorEstimado: 9000 })])),
      ),
    };
    const cotizacionesSpy = {
      listar: vi.fn().mockReturnValue(of(paginaComercial([cotizacion()]))),
    };

    TestBed.configureTestingModule({
      imports: [EmpresaHome],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authFake(modulos, nombreMostrado) },
        { provide: BrandingService, useValue: brandingSpy },
        { provide: MiEmpresaService, useValue: miEmpresaSpy },
        { provide: EstrategiaVistasService, useValue: estrategiaSpy },
        { provide: TableroService, useValue: tableroSpy },
        { provide: OportunidadesService, useValue: oportunidadesSpy },
        { provide: CotizacionesService, useValue: cotizacionesSpy },
      ],
    });

    const fixture = TestBed.createComponent(EmpresaHome);
    fixture.detectChanges();
    const componente = fixture.componentInstance;
    return { fixture, componente, estrategiaSpy, tableroSpy, miEmpresaSpy, oportunidadesSpy, cotizacionesSpy };
  }

  it('con modulos=[estrategia] carga la estrategia y NO consulta el Tablero', () => {
    const { componente, estrategiaSpy, tableroSpy } = crear(['estrategia']);

    expect(estrategiaSpy.consultarEsencia).toHaveBeenCalledTimes(1);
    expect(estrategiaSpy.listarObjetivos).toHaveBeenCalledTimes(1);
    // Modulo 'reportes-bi' no contratado: no se consulta el Tablero.
    expect(tableroSpy.consultar).not.toHaveBeenCalled();

    expect(componente['mostrarEstrategia']()).toBe(true);
    expect(componente['mostrarTablero']()).toBe(false);
  });

  it('con modulos=[] solo muestra bienvenida/branding y no consulta estrategia ni tablero', () => {
    const { componente, estrategiaSpy, tableroSpy } = crear([]);

    expect(estrategiaSpy.consultarEsencia).not.toHaveBeenCalled();
    expect(estrategiaSpy.listarObjetivos).not.toHaveBeenCalled();
    expect(tableroSpy.consultar).not.toHaveBeenCalled();

    expect(componente['mostrarEstrategia']()).toBe(false);
    expect(componente['mostrarTablero']()).toBe(false);
  });

  it('con modulos=[estrategia, reportes-bi] carga ambos bloques', () => {
    const { componente, estrategiaSpy, tableroSpy } = crear(['estrategia', 'reportes-bi']);

    expect(estrategiaSpy.consultarEsencia).toHaveBeenCalledTimes(1);
    expect(tableroSpy.consultar).toHaveBeenCalledTimes(1);
    expect(componente['mostrarEstrategia']()).toBe(true);
    expect(componente['mostrarTablero']()).toBe(true);
  });

  // ---------------------------------------------------------------------------
  // Resumen comercial (Req 6.3): gating por Modulo 'comercial' + degradacion.
  // ---------------------------------------------------------------------------
  it('con modulo comercial contratado renderiza el resumen y COMPUTA desde las listas', () => {
    const { fixture, componente, oportunidadesSpy, cotizacionesSpy } = crear(['comercial']);

    // Se consultan las listas (una vez cada una) para calcular los indicadores.
    expect(oportunidadesSpy.listar).toHaveBeenCalledTimes(1);
    expect(cotizacionesSpy.listar).toHaveBeenCalledTimes(1);

    expect(componente['mostrarComercial']()).toBe(true);
    // Solo la oportunidad 'propuesta' cuenta como abierta; la 'ganado' se excluye.
    expect(componente['resumenComercial']().datos?.oportunidadesAbiertas).toBe(1);
    expect(componente['resumenComercial']().datos?.valorPipeline).toBe(15000);
    expect(componente['resumenComercial']().datos?.cotizaciones).toBe(1);

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Resumen comercial');
    expect(texto).toContain('Valor en pipeline');
  });

  it('SIN modulo comercial oculta el bloque y NO consulta oportunidades ni cotizaciones', () => {
    const { fixture, componente, oportunidadesSpy, cotizacionesSpy } = crear(['estrategia']);

    // Degradacion: sin el Modulo no se llama a ningun endpoint comercial.
    expect(oportunidadesSpy.listar).not.toHaveBeenCalled();
    expect(cotizacionesSpy.listar).not.toHaveBeenCalled();

    expect(componente['mostrarComercial']()).toBe(false);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).not.toContain('Resumen comercial');
  });

  it('con modulos=[] tampoco consulta los endpoints comerciales', () => {
    const { componente, oportunidadesSpy, cotizacionesSpy } = crear([]);
    expect(oportunidadesSpy.listar).not.toHaveBeenCalled();
    expect(cotizacionesSpy.listar).not.toHaveBeenCalled();
    expect(componente['mostrarComercial']()).toBe(false);
  });

  // ---------------------------------------------------------------------------
  // Encabezado (nombre de la Empresa) y saludo (identificador legible): nunca la
  // marca de terceros "Dess-TI" ni el UUID tecnico (`sub`).
  // ---------------------------------------------------------------------------
  it('el saludo usa el identificador legible, no el UUID (sub)', () => {
    const { componente } = crear([]);
    // nombreUsuario proviene de nombreMostrado (claim legible), nunca del sub.
    expect(componente['nombreUsuario']()).toBe('admin@demofactura.com');
    expect(componente['nombreUsuario']()).not.toBe('11111111-2222-3333-4444-555555555555');
    // El saludo muestra el nombre legible.
    expect(componente['saludo']()).toContain('admin@demofactura.com');
  });

  it('el saludo degrada a un texto neutro cuando nombreMostrado es un UUID (token antiguo)', () => {
    const uuid = '11111111-2222-3333-4444-555555555555';
    const { componente } = crear([], undefined, uuid);
    // Nunca imprime el UUID; usa un saludo neutro.
    expect(componente['saludo']()).toBe('Hola, bienvenido.');
    expect(componente['saludo']()).not.toContain(uuid);
  });

  it('el saludo degrada a un texto neutro cuando no hay nombre (sin sesion)', () => {
    const { componente } = crear([], undefined, null);
    expect(componente['saludo']()).toBe('Hola, bienvenido.');
  });

  it('el encabezado muestra el nombre de la Empresa (brandingNombreVisible), nunca "Dess-TI"', () => {
    const { componente, miEmpresaSpy } = crear([]);
    expect(miEmpresaSpy.consultarMiEmpresa).toHaveBeenCalledTimes(1);
    expect(componente['nombreEmpresa']()).toBe('ACME Rotulos');
    expect(componente['nombreEmpresa']()).not.toBe('Dess-TI');
  });

  it('usa la razon social (nombre) cuando no hay brandingNombreVisible', () => {
    const { componente } = crear([], of({ brandingNombreVisible: null, nombre: 'ACME S.A. de C.V.' }));
    expect(componente['nombreEmpresa']()).toBe('ACME S.A. de C.V.');
  });

  it('degrada a un nombre neutro cuando falla la consulta de mi-empresa (nunca "Dess-TI" ni UUID)', () => {
    const { componente } = crear([], throwError(() => new Error('fallo de red')));
    expect(componente['nombreEmpresa']()).toBe('Mi empresa');
    expect(componente['nombreEmpresa']()).not.toBe('Dess-TI');
  });
});
