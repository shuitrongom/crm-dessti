// =============================================================================
// OperacionOverlay — feedback visual animado de operaciones (Sistema de Diseño)
// -----------------------------------------------------------------------------
// Overlay central, breve y bloqueante, que da feedback "vivo" mientras se
// ejecuta una operación (guardar, crear, procesar, traspasar, eliminar) y
// remata con una animación de éxito antes de cerrarse. Complementa al toast
// (NotificacionesService): el overlay comunica el PROGRESO de la acción; el
// toast, el resultado textual.
//
// Uso típico (envuelve un Observable de la operación):
//   this.overlay.ejecutar(this.servicio.guardar(cmd), {
//     tipo: 'guardar',
//     textoProceso: 'Guardando plan…',
//     textoExito: 'Plan guardado',
//   }).subscribe({ next: ..., error: ... });
//
// También admite control manual (mostrar/exito/cerrar) para casos no basados en
// un único Observable.
//
// Accesibilidad (Req 57): role="alertdialog" con aria-busy mientras procesa;
// texto de estado con aria-live. La animación respeta prefers-reduced-motion.
// =============================================================================

import {
  ApplicationRef,
  Component,
  EnvironmentInjector,
  Injectable,
  createComponent,
  inject,
  signal,
} from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Observable } from 'rxjs';

/** Tipo de operación: determina el ícono y la animación contextual. */
export type TipoOperacion = 'guardar' | 'crear' | 'procesar' | 'traspasar' | 'eliminar';

/** Fase visual del overlay. */
type FaseOverlay = 'proceso' | 'exito';

/** Textos y tipo configurables de una operación. */
export interface OpcionesOperacion {
  /** Tipo (ícono/animación contextual). Por defecto 'procesar'. */
  tipo?: TipoOperacion;
  /** Texto mostrado mientras procesa. */
  textoProceso?: string;
  /** Texto mostrado en el remate de éxito. */
  textoExito?: string;
  /** Milisegundos que se muestra el estado de éxito antes de cerrar (por defecto 700). */
  duracionExitoMs?: number;
}

/**
 * Íconos (Material Symbols) por tipo de operación. Seleccionados con un aire más
 * tecnológico/futurista: nube de guardado, chispa de creación, órbita de proceso,
 * intercambio de traspaso y borrado. El anillo orbital y el glow del overlay
 * completan el look "tech".
 */
const ICONO_POR_TIPO: Record<TipoOperacion, string> = {
  guardar: 'cloud_sync',
  crear: 'auto_awesome',
  procesar: 'autorenew',
  traspasar: 'swap_horiz',
  eliminar: 'delete_sweep',
};

/**
 * Componente del overlay. No se declara en ninguna plantilla: el servicio lo
 * crea dinámicamente y lo adjunta al {@link ApplicationRef}, montándolo en un
 * contenedor propio en el <body>.
 */
@Component({
  selector: 'app-operacion-overlay',
  standalone: true,
  template: `
    <div
      class="ds-opov__backdrop"
      [class.ds-opov__backdrop--visible]="visible()"
    >
      <div
        class="ds-opov__panel"
        role="alertdialog"
        aria-live="assertive"
        [attr.aria-busy]="fase() === 'proceso'"
        [attr.aria-label]="texto()"
      >
        <div
          class="ds-opov__icono"
          [class.ds-opov__icono--proceso]="fase() === 'proceso'"
          [class.ds-opov__icono--exito]="fase() === 'exito'"
        >
          @if (fase() === 'proceso') {
            <span class="ds-opov__halo" aria-hidden="true"></span>
            <span class="ds-opov__spinner" aria-hidden="true"></span>
            <span class="ds-opov__orbita" aria-hidden="true"></span>
            <mat-icon class="ds-opov__glifo" aria-hidden="true">{{ icono() }}</mat-icon>
          } @else {
            <span class="ds-opov__destello" aria-hidden="true"></span>
            <svg
              class="ds-opov__check"
              viewBox="0 0 52 52"
              aria-hidden="true"
              focusable="false"
            >
              <circle class="ds-opov__check-circulo" cx="26" cy="26" r="24" fill="none" />
              <path
                class="ds-opov__check-trazo"
                fill="none"
                d="M14 27 l8 8 l16 -18"
              />
            </svg>
          }
        </div>
        <p class="ds-opov__texto">{{ texto() }}</p>
      </div>
    </div>
  `,
  styleUrl: './operacion-overlay.scss',
  imports: [MatIconModule],
})
export class OperacionOverlay {
  readonly visible = signal(false);
  readonly fase = signal<FaseOverlay>('proceso');
  readonly texto = signal('Procesando…');
  readonly icono = signal(ICONO_POR_TIPO.procesar);
}

/**
 * Servicio singleton que gestiona un único overlay global. Monta el componente
 * bajo demanda y lo reutiliza.
 */
@Injectable({ providedIn: 'root' })
export class OperacionOverlayService {
  private readonly appRef = inject(ApplicationRef);
  private readonly injector = inject(EnvironmentInjector);

  private ref: ReturnType<typeof createComponent<OperacionOverlay>> | null = null;
  private host: HTMLElement | null = null;

  /**
   * Ejecuta una operación mostrando el overlay durante su curso y rematando con
   * la animación de éxito. Reemite exactamente lo que emite `origen`.
   */
  ejecutar<T>(origen: Observable<T>, opciones: OpcionesOperacion = {}): Observable<T> {
    return new Observable<T>((observer) => {
      this.mostrar(opciones);
      let ultimo: T;
      let recibido = false;
      const sub = origen.subscribe({
        next: (v) => {
          ultimo = v;
          recibido = true;
        },
        error: (e) => {
          this.cerrarInmediato();
          observer.error(e);
        },
        complete: () => {
          const cerrar = () => {
            observer.complete();
          };
          this.exito(opciones, cerrar);
          if (recibido) {
            observer.next(ultimo);
          }
        },
      });
      return () => sub.unsubscribe();
    });
  }

  /** Muestra el overlay en fase de proceso. */
  mostrar(opciones: OpcionesOperacion = {}): void {
    const cmp = this.asegurarMontado();
    const tipo = opciones.tipo ?? 'procesar';
    cmp.instance.fase.set('proceso');
    cmp.instance.icono.set(ICONO_POR_TIPO[tipo]);
    cmp.instance.texto.set(opciones.textoProceso ?? 'Procesando…');
    cmp.instance.visible.set(true);
  }

  /** Cambia a fase de éxito y cierra tras la duración indicada. */
  private exito(opciones: OpcionesOperacion, alCerrar?: () => void): void {
    const cmp = this.ref?.instance;
    if (!cmp) {
      alCerrar?.();
      return;
    }
    cmp.fase.set('exito');
    cmp.texto.set(opciones.textoExito ?? 'Listo');
    const dur = opciones.duracionExitoMs ?? 700;
    window.setTimeout(() => {
      this.cerrarInmediato();
      alCerrar?.();
    }, dur);
  }

  /** Oculta y desmonta el overlay de inmediato. */
  cerrarInmediato(): void {
    if (this.ref) {
      this.ref.instance.visible.set(false);
    }
  }

  private asegurarMontado(): NonNullable<typeof this.ref> {
    if (this.ref) {
      return this.ref;
    }
    this.host = document.createElement('div');
    this.host.classList.add('ds-opov-host');
    document.body.appendChild(this.host);
    this.ref = createComponent(OperacionOverlay, {
      environmentInjector: this.injector,
      hostElement: this.host,
    });
    this.appRef.attachView(this.ref.hostView);
    return this.ref;
  }
}
