package com.dessti.crm.tesoreria.application;

/**
 * Puerto de salida de <strong>importacion de estados de cuenta bancarios</strong>
 * (Req 43.2). Es la frontera hexagonal que <em>desacopla</em> el modulo de tesoreria
 * de la fuente concreta del estado de cuenta (archivo CSV/OFX, API del banco), de
 * modo que el adaptador real pueda intercambiarse sin tocar la aplicacion.
 *
 * <h2>Contrato</h2>
 * <p>{@link #importar(SolicitudImportacion)} recibe la referencia/contenido del
 * estado de cuenta a importar (junto con la Cuenta_Bancaria y el periodo) y devuelve
 * un {@link EstadoCuentaImportado} inmutable con el saldo inicial, el saldo final y
 * la lista de {@link MovimientoImportado} (fecha, monto con signo, referencia y
 * descripcion). La aplicacion ({@code ServicioTesoreria}) persiste ese resultado
 * como Estado_Cuenta_Bancario + Movimiento_Bancario (Req 43.2).</p>
 *
 * <h2>Portabilidad y secretos (Req 11)</h2>
 * <p>La implementacion real (adaptador de archivo o de API del banco) es trabajo
 * futuro; el {@link com.dessti.crm.tesoreria.adapter.out.importacion.ImportacionBancariaStubAdapter
 * stub} determinista cubre las pruebas y el arranque sin credenciales. Las
 * credenciales de la API del banco se resolverian <strong>exclusivamente</strong>
 * desde la gestion de secretos (Req 11) y <strong>nunca</strong> se embeberian en el
 * codigo ni se escribirian en logs.</p>
 *
 * <p>El contrato usa <em>records</em> inmutables, sin tipos de dominio ni de
 * persistencia, para mantener el puerto estable y portable.</p>
 */
public interface ImportacionBancariaPort {

    /**
     * Importa un estado de cuenta bancario desde la fuente configurada (Req 43.2).
     *
     * @param solicitud datos de la importacion (Cuenta_Bancaria, periodo y
     *                  referencia/contenido del estado de cuenta); obligatorio.
     * @return el estado de cuenta importado con sus saldos y movimientos.
     */
    EstadoCuentaImportado importar(SolicitudImportacion solicitud);
}
