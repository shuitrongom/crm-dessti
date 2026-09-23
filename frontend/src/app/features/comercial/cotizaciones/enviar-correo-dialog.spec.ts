// =============================================================================
// Pruebas del dialogo EnviarCorreoDialog (Req 6)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona:
//   - Prellena el campo con el correo del cliente cuando existe.
//   - Al enviar con un correo valido, cierra el dialogo con ese correo.
//   - Con el campo vacio no cierra (validacion required) y con correo invalido
//     tampoco.
//   - Cancelar cierra el dialogo con null.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { EnviarCorreoDialog } from './enviar-correo-dialog';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** MatDialogRef de prueba que registra el resultado del cierre. */
class DialogRefStub {
  cerradoCon: string | null | undefined = undefined;
  close(resultado?: string | null): void {
    this.cerradoCon = resultado ?? null;
  }
}

/** Superficie de prueba del dialogo. */
interface DialogTest {
  form: { controls: { email: { setValue(v: string): void } } };
  enviar(): void;
  cancelar(): void;
}

describe('EnviarCorreoDialog', () => {
  let fixture: ComponentFixture<EnviarCorreoDialog>;
  let dialogRef: DialogRefStub;

  async function crear(correoCliente: string | null): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [EnviarCorreoDialog, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { folio: 'COT-2026-0001', correoCliente } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(EnviarCorreoDialog);
    fixture.detectChanges();
  }

  function componente(): DialogTest {
    return fixture.componentInstance as unknown as DialogTest;
  }

  it('prellena el correo del cliente y lo devuelve al enviar', async () => {
    await crear('contacto@acme.com');
    componente().enviar();
    expect(dialogRef.cerradoCon).toBe('contacto@acme.com');
  });

  it('permite corregir el correo antes de enviar', async () => {
    await crear('contacto@acme.com');
    componente().form.controls.email.setValue('otro@acme.com');
    componente().enviar();
    expect(dialogRef.cerradoCon).toBe('otro@acme.com');
  });

  it('no cierra si el correo esta vacio (required)', async () => {
    await crear(null);
    componente().enviar();
    expect(dialogRef.cerradoCon).toBeUndefined();
  });

  it('no cierra si el correo es invalido', async () => {
    await crear(null);
    componente().form.controls.email.setValue('no-es-correo');
    componente().enviar();
    expect(dialogRef.cerradoCon).toBeUndefined();
  });

  it('cancelar cierra el dialogo con null', async () => {
    await crear('contacto@acme.com');
    componente().cancelar();
    expect(dialogRef.cerradoCon).toBeNull();
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await crear('contacto@acme.com');
    await esperarSinViolaciones(fixture);
  });
});
