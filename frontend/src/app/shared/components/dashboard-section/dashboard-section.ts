// =============================================================================
// Componente DashboardSection — layout de tablero reutilizable (Req 22, 52)
// -----------------------------------------------------------------------------
// Encapsula la DISTRIBUCION visual premium de un tablero, que estaba duplicada en
// muchos bloques: una fila/grid de KPIs (proyectada en el slot [kpis]) y una zona
// de graficas (proyectada en el slot [grafica]). Dos disposiciones:
//   - 'split' (por defecto): en pantallas anchas, KPIs a la izquierda (2fr) y
//     graficas a la derecha (1fr). Es el patron dominante del proyecto.
//   - 'apilado': KPIs a todo el ancho arriba y las graficas debajo (fila).
// La tarjeta de KPI recomendada es <app-kpi-tile>; la grafica, <app-metric-chart>.
// Solo maquetacion: no conoce datos ni logica de negocio.
// =============================================================================

import { Component, input, ChangeDetectionStrategy } from '@angular/core';

/** Disposicion del tablero. */
export type LayoutDashboard = 'split' | 'apilado';

@Component({
  selector: 'app-dashboard-section',
  template: `
    <section class="dash" [class]="'dash--' + layout()">
      <div class="dash__kpis">
        <ng-content select="[kpis]" />
      </div>
      <div class="dash__graficas">
        <ng-content select="[grafica]" />
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './dashboard-section.scss',
})
export class DashboardSection {
  /** Disposicion: 'split' (KPIs izquierda + graficas derecha) o 'apilado'. */
  readonly layout = input<LayoutDashboard>('split');
}
