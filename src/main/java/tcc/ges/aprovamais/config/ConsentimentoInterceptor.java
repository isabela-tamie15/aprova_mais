package tcc.ges.aprovamais.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tcc.ges.aprovamais.service.ConsentimentoService;

import java.io.IOException;

/*
   LGPD: quem ainda não aceitou a versão atual dos termos é levado à página de
   consentimento antes de abrir qualquer tela do sistema. Vale quando os termos
   mudam de versão e para as contas criadas pela inicialização (administrador e
   secretaria inicial), que não passam pelo primeiro acesso
*/
@Component
@RequiredArgsConstructor
public class ConsentimentoInterceptor implements HandlerInterceptor {

    private final ConsentimentoService consentimentoService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest requisicao, @NonNull HttpServletResponse resposta,
                             @NonNull Object handler) throws IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()
                || autenticacao instanceof AnonymousAuthenticationToken) {
            return true;
        }
        if (consentimentoService.precisaConsentir(autenticacao.getName())) {
            resposta.sendRedirect("/consentimento");
            return false;
        }
        return true;
    }
}
