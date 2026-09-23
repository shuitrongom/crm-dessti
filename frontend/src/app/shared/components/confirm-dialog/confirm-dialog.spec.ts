// =============================================================================
// Pruebas de ConfirmDialog / ConfirmDialogService (Req 54, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona:
//   - El componente renderiza titulo, mensaje y acciones, y resuelve el
//     MatDialogRef con true/false segun el boton pulsado.
//   - El servicio abre el dialogo y resuelve la Promesa<boolean> con la decision.
//   - La accion destructiva aplica la clase de resalte (Req 54.2).
//   - Accesibilidad WCAG 2.1 A/AA del contenido del dialogo (axe-core, jsdom).
// =============================================================================

import { ApplicationRef, Component, inject } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import {
  ConfirmDialog,
  ConfirmDialogService,
  type DatosConfirmacion,
} from './confirm-dialog';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** Doble de MatDialogRef que registra el resultado con el que se cierra. */
class MatDialogRefFalso {
  cerradoCon: boolean | undefined;
  close(resultado: boolean): void {
    this.cerradoCon = resultado;
  }
}

describe('ConfirmDialog (componente)', () => {
  let fixture: ComponentFixture<ConfirmDialog>;
  let dialogRef: MatDialogRefFalso;

  async function crear(datos: DatosConfirmacion): Promise<void> {
    dialogRef = new MatDialogRefFalso();
    await TestBed.configureTestingModule({
      imports: [ConfirmDialog, MatDialogModule, MatButtonModule],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: datos },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ConfirmDialog);
    await fixture.whenStable();
  }

  function botones(): HTMLButtonElement[] {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
  }

  it('renderiza titulo y mensaje', async () => {
    await crear({ titulo: 'Confirmar accion', mensaje: 'Deseas continuar?' });
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Confirmar accion');
    expect(texto).toContain('Deseas continuar?');
  });

  it('cierra con true al confirmar y con false al cancelar', async () => {
    await crear({ titulo: 'T', mensaje: 'M', textoConfirmar: 'Aceptar', textoCancelar: 'Volver' });
    const [cancelar, confirmar] = botones();
    confirmar.click();
    expect(dialogRef.cerradoCon).toBe(true);
    cancelar.click();
    expect(dialogRef.cerradoCon).toBe(false);
  });

  it('aplica la clase destructiva al boton de confirmacion cuando destructiva=true', async () => {
    await crear({ titulo: 'T', mensaje: 'M', destructiva: true });
    const destructivo = (fixture.nativeElement as HTMLElement).querySelector('.ds-boton-destructivo');
    expect(destructivo).toBeTruthy();
  });

  describe('accesibilidad (axe-core, WCAG 2.1 A/AA)', () => {
    it('no tiene violaciones en un dialogo estandar', async () => {
      await crear({ titulo: 'Confirmar', mensaje: 'Se aplicaran los cambios.' });
      await esperarSinViolaciones(fixture);
    });

    it('no tiene violaciones en un dialogo destructivo', async () => {
      await crear({
        titulo: 'Eliminar',
        mensaje: 'Esta accion no se puede deshacer.',
        destructiva: true,
        textoConfirmar: 'Eliminar',
      });
      await esperarSinViolaciones(fixture);
    });
  });
});

describe('ConfirmDialogService', () => {
  /** Host minimo para inyectar el servicio en un contexto de inyeccion Angular. */
  @Component({ template: '' })
  class HostVacio {
    readonly servicio = inject(ConfirmDialogService);
  }

  let host: HostVacio;
  let appRef: ApplicationRef;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostVacio, MatDialogModule, NoopAnimationsModule],
    }).compileComponents();
    host = TestBed.createComponent(HostVacio).componentInstance;
    appRef = TestBed.inject(ApplicationRef);
  });

  afterEach(() => {
    // Limpia cualquier overlay residual del contenedor de dialogos.
    document.querySelectorAll('.cdk-overlay-container').forEach((n) => n.remove());
  });

  /**
   * Espera a que el overlay renderice el boton con la etiqueta indicada,
   * detectando cambios entre reintentos, y lo devuelve.
   */
  async function esperarBotonOverlay(etiqueta: string): Promise<HTMLButtonElement> {
    for (let intento = 0; intento < 20; intento++) {
      appRef.tick();
      await Promise.resolve();
      const nodos = Array.from(
        document.querySelectorAll<HTMLButtonElement>('.cdk-overlay-container button'),
      );
      const encontrado = nodos.find((b) => (b.textContent ?? '').includes(etiqueta));
      if (encontrado) {
        return encontrado;
      }
    }
    throw new Error(`No se encontro el boton "${etiqueta}" en el overlay.`);
  }

  it('resuelve true cuando el Usuario confirma', async () => {
    const promesa = host.servicio.confirmar({
      titulo: 'Confirmar',
      mensaje: 'Deseas continuar?',
      textoConfirmar: 'Confirmar',
    });
    (await esperarBotonOverlay('Confirmar')).click();
    await expect(promesa).resolves.toBe(true);
  });

  it('resuelve false cuando el Usuario cancela', async () => {
    const promesa = host.servicio.confirmar({
      titulo: 'Confirmar',
      mensaje: 'Deseas continuar?',
      textoCancelar: 'Cancelar',
    });
    (await esperarBotonOverlay('Cancelar')).click();
    await expect(promesa).resolves.toBe(false);
  });
});
