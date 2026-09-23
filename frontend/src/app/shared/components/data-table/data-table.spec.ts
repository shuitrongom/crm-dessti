// =============================================================================
// Pruebas de DataTable: renderizado, paginacion y accesibilidad (Req 12, 52, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona:
//   - Renderizado de encabezados y celdas a partir de columnas/filas y plantillas.
//   - Emision de `cambioPagina` al cambiar de pagina en el paginador.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom; `color-contrast`
//     se valida en la capa e2e de Playwright).
// =============================================================================

import { Component, signal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from './data-table';
import { esperarSinViolaciones } from '../../../../testing/axe';

interface Cliente {
  nombre: string;
  rfc: string;
}

@Component({
  imports: [DataTable, CeldaTablaDirective],
  template: `
    <app-data-table
      [columnas]="columnas"
      [filas]="filas()"
      [total]="total()"
      [page]="page()"
      [size]="size()"
      (cambioPagina)="ultimoCambio = $event"
    >
      <ng-template appCelda="nombre" let-fila>{{ fila.nombre }}</ng-template>
      <ng-template appCelda="rfc" let-fila>{{ fila.rfc }}</ng-template>
    </app-data-table>
  `,
})
class HostTabla {
  readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Nombre' },
    { clave: 'rfc', encabezado: 'RFC', alineacion: 'fin', ocultarEnMovil: true },
  ];
  readonly filas = signal<Cliente[]>([
    { nombre: 'Anuncios del Norte', rfc: 'AAA010101AAA' },
    { nombre: 'Luminosos del Bajio', rfc: 'BBB020202BBB' },
  ]);
  readonly total = signal(40);
  readonly page = signal(0);
  readonly size = signal(20);
  ultimoCambio: CambioPagina | null = null;
}

describe('DataTable', () => {
  let fixture: ComponentFixture<HostTabla>;
  let host: HostTabla;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostTabla, NoopAnimationsModule],
    }).compileComponents();
    fixture = TestBed.createComponent(HostTabla);
    host = fixture.componentInstance;
    await fixture.whenStable();
  });

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('renderiza los encabezados de columna', () => {
    const encabezados = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('th'),
    ).map((th) => th.textContent?.trim());
    expect(encabezados).toContain('Nombre');
    expect(encabezados).toContain('RFC');
  });

  it('renderiza las celdas de cada fila mediante las plantillas', () => {
    expect(texto()).toContain('Anuncios del Norte');
    expect(texto()).toContain('AAA010101AAA');
    expect(texto()).toContain('Luminosos del Bajio');
  });

  it('aplica la clase de ocultamiento en movil a la columna marcada (ocultarEnMovil)', () => {
    const root = fixture.nativeElement as HTMLElement;
    // El encabezado de RFC (ocultarEnMovil: true) lleva la clase; el de Nombre no.
    const encabezados = Array.from(root.querySelectorAll('th'));
    const thRfc = encabezados.find((th) => th.textContent?.trim() === 'RFC')!;
    const thNombre = encabezados.find((th) => th.textContent?.trim() === 'Nombre')!;
    expect(thRfc.classList.contains('data-table__col--movil-oculta')).toBe(true);
    expect(thNombre.classList.contains('data-table__col--movil-oculta')).toBe(false);
    // Todas las celdas de datos de esa columna tambien la llevan.
    const celdasRfc = Array.from(root.querySelectorAll('td')).filter((td) =>
      /[A-Z]{3}\d{6}[A-Z]{3}/.test(td.textContent ?? ''),
    );
    expect(celdasRfc.length).toBeGreaterThan(0);
    for (const td of celdasRfc) {
      expect(td.classList.contains('data-table__col--movil-oculta')).toBe(true);
    }
  });

  it('emite cambioPagina al avanzar de pagina', () => {
    const siguiente = (fixture.nativeElement as HTMLElement).querySelector(
      '.mat-mdc-paginator-navigation-next',
    ) as HTMLButtonElement;
    expect(siguiente).toBeTruthy();
    siguiente.click();
    expect(host.ultimoCambio).toEqual({ page: 1, size: 20 });
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await esperarSinViolaciones(fixture);
  });
});
