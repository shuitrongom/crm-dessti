// =============================================================================
// Shell de navegacion de la aplicacion (Req 3, 26, 52, 57)
// -----------------------------------------------------------------------------
// Estructura de navegacion reutilizable por los tres ambitos (plataforma,
// empresa, portal): barra superior con branding + tema + menu de Usuario, y una
// barra lateral (drawer) responsive cuyos items se componen DINAMICAMENTE a
// partir de los permisos/roles del Usuario (deny-by-default). Incluye landmarks
// accesibles (header/nav/main) y un enlace de salto al contenido.
//
// El drawer es persistente (side) desde el breakpoint `md` y superpuesto (over)
// en pantallas pequenas, colapsando bajo `md` (mobile-first, Req 52).
// =============================================================================

import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, RouterLink, RouterLinkActive, RouterOutlet, Router } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';

import { AuthService } from '../../auth/auth.service';
import { ModulosEmpresaService } from '../../auth/modulos-empresa.service';
import { NavigationService } from '../../navigation/navigation';
import { ThemeService } from '../../services/theme.service';
import { TematizacionService } from '../../services/tematizacion.service';
import { BrandingService } from '../../../features/empresa/services/branding.service';
import { Branding } from '../../../features/empresa/home/home.models';

/**
 * Etiqueta neutral de empresa cuando el tenant aun no ha configurado su branding
 * (sin nombreVisible ni logo). Nunca se muestra "Dess-TI" dentro de una empresa:
 * Dess-TI es el proveedor SaaS y solo aparece en plataforma/login (Req 26, 57).
 */
const ETIQUETA_EMPRESA_NEUTRAL = 'Mi empresa';

@Component({
  selector: 'app-shell-layout',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
  ],
  templateUrl: './shell-layout.html',
  styleUrl: './shell-layout.scss',
})
export class ShellLayout {
  private readonly auth = inject(AuthService);
  private readonly navegacion = inject(NavigationService);
  private readonly modulosEmpresa = inject(ModulosEmpresaService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly breakpoint = inject(BreakpointObserver);
  private readonly branding = inject(BrandingService);
  private readonly tematizacion = inject(TematizacionService);
  protected readonly theme = inject(ThemeService);

  /** Grupos de navegacion visibles (por seccion, filtrados por permiso/rol). */
  protected readonly grupos = this.navegacion.grupos;

  /**
   * URL actual de navegacion (sin fragmento ni query), fuente UNICAMENTE para
   * resaltar el encabezado de la seccion que contiene la ruta vigente. Se
   * refresca en cada NavigationEnd; NO gobierna la expansion del acordeon (las
   * secciones nunca se auto-abren).
   */
  private readonly urlActual = signal<string>('');

  /**
   * Conjunto de titulos de seccion actualmente EXPANDIDOS en el acordeon. TODAS
   * las secciones (incluida "Cuenta") son colapsables y arrancan CERRADAS: el
   * conjunto parte vacio y solo cambia por accion explicita del Usuario sobre un
   * encabezado ({@link alternarSeccion}). Ninguna navegacion lo modifica, de
   * modo que el estado abierto/cerrado que el Usuario deja se conserva durante
   * la sesion y nada se fuerza a abrir automaticamente.
   */
  private readonly seccionesExpandidas = signal<ReadonlySet<string>>(new Set());

  /**
   * Titulo de la seccion que contiene la ruta activa, o `null` si ninguna
   * coincide. Se elige la coincidencia de prefijo mas larga entre las rutas de
   * los items, de modo que rutas profundas (p. ej. /empresa/comercial/clientes)
   * resuelvan a su seccion (Comercial (CRM)). Solo alimenta el resaltado del
   * encabezado; NO expande la seccion.
   */
  protected readonly seccionActiva = computed<string | null>(() => {
    const url = this.urlActual();
    if (!url) {
      return null;
    }
    let mejorSeccion: string | null = null;
    let mejorLongitud = -1;
    for (const grupo of this.grupos()) {
      if (grupo.titulo === null) {
        continue;
      }
      for (const item of grupo.items) {
        const ruta = item.ruta;
        const coincide = url === ruta || url.startsWith(ruta + '/');
        if (coincide && ruta.length > mejorLongitud) {
          mejorLongitud = ruta.length;
          mejorSeccion = grupo.titulo;
        }
      }
    }
    return mejorSeccion;
  });

  /** `true` cuando el Usuario opera en el ambito de empresa (tenant cliente). */
  protected readonly esEmpresa = computed(() => this.auth.ambito() === 'empresa');

  /**
   * Branding propio de la empresa (Req 26), cargado de forma perezosa SOLO en el
   * ambito empresa. `null` mientras no se ha resuelto o si la carga falla (en tal
   * caso la UI recae en una etiqueta neutral, nunca en "Dess-TI").
   */
  private readonly brandingEmpresa = signal<Branding | null>(null);

  constructor() {
    // Carga reactiva y perezosa del branding: solo para el ambito empresa y una
    // sola vez por sesion de empresa. Ni plataforma (super_admin) ni portal
    // consultan GET /empresa/branding, de modo que la plataforma nunca aplica
    // color de marca (Req 5.1). Fallo de red -> etiqueta neutral (sin tarjeta de
    // error en el shell).
    let cargado = false;
    effect(() => {
      if (!this.esEmpresa()) {
        // Ambito plataforma (super_admin) o portal: NUNCA se aplica branding de
        // empresa. Se limpia cualquier color que hubiera quedado de una sesion de
        // empresa previa en la misma pestana (SPA sin recarga), garantizando que
        // el color de marca queda aislado al ambito empresa (Req 5.1).
        this.tematizacion.limpiar();
        return;
      }
      if (this.esEmpresa() && !cargado) {
        cargado = true;
        this.branding.consultar().subscribe({
          next: (b) => {
            this.brandingEmpresa.set(b);
            // Tematizacion por empresa (Req 4.1-4.3): al entrar al ambito empresa
            // se aplica el Color_Primario_Marca del tenant si esta configurado.
            // TematizacionService.aplicar deriva la paleta accesible y usa el modo
            // activo del tema por defecto; ademas ya reacciona internamente a los
            // cambios de modo claro/oscuro, por lo que aqui NO se duplica esa
            // logica. Si el tenant no tiene color (null), se limpia cualquier
            // sobrescritura para mantener el Tema_Corporativo (Req 4.3).
            if (b.colorPrimario) {
              this.tematizacion.aplicar(b.colorPrimario);
            } else {
              this.tematizacion.limpiar();
            }
          },
          // Un fallo de carga no debe romper el shell (Req 4.4): se recae en la
          // etiqueta neutral y se mantiene el Tema_Corporativo limpiando cualquier
          // color previo. El error se traga a proposito (sin tarjeta de error).
          error: () => {
            this.brandingEmpresa.set(null);
            this.tematizacion.limpiar();
          },
        });
      }
    });

    // Modulos VIVOS del tenant (gating por modulo sin re-login): carga inicial en
    // el ambito empresa (una vez al montarse el shell para un Usuario de empresa)
    // y refresco en cada navegacion exitosa (NavigationEnd), de modo que el menu
    // refleje altas/bajas de plan a medida que el Usuario navega. Solo ambito
    // empresa: plataforma (super_admin) y portal no consultan (recibirian 403).
    // La guarda por ambito vive en ModulosEmpresaService.refrescar().
    let modulosCargados = false;
    effect(() => {
      if (this.esEmpresa() && !modulosCargados) {
        modulosCargados = true;
        this.modulosEmpresa.refrescar();
      }
    });

    // Estado inicial del acordeon: TODAS las secciones arrancan cerradas. Solo
    // se registra la URL vigente para resaltar el encabezado de la seccion
    // activa; nunca se auto-abre ninguna seccion.
    this.urlActual.set(this.router.url);

    this.router.events
      .pipe(
        filter((evento): evento is NavigationEnd => evento instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((evento) => {
        // GET barato; ModulosEmpresaService ignora el refresco fuera de empresa.
        this.modulosEmpresa.refrescar();
        // Actualiza SOLO el resaltado de la seccion activa. NO toca el conjunto
        // expandido: la navegacion nunca abre ni cierra secciones (se respeta el
        // estado que el Usuario dejo).
        this.urlActual.set(evento.urlAfterRedirects);
      });
  }

  /** `true` si la seccion indicada esta expandida en el acordeon. */
  protected estaExpandida(titulo: string): boolean {
    return this.seccionesExpandidas().has(titulo);
  }

  /**
   * Alterna la expansion de una seccion (clic/teclado en su encabezado). Muta el
   * conjunto expandido sin forzar cierre unico: el Usuario puede tener varias
   * secciones abiertas a la vez. Es la UNICA via de cambio del estado del
   * acordeon.
   */
  protected alternarSeccion(titulo: string): void {
    const siguiente = new Set(this.seccionesExpandidas());
    if (siguiente.has(titulo)) {
      siguiente.delete(titulo);
    } else {
      siguiente.add(titulo);
    }
    this.seccionesExpandidas.set(siguiente);
  }

  /** Identificador estable del contenedor de items de una seccion (aria-controls). */
  protected idSeccion(titulo: string): string {
    // Normaliza a un slug apto para id: minusculas, alfanumerico y guiones.
    const slug = titulo
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
    return `shell-seccion-${slug}`;
  }

  /**
   * Nombre visible de la empresa cuando esta configurado; `null` en otro caso.
   * Deriva del branding propio del tenant (Req 26.2).
   */
  protected readonly nombreEmpresa = computed<string | null>(() => {
    const b = this.brandingEmpresa();
    const nombre = b?.nombreVisible?.trim();
    return nombre ? nombre : null;
  });

  /** Logotipo de la empresa (data URI/URL) cuando esta configurado; `null` si no. */
  protected readonly logoEmpresa = computed<string | null>(() => {
    const logo = this.brandingEmpresa()?.logo?.trim();
    return logo ? logo : null;
  });

  /**
   * Etiqueta de marca de la empresa para el drawer/titulo: el nombreVisible
   * propio o, en su defecto, una etiqueta neutral. NUNCA "Dess-TI".
   */
  protected readonly etiquetaEmpresa = computed<string>(
    () => this.nombreEmpresa() ?? ETIQUETA_EMPRESA_NEUTRAL,
  );

  /**
   * Nombre legible del Usuario para el menu de cuenta. Prefiere el claim
   * `identificador` (p. ej. "superadmin@dessti") sobre el `sub` (UUID), de modo
   * que el menu muestre un texto humano y no el identificador tecnico.
   */
  protected readonly nombreMostrado = this.auth.nombreMostrado;

  /** Iniciales del Usuario derivadas de {@link nombreMostrado} (avatar del menu). */
  protected readonly iniciales = computed(() => {
    const nombre = this.nombreMostrado();
    if (!nombre) {
      return '?';
    }
    // Toma la parte anterior a la @ (si la hay) y sus dos primeras letras.
    const base = nombre.includes('@') ? nombre.split('@')[0] : nombre;
    const limpio = base.replace(/[^\p{L}\p{N}]/gu, '');
    return (limpio.slice(0, 2) || '?').toUpperCase();
  });

  /** `true` cuando el Usuario opera en el ambito de plataforma (super_admin). */
  protected readonly esPlataforma = computed(() => this.auth.ambito() === 'plataforma');

  /**
   * Ruta de "Mi perfil" segun el ambito del Usuario (o `null` cuando el ambito
   * no expone perfil propio, p. ej. portal): el ambito plataforma apunta a
   * `/plataforma/perfil` y el ambito empresa a `/empresa/perfil` (mismo
   * componente, con la seccion "Datos de mi empresa" para admin_empresa).
   */
  protected readonly rutaPerfil = computed<string | null>(() => {
    switch (this.auth.ambito()) {
      case 'plataforma':
        return '/plataforma/perfil';
      case 'empresa':
        return '/empresa/perfil';
      default:
        return null;
    }
  });

  /** Etiqueta del ambito actual para el titulo de la barra superior. */
  protected readonly tituloAmbito = computed(() => {
    switch (this.auth.ambito()) {
      case 'plataforma':
        return 'Administración de plataforma';
      case 'portal':
        return 'Portal del cliente';
      default:
        // Empresa: refleja su propio nombre visible o una etiqueta neutral,
        // nunca "Dess-TI" (proveedor SaaS).
        return this.etiquetaEmpresa();
    }
  });

  /** `true` cuando el ancho es de escritorio (drawer persistente). */
  protected readonly esEscritorio = toSignal(
    this.breakpoint
      .observe(['(min-width: 905px)'])
      .pipe(map((estado) => estado.matches)),
    { initialValue: false },
  );

  /** Estado abierto/cerrado del drawer (relevante en modo superpuesto). */
  protected readonly drawerAbierto = signal(false);

  /** Modo del drawer segun el tamano de pantalla (side en escritorio, over en movil). */
  protected readonly modoDrawer = computed<'side' | 'over'>(() =>
    this.esEscritorio() ? 'side' : 'over',
  );

  /** Alterna el drawer (boton de menu en movil). */
  protected alternarDrawer(): void {
    this.drawerAbierto.set(!this.drawerAbierto());
  }

  /** Cierra el drawer al navegar en modo superpuesto (mejor UX en movil). */
  protected alNavegar(): void {
    if (!this.esEscritorio()) {
      this.drawerAbierto.set(false);
    }
  }

  /** Cierra la sesion y navega al login (Req 68.1). */
  protected cerrarSesion(): void {
    this.auth.logout().subscribe(() => {
      // Descarta la lista viva de Modulos para que un proximo Usuario no herede
      // el gating del tenant anterior (recae en su propio claim hasta refrescar).
      this.modulosEmpresa.limpiar();
      void this.router.navigate(['/login']);
    });
  }
}


