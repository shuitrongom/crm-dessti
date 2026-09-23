// =============================================================================
// Pruebas del tablero de Estrategia (planeacion estrategica / OKR) (Req 58)
// -----------------------------------------------------------------------------
// Verifican, sin red ni zona real:
//   - La esencia capturada (mision/vision/valores) se MUESTRA en pantalla.
//   - Los objetivos se renderizan como tarjetas con avance y estado.
//   - El resumen de KPIs (total, avance promedio, conteo por estado) se computa.
//   - Actualizar el valor de un resultado clave llama al servicio y refresca la
//     tarjeta con el objetivo devuelto.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { of } from 'rxjs';

import { EstrategiaVistas } from './estrategia';
import { EstrategiaVistasService } from '../services/estrategia-vistas.service';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { OportunidadesService } from '../../comercial/services/oportunidades.service';
import { CotizacionesService } from '../../comercial/services/cotizaciones.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { EsenciaEmpresa, ObjetivoEstrategico } from '../models/estrategia.models';
import { Cotizacion, Oportunidad } from '../../comercial/models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

function esencia(): EsenciaEmpresa {
  return {
    id: 'e1',
    mision: 'Nuestra misión capturada',
    vision: 'Nuestra visión capturada',
    valores: 'Integridad y excelencia',
    version: 1,
    createdAt: '',
    updatedAt: '',
  };
}

function objetivo(parcial: Partial<ObjetivoEstrategico> = {}): ObjetivoEstrategico {
  return {
    id: 'o1',
    nombre: 'Crecer en el mercado',
    responsable: 'Dirección Comercial',
    periodoInicio: '2025-01-01',
    periodoFin: '2025-12-31',
    meta: '25% de participación',
    avance: 40,
    estadoDerivado: 'en_curso',
    resultadosClave: [
      { id: 'rc1', descripcion: 'Contratos nuevos', valorObjetivo: 30, valorActual: 12, peso: 1 },
    ],
    version: 0,
    createdAt: '',
    updatedAt: '',
    ...parcial,
  };
}

function pagina(content: ObjetivoEstrategico[]): PaginaResponse<ObjetivoEstrategico> {
  return { content, totalElements: content.length, totalPages: 1, size: 100, page: 0 };
}

/** Oportunidad de prueba (solo los campos que consume el indicador comercial). */
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

/** Cotizacion de prueba (solo los campos que consume el indicador comercial). */
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

/**
 * AuthService simulado: todos los permisos concedidos y los Modulos indicados
 * contratados. Se parametriza `modulos` para aislar el gating comercial.
 */
function authFake(modulos: readonly string[] = []): Partial<AuthService> {
  return {
    tienePermiso: () => true,
    tieneModulo: (clave: string) => modulos.includes(clave),
  } as Partial<AuthService>;
}

describe('EstrategiaVistas (tablero OKR)', () => {
  function crear(
    objetivos: ObjetivoEstrategico[] = [objetivo()],
    esenciaResp = esencia(),
    modulos: readonly string[] = [],
  ) {
    const serviceSpy = {
      consultarEsencia: vi.fn().mockReturnValue(of(esenciaResp)),
      listarObjetivos: vi.fn().mockReturnValue(of(pagina(objetivos))),
      guardarEsencia: vi.fn().mockReturnValue(of(esenciaResp)),
      crearObjetivo: vi.fn(),
      agregarResultadoClave: vi.fn(),
      actualizarValorResultadoClave: vi.fn(),
    };
    const toastSpy = { exito: vi.fn(), error: vi.fn(), info: vi.fn() };
    const oportunidadesSpy = {
      listar: vi.fn().mockReturnValue(
        of(paginaComercial([oportunidad({ etapa: 'propuesta', valorEstimado: 15000 }), oportunidad({ id: 'op2', etapa: 'ganado', valorEstimado: 9000 })])),
      ),
    };
    const cotizacionesSpy = {
      listar: vi.fn().mockReturnValue(of(paginaComercial([cotizacion()]))),
    };

    TestBed.configureTestingModule({
      imports: [EstrategiaVistas],
      providers: [
        { provide: EstrategiaVistasService, useValue: serviceSpy },
        { provide: AuthService, useValue: authFake(modulos) },
        { provide: NotificacionesService, useValue: toastSpy },
        { provide: OportunidadesService, useValue: oportunidadesSpy },
        { provide: CotizacionesService, useValue: cotizacionesSpy },
      ],
    });

    const fixture = TestBed.createComponent(EstrategiaVistas);
    fixture.detectChanges();
    return {
      fixture,
      componente: fixture.componentInstance,
      serviceSpy,
      toastSpy,
      oportunidadesSpy,
      cotizacionesSpy,
    };
  }

  function textoDe(fixture: ComponentFixture<EstrategiaVistas>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('muestra la esencia capturada (misión, visión y valores)', () => {
    const { fixture } = crear();
    const texto = textoDe(fixture);
    expect(texto).toContain('Nuestra misión capturada');
    expect(texto).toContain('Nuestra visión capturada');
    expect(texto).toContain('Integridad y excelencia');
  });

  it('renderiza los objetivos como tarjetas con nombre, responsable y avance', () => {
    const { fixture } = crear();
    const texto = textoDe(fixture);
    expect(texto).toContain('Crecer en el mercado');
    expect(texto).toContain('Dirección Comercial');
    // El progress-badge muestra el avance y su estado.
    expect(texto).toContain('40%');
    expect(texto).toContain('En curso');
  });

  it('computa el resumen de KPIs (total, avance promedio y conteo por estado)', () => {
    const { componente } = crear([
      objetivo({ id: 'a', avance: 20, estadoDerivado: 'en_riesgo' }),
      objetivo({ id: 'b', avance: 60, estadoDerivado: 'en_curso' }),
      objetivo({ id: 'c', avance: 100, estadoDerivado: 'cumplido' }),
    ]);
    const kpi = componente['resumen']();
    expect(kpi.total).toBe(3);
    expect(kpi.avancePromedio).toBe(60); // (20+60+100)/3 = 60
    expect(kpi.enRiesgo).toBe(1);
    expect(kpi.enCurso).toBe(1);
    expect(kpi.cumplido).toBe(1);
  });

  it('muestra un estado vacío con guía OKR cuando no hay objetivos', () => {
    const { fixture, componente } = crear([]);
    expect(componente['resumen']().total).toBe(0);
    expect(textoDe(fixture)).toContain('Aún no hay objetivos estratégicos');
  });

  it('actualizar el valor de un resultado clave llama al servicio y refresca la tarjeta', () => {
    const { componente, serviceSpy } = crear();
    const actualizado = objetivo({ avance: 55, estadoDerivado: 'en_curso' });
    serviceSpy.actualizarValorResultadoClave.mockReturnValue(of(actualizado));

    componente['actualizarValor']('rc1', '18');

    expect(serviceSpy.actualizarValorResultadoClave).toHaveBeenCalledWith('rc1', 18);
    expect(componente['objetivos']().datos?.[0].avance).toBe(55);
  });

  it('guardar la esencia envía null en los campos vacíos y refresca lo mostrado', () => {
    const { componente, serviceSpy } = crear();
    componente['editarEsencia']();
    componente['formEsencia'].patchValue({ mision: '  ', vision: 'V nueva', valores: '' });
    componente['guardarEsencia']();

    expect(serviceSpy.guardarEsencia).toHaveBeenCalledWith({
      mision: null,
      vision: 'V nueva',
      valores: null,
    });
    expect(componente['editandoEsencia']()).toBe(false);
  });

  // ---------------------------------------------------------------------------
  // Indicadores comerciales (Req 6.1): solo con Modulo 'comercial'; degradacion.
  // ---------------------------------------------------------------------------
  it('con modulo comercial muestra los indicadores y los COMPUTA desde las listas', () => {
    const { fixture, componente, oportunidadesSpy, cotizacionesSpy } = crear(
      [objetivo()],
      esencia(),
      ['comercial'],
    );

    expect(oportunidadesSpy.listar).toHaveBeenCalledTimes(1);
    expect(cotizacionesSpy.listar).toHaveBeenCalledTimes(1);
    expect(componente['mostrarComercial']()).toBe(true);
    // Solo la oportunidad 'propuesta' cuenta como abierta; la 'ganado' se excluye.
    expect(componente['numOportunidadesAbiertas']()).toBe(1);
    expect(componente['valorPipeline']()).toBe(15000);
    expect(componente['numCotizaciones']()).toBe(1);

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Indicadores comerciales');
    expect(texto).toContain('Valor en pipeline');
  });

  it('SIN modulo comercial oculta la seccion y NO consulta los endpoints comerciales', () => {
    const { fixture, componente, oportunidadesSpy, cotizacionesSpy } = crear();

    expect(oportunidadesSpy.listar).not.toHaveBeenCalled();
    expect(cotizacionesSpy.listar).not.toHaveBeenCalled();
    expect(componente['mostrarComercial']()).toBe(false);

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).not.toContain('Indicadores comerciales');
  });

  it('no expone UUIDs en los indicadores comerciales', () => {
    const { fixture } = crear([objetivo()], esencia(), ['comercial']);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i);
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    const { fixture } = crear([objetivo()], esencia(), ['comercial']);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
