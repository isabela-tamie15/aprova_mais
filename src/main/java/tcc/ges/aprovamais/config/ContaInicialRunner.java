package tcc.ges.aprovamais.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import tcc.ges.aprovamais.service.ContaInicialService;


// Esse component roda uma vez quando a aplicação sobe, para criar o administrador se ainda não existir
@Component
@RequiredArgsConstructor
public class ContaInicialRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContaInicialRunner.class);

    private static final String NOME_PADRAO_ADMINISTRADOR = "Administrador";

    private final ContaInicialService contaInicialService;

    // Os valores vêm do application.properties e ficam vazios quando a propriedade não existe
    @Value("${app.admin-inicial.email:}")
    private String emailAdministrador;

    @Value("${app.admin-inicial.senha:}")
    private String senhaAdministrador;

    @Value("${app.admin-inicial.nome:}")
    private String nomeAdministrador;

    // Chamado pelo Spring quando a aplicação termina de subir
    @Override
    public void run(ApplicationArguments args) {
        // Se a criação falhar no banco, a aplicação sobe do mesmo jeito, só com o erro no log
        try {
            contaInicialService.criarAdministradorInicialSeNecessario(emailAdministrador, senhaAdministrador,
                    nomeOuPadrao(nomeAdministrador, NOME_PADRAO_ADMINISTRADOR));
        } catch (DataAccessException excecao) {
            log.error("[CONTA INICIAL] Não foi possível criar o administrador inicial. Num banco criado antes "
                            + "do perfil ADMINISTRADOR, a restrição usuarios_perfil_check precisa ser atualizada. Erro: {}",
                    excecao.getMostSpecificCause().getMessage());
        }
    }

    // Se o nome não foi configurado, usa o padrão
    private String nomeOuPadrao(String nome, String padrao) {
        return (nome == null || nome.isBlank()) ? padrao : nome;
    }
}