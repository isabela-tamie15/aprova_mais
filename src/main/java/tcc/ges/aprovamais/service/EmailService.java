package tcc.ges.aprovamais.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// Esse service é quem envia os e-mails transacionais do sistema, usando a API do Resend
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    // Chave de API do Resend, configurada no application.yml
    @Value("${resend.api-key}")
    private String apiKey;

    /*
       Remetente padrão do Resend. Como o domínio da UMC não tá verificado
       na conta, o Resend só permite enviar a partir do próprio domínio de
       teste deles. Em produção isso vira algo tipo no-reply@aprova.com.br
    */
    private static final String REMETENTE = "Aprova+ <onboarding@resend.dev>";

    // Esse é o método que envia o e-mail de convite pra quem foi convidado
    @Async
    public void enviarConvite(String destinatario, String nomeDestinatario, String linkConvite) {
        try {
            /*
               Cria um cliente novo do Resend a cada chamada. Como o método
               roda em thread separada por causa do @Async, não dá pra manter
               uma instância única compartilhada com segurança
            */
            Resend resend = new Resend(apiKey);

            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(REMETENTE)
                    .to(destinatario)
                    .subject("Convite de acesso - Aprova+")
                    .html(montarCorpoConvite(nomeDestinatario, linkConvite))
                    .build();

            resend.emails().send(email);

            log.info("[EMAIL] Convite enviado para: {}", destinatario);

        } catch (ResendException e) {
            /*
               Se o envio falhar, o erro fica só no log. Faz sentido porque
               o @Async já desacoplou do fluxo principal, não tem como
               avisar quem chamou, e o convite continua válido no banco
               mesmo que o e-mail não tenha ido
            */
            log.error("[EMAIL] Erro ao enviar convite para {}: {}", destinatario, e.getMessage(), e);
        }
    }

    // Esse é o método que envia o e-mail de recuperação de senha
    @Async
    public void enviarRecuperacaoSenha(String destinatario, String nomeDestinatario, String linkRecuperacao) {
        try {
            Resend resend = new Resend(apiKey);

            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(REMETENTE)
                    .to(destinatario)
                    .subject("Recuperação de senha - Aprova+")
                    .html(montarCorpoRecuperacaoSenha(nomeDestinatario, linkRecuperacao))
                    .build();

            resend.emails().send(email);

            log.info("[EMAIL] Recuperação de senha enviada para: {}", destinatario);

        } catch (ResendException e) {
            log.error("[EMAIL] Erro ao enviar recuperação de senha para {}: {}", destinatario, e.getMessage(), e);
        }
    }

    /*
       Monta o HTML do e-mail de convite. O estilo é todo inline porque
       clientes de e-mail tipo Gmail e Outlook ignoram CSS externo, então
       a única forma de garantir a aparência é colocar tudo direto nas tags
    */
    private String montarCorpoConvite(String nome, String link) {
        return """
                <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #2B4ECC;">Bem-vindo ao Aprova+</h2>
                    <p>Olá, <strong>%s</strong>!</p>
                    <p>Você foi convidado a acessar o portal de gestão de estágios da UMC.</p>
                    <p>Clique no botão abaixo para concluir seu cadastro e aceitar os termos de uso:</p>
                    <a href="%s"
                       style="display: inline-block; background: #2B4ECC; color: white;
                              padding: 12px 24px; border-radius: 8px; text-decoration: none;
                              font-weight: 600; margin: 16px 0;">
                        Concluir cadastro
                    </a>
                    <p style="color: #6B7280; font-size: 0.85rem;">
                        Este link expira em 24 horas. Se você não esperava este convite, ignore este email.
                    </p>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 24px 0;">
                    <p style="color: #9CA3AF; font-size: 0.8rem;">Aprova+ - UMC Gestão de Estágios</p>
                </div>
                """.formatted(nome, link);
    }

    // Mesma ideia do convite, mas com texto e prazo de expiração diferentes
    private String montarCorpoRecuperacaoSenha(String nome, String link) {
        return """
                <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #2B4ECC;">Recuperação de senha</h2>
                    <p>Olá, <strong>%s</strong>!</p>
                    <p>Recebemos uma solicitação para redefinir a senha da sua conta no Aprova+.</p>
                    <p>Clique no botão abaixo para criar uma nova senha:</p>
                    <a href="%s"
                       style="display: inline-block; background: #2B4ECC; color: white;
                              padding: 12px 24px; border-radius: 8px; text-decoration: none;
                              font-weight: 600; margin: 16px 0;">
                        Redefinir senha
                    </a>
                    <p style="color: #6B7280; font-size: 0.85rem;">
                        Este link expira em 30 minutos. Se você não solicitou a recuperação, ignore este email.
                    </p>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 24px 0;">
                    <p style="color: #9CA3AF; font-size: 0.8rem;">Aprova+ - UMC Gestão de Estágios</p>
                </div>
                """.formatted(nome, link);
    }
}