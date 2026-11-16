package tcc.ges.aprovamais.external;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import tcc.ges.aprovamais.entity.enums.ParametroSistema;
import tcc.ges.aprovamais.service.ParametrosVigentes;

// E-mails transacionais do sistema, enviados pela API do Resend
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final int TENTATIVAS_DE_ENVIO = 3;

    // Chave de API do Resend, configurada no application.yml
    @Value("${resend.api-key}")
    private String apiKey;

    // Remetente dos e-mails, da variável EMAIL_REMETENTE
    @Value("${app.email.remetente}")
    private String remetente;

    // Envia o e-mail de convite
    @Async
    public void enviarConvite(String destinatario, String nomeDestinatario, String linkConvite) {
        CreateEmailOptions email = CreateEmailOptions.builder()
                .from(remetente)
                .to(destinatario)
                .subject("Convite de acesso - Aprova+")
                .html(montarCorpoConvite(nomeDestinatario, linkConvite))
                .build();
        enviarComTentativas(email, destinatario, "Convite");
    }

    // Envia o e-mail de recuperação de senha
    @Async
    public void enviarRecuperacaoSenha(String destinatario, String nomeDestinatario, String linkRecuperacao) {
        CreateEmailOptions email = CreateEmailOptions.builder()
                .from(remetente)
                .to(destinatario)
                .subject("Recuperação de senha - Aprova+")
                .html(montarCorpoRecuperacaoSenha(nomeDestinatario, linkRecuperacao))
                .build();
        enviarComTentativas(email, destinatario, "Recuperação de senha");
    }

    /*
       Avisa o aluno de que a entrega foi aprovada ou devolvida. O e-mail traz só o resultado e o link,
       e o feedback fica no sistema, que exige login
    */
    @Async
    public void enviarResultadoEntrega(String destinatario, String nomeDestinatario, String nomeTarefa,
                                       boolean aprovada, String linkTrilha) {
        CreateEmailOptions email = CreateEmailOptions.builder()
                .from(remetente)
                .to(destinatario)
                .subject((aprovada ? "Entrega aprovada" : "Entrega devolvida para ajustes") + " - Aprova+")
                .html(montarCorpoResultadoEntrega(nomeDestinatario, nomeTarefa, aprovada, linkTrilha))
                .build();
        enviarComTentativas(email, destinatario, "Resultado da entrega");
    }

    /*
       Avisa o aluno de que o orientador aprovou ou rejeitou o estágio. O e-mail traz só a decisão e o
       link, e a justificativa fica no sistema, que exige login
    */
    @Async
    public void enviarResultadoValidacaoEstagio(String destinatario, String nomeDestinatario,
                                                boolean aprovado, String linkEstagio) {
        CreateEmailOptions email = CreateEmailOptions.builder()
                .from(remetente)
                .to(destinatario)
                .subject((aprovado ? "Estágio aprovado" : "Estágio rejeitado para ajustes") + " - Aprova+")
                .html(montarCorpoResultadoValidacao(nomeDestinatario, aprovado, linkEstagio))
                .build();
        enviarComTentativas(email, destinatario, "Resultado da validação do estágio");
    }

    // Avisa o aluno de que o estágio foi concluído ou encerrado. A observação de quem encerrou fica no sistema
    @Async
    public void enviarFinalizacaoEstagio(String destinatario, String nomeDestinatario,
                                         boolean concluido, String linkEstagio) {
        CreateEmailOptions email = CreateEmailOptions.builder()
                .from(remetente)
                .to(destinatario)
                .subject((concluido ? "Estágio concluído" : "Estágio encerrado") + " - Aprova+")
                .html(montarCorpoFinalizacaoEstagio(nomeDestinatario, concluido, linkEstagio))
                .build();
        enviarComTentativas(email, destinatario, "Finalização do estágio");
    }

    /*
       Aviso sobre o andamento de uma solicitação do aluno. O texto vem pronto de quem chama e não traz
       o motivo nem a justificativa, que ficam no sistema
    */
    @Async
    public void enviarAvisoSolicitacao(String destinatario, String nomeDestinatario, String assunto,
                                       String texto, String link) {
        CreateEmailOptions email = CreateEmailOptions.builder()
                .from(remetente)
                .to(destinatario)
                .subject(assunto + " - Aprova+")
                .html(montarCorpoAvisoSolicitacao(nomeDestinatario, assunto, texto, link))
                .build();
        enviarComTentativas(email, destinatario, "Aviso de solicitação");
    }


    /*
       Envia pelo Resend com até 3 tentativas, esperando 2 e depois 4 segundos entre elas. Se todas
       falharem, o erro fica no log e a operação que pediu o e-mail não é desfeita. O cliente é criado
       a cada envio, porque o método roda em outra thread por causa do @Async
    */
    private void enviarComTentativas(CreateEmailOptions email, String destinatario, String descricao) {
        Resend resend = new Resend(apiKey);
        for (int tentativa = 1; tentativa <= TENTATIVAS_DE_ENVIO; tentativa++) {
            try {
                resend.emails().send(email);
                log.info("[EMAIL] Enviado: {} para {}", descricao, destinatario);
                return;
            } catch (ResendException | RuntimeException excecao) {
                if (tentativa == TENTATIVAS_DE_ENVIO) {
                    log.error("[EMAIL] Envio falhou depois de {} tentativas ({} para {}): {}",
                            TENTATIVAS_DE_ENVIO, descricao, destinatario, excecao.getMessage(), excecao);
                    return;
                }
                log.warn("[EMAIL] Tentativa {} de {} falhou ({} para {}): {}", tentativa, TENTATIVAS_DE_ENVIO,
                        descricao, destinatario, excecao.getMessage());
                esperar(tentativa * 2000L);
            }
        }
    }

    private void esperar(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException interrupcao) {
            Thread.currentThread().interrupt();
        }
    }

    // Monta o HTML do e-mail de convite. O estilo fica inline porque Gmail e Outlook ignoram CSS externo
    private String montarCorpoConvite(String nomeDoDestinatario, String link) {
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
                        Este link expira em %s horas. Se você não esperava este convite, ignore este email.
                    </p>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 24px 0;">
                    <p style="color: #9CA3AF; font-size: 0.8rem;">Aprova+ - UMC Gestão de Estágios</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(nomeDoDestinatario), link,
                ParametrosVigentes.valor(ParametroSistema.HORAS_VALIDADE_CONVITE));
    }

    // O nome da tarefa vem do cadastro do coordenador, então passa pelo escape de HTML
    private String montarCorpoResultadoEntrega(String nomeDoDestinatario, String tarefa, boolean aprovada, String link) {
        String textoDoResultado = aprovada
                ? "foi <strong>aprovada</strong> pelo seu orientador."
                : "foi <strong>devolvida para ajustes</strong>. Confira o feedback do orientador e envie uma nova versão.";
        return """
                <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #2B4ECC;">Sua entrega foi avaliada</h2>
                    <p>Olá, <strong>%s</strong>!</p>
                    <p>A entrega da tarefa <strong>%s</strong> %s</p>
                    <a href="%s"
                       style="display: inline-block; background: #2B4ECC; color: white;
                              padding: 12px 24px; border-radius: 8px; text-decoration: none;
                              font-weight: 600; margin: 16px 0;">
                        Ver minha trilha
                    </a>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 24px 0;">
                    <p style="color: #9CA3AF; font-size: 0.8rem;">Aprova+ - UMC Gestão de Estágios</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(nomeDoDestinatario), HtmlUtils.htmlEscape(tarefa), textoDoResultado, link);
    }

    // Mesmo modelo do resultado da entrega, com o texto da decisão do orientador sobre o estágio
    private String montarCorpoResultadoValidacao(String nomeDoDestinatario, boolean aprovado, String link) {
        String textoDoResultado = aprovado
                ? "O seu estágio foi <strong>aprovado</strong> pelo orientador. A partir de agora a carga horária é contada e as tarefas da trilha ficam liberadas."
                : "O seu estágio foi <strong>rejeitado para ajustes</strong>. Confira a justificativa do orientador no sistema, corrija os dados e envie novamente.";
        return """
                <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #2B4ECC;">Seu estágio foi analisado</h2>
                    <p>Olá, <strong>%s</strong>!</p>
                    <p>%s</p>
                    <a href="%s"
                       style="display: inline-block; background: #2B4ECC; color: white;
                              padding: 12px 24px; border-radius: 8px; text-decoration: none;
                              font-weight: 600; margin: 16px 0;">
                        Ver meu estágio
                    </a>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 24px 0;">
                    <p style="color: #9CA3AF; font-size: 0.8rem;">Aprova+ - UMC Gestão de Estágios</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(nomeDoDestinatario), textoDoResultado, link);
    }

    private String montarCorpoFinalizacaoEstagio(String nomeDoDestinatario, boolean concluido, String link) {
        String textoDoResultado = concluido
                ? "O seu estágio foi <strong>concluído</strong>. A carga horária e as tarefas da trilha foram cumpridas e aprovadas pelo orientador."
                : "O seu estágio foi <strong>encerrado</strong>. Confira o motivo e a observação no sistema. Se for o caso, você pode cadastrar um novo estágio.";
        return """
                <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #2B4ECC;">Atualização do seu estágio</h2>
                    <p>Olá, <strong>%s</strong>!</p>
                    <p>%s</p>
                    <a href="%s"
                       style="display: inline-block; background: #2B4ECC; color: white;
                              padding: 12px 24px; border-radius: 8px; text-decoration: none;
                              font-weight: 600; margin: 16px 0;">
                        Ver meu estágio
                    </a>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 24px 0;">
                    <p style="color: #9CA3AF; font-size: 0.8rem;">Aprova+ - UMC Gestão de Estágios</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(nomeDoDestinatario), textoDoResultado, link);
    }

    private String montarCorpoAvisoSolicitacao(String nomeDoDestinatario, String assunto, String texto, String link) {
        return """
                <div style="font-family: Inter, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #2B4ECC;">%s</h2>
                    <p>Olá, <strong>%s</strong>!</p>
                    <p>%s</p>
                    <a href="%s"
                       style="display: inline-block; background: #2B4ECC; color: white;
                              padding: 12px 24px; border-radius: 8px; text-decoration: none;
                              font-weight: 600; margin: 16px 0;">
                        Abrir no Aprova+
                    </a>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 24px 0;">
                    <p style="color: #9CA3AF; font-size: 0.8rem;">Aprova+ - UMC Gestão de Estágios</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(assunto), HtmlUtils.htmlEscape(nomeDoDestinatario),
                HtmlUtils.htmlEscape(texto), link);
    }

    // Mesma ideia do convite, mas com texto e prazo de expiração diferentes
    private String montarCorpoRecuperacaoSenha(String nomeDoDestinatario, String link) {
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
                """.formatted(HtmlUtils.htmlEscape(nomeDoDestinatario), link);
    }
}