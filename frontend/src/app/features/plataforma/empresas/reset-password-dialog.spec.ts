// =============================================================================
// Pruebas del dialogo ResetPasswordDialog (super_admin) (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Modo "generar" (por defecto): envia cuerpo vacio y, cuando el servidor
//     devuelve una contrasena temporal, la muestra con el aviso de copiado.
//   - Modo "explicita": envia { password } y, sin passwordTemporal, notifica el
//     exito y cierra el dialogo con `true`.
//   - Mapeo de errores 404 (admin no encontrado) y 422 (contrasena invalida).
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { ResetPasswordDialog } from './reset-password-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** MatDialogRef de prueba que registra si se cerro y con que resultado. */
class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

/** Espia del servicio de notificaciones. */
class ToastSpy {
  exitos: string[] = [];
  exito(m: string): void {
    this.exitos.push(m);
  }
  error(): void {}
  info(): void {}
}

/** Forma minima del componente accedida por las pruebas. */
interface ResetTest {
  formulario: { patchValue(v: Record<string, unknown>): void };
  cambiarModo(modo: 'generar' | 'explicita'): void;
  confirmar(): void;
  error(): string | null;
  hayTemporal(): boolean;
  modoExplicito(): boolean;
}

describe('ResetPasswordDialog', () => {
  let fixture: ComponentFixture<ResetPasswordDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;
  let toast: ToastSpy;

  const URL = '/api/v1/empresas/e1/admin/reset-password';

  beforeEach(async () => {
    dialogRef = new DialogRefStub();
    toast = new ToastSpy();
    await TestBed.configureTestingModule({
      imports: [ResetPasswordDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: NotificacionesService, useValue: toast },
        { provide: MAT_DIALOG_DATA, useValue: { empresaId: 'e1', empresaNombre: 'Acme' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ResetPasswordDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function componenteDe(): ResetTest {
    return fixture.componentInstance as unknown as ResetTest;
  }

  it('modo generar envia cuerpo vacio y muestra la contrasena temporal', () => {
    const c = componenteDe();
    c.confirmar();
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ usuarioId: 'u1', identificador: 'admin@acme.test', passwordTemporal: 'Temp0ral!' });
    fixture.detectChanges();

    expect(c.hayTemporal()).toBe(true);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Temp0ral!');
    expect(texto).toContain('no se volverá a mostrar');
    // No se cierra: la contrasena debe verse antes de continuar.
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('al elegir "Establecer contrasena" muestra el campo de contrasena', () => {
    const c = componenteDe();
    expect(c.modoExplicito()).toBe(false);
    c.cambiarModo('explicita');
    fixture.detectChanges();

    expect(c.modoExplicito()).toBe(true);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Nueva contraseña');
    expect(texto).not.toContain('Se generará una contraseña temporal');

    // Al volver a "generar" el campo se oculta de nuevo.
    c.cambiarModo('generar');
    fixture.detectChanges();
    expect(c.modoExplicito()).toBe(false);
  });

  it('modo explicita envia la contrasena, notifica el exito y cierra con true', () => {
    const c = componenteDe();
    c.cambiarModo('explicita');
    c.formulario.patchValue({
      modo: 'explicita',
      password: 'ClaveSegura1',
      confirmarPassword: 'ClaveSegura1',
    });
    c.confirmar();
    const req = http.expectOne(URL);
    expect(req.request.body).toEqual({ password: 'ClaveSegura1' });
    req.flush({ usuarioId: 'u1', identificador: 'admin@acme.test', passwordTemporal: null });

    expect(toast.exitos.some((m) => m.includes('admin@acme.test'))).toBe(true);
    expect(dialogRef.cerradoCon).toBe(true);
  });

  it('mapea el error 404 (admin no encontrado) a un mensaje en espanol', () => {
    const c = componenteDe();
    c.confirmar();
    http.expectOne(URL).flush({}, { status: 404, statusText: 'Not Found' });
    expect(c.error()).toContain('No se encontró un administrador');
  });

  it('mapea el error 422 (contrasena invalida) a un mensaje en espanol', () => {
    const c = componenteDe();
    c.cambiarModo('explicita');
    c.formulario.patchValue({
      modo: 'explicita',
      password: 'ClaveSegura1',
      confirmarPassword: 'ClaveSegura1',
    });
    c.confirmar();
    http.expectOne(URL).flush({}, { status: 422, statusText: 'Unprocessable Entity' });
    expect(c.error()).toContain('no es válida');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
