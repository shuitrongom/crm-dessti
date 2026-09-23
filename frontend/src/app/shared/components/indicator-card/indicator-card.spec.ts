// =============================================================================
// Pruebas de IndicatorCard: valor/tendencia + accesibilidad (Req 22, 48, 57)
// -----------------------------------------------------------------------------
// Verifican el formato del valor, la comunicacion de la tendencia con texto e
// icono (no solo color, Req 57), la omision del comparativo cuando no existe y
// la ausencia de violaciones WCAG 2.1 A/AA.
// =============================================================================

import { Component, signal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';

import { IndicatorCard, type Indicador } from './indicator-card';
import { esperarSinViolaciones } from '../../../../testing/axe';

@Component({
  imports: [IndicatorCard],
  template: `<app-indicator-card [indicador]="indicador()" />`,
})
class HostCard {
  readonly indicador = signal<Indicador>({
    clave: 'ventas',
    etiqueta: 'Ventas del mes',
    valor: 12500.5,
    unidad: 'MXN',
    comparativo: 10000,
    variacion: 25,
  });
}

describe('IndicatorCard', () => {
  let fixture: ComponentFixture<HostCard>;
  let host: HostCard;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostCard] }).compileComponents();
    fixture = TestBed.createComponent(HostCard);
    host = fixture.componentInstance;
    await fixture.whenStable();
  });

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('muestra la etiqueta y la unidad', () => {
    expect(texto()).toContain('Ventas del mes');
    expect(texto()).toContain('MXN');
  });

  it('comunica la tendencia al alza con texto (no solo color)', () => {
    expect(texto()).toContain('al alza');
  });

  it('comunica la tendencia a la baja cuando la variacion es negativa', async () => {
    host.indicador.set({
      clave: 'gastos',
      etiqueta: 'Gastos',
      valor: 800,
      unidad: 'MXN',
      comparativo: 1000,
      variacion: -20,
    });
    await fixture.whenStable();
    expect(texto()).toContain('a la baja');
  });

  it('omite el comparativo cuando no hay variacion', async () => {
    host.indicador.set({
      clave: 'nuevos',
      etiqueta: 'Clientes nuevos',
      valor: 5,
      unidad: '',
      comparativo: null,
      variacion: null,
    });
    await fixture.whenStable();
    const comparativo = (fixture.nativeElement as HTMLElement).querySelector(
      '.indicator-card__comparativo',
    );
    expect(comparativo).toBeNull();
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await esperarSinViolaciones(fixture);
  });
});
