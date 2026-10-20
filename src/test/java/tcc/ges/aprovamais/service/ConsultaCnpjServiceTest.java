package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tcc.ges.aprovamais.dto.ConsultaCnpjResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Testa a consulta com um servidor falso no lugar da BrasilAPI, então roda sem internet
class ConsultaCnpjServiceTest {

    private static final String URL = "https://brasilapi.teste/api/cnpj/v1";

    private MockRestServiceServer servidor;
    private ConsultaCnpjService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        service = new ConsultaCnpjService(builder, URL);
    }

    @Test
    @DisplayName("CNPJ encontrado traz razão social, nome fantasia e situação")
    void deveDevolverDadosQuandoEncontrado() {
        servidor.expect(requestTo(URL + "/11222333000181"))
                .andRespond(withSuccess("""
                        {
                          "cnpj": "11222333000181",
                          "razao_social": "EMPRESA TESTE LTDA",
                          "nome_fantasia": "TESTE",
                          "situacao_cadastral": 2,
                          "descricao_situacao_cadastral": "ATIVA",
                          "uf": "SP"
                        }
                        """, MediaType.APPLICATION_JSON));

        ConsultaCnpjResponse resposta = service.consultar("11.222.333/0001-81");

        assertEquals(ConsultaCnpjResponse.Status.ENCONTRADO, resposta.status());
        assertEquals("EMPRESA TESTE LTDA", resposta.razaoSocial());
        assertEquals("TESTE", resposta.nomeFantasia());
        assertEquals("ATIVA", resposta.situacao());
        servidor.verify();
    }

    @Test
    @DisplayName("404 da BrasilAPI vira NAO_ENCONTRADO")
    void deveDevolverNaoEncontradoNo404() {
        servidor.expect(requestTo(URL + "/11222333000181")).andRespond(withResourceNotFound());

        assertEquals(ConsultaCnpjResponse.Status.NAO_ENCONTRADO, service.consultar("11222333000181").status());
        servidor.verify();
    }

    @Test
    @DisplayName("Erro no servidor da BrasilAPI vira INDISPONIVEL, sem derrubar o cadastro")
    void deveDevolverIndisponivelNoErro() {
        servidor.expect(requestTo(URL + "/11222333000181")).andRespond(withServerError());

        ConsultaCnpjResponse resposta = service.consultar("11222333000181");

        assertEquals(ConsultaCnpjResponse.Status.INDISPONIVEL, resposta.status());
        assertNull(resposta.situacao());
        servidor.verify();
    }

    @Test
    @DisplayName("CNPJ com dígito errado nem chega a ser consultado")
    void naoDeveConsultarCnpjInvalido() {
        assertEquals(ConsultaCnpjResponse.Status.NAO_ENCONTRADO, service.consultar("11222333000180").status());
        servidor.verify();
    }
}
