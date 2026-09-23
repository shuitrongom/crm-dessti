import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Componente raiz de la aplicacion.
 *
 * Es un contenedor minimo: aloja el {@link RouterOutlet} de nivel superior sobre
 * el que se montan los ambitos (login publico, plataforma, empresa, portal y la
 * vista de acceso denegado). El shell de navegacion (barra + drawer) lo aporta
 * cada ambito autenticado mediante `ShellLayout`, de modo que las rutas publicas
 * (login) se rendericen sin la navegacion interna.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
