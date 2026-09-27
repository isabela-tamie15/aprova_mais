package tcc.ges.aprovamais.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tcc.ges.aprovamais.service.ContaInicialService;


// Esse component roda uma vez quando a aplicação sobe, pra criar a secretaria inicial se ainda não existir
@Component
@RequiredArgsConstructor
public class ContaInicialRunner implements ApplicationRunner {

    private static final String NOME_PADRAO = "Secretaria Acadêmica";

    private final ContaInicialService contaInicialService;

    /*
       Os três vêm do application.yml e o : no final significa que se a
       propriedade não existir, o valor padrão é vazio. Sem essa configuração
       a aplicação nem subiria se as propriedades não estivessem definidas
    */
    @Value("${app.secretaria-inicial.email:}")
    private String email;

    @Value("${app.secretaria-inicial.senha:}")
    private String senha;

    @Value("${app.secretaria-inicial.nome:}")
    private String nome;

    // Esse é o método que o Spring chama automaticamente quando a aplicação termina de subir
    @Override
    public void run(ApplicationArguments args) {

        // Se o nome não foi configurado, usa o padrão
        String nomeSecretaria = (nome == null || nome.isBlank()) ? NOME_PADRAO : nome;
        contaInicialService.criarSecretariaInicialSeNecessario(email, senha, nomeSecretaria);
    }
}
