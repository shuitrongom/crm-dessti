// =============================================================================
// Pruebas unitarias del avance de estado del Ticket_Servicio (Req 20.4, 20.5)
// -----------------------------------------------------------------------------
// Verifican el flujo abierto -> asignado -> en_proceso -> resuelto -> cerrado: la
// asignacion (abierto) usa su propio formulario (no hay accion generica), y los
// estados terminales no ofrecen avance. El backend valida cada transicion.
// =============================================================================

import { avanceDeTicket } from './ticket-estados';

describe('ticket-estados', () => {
  it('en abierto no ofrece accion generica (la asignacion es aparte)', () => {
    expect(avanceDeTicket('abierto')).toBeNull();
  });

  it('en asignado ofrece iniciar atencion (en_proceso)', () => {
    expect(avanceDeTicket('asignado')?.estado).toBe('en_proceso');
  });

  it('en en_proceso ofrece marcar resuelto', () => {
    expect(avanceDeTicket('en_proceso')?.estado).toBe('resuelto');
  });

  it('en resuelto ofrece cerrar', () => {
    expect(avanceDeTicket('resuelto')?.estado).toBe('cerrado');
  });

  it('en cerrado (terminal) no ofrece avance', () => {
    expect(avanceDeTicket('cerrado')).toBeNull();
  });
});
