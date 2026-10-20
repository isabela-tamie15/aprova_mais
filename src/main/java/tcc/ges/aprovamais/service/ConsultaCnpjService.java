package tcc.ges.aprovamais.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tcc.ges.aprovamais.dto.ConsultaCnpjResponse;

import java.time.Duration;

/*
   Esse service consulta o CNPJ na BrasilAPI (https://brasilapi.com.br), que
   repassa os dados públicos da Receita Federal.
   A consulta nunca trava o cadastro por problema da API, se ela demorar mais
   que o tempo limite ou cair, o resultado é INDISPONIVEL e o orientador
   confere o CNPJ manualmente. Só o CNPJ que a Receita diz não existir é recusado
*/
@Service
public class ConsultaCnpjService {

    private static final Logger log = LoggerFactory.getLogger(ConsultaCnpjService.class);

    private final RestClient restClient;

    @Autowired
    public ConsultaCnpjService(
            @Value("${aprova.cnpj.api-url:https://brasilapi.com.br/api/cnpj/v1}") String urlApi,
            @Value("${aprova.cnpj.timeout-conexao-ms:3000}") long timeoutConexaoMs,
            @Value("${aprova.cnpj.timeout-leitura-ms:5000}") long timeoutLeituraMs) {
        this(RestClient.builder().requestFactory(criarRequestFactory(timeoutConexaoMs, timeoutLeituraMs)), urlApi);
    }

    // Construtor usado nos testes, que passam um builder ligado a um servidor falso
    ConsultaCnpjService(RestClient.Builder builder, String urlApi) {
        this.restClient = builder.baseUrl(urlApi).build();
    }

    private static SimpleClientHttpRequestFactory criarRequestFactory(long conexaoMs, long leituraMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(conexaoMs));
        factory.setReadTimeout(Duration.ofMillis(leituraMs));
        return factory;
    }

    // Recebe o CNPJ já validado pelos dígitos. Só manda pra API o que passou nessa conta
    public ConsultaCnpjResponse consultar(String cnpj) {
        String valor = ValidadorCnpj.normalizar(cnpj);
        if (!ValidadorCnpj.valido(valor)) {
            return ConsultaCnpjResponse.naoEncontrado();
        }

        try {
            RespostaBrasilApi resposta = restClient.get()
                    .uri("/{cnpj}", valor)
                    .retrieve()
                    .body(RespostaBrasilApi.class);

            if (resposta == null) {
                log.warn("[CNPJ] BrasilAPI respondeu sem corpo para o CNPJ {}", mascarar(valor));
                return ConsultaCnpjResponse.indisponivel();
            }
            return ConsultaCnpjResponse.encontrado(
                    resposta.razaoSocial(), resposta.nomeFantasia(), resposta.situacao());

        } catch (HttpClientErrorException.NotFound e) {
            log.info("[CNPJ] CNPJ {} não encontrado na Receita", mascarar(valor));
            return ConsultaCnpjResponse.naoEncontrado();
        } catch (RestClientException e) {
            // Tempo esgotado, API fora do ar, limite de requisições ou resposta inesperada
            log.warn("[CNPJ] BrasilAPI indisponível ({}), segue sem verificação", e.getClass().getSimpleName());
            return ConsultaCnpjResponse.indisponivel();
        }
    }

    // Nos logs aparece só o começo do CNPJ, o suficiente pra investigar sem expor o número inteiro
    private static String mascarar(String cnpj) {
        return cnpj.substring(0, 8) + "******";
    }

    // Só os campos que o Aprova+ usa. O resto da resposta é ignorado
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespostaBrasilApi(
            @JsonProperty("razao_social") String razaoSocial,
            @JsonProperty("nome_fantasia") String nomeFantasia,
            @JsonProperty("descricao_situacao_cadastral") String situacao) {
    }
}
