// =============================================================================
// Pruebas de ProgressBadge: avance/estado + accesibilidad (Req 58, 57)
// -----------------------------------------------------------------------------
// Verifican el acotado del avance a [0,100], la etiqueta legible del estado
// (el color nunca es el unico portador de significado, Req 57) y la ausencia de
// violaciones WCAG 2.1 A/AA.
// =============================================================================

import { Component, signal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { ProgressBadge, type EstadoDerivado } from './progress-badge';
import { esperarSinViolaciones } from '../../../../testing/axe';

@Component({
  imports: [ProgressBadge],
  template: `<app-progress-badge [avance]="avance()" [estado]="estado()" />`,
})
class HostBadge {
  readonly avance = signal(50);
  readonly estado = signal<EstadoDerivado>('en_curso');
}

describe('ProgressBadge', () => {
  let fixture: ComponentFixture<HostBadge>;
  let host: HostBadge;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostBadge, NoopAnimationsModule],
    }).compileComponents();
    fixture = TestBed.createComponent(HostBadge);
    host = fixture.componentInstance;
    await fixture.whenStable();
  });

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('muestra el avance como porcentaje', () => {
    expect(texto()).toContain('50%');
  });

  it('acota el avance por encima de 100', async () => {
    host.avance.set(150);
    await fixture.whenStable();
    expect(texto()).toContain('100%');
  });

  it('acota el avance por debajo de 0', async () => {
    host.avance.set(-20);
    await fixture.whenStable();
    expect(texto()).toContain('0%');
  });

  it('muestra la etiqueta legible del estado (texto ademas de color)', async () => {
    host.estado.set('en_riesgo');
    await fixture.whenStable();
    expect(texto()).toContain('En riesgo');
  });

  it('provee una barra de progreso con etiqueta accesible', () => {
    const barra = (fixture.nativeElement as HTMLElement).querySelector('mat-progress-bar');
    expect(barra?.getAttribute('aria-label')).toContain('Avance 50 por ciento');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await esperarSinViolaciones(fixture);
  });
});
