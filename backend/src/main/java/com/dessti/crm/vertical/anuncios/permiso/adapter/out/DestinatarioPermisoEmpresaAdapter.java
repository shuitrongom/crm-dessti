package com.dessti.crm.vertical.anuncios.permiso.adapter.out;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.empresas.Empresa;
import com.dessti.crm.platform.empresas.EmpresaRepository;
import com.dessti.crm.vertical.anuncios.permiso.application.DestinatarioPermisoPort;
import com.dessti.crm.vertical.anuncios.permiso.application.NotificacionVencimientoPermiso;

/**
 * Adaptador de salida que resuelve el correo destinatario de la Notificación de
 * vencimiento de un permiso (Req 13.2, §E1, Decisión D7).
 *
 * <p><strong>Resolución (mínima y explícita):</strong> el modelo de datos del giro
 * anuncios no almacena hoy un responsable con correo por Sitio (la entidad
 * {@code Sitio} sólo conserva identidad, vínculo al Proyecto, nombre y dirección),
 * por lo que no hay forma de resolver el correo del responsable del Sitio. En su
 * defecto —tal como prevé el diseño (§E1)— se usa el correo de contacto de la
 * Empresa del tenant en contexto ({@code email_contacto} de la tabla {@code empresa}).
 * Si la Empresa no tiene correo de contacto registrado, se devuelve
 * {@link Optional#empty()} para que el notificador registre la omisión sin romper el
 * barrido.</p>
 *
 * <p>La Empresa <strong>es</strong> el tenant, por lo que se carga por
 * {@code id == tenantId} vía {@link EmpresaRepository}. La tabla {@code empresa} no
 * es tenant-scoped (no lleva RLS), por lo que no se fija {@code app.current_tenant}
 * aquí; el {@code tenantId} proviene del propio evento de vencimiento resuelto por
 * {@code ServicioPermisos} desde el {@code TenantContext}.</p>
 */
@Component
public class DestinatarioPermisoEmpresaAdapter implements DestinatarioPermisoPort {

    private final EmpresaRepository empresaRepository;

    public DestinatarioPermisoEmpresaAdapter(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> resolverCorreo(NotificacionVencimientoPermiso notificacion) {
        if (notificacion == null || notificacion.tenantId() == null) {
            return Optional.empty();
        }
        UUID tenantId = notificacion.tenantId();
        return empresaRepository.findById(tenantId)
                .map(Empresa::getEmailContacto)
                .filter(correo -> correo != null && !correo.isBlank());
    }
}
