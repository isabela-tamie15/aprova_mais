package tcc.ges.aprovamais.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tcc.ges.aprovamais.entity.enums.ParametroSistema;
import tcc.ges.aprovamais.service.ParametrosVigentes;

// Monta o cookie do JWT, usado no login, no logout e na desativação do 2FA
@Component
public class CookieSessao {

    // Diz se o cookie vai com o atributo Secure (só trafega em HTTPS).
    @Value("${app.cookie.secure:false}")
    private boolean cookieSeguro;

    public void gravar(HttpServletResponse respostaHttp, String token) {
        // Mesmo prazo do token, que o administrador pode ajustar
        adicionar(respostaHttp, token, ParametrosVigentes.valor(ParametroSistema.HORAS_VALIDADE_SESSAO) * 3600);
    }

    // Valor vazio com Max-Age 0 pede ao navegador para apagar o cookie
    public void apagar(HttpServletResponse respostaHttp) {
        adicionar(respostaHttp, "", 0);
    }

    /*
       A classe Cookie do Java não tem o atributo SameSite, por isso o cabeçalho é montado aqui.
       O HttpOnly impede a leitura por JavaScript e o SameSite=Strict impede o envio a partir de outros sites
    */
    private void adicionar(HttpServletResponse respostaHttp, String valorDoCookie, int segundosDeValidade) {
        String secure = cookieSeguro ? "; Secure" : "";
        String cookie = String.format(
                "jwt=%s; HttpOnly; Path=/; Max-Age=%d; SameSite=Strict%s",
                valorDoCookie, segundosDeValidade, secure
        );
        respostaHttp.addHeader("Set-Cookie", cookie);
    }
}
