// =============================================================================
// Pruebas de la vista PlataformaOffboarding: periodo de gracia y compuerta de
// eliminacion (Req 69, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless):
//   - "Eliminar definitivamente" DESHABILITADO cuando la Empresa no esta cancelada.
//   - DESHABILITADO cuando esta cancelada pero el periodo de gracia NO ha vencido
//     (finPeriodoGracia en el futuro) y se muestran los dias restantes.
//   - HABILITADO cuando el periodo de gracia ya vencio (finPeriodoGracia en el
//     pasado) y el panel indica que ya se puede eliminar.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// Se usan fechas fijas (el reloj del sistema se congela) para que los dias
// restantes sean deterministas.
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { PlataformaOffboarding } from './offboarding';
import { AuthService } from '../../../core/auth/auth.service';
import { Empresa } from '../models/plataforma.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** AuthService de prueba: super_admin con permisos de offboarding. */
class AuthServiceStub {
  tienePermiso(recurso: string, _operacion: string): boolean {
    return recurso === 'offboarding';
  }
}

/** Fabrica de Empresa con valores por defecto sobreescribibles. */
function empresa(parcial: Partial<Empresa> = {}): Empresa {
  return {
    id: 'e1',
    nombre: 'Acme',
    rfc: 'ABCD901231XYZ',
    giroId: 'g1',
    estado: 'activa',
    brandingNombreVisible: null,
    brandingLogo: null,
    nombreComercial: null,
    emailContacto: null,
    telefono: null,
    sitioWeb: null,
    direccion: null,
    notas: null,
    fechaCancelacion: null,
    finPeriodoGracia: null,
    planVigente: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...parcial,
  };
}

/** Superficie protegida que las pruebas necesitan tocar. */
interface OffboardingProbe {
  seleccionada: { set(v: Empresa | null): void };
  diasRestantesGracia(): number | null;
  graciaVencida(): boolean;
  puedeEliminar(): boolean;
}

/** Momento "ahora" fijo para todas las pruebas: 15 de marzo de 2026, 12:00 UTC. */
const AHORA = new Date('2026-03-15T12:00:00Z');

describe('PlataformaOffboarding', () => {
  let fixture: ComponentFixture<PlataformaOffboarding>;

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(AHORA);
    await TestBed.configureTestingModule({
      imports: [PlataformaOffboarding, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PlataformaOffboarding);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  /** Fija la Empresa seleccionada mediante el signal protegido y re-renderiza. */
  function seleccionar(e: Empresa): void {
    (fixture.componentInstance as unknown as OffboardingProbe).seleccionada.set(e);
    fixture.detectChanges();
  }

  /** Localiza el boton "Eliminar definitivamente". */
  function botonEliminar(): HTMLButtonElement {
    const botones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ) as HTMLButtonElement[];
    const boton = botones.find((b) => (b.textContent ?? '').includes('Eliminar definitivamente'));
    expect(boton).toBeTruthy();
    return boton!;
  }

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('deshabilita "Eliminar definitivamente" cuando la empresa NO esta cancelada', () => {
    seleccionar(empresa({ estado: 'activa' }));
    const probe = fixture.componentInstance as unknown as OffboardingProbe;
    expect(probe.puedeEliminar()).toBe(false);
    expect(botonEliminar().disabled).toBe(true);
    // No se muestra el panel de gracia para una empresa activa.
    expect(texto()).not.toContain('Fin del periodo de gracia');
  });

  it('deshabilita "Eliminar" cuando esta cancelada pero la gracia NO ha vencido', () => {
    // finPeriodoGracia 5 dias en el futuro respecto de AHORA.
    const empresaCancelada = empresa({
      estado: 'cancelada',
      fechaCancelacion: '2026-03-10T12:00:00Z',
      finPeriodoGracia: '2026-03-20T12:00:00Z',
    });
    seleccionar(empresaCancelada);
    const probe = fixture.componentInstance as unknown as OffboardingProbe;

    expect(probe.diasRestantesGracia()).toBe(5);
    expect(probe.graciaVencida()).toBe(false);
    expect(probe.puedeEliminar()).toBe(false);
    expect(botonEliminar().disabled).toBe(true);
    // El panel indica los dias restantes.
    expect(texto()).toContain('Faltan 5');
    expect(texto()).toContain('Fin del periodo de gracia');
  });

  it('habilita "Eliminar" cuando el periodo de gracia ya vencio', () => {
    // finPeriodoGracia en el pasado respecto de AHORA.
    const empresaVencida = empresa({
      estado: 'cancelada',
      fechaCancelacion: '2026-02-01T12:00:00Z',
      finPeriodoGracia: '2026-03-03T12:00:00Z',
    });
    seleccionar(empresaVencida);
    const probe = fixture.componentInstance as unknown as OffboardingProbe;

    expect(probe.diasRestantesGracia()).toBeLessThanOrEqual(0);
    expect(probe.graciaVencida()).toBe(true);
    expect(probe.puedeEliminar()).toBe(true);
    expect(botonEliminar().disabled).toBe(false);
    expect(texto()).toContain('Ya se puede eliminar definitivamente');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    seleccionar(
      empresa({
        estado: 'cancelada',
        fechaCancelacion: '2026-03-10T12:00:00Z',
        finPeriodoGracia: '2026-03-20T12:00:00Z',
      }),
    );
    // axe usa temporizadores internos; se ejecuta con los reales.
    vi.useRealTimers();
    await esperarSinViolaciones(fixture);
  });
});
