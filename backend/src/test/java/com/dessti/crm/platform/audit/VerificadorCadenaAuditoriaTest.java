package com.dessti.crm.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Pruebas unitarias de la verificacion de integridad de la cadena de auditoria
 * (Req 10.13). Usan un repositorio simulado (Mockito) para no depender de una
 * base de datos: construyen una cadena valida con la misma
 * {@link CalculadoraHashCadena} de produccion y luego alteran un registro para
 * comprobar que la ruptura se detecta y se reporta su id.
 */
class VerificadorCadenaAuditoriaTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /**
     * Crea un {@link RegistroAuditoria} con id asignado por reflexion (el id lo
     * asigna normalmente la BD) y su hash encadenado a partir del hash previo.
     */
    private RegistroAuditoria registro(long id, String actor, String hashPrevio) {
        Instant ts = Instant.parse("2024-01-15T10:30:00Z");
        String hashActual = CalculadoraHashCadena.calcular(
                TENANT, actor, "crear", "cliente", "detalle", null, null, "trace", ts, hashPrevio);
        RegistroAuditoria r = new RegistroAuditoria(
                TENANT, actor, "crear", "cliente", "detalle", null, null, "trace", ts, hashPrevio, hashActual);
        asignarId(r, id);
        return r;
    }

    private RegistroAuditoria registroConHashCorrupto(long id, String actor, String hashPrevio) {
        Instant ts = Instant.parse("2024-01-15T10:30:00Z");
        RegistroAuditoria r = new RegistroAuditoria(
                TENANT, actor, "crear", "cliente", "detalle", null, null, "trace", ts,
                hashPrevio, "f".repeat(64)); // hash_actual manipulado
        asignarId(r, id);
        return r;
    }

    private static void asignarId(RegistroAuditoria r, long id) {
        try {
            Field f = RegistroAuditoria.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(r, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private RegistroAuditoriaRepository repositorioCon(List<RegistroAuditoria> registros) {
        RegistroAuditoriaRepository repo = mock(RegistroAuditoriaRepository.class);
        // Primer lote: todos los registros; siguientes lotes: vacio (fin).
        lenient().when(repo.recorrerCadenaPorId(isNull(), isNull(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Pageable p = inv.getArgument(2);
                    if (p.getPageNumber() == 0) {
                        return new PageImpl<>(new ArrayList<>(registros), p, registros.size());
                    }
                    return new PageImpl<RegistroAuditoria>(List.of(), p, registros.size());
                });
        return repo;
    }

    @Test
    @DisplayName("Cadena valida (genesis -> N) se reporta intacta")
    void cadenaValidaEsIntacta() {
        RegistroAuditoria r1 = registro(1, "ana", CalculadoraHashCadena.HASH_SEMILLA);
        RegistroAuditoria r2 = registro(2, "luis", r1.getHashActual());
        RegistroAuditoria r3 = registro(3, "eva", r2.getHashActual());

        VerificadorCadenaAuditoria verificador =
                new VerificadorCadenaAuditoria(repositorioCon(List.of(r1, r2, r3)));

        ResultadoVerificacionCadena resultado = verificador.verificarCadenaCompleta();

        assertThat(resultado.intacta()).isTrue();
        assertThat(resultado.registrosVerificados()).isEqualTo(3);
        assertThat(resultado.idRuptura()).isEmpty();
    }

    @Test
    @DisplayName("Alterar el contenido de un registro rompe su hash_actual y se reporta el id")
    void detectaContenidoAlterado() {
        RegistroAuditoria r1 = registro(1, "ana", CalculadoraHashCadena.HASH_SEMILLA);
        // r2 con hash_actual corrupto (contenido/hash inconsistente)
        RegistroAuditoria r2 = registroConHashCorrupto(2, "luis", r1.getHashActual());

        VerificadorCadenaAuditoria verificador =
                new VerificadorCadenaAuditoria(repositorioCon(List.of(r1, r2)));

        ResultadoVerificacionCadena resultado = verificador.verificarCadenaCompleta();

        assertThat(resultado.intacta()).isFalse();
        assertThat(resultado.idRuptura()).hasValue(2);
        assertThat(resultado.motivo()).isPresent();
    }

    @Test
    @DisplayName("Romper el enlace hash_previo entre registros se detecta en el registro afectado")
    void detectaEnlaceRoto() {
        RegistroAuditoria r1 = registro(1, "ana", CalculadoraHashCadena.HASH_SEMILLA);
        // r2 encadenado a un hash_previo que NO es el hash_actual de r1
        RegistroAuditoria r2 = registro(2, "luis", "0".repeat(63) + "1");

        VerificadorCadenaAuditoria verificador =
                new VerificadorCadenaAuditoria(repositorioCon(List.of(r1, r2)));

        ResultadoVerificacionCadena resultado = verificador.verificarCadenaCompleta();

        assertThat(resultado.intacta()).isFalse();
        assertThat(resultado.idRuptura()).hasValue(2);
    }
}
