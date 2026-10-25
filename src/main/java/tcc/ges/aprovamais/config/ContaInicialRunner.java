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


// Esse component roda uma vez quando a aplicação sobe, pra criar o administrador e a secretaria se ainda não existirem
@Component
@RequiredArgsConstructor
public class ContaInicialRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContaInicialRunner.class);

    private static final String NOME_PADRAO_ADMINISTRADOR = "Administrador";
    private static final String NOME_PADRAO_SECRETARIA = "Secretaria Acadêmica";

    private final ContaInicialService contaInicialService;

    //Todos vêm do application.properties e o : no final significa que se a propriedade não existir, o valor padrão é vazio.
    @Value("${app.admin-inicial.email:}")
    private String emailAdministrador;

    @Value("${app.admin-inicial.senha:}")
    private String senhaAdministrador;

    @Value("${app.admin-inicial.nome:}")
    private String nomeAdministrador;

    @Value("${app.secretaria-inicial.email:}")
    private String emailSecretaria;

    @Value("${app.secretaria-inicial.senha:}")
    private String senhaSecretaria;

    @Value("${app.secretaria-inicial.nome:}")
    private String nomeSecretaria;

    // Esse é o método que o spring chama automaticamente quando a aplicação termina de subir
    @Override
    public void run(ApplicationArguments args) {
        /*
           Cada conta fica num try separado e se uma falhar no banco, a outra
           ainda é criada e a aplicação sobe, só com o erro no log
        */
        try {
            contaInicialService.criarAdministradorInicialSeNecessario(emailAdministrador, senhaAdministrador,
                    nomeOuPadrao(nomeAdministrador, NOME_PADRAO_ADMINISTRADOR));
        } catch (DataAccessException e) {
            log.error("[CONTA INICIAL] Não foi possível criar o administrador inicial. Num banco criado antes "
                    + "do perfil ADMINISTRADOR, a restrição usuarios_perfil_check precisa ser atualizada. Erro: {}",
                    e.getMostSpecificCause().getMessage());
        }

        try {
            contaInicialService.criarSecretariaInicialSeNecessario(emailSecretaria, senhaSecretaria,
                    nomeOuPadrao(nomeSecretaria, NOME_PADRAO_SECRETARIA));
        } catch (DataAccessException e) {
            log.error("[CONTA INICIAL] Não foi possível criar a secretaria inicial. Erro: {}",
                    e.getMostSpecificCause().getMessage());
        }
    }

    // Se o nome não foi configurado, usa o padrão
    private String nomeOuPadrao(String nome, String padrao) {
        return (nome == null || nome.isBlank()) ? padrao : nome;
    }
}
