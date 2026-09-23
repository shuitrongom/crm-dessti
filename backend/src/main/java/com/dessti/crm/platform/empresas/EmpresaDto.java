package com.dessti.crm.platform.empresas;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de salida de una Empresa (Req 12.2, 24), distinto de la entidad de
 * persistencia {@link Empresa}.
 *
 * <p><strong>Aislamiento del super_admin (Req 24.3):</strong> este DTO expone
 * unicamente datos de <em>plataforma</em> de la Empresa (identidad, estado,
 * Giro, branding, ficha descriptiva/de contacto y marcas de auditoria). No
 * expone ni referencia dato alguno de negocio de la Empresa (Clientes,
 * Cotizaciones, etc.), coherente con que el {@code super_admin} no accede a los
 * datos de negocio.</p>
 *
 * @param id                    identificador de la Empresa (que es su tenant_id).
 * @param nombre                nombre de la Empresa.
 * @param rfc                   identificador fiscal.
 * @param giroId                Giro (vertical) al que pertenece la Empresa (Req 2.3).
 * @param estado                estado del ciclo de vida (activa/suspendida/cancelada).
 * @param brandingNombreVisible nombre visible de branding (Req 26); puede ser {@code null}.
 * @param brandingLogo          logotipo de branding (URL o data URI, Req 26); puede ser {@code null}.
 * @param nombreComercial       nombre comercial (marca); puede ser {@code null}.
 * @param emailContacto         correo de contacto; puede ser {@code null}.
 * @param telefono              telefono de contacto; puede ser {@code null}.
 * @param sitioWeb              sitio web; puede ser {@code null}.
 * @param direccion             direccion desglosada; nunca {@code null} (sus campos pueden serlo).
 * @param notas                 notas libres del super_admin; puede ser {@code null}.
 * @param fechaCancelacion      instante de cancelacion (offboarding, Req 69.2); {@code null} si no esta cancelada.
 * @param finPeriodoGracia      fin del Periodo_Gracia (Req 69.2/69.3); {@code null} si no esta cancelada.
 * @param createdAt             instante de alta (UTC).
 * @param updatedAt             instante de la ultima modificacion (UTC).
 * @param planVigente           plan y suscripcion vigentes de la Empresa (dato enriquecido de plataforma, Req 4.2); {@code null} cuando la Empresa no tiene suscripcion vigente o en las rutas que no lo enriquecen.
 */
public record EmpresaDto(
        UUID id,
        String nombre,
        String rfc,
        UUID giroId,
        EstadoEmpresa estado,
        String brandingNombreVisible,
        String brandingLogo,
        String nombreComercial,
        String emailContacto,
        String telefono,
        String sitioWeb,
        DireccionDto direccion,
        String notas,
        Instant fechaCancelacion,
        Instant finPeriodoGracia,
        Instant createdAt,
        Instant updatedAt,
        PlanVigenteDto planVigente) {

    /**
     * Direccion desglosada de la Empresa (Req 24); todos sus campos son
     * opcionales ({@code null} si no se registraron).
     *
     * @param calle  calle y numero.
     * @param ciudad ciudad.
     * @param estado estado/provincia.
     * @param cp     codigo postal.
     * @param pais   pais.
     */
    public record DireccionDto(
            String calle,
            String ciudad,
            String estado,
            String cp,
            String pais) {
    }

    /**
     * Plan y suscripcion vigentes de la Empresa (dato enriquecido de PLATAFORMA,
     * Req 4.1/4.2/4.8).
     *
     * <p>Representa el plan que la Empresa tiene contratado a traves de su
     * suscripcion vigente, para que el super_admin lo vea en el listado y la ficha
     * sin derivar la informacion en el frontend. El plan/suscripcion son datos de
     * <em>plataforma</em> (monetizacion), coherentes con el aislamiento del
     * super_admin documentado en {@link EmpresaDto}: no exponen dato alguno de
     * negocio del tenant.</p>
     *
     * <p><strong>Patron NO-UUID:</strong> {@code nombrePlan} es el nombre
     * <em>legible</em> del plan que resuelve el backend; el usuario nunca ve el
     * UUID. Los campos {@code planId} y {@code suscripcionId} son de uso INTERNO
     * (preseleccion en selectores e invocacion de acciones desde el frontend) y NO
     * se muestran como UUID al usuario.</p>
     *
     * @param nombrePlan          nombre legible del plan vigente (patron NO-UUID). Se
     *                            conserva por compatibilidad con el frontend; contiene
     *                            el mismo nombre que {@code nombreInstrumento}.
     * @param estado              estado de la suscripcion vigente (activa/en prueba/suspendida/cancelada/vencida).
     * @param planId              identificador del plan vigente; uso interno, no se muestra como UUID.
     * @param suscripcionId       identificador de la suscripcion vigente; uso interno, no se muestra como UUID.
     * @param vigenciaInicio      inicio de vigencia de la suscripcion vigente.
     * @param vigenciaFin         fin de vigencia de la suscripcion vigente; {@code null} si no tiene fecha de fin.
     * @param tipoInstrumento     tipo del instrumento vigente ({@code 'plan'} o {@code 'suscripcion'}).
     * @param nombreInstrumento   nombre legible del Plan o del Paquete vigente (patron NO-UUID);
     *                            reemplaza semanticamente a {@code nombrePlan}, que se conserva por compatibilidad.
     * @param paqueteSuscripcionId identificador del Paquete de suscripcion cuando el instrumento
     *                            es una suscripcion; {@code null} si es un Plan. Uso interno, no se muestra.
     * @param diasRestantes       dias que faltan hasta {@code vigenciaFin}; {@code null} cuando {@code vigenciaFin} es {@code null}.
     * @param enPrueba            {@code true} cuando el estado del contrato es {@code EN_PRUEBA}.
     * @param vencida             {@code true} cuando el contrato esta vencido (dato derivado).
     * @param porVencer           {@code true} cuando el contrato esta por vencer (dentro del umbral de aviso).
     */
    public record PlanVigenteDto(
            String nombrePlan,
            EstadoSuscripcion estado,
            UUID planId,
            UUID suscripcionId,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFin,
            TipoInstrumento tipoInstrumento,
            String nombreInstrumento,
            UUID paqueteSuscripcionId,
            Integer diasRestantes,
            boolean enPrueba,
            boolean vencida,
            boolean porVencer) {
    }

    /**
     * Proyecta una entidad {@link Empresa} a su DTO de salida <em>sin</em>
     * enriquecer el plan vigente.
     *
     * <p>Se conserva por compatibilidad con las rutas que no requieren el dato
     * enriquecido (alta, edicion, mi-empresa, cambio de Giro): delega en
     * {@link #de(Empresa, PlanVigenteDto)} pasando {@code planVigente = null},
     * de modo que el contrato de los campos existentes no cambia (Req 4.9).</p>
     *
     * @param empresa entidad a proyectar.
     * @return el DTO correspondiente con {@code planVigente == null}.
     */
    public static EmpresaDto de(Empresa empresa) {
        return de(empresa, null);
    }

    /**
     * Proyecta una entidad {@link Empresa} a su DTO de salida enriquecido con el
     * plan y la suscripcion vigentes.
     *
     * <p>La usan las rutas de plataforma que resuelven el plan vigente por lote
     * (listado y consulta de Empresas, Req 4.2); el resto del contrato de campos
     * es identico al de {@link #de(Empresa)}.</p>
     *
     * @param empresa      entidad a proyectar.
     * @param planVigente  plan y suscripcion vigentes de la Empresa; {@code null}
     *                     cuando la Empresa no tiene suscripcion vigente (Req 4.3).
     * @return el DTO correspondiente con el {@code planVigente} recibido.
     */
    public static EmpresaDto de(Empresa empresa, PlanVigenteDto planVigente) {
        return new EmpresaDto(
                empresa.getId(),
                empresa.getNombre(),
                empresa.getRfc(),
                empresa.getGiroId(),
                empresa.getEstado(),
                empresa.getBrandingNombreVisible(),
                empresa.getBrandingLogo(),
                empresa.getNombreComercial(),
                empresa.getEmailContacto(),
                empresa.getTelefono(),
                empresa.getSitioWeb(),
                new DireccionDto(
                        empresa.getDireccionCalle(),
                        empresa.getDireccionCiudad(),
                        empresa.getDireccionEstado(),
                        empresa.getDireccionCp(),
                        empresa.getDireccionPais()),
                empresa.getNotas(),
                empresa.getFechaCancelacion(),
                empresa.getFinPeriodoGracia(),
                empresa.getCreatedAt(),
                empresa.getUpdatedAt(),
                planVigente);
    }
}

