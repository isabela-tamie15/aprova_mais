package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import tcc.ges.aprovamais.entity.enums.ResultadoCriterio;
import tcc.ges.aprovamais.security.AesEncryptor;

/*
   Resposta do orientador a um critério, ligada à versão corrigida. Como a versão, a resposta não
   muda depois de gravada (updatable = false). A descrição do critério é copiada no momento da
   correção, então, se o coordenador alterar o critério depois, a correção antiga continua igual
*/
@Data
@Entity
@Table(name = "respostas_criterio_checklist",
        uniqueConstraints = @UniqueConstraint(name = "uk_resposta_versao_criterio",
                columnNames = {"versao_id", "criterio_id"}))
public class RespostaCriterioChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "versao_id", nullable = false, updatable = false)
    @ToString.Exclude
    private EntregaVersao versao;

    @ManyToOne
    @JoinColumn(name = "criterio_id", nullable = false, updatable = false)
    @ToString.Exclude
    private CriterioChecklist criterio;

    // Cópia da descrição do critério no dia da correção
    @Column(name = "descricao_criterio", nullable = false, updatable = false, length = 200)
    private String descricaoCriterio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ResultadoCriterio resultado;

    // Observação opcional do orientador. É texto livre sobre o aluno, então é cifrada como o feedback
    @Convert(converter = AesEncryptor.class)
    @Column(columnDefinition = "TEXT", updatable = false)
    private String observacao;
}
