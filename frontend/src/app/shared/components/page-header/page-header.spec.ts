// =============================================================================
// Pruebas de PageHeader: renderizado + accesibilidad (Req 52, 53, 57)
// -----------------------------------------------------------------------------
// Verifican el titulo como h1 (landmark de la vista), el subtitulo opcional, la
// zona de acciones proyectada y la ausencia de violaciones WCAG 2.1 A/AA.
// =============================================================================

import { Component, signal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';

import { PageHeader } from './page-header';
import { esperarSinViolaciones } from '../../../../testing/axe';

@Component({
  imports: [PageHeader],
  template: `
    <app-page-header [titulo]="titulo()" [subtitulo]="subtitulo()">
      <button acciones type="button">Nueva</button>
    </app-page-header>
  `,
})
class HostHeader {
  readonly titulo = signal('Cotizaciones');
  readonly subtitulo = signal<string | undefined>('Gestiona las cotizaciones de la empresa');
}

describe('PageHeader', () => {
  let fixture: ComponentFixture<HostHeader>;
  let host: HostHeader;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostHeader] }).compileComponents();
    fixture = TestBed.createComponent(HostHeader);
    host = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('renderiza el titulo como h1', () => {
    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('Cotizaciones');
  });

  it('renderiza el subtitulo cuando se proporciona', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Gestiona las cotizaciones de la empresa',
    );
  });

  it('omite el subtitulo cuando es indefinido', async () => {
    host.subtitulo.set(undefined);
    await fixture.whenStable();
    const subtitulo = (fixture.nativeElement as HTMLElement).querySelector('.page-header__subtitulo');
    expect(subtitulo).toBeNull();
  });

  it('proyecta las acciones', () => {
    const boton = (fixture.nativeElement as HTMLElement).querySelector('.page-header__acciones button');
    expect(boton?.textContent).toContain('Nueva');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await esperarSinViolaciones(fixture);
  });
});
