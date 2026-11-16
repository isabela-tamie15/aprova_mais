package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tcc.ges.aprovamais.entity.enums.ParametroSistema;

import java.time.OffsetDateTime;

// Valor de um parâmetro alterado pelo administrador. Parâmetro sem linha aqui usa o valor padrão
@Getter
@Setter
@Entity
@Table(name = "configuracoes_sistema")
public class ConfiguracaoSistema {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ParametroSistema parametro;

    @Column(nullable = false)
    private Integer valor;

    @ManyToOne
    @JoinColumn(name = "alterado_por_id")
    private Usuario alteradoPor;

    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;
}
