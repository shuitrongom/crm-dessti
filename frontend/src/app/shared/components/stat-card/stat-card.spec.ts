// =============================================================================
// Pruebas de StatCard: contenido, tono y accesibilidad (Req 52, 53, 57)
// -----------------------------------------------------------------------------
// Verifican que la tarjeta muestra etiqueta/valor/icono, que el tono aplica su
// clase modificadora, que expone una etiqueta accesible que combina etiqueta y
// valor, y que no presenta violaciones WCAG 2.1 A/AA.
// =============================================================================

import { Component, signal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';

import { StatCard, type TonoStatCard } from './stat-card';
import { esperarSinViolaciones } from '../../../../testing/axe';

@Component({
  imports: [StatCard],
  template: `<app-stat-card [etiqueta]="etiqueta()" [valor]="valor()" [icono]="icono()" [tono]="tono()" [ayuda]="ayuda()" />`,
})
class HostStat {
  readonly etiqueta = signal('Empresas activas');
  readonly valor = signal<string | number>(42);
  readonly icono = signal('domain');
  readonly tono = signal<TonoStatCard>('exito');
  readonly ayuda = signal<string | undefined>('En operacion normal');
}

describe('StatCard', () => {
  let fixture: ComponentFixture<HostStat>;
  let host: HostStat;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostStat] }).compileComponents();
    fixture = TestBed.createComponent(HostStat);
    host = fixture.componentInstance;
    await fixture.whenStable();
  });

  function raiz(): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector('.stat-card') as HTMLElement;
  }

  it('muestra la etiqueta, el valor y el icono', () => {
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Empresas activas');
    expect(texto).toContain('42');
    expect(texto).toContain('domain');
  });

  it('muestra el texto de ayuda cuando se proporciona', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('En operacion normal');
  });

  it('aplica la clase de tono', () => {
    expect(raiz().classList).toContain('stat-card--exito');
    host.tono.set('error');
    fixture.detectChanges();
    expect(raiz().classList).toContain('stat-card--error');
  });

  it('expone una etiqueta accesible que combina etiqueta y valor', () => {
    expect(raiz().getAttribute('role')).toBe('group');
    expect(raiz().getAttribute('aria-label')).toBe('Empresas activas: 42');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await esperarSinViolaciones(fixture);
  });
});
