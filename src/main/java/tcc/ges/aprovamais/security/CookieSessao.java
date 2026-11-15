package tcc.ges.aprovamais.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Monta o cookie do JWT, usado no login, no logout e na desativação do 2FA
@Component
public class CookieSessao {

    // Mesmo prazo do token: 8 horas
    public static final int SEGUNDOS_DE_VALIDADE = 28800;

    // Diz se o cookie vai com o atributo Secure (só trafega em HTTPS).
    @Value("${app.cookie.secure:false}")
    private boolean cookieSeguro;

    public void gravar(HttpServletResponse respostaHttp, String token) {
        adicionar(respostaHttp, token, SEGUNDOS_DE_VALIDADE);
    }

    // Valor vazio com Max-Age 0 pede ao navegador para apagar o cookie
    public void apagar(HttpServletResponse respostaHttp) {
        adicionar(respostaHttp, "", 0);
    }

    /*
       Monta na mão em vez de usar a classe Cookie do Java porque ela não
       expõe o atributo SameSite. O HttpOnly impede leitura por JavaScript,
       o Path=/ faz valer para toda aplicação e o SameSite=Strict evita
       envio em requisições de outros sites
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
