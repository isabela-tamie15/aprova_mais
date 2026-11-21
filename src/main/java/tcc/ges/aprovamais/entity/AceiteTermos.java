package tcc.ges.aprovamais.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.OffsetDateTime;

/*
   Cada aceite dos Termos de Uso e da Política de Privacidade vira uma linha que nunca é alterada.
   O usuário guarda só o último aceite; aqui fica o histórico de todas as versões aceitas, que não
   é apagado pela rotina de retenção da auditoria
*/
@Data
@NoArgsConstructor
@Entity
@Table(name = "aceites_termos")
public class AceiteTermos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    @ToString.Exclude
    private Usuario usuario;

    @Column(nullable = false, updatable = false, length = 10)
    private String versao;

    @Column(name = "aceito_em", nullable = false, updatable = false)
    private OffsetDateTime aceitoEm;

    public AceiteTermos(Usuario usuario, String versao, OffsetDateTime aceitoEm) {
        this.usuario = usuario;
        this.versao = versao;
        this.aceitoEm = aceitoEm;
    }
}
