package com.dessti.crm.rhnomina.nomina.domain;

import java.math.BigDecimal;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Componente de dominio <strong>puro</strong> que calcula la nomina de un Empleado
 * en un Periodo_Nomina: percepciones, deducciones (ISR, IMSS, Infonavit), subsidio
 * al empleo y neto a pagar (Req 41.1, 41.2; Property 19).
 *
 * <h2>Identidad aritmetica de la nomina (Property 19, Req 41.1, 41.2)</h2>
 * <pre>
 *   salario      = round(salarioDiario * diasPeriodo, 2)
 *   percepciones = round(salario + tiempoExtra + aguinaldo + ptu, 2)
 *   isr          = TablasFiscalesNomina.isrMensual(percepciones)
 *   imss         = TablasFiscalesNomina.cuotaImssObrera(salario)
 *   infonavit    = TablasFiscalesNomina.descuentoInfonavit(salario, tasaInfonavit)
 *   deducciones  = round(isr + imss + infonavit, 2)
 *   subsidio     = TablasFiscalesNomina.subsidioAlEmpleoMensual(percepciones)
 *   neto         = round(percepciones - deducciones + subsidio, 2)   y   neto &gt;= 0
 * </pre>
 *
 * <p>Clase de utilidad no instanciable, sin dependencias de framework ni de
 * persistencia, para que la <strong>Property 19</strong> la ejercite directamente
 * sobre miles de entradas y las pruebas unitarias de tablas fiscales (tarea 35.3)
 * verifiquen montos concretos. El redondeo half-up y la escala 2 son coherentes con
 * {@code CalculoFiscalCfdi} (aritmetica monetaria del sistema, NUMERIC(18,2)).</p>
 *
 * <p><strong>Neto no negativo (Req 41.1):</strong> para entradas realistas el
 * subsidio y las percepciones cubren las deducciones, por lo que el neto es &gt;= 0.
 * Si una combinacion de entradas produjera un neto negativo, se rechaza con
 * {@link ReglaNegocioException} (422): indica una entrada invalida (por ejemplo
 * deducciones externas incoherentes), no un neto negativo legitimo.</p>
 *
 * <p>La <strong>presencia</strong> de los datos fiscales del Empleado (RFC/CURP/NSS
 * y Contrato_Laboral) NO se valida aqui (Req 41.3): la capa de aplicacion la
 * verifica antes de construir la {@link EntradaNomina}. Este motor asume una entrada
 * ya presente y solo comprueba la coherencia numerica (no negatividad, dias &gt; 0).</p>
 */
public final class CalculoNomina {

    private CalculoNomina() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Calcula la nomina de un Empleado a partir de su {@link EntradaNomina}
     * (Req 41.1, 41.2; Property 19). Todos los importes del resultado quedan a
     * escala {@value TablasFiscalesNomina#ESCALA_MONETARIA} (half-up) y cumplen la
     * identidad {@code neto == round(percepciones - deducciones + subsidio, 2)} con
     * {@code neto >= 0}.
     *
     * @param entrada datos ya validados del calculo; obligatorio.
     * @return el resultado con el desglose y los totales agregados.
     * @throws ReglaNegocioException si la entrada es nula, algun importe es negativo,
     *         los dias no son positivos, el salario diario no es positivo, o el neto
     *         resultara negativo (422).
     */
    public static ResultadoNomina calcular(EntradaNomina entrada) {
        if (entrada == null) {
            throw new ReglaNegocioException("La entrada del calculo de nomina es obligatoria.");
        }
        if (entrada.diasPeriodo() <= 0) {
            throw new ReglaNegocioException("Los dias del Periodo_Nomina deben ser positivos.");
        }
        if (entrada.salarioDiario() == null || entrada.salarioDiario().signum() <= 0) {
            throw new ReglaNegocioException("El salario diario debe ser mayor que cero.");
        }
        BigDecimal tiempoExtra = noNegativo(entrada.tiempoExtra(), "El tiempo extra");
        BigDecimal aguinaldo = noNegativo(entrada.aguinaldo(), "El aguinaldo");
        BigDecimal ptu = noNegativo(entrada.ptu(), "La PTU");

        // Percepciones (Req 41.1).
        BigDecimal salario = TablasFiscalesNomina.redondear(
                entrada.salarioDiario().multiply(BigDecimal.valueOf(entrada.diasPeriodo())));
        BigDecimal percepciones = TablasFiscalesNomina.redondear(
                salario.add(tiempoExtra).add(aguinaldo).add(ptu));

        // Deducciones (Req 41.2): ISR sobre la base gravable (aqui, las percepciones),
        // IMSS y Infonavit sobre el salario base del periodo.
        BigDecimal isr = TablasFiscalesNomina.isrMensual(percepciones);
        BigDecimal imss = TablasFiscalesNomina.cuotaImssObrera(salario);
        BigDecimal infonavit = TablasFiscalesNomina.descuentoInfonavit(
                salario, entrada.tasaInfonavit());
        BigDecimal deducciones = TablasFiscalesNomina.redondear(isr.add(imss).add(infonavit));

        // Subsidio al empleo cuando corresponde (Req 41.1).
        BigDecimal subsidio = TablasFiscalesNomina.subsidioAlEmpleoMensual(percepciones);

        // Identidad aritmetica: neto = percepciones - deducciones + subsidio (Property 19).
        BigDecimal neto = TablasFiscalesNomina.redondear(
                percepciones.subtract(deducciones).add(subsidio));
        if (neto.signum() < 0) {
            throw new ReglaNegocioException(
                    "El neto a pagar no puede ser negativo; revise los datos de la nomina.");
        }

        return new ResultadoNomina(
                salario, tiempoExtra, aguinaldo, ptu, percepciones,
                isr, imss, infonavit, deducciones, subsidio, neto);
    }

    private static BigDecimal noNegativo(BigDecimal valor, String etiqueta) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(TablasFiscalesNomina.ESCALA_MONETARIA);
        }
        if (valor.signum() < 0) {
            throw new ReglaNegocioException(etiqueta + " no puede ser negativo.");
        }
        return valor;
    }
}
