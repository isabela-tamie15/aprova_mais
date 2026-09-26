package tcc.ges.aprovamais.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tcc.ges.aprovamais.service.ContaInicialService;


@Component
@RequiredArgsConstructor
public class ContaInicialRunner implements ApplicationRunner {

    private static final String NOME_PADRAO = "Secretaria Acadêmica";

    private final ContaInicialService contaInicialService;

    @Value("${app.secretaria-inicial.email:}")
    private String email;

    @Value("${app.secretaria-inicial.senha:}")
    private String senha;

    @Value("${app.secretaria-inicial.nome:}")
    private String nome;

    @Override
    public void run(ApplicationArguments args) {
        String nomeSecretaria = (nome == null || nome.isBlank()) ? NOME_PADRAO : nome;
        contaInicialService.criarSecretariaInicialSeNecessario(email, senha, nomeSecretaria);
    }
}
