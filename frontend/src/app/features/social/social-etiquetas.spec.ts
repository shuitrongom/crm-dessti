// =============================================================================
// Pruebas de etiquetas/iconos de canal y del origen unico de opciones (Req 1, 4)
// -----------------------------------------------------------------------------
// Verifican que ETIQUETA_CANAL/ICONO_CANAL cubren los CINCO canales y que
// OPCIONES_CANAL (origen unico que alimenta los selectores/filtros de
// Conexiones, Publicaciones, Campanas y Analitica) los expone con su etiqueta
// legible en el mismo orden. El color no es el unico portador: cada canal tiene
// etiqueta textual.
// =============================================================================

import { CanalSocial } from './models/social.models';
import { ETIQUETA_CANAL, ICONO_CANAL, OPCIONES_CANAL } from './social-etiquetas';

const CINCO_CANALES: CanalSocial[] = [
  'whatsapp',
  'facebook',
  'instagram',
  'messenger',
  'tiktok',
];

describe('etiquetas e iconos de Canal_Social', () => {
  it('ETIQUETA_CANAL cubre los cinco canales con texto legible', () => {
    for (const canal of CINCO_CANALES) {
      expect(ETIQUETA_CANAL[canal]).toBeTruthy();
    }
    expect(ETIQUETA_CANAL.facebook).toBe('Facebook');
    expect(ETIQUETA_CANAL.tiktok).toBe('TikTok');
  });

  it('ICONO_CANAL cubre los cinco canales', () => {
    for (const canal of CINCO_CANALES) {
      expect(ICONO_CANAL[canal]).toBeTruthy();
    }
  });
});

describe('OPCIONES_CANAL (origen unico de los selectores/filtros)', () => {
  it('ofrece exactamente los cinco canales', () => {
    expect(OPCIONES_CANAL.map((o) => o.valor)).toEqual(CINCO_CANALES);
  });

  it('cada opcion incluye la etiqueta legible del canal', () => {
    for (const opcion of OPCIONES_CANAL) {
      expect(opcion.etiqueta).toBe(ETIQUETA_CANAL[opcion.valor]);
    }
  });
});
