package com.dessti.crm.contabilidad.electronica.domain.xml;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utilidades PURAS compartidas por los generadores XML de Contabilidad Electronica
 * (Anexo 24). Sin dependencias de Spring ni de JPA.
 */
public final class XmlUtil {

    private XmlUtil() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * Formatea un importe a exactamente dos decimales con punto decimal, como exige
     * el SAT para los atributos monetarios (p. ej. {@code 1234.50}). Un valor nulo
     * se trata como cero. Redondeo HALF_UP.
     *
     * @param valor importe a formatear; {@code null} se trata como {@code 0.00}.
     * @return la representacion con dos decimales (nunca notacion cientifica).
     */
    public static String importe(BigDecimal valor) {
        BigDecimal v = (valor == null) ? BigDecimal.ZERO : valor;
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
