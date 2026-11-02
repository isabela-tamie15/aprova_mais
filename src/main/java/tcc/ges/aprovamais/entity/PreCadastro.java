package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;

import java.time.OffsetDateTime;

/*
   Pessoa autorizada pelo coordenador a receber convite. Sem estar
   aqui, a secretaria não consegue convidar aluno nem orientador. Para aluno, a
   turma é obrigatória, porque é nela que a matrícula é criada no aceite.
   O identificador é o RGM do aluno ou a matrícula institucional do orientador
*/
@Data
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "pre_cadastros")
public class PreCadastro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PerfilDestino perfil;

    @Column(name = "identificador_institucional", nullable = false, unique = true, length = 50)
    private String identificadorInstitucional;

    // Só para aluno; orientador fica sem turma, porque o vínculo dele é feito pelo coordenador (RF13)
    @ManyToOne
    @JoinColumn(name = "turma_id")
    @ToString.Exclude
    private Turma turma;

    // Quem cadastrou. É por ele que a lista fica restrita a cada coordenador (RN21)
    @ManyToOne
    @JoinColumn(name = "coordenador_id", nullable = false)
    @ToString.Exclude
    private Coordenador coordenador;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;
}
