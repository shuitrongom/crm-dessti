// =============================================================================
// Pruebas del dialogo GiroDialog: alta de giro y panel informativo de
// completitud tras crear (Req 9, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Tras crear un giro el dialogo NO se cierra en silencio: muestra un segundo
//     estado (panel informativo) que comunica que el giro nace "Base".
//   - El dialogo se cierra (devolviendo el giro creado) solo al pulsar "Entendido".
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { GiroDialog } from './giro-dialog';
import { Giro } from '../models/plataforma.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** MatDialogRef de prueba que registra si se cerro y con que resultado. */
class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

/** Forma minima del componente accedida por las pruebas. */
interface GiroDialogTest {
  formulario: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
}

function giroCreado(parcial: Partial<Giro> = {}): Giro {
  return {
    id: 'g9',
    clave: 'carpinteria',
    nombreVisible: 'Carpinteria y ebanisteria',
    descripcion: null,
    activo: true,
    version: 0,
    tieneReglasNegocio: false,
    modulosEspecificos: 0,
    ...parcial,
  };
}

describe('GiroDialog', () => {
  let fixture: ComponentFixture<GiroDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  beforeEach(async () => {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [GiroDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(GiroDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function componenteDe(): GiroDialogTest {
    return fixture.componentInstance as unknown as GiroDialogTest;
  }

  /** Envia el alta y resuelve el POST devolviendo el giro creado. */
  function crearGiro(giro: Giro): void {
    const c = componenteDe();
    c.formulario.patchValue({ clave: giro.clave, nombreVisible: giro.nombreVisible });
    c.guardar();
    http.expectOne('/api/v1/plataforma/giros').flush(giro);
    fixture.detectChanges();
  }

  it('tras crear muestra el panel informativo de completitud sin cerrar el dialogo', () => {
    crearGiro(giroCreado({ nombreVisible: 'Carpinteria y ebanisteria' }));

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('se creó correctamente');
    expect(texto).toContain('Carpinteria y ebanisteria');
    expect(texto).toContain('módulos base');
    expect(texto).toContain('aún NO están programadas');
    // El dialogo NO se cerro todavia: sigue en su segundo estado.
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('cierra el dialogo devolviendo el giro creado al pulsar "Entendido"', () => {
    const giro = giroCreado();
    crearGiro(giro);

    const boton = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find(
      (b) => b.textContent?.includes('Entendido'),
    ) as HTMLButtonElement;
    boton.click();

    expect(dialogRef.cerradoCon).toEqual(giro);
  });

  it('no tiene violaciones de accesibilidad tras crear (WCAG 2.1 A/AA)', async () => {
    crearGiro(giroCreado());
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
