# Aprova+ - Sistema de Gestão de Estágios Supervisionados

## Sobre o Projeto

O Aprova+ é um sistema web desenvolvido como Projeto Final de Conclusão de Curso (PFC) para a gestão de estágios supervisionados da Universidade de Mogi das Cruzes (UMC).

O sistema permite que alunos cadastrem seus estágios, orientadores validem as solicitações e acompanhem o progresso, promovendo uma gestão mais eficiente e digitalizada do processo de estágio.

---

## Equipe

| Nome | RGM | Função |
|------|-----|--------|
| Leonardo Valdir Silva | 11232100617 | Desenvolvedor - Feature: Trilha Personalizada |
| Isabela Tamie Shihara | 11231203887 | Desenvolvedora - Feature: Cadastro e Validação de Estágio |

Turma: 8B - Engenharia de Software - UMC
Orientador: Prof. Leonardo Torres

---

## Tecnologias Utilizadas

- Java 25
- Spring Boot 4.1.1
- Spring Security 7
- PostgreSQL 18
- Redis - lista de tokens JWT revogados no logout
- Thymeleaf - renderização server-side
- Alpine.js - interatividade leve no frontend
- Bootstrap 5 - estilização (Alpine.js, Bootstrap e qrcodejs carregados do jsDelivr)
- JWT (jjwt) via cookie HttpOnly com SameSite=Strict
- BCrypt - hash de senhas
- AES-256-GCM - criptografia de dados sensíveis em repouso
- googleauth - autenticação de dois fatores via TOTP (RFC 6238)
- qrcodejs - geração do QR Code do 2FA no navegador
- Resend - API externa de envio de e-mails (convites, recuperação de senha e notificações)
- BrasilAPI - consulta pública do CNPJ da empresa na Receita Federal
- Cloudflare R2 - armazenamento dos arquivos das entregas (opcional; sem ele, os arquivos ficam no disco)
- springdoc-openapi - documentação da API (Swagger UI)
- JUnit 5, Mockito, H2 e JaCoCo - testes automatizados e cobertura
- Docker + Docker Compose
- Prometheus + Grafana (monitoramento)
- GitHub Actions (CI) e Render (implantação)

---

## Funcionalidades Implementadas

### Feature 1 - Cadastro e Validação de Estágio
- Aluno cadastra estágio escolhendo tipo, CNPJ e nome da empresa e data de início
- Só aparecem os tipos de estágio do curso da matrícula do aluno
- CNPJ conferido pelo dígito verificador e consultado na Receita Federal pela BrasilAPI; o nome da empresa é preenchido com a razão social
- Carga total e jornada semanal copiadas do tipo de estágio e congeladas no estágio
- Estágio enviado com status PENDENTE para aprovação
- Orientador visualiza estágios PENDENTE e REJEITADO da sua turma, com a situação do CNPJ na Receita
- Orientador aprova confirmando a data de início do termo de compromisso - status muda para ATIVO
- Orientador rejeita com justificativa - status muda para REJEITADO
- Aluno pode reenviar apenas o estágio REJEITADO; estágio ATIVO só muda por solicitação de troca de modalidade
- Histórico de todas as aprovações e rejeições, com data, orientador e justificativa
- Histórico de estágios por matrícula: só um estágio em aberto por vez, os concluídos e encerrados ficam guardados

### Feature 2 - Cálculo Automático de Progresso com Alertas Visuais
- Carga horária cumprida calculada pelas semanas completas desde o início vezes a jornada semanal, descontadas as semanas de trancamento
- Painel do aluno e do orientador com horas cumpridas e restantes, o cálculo por semanas, as atividades aprovadas e restantes e a data prevista de conclusão
- Aviso "FINALIZAÇÃO DO PRAZO" em amarelo (por padrão, de 30 a 11 dias antes do encerramento das entregas da turma) e em vermelho (a partir de 10 dias), "PRAZO VENCE HOJE" no último dia e "PRAZO VENCEU" depois da data
- Painel do coordenador com indicadores por etapa do estágio e gráficos por turma, com filtros de curso, semestre, turma e orientador

### Feature 3 - Trilha Personalizada de Tarefas
- Coordenador cadastra cursos, turmas, tipos de estágio e a trilha de cada tipo, com tarefas em ordem, descrição e link de material de apoio
- Aluno com estágio ATIVO acessa a trilha do seu tipo de estágio
- Diferentes tipos de estágio possuem trilhas diferentes
- Tarefa que já recebeu entregas não pode ser excluída

### Feature 4 - Painel de Correção Centralizado
- Aluno envia o arquivo de cada tarefa (PDF, DOCX, XLSX, PPTX, JPG, JPEG ou PNG, até 10 MB, conferido pela assinatura do formato)
- Orientador vê num só painel as entregas dos seus alunos, das mais antigas para as mais recentes, com filtros por aluno, tarefa, status e data
- Orientador aprova ou devolve com feedback, e o aluno é avisado por e-mail
- Tela de acompanhamento de cada aluno, com o progresso e as tarefas, onde o orientador conclui ou reprova o estágio

### Feature 5 - Histórico de Versões das Entregas
- Cada envio gera uma nova versão, e as anteriores ficam guardadas com o feedback recebido
- Nova versão só pode ser enviada depois de uma devolução
- Orientador compara a versão atual com a anterior e vê o histórico de correções que já fez

### Feature 6 - Avaliação das Entregas por Checklist de Critérios
- Coordenador define os critérios de avaliação de cada tarefa, em ordem
- Orientador marca cada critério como Suficiente ou Insuficiente, com observação opcional; com algum critério insuficiente, a entrega só pode ser devolvida
- Aluno vê o resultado de cada critério em cada versão
- Critério já usado em uma correção é desativado, e não excluído, para não mudar as avaliações já feitas

### Acesso, Segurança e LGPD
- Acesso somente por convite, sem cadastro aberto, seguindo a hierarquia: Administrador convida coordenadores, Coordenador convida secretarias, Secretaria convida alunos e orientadores
- Lista de pré-cadastro mantida pelo coordenador, com RGM só com números para aluno: a secretaria só convida quem está nela, vê na tela de convites quem ainda aguarda convite e convida pela própria lista, e a matrícula do aluno é criada na turma indicada ao aceitar o convite
- Primeiro acesso pelo link do convite: definição de senha, aceite dos Termos de Uso e ciência da Política de Privacidade; o aluno confirma o RGM do pré-cadastro, e 5 tentativas com RGM errado cancelam o convite
- Autenticação de dois fatores (2FA), obrigatória para os perfis administrativos, e tela de segurança da conta, com a troca de senha
- Recuperação de senha por link enviado por e-mail
- Auditoria das ações de acesso e das operações relevantes, com e-mail e IP mascarados na consulta
- Páginas de Termos de Uso e Política de Privacidade com versão, vigência e histórico de versões, novo aceite quando a versão muda e histórico de aceites de cada usuário
- Exercício dos direitos do titular: acesso e correção (com protocolo e prazo de resposta) e anonimização dos dados

### Solicitações, Encerramento e Administração
- Aluno pede troca de modalidade (decidida pelo coordenador) ou trancamento (analisado pela secretaria e aprovado pelo coordenador), e pode cancelar enquanto estiver em análise
- Com o trancamento aprovado, o estágio fica suspenso com as horas mantidas, e o aluno pode pedir a reabertura dentro do prazo de arrependimento; sem reabertura, o estágio é encerrado ao fim do prazo
- Orientador conclui ou reprova o estágio, e o coordenador encerra por desligamento
- Coordenador atribui e troca o orientador de cada turma, levando junto os estágios em aberto
- Administrador ajusta os parâmetros do sistema (prazos, bloqueio, validade da sessão e retenção da auditoria) dentro de limites, e faz correções excepcionais com justificativa (desativar o 2FA de um usuário e corrigir o status de um estágio)

---

## Segurança

As regras abaixo estão implementadas no backend. Ocultar botões no frontend não substitui essas verificações.

### Autenticação
- JWT assinado (HMAC) armazenado em cookie HttpOnly com SameSite=Strict e flag Secure em produção
- O JWT nunca é enviado no corpo das respostas: fica apenas no cookie, inacessível ao JavaScript da página
- O browser envia o cookie automaticamente, não é necessário enviar header Authorization nas páginas
- Senhas armazenadas com BCrypt: de 8 a 72 caracteres, com pelo menos uma letra e um número, e confirmação validada no backend (o limite de 72 é o máximo considerado pelo BCrypt)
- Resposta genérica (401) para e-mail inexistente, senha incorreta ou conta inativa, evitando a descoberta de contas cadastradas
- Bloqueio automático da conta após 5 tentativas falhas (senha ou código 2FA), com desbloqueio automático após 5 minutos. Esses são os valores padrão, que o administrador pode ajustar
- Limite de 250 requisições por minuto por endereço IP nas rotas de autenticação, no aceite do primeiro acesso e na consulta de CNPJ; acima disso, a resposta é 429
- Conta desativada perde o acesso imediatamente, mesmo com JWT ainda dentro da validade (verificação no JwtFilter)
- Validade do JWT: 8 horas por padrão
- Logout revoga o JWT no servidor: o token entra numa lista no Redis até expirar (com cópia em memória, para o logout valer mesmo se o Redis cair)

### Autenticação de Dois Fatores (2FA)
- Códigos TOTP de 6 dígitos, compatíveis com Google Authenticator, Microsoft Authenticator e similares
- Obrigatória para ADMINISTRADOR, COORDENADOR e SECRETARIA: configurada no primeiro login, antes da emissão do JWT, e não pode ser desativada pelo próprio usuário
- Opcional para ALUNO e ORIENTADOR: ativada e desativada em /conta/seguranca, sempre mediante código válido
- O JWT só é emitido depois da validação do segundo fator
- Cada código TOTP é aceito uma única vez
- Token de pré-autenticação: aleatório, de uso único, válido por 2 minutos (ajustável pelo administrador) e armazenado apenas como hash SHA-256
- Código errado conta como tentativa falha e compartilha o bloqueio da senha; o contador só é zerado após o login completo
- QR Code gerado no navegador: o segredo não é enviado a serviços externos; o script do CDN usa verificação de integridade (SRI)
- Segredo do 2FA armazenado cifrado com AES-256-GCM

Fluxo de login:
```
POST /api/v1/auth/login (e-mail e senha)
 ├─ perfil opcional sem 2FA ativo      -> cookie JWT
 ├─ conta com 2FA ativo               -> token de pré-autenticação
 │     POST /api/v1/auth/2fa/verificar                    -> cookie JWT
 └─ perfil obrigatório sem 2FA ativo  -> token de pré-autenticação
       POST /api/v1/auth/2fa/configuracao/iniciar         -> QR Code
       POST /api/v1/auth/2fa/configuracao/confirmar       -> ativa o 2FA e cria o cookie JWT
```

### Autorização
- Defense in depth: regras de acesso por rota no SecurityConfig e @PreAuthorize nos controllers
- Acesso negado por padrão (anyRequest().authenticated())
- Operações sempre buscam os dados do usuário autenticado (authentication.getName())
- Orientador só acessa estágios e entregas das suas turmas, e o coordenador só os registros dos seus cursos: a checagem é feita na própria consulta ao banco, e registros de outros usuários retornam 404
- Aluno só cadastra estágio com tipo do próprio curso: tipo de outro curso retorna 404, igual a um id inexistente
- HTTPS obrigatório no perfil prod (requiresSecure)
- Swagger e /v3/api-docs desativados no perfil prod

### Proteções no Navegador
- Content-Security-Policy: scripts só do próprio sistema e do jsDelivr, fontes do Google Fonts, nenhum script inline e nenhuma abertura dentro de frame de outro site
- Referrer-Policy no-referrer, X-Content-Type-Options e X-Frame-Options
- Os tokens do convite e da redefinição de senha são retirados da barra de endereço assim que a página abre
- Arquivos das entregas baixados com Content-Disposition: attachment e X-Content-Type-Options: nosniff

### Validação de Entrada
- Todos os formulários são validados no frontend, para avisar antes do envio, e de novo no backend com Bean Validation (@Valid), porque a validação da tela pode ser contornada chamando a API direto
- Erro de validação retorna 400 com a mensagem do primeiro erro e o mapa `campos`, com o erro de cada campo; as telas marcam o campo correspondente
- JSON inválido ou data fora do formato retorna 400, sem expor detalhes internos
- Regras: e-mail com até 254 caracteres; nome da empresa entre 2 e 150 caracteres; data de início no máximo 1 ano no futuro; CNPJ com dígito verificador válido; justificativas com até 1000 caracteres; arquivos de até 10 MB conferidos pela assinatura do formato

| Perfil | Pode acessar |
|--------|--------------|
| ADMINISTRADOR | convites de coordenadores, auditoria, configurações do sistema, correções excepcionais, segurança da conta |
| COORDENADOR | painel, cursos, turmas e orientador da turma, tipos de estágio, trilhas e critérios, pré-cadastro, estágios, solicitações, convites de secretarias, segurança da conta, direitos LGPD |
| SECRETARIA | envio e acompanhamento de convites, solicitações de trancamento e reabertura, segurança da conta, direitos LGPD |
| ORIENTADOR | validação dos estágios, correções, histórico de correções, acompanhamento dos alunos, segurança da conta, direitos LGPD |
| ALUNO | cadastro de estágio, painel, trilha e entregas, solicitações, segurança da conta, direitos LGPD |

### Criptografia
- Em trânsito: HTTPS (TLS) no perfil prod
- Senhas: hash BCrypt, nunca armazenadas nem registradas em log em texto puro
- Em repouso: AES-256-GCM (AesEncryptor, chave derivada de ENCRYPTION_SECRET com SHA-256) nos campos:
  - segredo do 2FA (usuarios.dois_fatores_segredo)
  - justificativa de rejeição do estágio (estagios.justificativa_rejeicao)
  - justificativa de cada decisão do orientador no histórico (validacoes_estagio.justificativa)
  - comentário do aluno e feedback do orientador nas entregas (entrega_versoes.comentario_aluno e entrega_versoes.feedback)
  - observação de cada critério do checklist (respostas_criterio_checklist.observacao)
  - motivo e justificativa das solicitações (solicitacoes.motivo e solicitacoes.justificativa_reprovacao)
  - observação do encerramento do estágio (estagios.observacao_encerramento)
- O texto cifrado não é copiado para o log de auditoria
- RGM e matrícula institucional não são cifrados por decisão de projeto: são identificadores únicos usados em buscas por secretaria, coordenação e orientador. A cifragem AES-GCM gera um valor diferente a cada gravação, o que impediria a restrição de unicidade e a busca. Esses campos são protegidos pelo controle de acesso por perfil.

### Auditoria
Os eventos são gravados na tabela registros_auditoria na mesma transação da operação, com usuário, e-mail, perfil, ação, detalhes, IP de origem, resultado e data/hora. Também são registrados no arquivo logs/auditoria.log. O administrador consulta os registros na tela Auditoria, com filtros por ação, e-mail, resultado e período. Na lista, o e-mail, o IP e os e-mails dos detalhes chegam do servidor já mascarados; o registro completo é aberto um de cada vez, pelo botão Ver, e cada abertura gera o evento AUDITORIA_REGISTRO_VISUALIZADO. Os registros são mantidos por 6 meses (valor padrão, ajustável pelo administrador), e uma rotina diária elimina os mais antigos.

| Grupo | Eventos |
|-------|---------|
| Login | LOGIN_SUCESSO, LOGIN_FALHA, LOGOUT, RECUPERACAO_SENHA_SOLICITADA, SENHA_REDEFINIDA |
| 2FA | LOGIN_2FA_REQUERIDO, LOGIN_CONFIGURACAO_2FA_REQUERIDA, LOGIN_2FA_FALHA, DOIS_FATORES_ATIVADO, DOIS_FATORES_DESATIVADO, DOIS_FATORES_FALHA |
| Acesso ao sistema | CONVITE_ENVIADO, PRIMEIRO_ACESSO_ACEITO, PRIMEIRO_ACESSO_RECUSADO, PRIMEIRO_ACESSO_RGM_INCORRETO, CONTA_INICIAL_CRIADA, MATRICULA_CRIADA, PRE_CADASTRO_INCLUIDO, PRE_CADASTRO_ALTERADO |
| Cadastros da coordenação | CURSO_CADASTRADO, CURSO_ALTERADO, TURMA_CADASTRADA, TURMA_ALTERADA, ORIENTADOR_ATRIBUIDO, TIPO_ESTAGIO_CADASTRADO, TIPO_ESTAGIO_ALTERADO, TRILHA_CRIADA, TRILHA_ALTERADA, TAREFA_ADICIONADA, TAREFA_ALTERADA, TAREFA_REMOVIDA, CRITERIO_ADICIONADO, CRITERIO_ALTERADO, CRITERIO_DESATIVADO, CRITERIO_REMOVIDO |
| Estágio | ESTAGIO_CADASTRADO, ESTAGIO_APROVADO, ESTAGIO_REJEITADO, ESTAGIO_CONCLUIDO, ESTAGIO_REPROVADO, ESTAGIO_DESLIGADO |
| Entregas | ENTREGA_ENVIADA, ENTREGA_APROVADA, ENTREGA_DEVOLVIDA, ARQUIVO_BAIXADO |
| Solicitações | SOLICITACAO_CRIADA, SOLICITACAO_CANCELADA, SOLICITACAO_APROVADA_SECRETARIA, SOLICITACAO_APROVADA, SOLICITACAO_REPROVADA, TRANCAMENTO_EFETIVADO |
| Administração | AUDITORIA_CONSULTADA, AUDITORIA_REGISTRO_VISUALIZADO, CONFIGURACAO_ALTERADA, CORRECAO_DOIS_FATORES, CORRECAO_STATUS_ESTAGIO |
| LGPD | CONSENTIMENTO_ACEITO, SOLICITACAO_ACESSO_DADOS, SOLICITACAO_CORRECAO_DADOS, ANONIMIZACAO_EXECUTADA, ANONIMIZACAO_FALHA |

Consulta rápida:
```
SELECT acao, email_tentativa, ip_origem, sucesso, detalhes, criado_em
FROM registros_auditoria ORDER BY id DESC LIMIT 20;
```

### LGPD
- Termos de Uso (/termos) e Política de Privacidade (/privacidade) disponíveis sem login
- Aceite registrado com data e versão (versão atual: 1.2, definida em ConsentimentoService, que também informa a data de vigência às páginas). O aceite não é a base legal do tratamento: os dados são tratados por obrigação legal, legítimo interesse e exercício regular de direitos, e o aceite registra que o titular foi informado (art. 9º da LGPD) e aceita as regras de uso
- Cada aceite fica no histórico de aceites (tabela aceites_termos), que não é apagado pela eliminação periódica da auditoria. Quando a versão muda, as páginas e a API exigem um novo aceite antes de continuar, e quem não concorda pode sair pela própria tela de aceite
- Contato do encarregado de proteção de dados (DPO) no rodapé de todas as páginas (art. 41 da LGPD)
- Direitos do titular na tela de privacidade: solicitar acesso e correção, com protocolo e prazo de resposta de 15 dias, e anonimizar os dados
- Anonimização substitui nome, e-mail, senha e identificadores por valores neutros, cancela as solicitações em aberto e desativa a conta; os registros acadêmicos de estágio são conservados para cumprimento de obrigação legal (art. 16, I, da LGPD), sem ligação com a conta, e os arquivos enviados ficam como foram enviados

### Riscos Residuais e Decisões de Projeto
- Conta bloqueada por tentativas falhas mantém a sessão já aberta: o bloqueio protege o login contra força bruta. A revogação imediata do acesso é feita desativando a conta (ativo = false)
- A resposta 423 (conta bloqueada) só ocorre para e-mail existente após as tentativas configuradas, mantida por usabilidade
- CSRF desabilitado, mitigado pelo cookie SameSite=Strict
- Perda do dispositivo autenticador: não há recuperação automática; o administrador desativa o 2FA da conta na tela Correções, com justificativa registrada na auditoria, e o usuário configura um novo no próximo login
- O Alpine.js precisa de 'unsafe-eval' na Content-Security-Policy, porque avalia as expressões dos atributos das páginas

---

## Integração com API Externa (Resend)

O envio dos e-mails é feito pela API do Resend (EmailService), serviço de e-mail transacional sediado nos EUA.

| Item | Descrição |
|------|-----------|
| Finalidade | Enviar o link de primeiro acesso, o link de recuperação de senha, as decisões sobre o estágio, o resultado das entregas e o andamento das solicitações |
| Dados enviados | E-mail e nome do destinatário e conteúdo da mensagem (o link de convite vale 24 horas por padrão) |
| Autenticação | Chave de API na variável RESEND_API_KEY (nunca fixa no código) |
| Execução | Assíncrona (@Async), com até 3 tentativas e intervalos de 2 e 4 segundos; a falha é registrada em log e não interrompe a requisição |
| Transferência internacional | Sim (EUA), declarada na Política de Privacidade |

Observação: com o remetente de testes do Resend (onboarding@resend.dev, usado quando EMAIL_REMETENTE fica vazio), os e-mails só são entregues ao endereço da conta cadastrada no Resend. Para enviar a qualquer endereço é necessário verificar um domínio próprio e informá-lo em EMAIL_REMETENTE.

---

## Integração com API Externa (BrasilAPI)

O CNPJ da empresa do estágio é consultado na BrasilAPI (ConsultaCnpjService), que repassa os dados públicos da Receita Federal. A API é gratuita e não exige chave.

| Item | Descrição |
|------|-----------|
| Finalidade | Confirmar que o CNPJ existe e obter a razão social e a situação cadastral |
| Dados enviados | Apenas o CNPJ, depois de conferido o dígito verificador |
| Tempo limite | 3 segundos para conectar e 5 para a resposta (aprova.cnpj.* no application.properties) |
| CNPJ inexistente (404) | O cadastro é recusado |
| API lenta ou indisponível | O cadastro segue sem a situação, e o orientador confere o CNPJ no termo de compromisso |
| Situação diferente de ATIVA | O cadastro segue, e a situação aparece destacada para o orientador |

O validador já aceita o CNPJ alfanumérico emitido pela Receita Federal a partir de julho de 2026. Nos logs aparece apenas o início do CNPJ, e a razão social não é registrada.

---

## Integração com Serviço Externo (Cloudflare R2)

Os arquivos das entregas são guardados no Cloudflare R2 (ArmazenamentoR2), compatível com a API do S3, quando as quatro variáveis R2_* estão preenchidas. Sem elas, os arquivos ficam no disco, na pasta definida em ARMAZENAMENTO_DIRETORIO (padrão `uploads`).

| Item | Descrição |
|------|-----------|
| Finalidade | Guardar os arquivos das entregas fora do servidor da aplicação |
| Dados enviados | O arquivo enviado pelo aluno, com nome gerado pelo servidor |
| Autenticação | Chaves de acesso nas variáveis R2_ACCESS_KEY e R2_SECRET_KEY |
| Transferência internacional | Sim (servidores distribuídos), declarada na Política de Privacidade |

No Render, o disco é apagado a cada implantação, então o R2 é necessário em produção.

---

## Monitoramento (Prometheus e Grafana)

O Spring Boot Actuator expõe a saúde da aplicação e as métricas no formato do Prometheus na porta de gerenciamento 9090, acessível só pela rede interna do Docker. O Prometheus lê essas métricas a cada 15 segundos, e o Grafana mostra o painel "Aprova+ - Visão geral", com as requisições por segundo, o tempo de resposta (p95) por rota, os erros 5xx, as respostas 401, 403 e 429, a memória da JVM, a CPU e as conexões com o banco. O painel é carregado automaticamente da pasta `monitoramento/`.

---

## Documentação da API (Swagger)

Com a aplicação rodando no perfil dev, a documentação fica em http://localhost:8080/swagger-ui.html, com os endpoints agrupados por módulo.

- Quem fez login pela tela do sistema, no mesmo navegador, já consegue chamar as rotas protegidas: o navegador envia o cookie `jwt` sozinho (esquema `cookieAuth`)
- Para testar pelo Postman ou curl, use o esquema `bearerAuth` com o token no cabeçalho Authorization
- Apenas as rotas /api/** aparecem; as páginas Thymeleaf ficam de fora
- No perfil prod o Swagger fica desativado

---

## Como Executar

### Pré-requisitos
- Git
- Docker e Docker Compose instalados (Docker Desktop no Windows e no macOS)
- Java 25, apenas para rodar os testes automatizados fora do Docker (o Maven vem pelo wrapper `mvnw`)
- OpenSSL, para gerar as chaves (já vem com o Git Bash no Windows)
- Um aplicativo autenticador no celular (Google Authenticator, Microsoft Authenticator ou similar), para o 2FA dos perfis administrativos
- As portas 8080, 5433, 6379, 9091 e 3000 livres na máquina

### Passos

**1. Clone o repositório:**
```
git clone https://github.com/isabela-tamie15/aprova_mais.git
cd aprova_mais
git checkout 1611
```

**2. Crie o arquivo .env na raiz do projeto, a partir do modelo:**
```
cp .env.example .env
```

Gere as chaves aleatórias. Cada comando gera um valor diferente:
```
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 48   # ENCRYPTION_SECRET
openssl rand -hex 24      # DB_PASSWORD
openssl rand -hex 24      # REDIS_PASSWORD
openssl rand -hex 24      # GRAFANA_SENHA
```

**3. Preencha o .env.** Os valores entre `<` e `>` são para trocar pelos seus:
```
# Obrigatório: dev na máquina local, prod no servidor. Sem ele, a aplicação não sobe
SPRING_PROFILES_ACTIVE=dev

DB_URL=jdbc:postgresql://postgres:5432/dbaprovamais
DB_USERNAME=usuario_aprovamais
DB_PASSWORD=<gerado com openssl rand -hex 24>

# Mínimo de 32 bytes aleatórios (assinatura HMAC-SHA256 do JWT)
JWT_SECRET=<gerado com openssl rand -base64 48>

# Chave da criptografia AES, também com 32 bytes aleatórios ou mais
ENCRYPTION_SECRET=<outro valor gerado com openssl rand -base64 48>

# Redis do docker-compose. Use só letras e números, porque a senha entra no REDIS_URL
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<gerado com openssl rand -hex 24>

# Contas criadas na inicialização, apenas se ainda não existirem
ADMIN_INICIAL_EMAIL=admin@exemplo.com
ADMIN_INICIAL_SENHA=<8 a 72 caracteres, com letra e número>
ADMIN_INICIAL_NOME=Administrador
SECRETARIA_INICIAL_EMAIL=secretaria@exemplo.com
SECRETARIA_INICIAL_SENHA=<8 a 72 caracteres, com letra e número>
SECRETARIA_INICIAL_NOME=Secretaria Acadêmica

# Endereço usado nos links dos e-mails
APP_URL=http://localhost:8080

# E-mail (opcional). Sem chave válida, os e-mails não saem e a falha fica no log
RESEND_API_KEY=<chave do Resend>
EMAIL_REMETENTE=

# Cloudflare R2 (opcional). Vazio, os arquivos ficam no disco
R2_ENDPOINT=
R2_ACCESS_KEY=
R2_SECRET_KEY=
R2_BUCKET=
ARMAZENAMENTO_DIRETORIO=

# Senha do usuário admin do Grafana
GRAFANA_SENHA=<gerado com openssl rand -hex 24>
```

| Variável | Obrigatória | Observação |
|----------|-------------|------------|
| SPRING_PROFILES_ACTIVE | Sim | `dev` na máquina local, `prod` no servidor |
| DB_URL, DB_USERNAME, DB_PASSWORD | Sim | O docker-compose cria o banco com esse usuário e essa senha |
| JWT_SECRET, ENCRYPTION_SECRET | Sim | No mínimo 32 bytes aleatórios |
| REDIS_PASSWORD, GRAFANA_SENHA | Sim | O docker-compose não sobe sem elas |
| ADMIN_INICIAL_*, SECRETARIA_INICIAL_* | Sim, na primeira subida | Criam as primeiras contas; depois são ignoradas |
| APP_URL | Recomendada | Base dos links enviados por e-mail |
| RESEND_API_KEY, EMAIL_REMETENTE | Não | Ver [Resend](#resend-e-mail) |
| R2_*, ARMAZENAMENTO_DIRETORIO | Não | Ver [Cloudflare R2](#cloudflare-r2-arquivos-das-entregas) |

A aplicação confere essas chaves ao subir: se faltar o perfil, ou se o JWT_SECRET ou o ENCRYPTION_SECRET tiverem menos de 32 bytes ou ainda estiverem com um valor de exemplo, ela para e o log diz qual variável corrigir.

O arquivo .env está no .gitignore e nunca deve ser versionado. Alterar ENCRYPTION_SECRET depois que existirem dados cifrados torna esses dados ilegíveis.

**4. Suba os containers:**
```
docker compose up --build -d
```
O Dockerfile compila o projeto dentro do contêiner, então não é preciso gerar o JAR antes. A primeira subida baixa as dependências e leva alguns minutos. Para acompanhar:
```
docker compose ps
docker compose logs -f backend
```
A aplicação está pronta quando o log mostra `Started AprovamaisApplication`. Nesse momento, o Hibernate já criou as tabelas e o ContaInicialRunner já criou o administrador e a secretaria do .env.

**5. Acesse:**

| Endereço | O que é |
|----------|---------|
| http://localhost:8080/login | Sistema |
| http://localhost:8080/swagger-ui.html | Documentação da API (perfil dev) |
| http://localhost:8080/livez e http://localhost:8080/readyz | Verificação de saúde |
| http://localhost:9091 | Prometheus |
| http://localhost:3000 | Grafana (usuário `admin`, senha do GRAFANA_SENHA) |

**6. Rode os testes:**
```
./mvnw test
```
No Windows, use `mvnw.cmd test`. Os testes usam o banco H2 em memória (perfil test), não precisam do Docker nem do .env e não chamam a BrasilAPI, o Resend nem o R2.

Para parar, use `docker compose down`. Para apagar também o banco e começar do zero, use `docker compose down -v`.

---

## Roteiro de Testes

O banco começa vazio, apenas com o administrador e a secretaria do .env, e não há seed: os dados são criados pela própria interface, na ordem em que o sistema exige. Use sempre e-mails fictícios, como `coordenador@exemplo.com`.

### Links de convite sem e-mail

No perfil dev, cada convite também registra o link de primeiro acesso no log, para o teste não depender do Resend:
```
docker compose logs backend | grep "Link de primeiro acesso"
```
Abra o link numa janela anônima do navegador, para não misturar a sessão de quem convidou com a de quem foi convidado.

### Passo a passo

1. **Administrador.** Entre em /login com o ADMIN_INICIAL_EMAIL e a senha do .env. O sistema pede o aceite dos Termos e da Política e mostra o QR Code do 2FA: leia com o aplicativo autenticador e digite o código de 6 dígitos.
2. **Coordenador.** Como administrador, no menu Convites, convide um coordenador. Abra o link do log, aceite os termos e defina a senha. Entre como coordenador e configure o 2FA.
3. **Cadastros.** Como coordenador, cadastre:
   - um curso (Cursos);
   - uma turma (Turmas), com a data de encerramento das entregas;
   - um tipo de estágio (Tipos de estágio), com carga total e jornada semanal;
   - a trilha desse tipo (Trilhas), com algumas tarefas;
   - em Critérios, o checklist de avaliação de uma tarefa.
4. **Pré-cadastro.** Em Pré-cadastro, inclua um aluno (com RGM só com números e turma) e um orientador (com matrícula institucional).
5. **Secretaria.** Entre com o SECRETARIA_INICIAL_EMAIL e configure o 2FA. Em Enviar convite, clique em Convidar na lista de pré-cadastrados para o aluno e o orientador e aceite os dois convites pelos links do log. No convite do aluno, informe o RGM do pré-cadastro: com um RGM errado, a tela mostra quantas tentativas restam. O coordenador também pode convidar outras secretarias no menu Convites.
6. **Orientador da turma.** Como coordenador, em Turmas, escolha o orientador na coluna Orientador.
7. **Cadastro do estágio.** Como aluno, em Meu estágio, cadastre o estágio. Ao digitar um CNPJ válido, o nome da empresa é preenchido pela BrasilAPI.
8. **Validação.** Como orientador, em Validações, confira a data de início e aprove. Teste também a rejeição com justificativa, que libera o reenvio para o aluno.
9. **Progresso.** Como aluno, confira no Painel a carga horária cumprida e restante, o cálculo por semanas e o aviso de prazo.
10. **Entregas e checklist.** Como aluno, em Minha trilha, envie um PDF numa tarefa. Como orientador, em Correções, abra a entrega, marque os critérios como Suficiente ou Insuficiente e devolva com feedback. O aluno vê o resultado e envia uma nova versão, que o orientador aprova.
11. **Solicitações.** Como aluno, em Solicitações, peça a troca de modalidade (decidida pelo coordenador) ou o trancamento (analisado pela secretaria e aprovado pelo coordenador). Com o trancamento aprovado, peça a reabertura dentro do prazo mostrado.
12. **Encerramento.** O orientador conclui ou reprova o estágio na tela de acompanhamento do aluno (botão Ver acompanhamento, em Validações). O coordenador encerra por desligamento em Estágios.
13. **Painel do coordenador.** Confira os indicadores por etapa e os gráficos por turma, e combine os filtros de curso, semestre, turma e orientador.
14. **Administração.** Como administrador:
    - em Configurações, altere um parâmetro, por exemplo o prazo de arrependimento;
    - em Correções, desative o 2FA de um usuário ou corrija o status de um estágio, com justificativa;
    - em Auditoria, filtre os registros das ações feitas nos passos anteriores e abra um registro pelo botão Ver para ver o e-mail e o IP sem máscara.
15. **LGPD.** Pelo rodapé, abra a Política de Privacidade:
    - peça acesso ou correção dos dados e confira o protocolo e o prazo;
    - teste a anonimização com uma conta de aluno de teste, informando a senha e o código do 2FA quando ele estiver ativo.
16. **Recuperação de senha.** Na tela de login, use "Esqueceu sua senha? Recupere agora". O link chega por e-mail, então este passo depende do Resend.

### Comandos úteis durante os testes

Acesso ao banco:
```
docker compose exec postgres psql -U usuario_aprovamais -d dbaprovamais
```

```
-- últimos registros da auditoria
SELECT acao, email_tentativa, sucesso, detalhes, criado_em FROM registros_auditoria ORDER BY id DESC LIMIT 20;

-- conferir que um campo sensível está cifrado (o texto aparece ilegível)
SELECT feedback FROM entrega_versoes WHERE feedback IS NOT NULL LIMIT 3;

-- desbloquear uma conta sem esperar o tempo do bloqueio
UPDATE usuarios SET conta_bloqueada = false, tentativas_falhas = 0 WHERE email = '...';
```

Para desativar o 2FA de uma conta (por exemplo, perda do celular), use a tela Correções do administrador, que registra a justificativa na auditoria.

Ao alterar entidades, recrie o banco com `docker compose down -v` antes de subir novamente.

---

## Como Testar as Integrações Externas

### Resend (e-mail)
1. Crie uma conta em https://resend.com e gere uma chave de API.
2. Coloque a chave em RESEND_API_KEY no .env e reinicie com `docker compose up -d`.
3. Envie um convite para o e-mail da conta do Resend (ou, com domínio verificado, para qualquer endereço).

Evidência: o e-mail chega com o link de primeiro acesso. Se o envio falhar, o log mostra `[EMAIL] Tentativa 1 de 3 falhou` e, depois das três tentativas, `[EMAIL] Envio falhou depois de 3 tentativas`, sem interromper a operação.

### BrasilAPI (CNPJ)
Não precisa de configuração. No cadastro do estágio, digite o CNPJ de uma empresa pública, por exemplo `00.000.000/0001-91`: o nome da empresa é preenchido com a razão social, e a situação cadastral aparece para o orientador. Pela API, com a sessão de um aluno: `GET /api/v1/aluno/estagio/cnpj/00000000000191`.

Um CNPJ com dígito verificador inválido é recusado sem chamar a BrasilAPI, e um CNPJ inexistente recusa o cadastro.

### Cloudflare R2 (arquivos das entregas)
1. No painel da Cloudflare, crie um bucket no R2 e um token de API do R2 com leitura e escrita nesse bucket.
2. Preencha R2_ENDPOINT (no formato `https://<id da conta>.r2.cloudflarestorage.com`), R2_ACCESS_KEY, R2_SECRET_KEY e R2_BUCKET.
3. Reinicie o backend.

Evidência: ao subir, o log mostra `[ARQUIVO] Arquivos das entregas no Cloudflare R2, bucket <nome>` (sem as variáveis: `[ARQUIVO] Arquivos das entregas no disco local, pasta uploads`). Envie uma entrega e confira o arquivo no bucket pelo painel da Cloudflare.

### Redis (revogação no logout)
Faça login, copie o valor do cookie `jwt` pelas ferramentas do navegador e clique em Sair. O token revogado fica no Redis até expirar:
```
docker compose exec redis sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning keys "aprova:jwt-revogado:*"'
```
Uma chamada com o token antigo recebe 401, mesmo dentro da validade:
```
curl -i http://localhost:8080/api/v1/conta/2fa -H "Cookie: jwt=<token copiado antes do logout>"
```

### Prometheus e Grafana
1. Em http://localhost:9091/targets, o alvo `aprovamais` aparece como UP.
2. Em http://localhost:3000, entre com o usuário `admin` e a senha do GRAFANA_SENHA e abra o painel "Aprova+ - Visão geral".
3. Navegue pelo sistema por alguns minutos para os gráficos se preencherem.

---

## Evidências de Execução

### Testes automatizados

| Evidência | Resultado |
|-----------|-----------|
| `./mvnw test` | 347 testes, entre testes de unidade dos serviços e testes de integração que sobem a aplicação e chamam a API, sem falhas |
| Cobertura (JaCoCo) | 78,2% das linhas, acima do mínimo de 50% configurado no pom.xml; o relatório fica em `target/site/jacoco/index.html` |
| CI (GitHub Actions) | `.github/workflows/ci.yml` roda os testes a cada push e pull request para a develop e a main; o selo no topo deste README mostra o último resultado |
| Regressão da API | 87 respostas da API capturadas antes e depois de cada branch, sem diferenças fora das mudanças previstas |
| Teste de carga | 30 usuários simultâneos por 1 minuto: 3.307 requisições, nenhum erro, p95 de 105 ms nas operações comuns e de 154 ms no painel do coordenador |

### Verificações pela linha de comando

Com o sistema rodando:
```
# saúde da aplicação: {"status":"UP"}
curl http://localhost:8080/readyz

# cabeçalhos de segurança: Content-Security-Policy, Referrer-Policy, X-Content-Type-Options e X-Frame-Options
curl -sI http://localhost:8080/login

# rota protegida sem login: 401 com mensagem genérica
curl -i http://localhost:8080/api/v1/aluno/estagio

# login com e-mail inexistente: 401, sem revelar se a conta existe
curl -i -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"naoexiste@exemplo.com","senha":"SenhaErrada1"}'
```

### Telas do sistema

Capturas feitas com dados fictícios, as mesmas do manual do usuário da monografia:

| Tela | Captura |
|------|---------|
| Primeiro acesso pelo convite | [figura12_primeiro_acesso.png](docs/evidencias/figura12_primeiro_acesso.png) |
| Login com a verificação em duas etapas | [figura13_login_2fa.png](docs/evidencias/figura13_login_2fa.png) |
| Painel do aluno | [figura14_painel_aluno.png](docs/evidencias/figura14_painel_aluno.png) |
| Trilha com o histórico de versões | [figura15_trilha_versoes.png](docs/evidencias/figura15_trilha_versoes.png) |
| Validação de estágios | [figura16_validacoes.png](docs/evidencias/figura16_validacoes.png) |
| Painel de correção | [figura17_painel_correcao.png](docs/evidencias/figura17_painel_correcao.png) |
| Avaliação com o checklist de critérios | [figura18_avaliacao_checklist.png](docs/evidencias/figura18_avaliacao_checklist.png) |
| Montagem da trilha | [figura19_montagem_trilha.png](docs/evidencias/figura19_montagem_trilha.png) |
| Auditoria | [figura20_auditoria.png](docs/evidencias/figura20_auditoria.png) |

---

## Implantação no Render

O arquivo `render.yaml` descreve a implantação no Render: o serviço web em Docker, o PostgreSQL e o Key Value (Redis).

1. No Render, crie um Blueprint apontando para este repositório.
2. O Render gera o JWT_SECRET e o ENCRYPTION_SECRET e liga o banco e o Redis automaticamente.
3. Na criação, informe APP_URL (o endereço público), RESEND_API_KEY, EMAIL_REMETENTE, as variáveis R2_* e as contas iniciais ADMIN_INICIAL_* e SECRETARIA_INICIAL_*.
4. O perfil prod liga o HTTPS obrigatório e o cookie Secure e desativa o Swagger. A verificação de saúde usa o /readyz.

No plano gratuito, o serviço hiberna sem uso, e a primeira requisição depois disso demora cerca de um minuto.

---

## Estrutura do Projeto

**src/main/java/tcc/ges/aprovamais/**
```
config/      - Configurações: SecurityConfig, WebConfig, AsyncConfig, ArmazenamentoConfig,
               ConsentimentoInterceptor, ContaInicialRunner, OpenApiConfig
controller/  - Controllers REST (/api/v1/...) e de página, separados por perfil
dto/         - Objetos de transferência de dados
entity/      - Entidades JPA e enums
exception/   - Tratamento global de exceções
external/    - Clientes externos: EmailService (Resend) e ConsultaCnpjService (BrasilAPI)
repository/  - Repositórios Spring Data JPA
security/    - JwtService, JwtFilter, AesEncryptor, TokenRevogadoService, LimiteRequisicoesFilter
service/     - Lógica de negócio: EstagioService, TrilhaService, EntregaService, ConviteService,
               SolicitacaoService, EncerramentoEstagioService, PainelCoordenadorService,
               ConfiguracaoService, CorrecaoExcepcionalService, AuditoriaService,
               AnonimizacaoService, DoisFatoresService, BloqueioContaService,
               CalculadoraCargaHoraria, ValidadorCnpj e outros
storage/     - Armazenamento dos arquivos: disco local e Cloudflare R2
```

**src/main/resources/**
```
templates/
├── login.html, primeiro-acesso.html, primeiro-acesso-erro.html
├── recuperar-senha.html, redefinir-senha.html
├── consentimento.html, termos.html, privacidade.html
├── admin/          auditoria, convites, configuracoes, correcoes
├── coordenador/    dashboard, cursos, turmas, tipos-estagio, trilhas, pre-cadastros,
│                   estagios, solicitacoes, convites
├── secretaria/     dashboard, enviar-convite, convites, solicitacoes
├── orientador/     validacoes, correcoes, correcao, historico, acompanhamento
├── aluno/          dashboard, estagio, trilha, solicitacoes
├── conta/          seguranca
└── fragments/      barras de navegação por perfil, rodapé LGPD e lista de solicitações
static/
├── css/
│   ├── main.css          estilos usados em várias páginas
│   └── paginas/          um arquivo por página (ex.: secretaria-convites.css)
└── js/
    ├── comum.js          funções usadas em várias páginas (logout, janelas de confirmação e aviso)
    ├── app.js            login e segurança da conta
    ├── coordenador.js    chamadas à API das telas da coordenação
    └── paginas/          um arquivo por página (ex.: aluno-estagio.js)
```

As páginas não têm `<script>` nem `<style>` dentro do HTML. O nome do arquivo segue o caminho do template: `templates/aluno/estagio.html` usa `js/paginas/aluno-estagio.js`. As referências usam `th:src="@{...}"` e `th:href="@{...}"`, que colocam um hash do conteúdo no nome do arquivo: quando ele muda, o navegador baixa a versão nova em vez de usar o cache. Os scripts que definem componentes do Alpine ficam no fim do `<body>`, sem `defer`, para rodar antes do Alpine.

---

## Endpoints da API

### Autenticação
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /api/v1/auth/login | Realiza login e cria cookie JWT (ou inicia a segunda etapa) |
| POST | /api/v1/auth/logout | Apaga o cookie e revoga o JWT |
| POST | /api/v1/auth/2fa/verificar | Segunda etapa do login (conta com 2FA ativo) |
| POST | /api/v1/auth/2fa/configuracao/iniciar | Gera o QR Code no primeiro login de perfil com 2FA obrigatório |
| POST | /api/v1/auth/2fa/configuracao/confirmar | Confirma o primeiro código, ativa o 2FA e cria o cookie JWT |
| POST | /api/v1/auth/recuperacao/solicitar | Envia o link de recuperação de senha |
| POST | /api/v1/auth/recuperacao/redefinir | Troca a senha usando o token do link |

### Conta (qualquer usuário autenticado)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /api/v1/conta/2fa | Situação do 2FA (ativo, obrigatório) |
| POST | /api/v1/conta/2fa/configurar | Gera novo segredo e QR Code |
| POST | /api/v1/conta/2fa/ativar | Ativa o 2FA com um código válido |
| POST | /api/v1/conta/2fa/desativar | Desativa o 2FA com um código válido (bloqueado para perfis obrigatórios) |

### Primeiro Acesso (público, protegido pelo token do convite)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /primeiro-acesso?token= | Exibe a tela de primeiro acesso |
| POST | /primeiro-acesso/aceitar?token= | Define a senha, aceita os termos e ativa a conta |
| POST | /primeiro-acesso/recusar?token= | Recusa o convite |

### Convites (Administrador, Coordenador e Secretaria)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /api/v1/convites | Envia convite de acesso por e-mail, conforme a hierarquia de perfis |
| GET | /api/v1/convites | Lista os convites enviados pelo usuário logado |

### LGPD (qualquer usuário autenticado)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /api/v1/consentimento/aceitar | Registra o aceite dos termos com data e versão |
| POST | /api/v1/lgpd/solicitar-acesso | Registra solicitação de acesso aos dados e devolve protocolo e prazo |
| POST | /api/v1/lgpd/solicitar-correcao | Registra solicitação de correção dos dados e devolve protocolo e prazo |
| DELETE | /api/v1/lgpd/meus-dados | Anonimiza os dados do titular e desativa a conta |

### Aluno
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /api/v1/aluno/estagio | Busca o estágio atual (o único em aberto) do aluno |
| GET | /api/v1/aluno/estagio/todos | Lista todos os estágios da matrícula, inclusive concluídos e encerrados |
| GET | /api/v1/aluno/estagio/historico | Lista as aprovações e rejeições do estágio atual |
| GET | /api/v1/aluno/estagio/tipos | Lista os tipos de estágio do curso do aluno |
| GET | /api/v1/aluno/estagio/cnpj/{cnpj} | Consulta o CNPJ na Receita (BrasilAPI) |
| POST | /api/v1/aluno/estagio | Cadastra ou reenvia o estágio (reenvio apenas de estágio REJEITADO) |
| GET | /api/v1/aluno/trilha | Busca a trilha personalizada do aluno |
| POST | /api/v1/aluno/tarefas/{tarefaId}/entregas | Envia o arquivo de uma tarefa, criando uma nova versão |
| GET | /api/v1/aluno/tarefas/{tarefaId}/entrega | Busca a entrega da tarefa, com o histórico de versões e o resultado do checklist |
| GET | /api/v1/aluno/entregas/versoes/{versaoId}/arquivo | Baixa o arquivo de uma versão |
| GET | /api/v1/aluno/solicitacoes | Lista as solicitações do aluno |
| POST | /api/v1/aluno/solicitacoes | Cria solicitação de troca de modalidade, trancamento ou reabertura |
| POST | /api/v1/aluno/solicitacoes/{id}/cancelar | Cancela uma solicitação em análise |

### Orientador
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /api/v1/orientador/validacoes | Lista estágios pendentes e rejeitados da turma |
| POST | /api/v1/orientador/validacoes/{id}/aprovar | Aprova um estágio (somente estágios do próprio orientador), com a data de início confirmada opcional |
| GET | /api/v1/orientador/validacoes/{id}/historico | Lista as aprovações e rejeições de um estágio do orientador |
| POST | /api/v1/orientador/validacoes/{id}/rejeitar | Rejeita um estágio com justificativa (máximo de 1000 caracteres) |
| GET | /api/v1/orientador/entregas | Painel de correção, com filtros e paginação |
| GET | /api/v1/orientador/entregas/historico | Histórico das correções feitas pelo orientador |
| GET | /api/v1/orientador/entregas/{id} | Detalhe da entrega, com as versões e os critérios |
| POST | /api/v1/orientador/entregas/{id}/avaliacao | Aprova ou devolve a entrega, com o checklist e o feedback |
| GET | /api/v1/orientador/entregas/versoes/{versaoId}/arquivo | Baixa o arquivo de uma versão |
| GET | /api/v1/orientador/estagios/{estagioId}/visao | Visão consolidada do aluno (progresso e tarefas) |
| POST | /api/v1/orientador/estagios/{estagioId}/concluir | Conclui o estágio |
| POST | /api/v1/orientador/estagios/{estagioId}/reprovar | Reprova o estágio |

### Coordenador
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /api/v1/coordenador/painel | Indicadores e gráficos do painel (cursoId, semestre, turmaId e orientadorId opcionais) |
| GET, POST, PUT | /api/v1/coordenador/cursos | Lista, cadastra e altera os cursos do coordenador |
| GET, POST, PUT | /api/v1/coordenador/turmas | Lista, cadastra e altera as turmas de um curso |
| GET | /api/v1/coordenador/turmas/orientadores | Lista os orientadores ativos |
| PUT | /api/v1/coordenador/turmas/{id}/orientador | Atribui ou troca o orientador da turma |
| GET, POST, PUT | /api/v1/coordenador/tipos-estagio | Lista, cadastra e altera os tipos de estágio |
| GET, POST, PUT | /api/v1/coordenador/trilhas | Busca, cria e altera a trilha de um tipo de estágio |
| POST | /api/v1/coordenador/trilhas/{id}/tarefas | Adiciona uma tarefa na trilha |
| PUT, DELETE | /api/v1/coordenador/trilhas/tarefas/{tarefaId} | Altera ou remove uma tarefa |
| GET, POST | /api/v1/coordenador/trilhas/tarefas/{tarefaId}/criterios | Lista e adiciona os critérios do checklist da tarefa |
| PUT, DELETE | /api/v1/coordenador/trilhas/criterios/{criterioId} | Altera ou remove (ou desativa, se já usado) um critério |
| GET, POST, PUT | /api/v1/coordenador/pre-cadastros | Lista, inclui e altera o pré-cadastro |
| GET | /api/v1/coordenador/estagios | Lista os estágios em aberto de um curso |
| POST | /api/v1/coordenador/estagios/{estagioId}/desligar | Encerra o estágio por desligamento |
| GET | /api/v1/coordenador/solicitacoes | Lista as solicitações dos cursos do coordenador |
| POST | /api/v1/coordenador/solicitacoes/{id}/aprovar | Aprova a troca de modalidade ou o trancamento |
| POST | /api/v1/coordenador/solicitacoes/{id}/reprovar | Reprova com justificativa |

### Secretaria
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /api/v1/secretaria/pre-cadastros | Lista os pré-cadastrados que ainda não têm conta, com a situação do convite |
| GET | /api/v1/secretaria/solicitacoes | Lista os pedidos de trancamento e de reabertura |
| POST | /api/v1/secretaria/solicitacoes/{id}/aprovar | Aprova (o trancamento segue para a coordenação) |
| POST | /api/v1/secretaria/solicitacoes/{id}/reprovar | Reprova com justificativa |

### Administrador
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /api/v1/admin/auditoria | Consulta os registros com filtros |
| GET | /api/v1/admin/auditoria/acoes | Lista as ações registradas, para o filtro |
| GET | /api/v1/admin/auditoria/{id} | Abre um registro sem máscara e registra a visualização |
| GET | /api/v1/admin/configuracoes | Lista os parâmetros, com valor, padrão e limites |
| PUT | /api/v1/admin/configuracoes/{parametro} | Altera um parâmetro dentro dos limites |
| POST | /api/v1/admin/correcoes/dois-fatores | Desativa o 2FA de um usuário, com justificativa |
| GET | /api/v1/admin/correcoes/estagios/{id} | Busca um estágio para correção |
| POST | /api/v1/admin/correcoes/estagios/{id}/status | Corrige o status de um estágio, com justificativa |

---

## Páginas

| Rota | Perfil | Descrição |
|------|--------|-----------|
| /login | Público | Tela de login, com a segunda etapa do 2FA |
| /primeiro-acesso | Público (token do convite) | Definição de senha e aceite dos termos |
| /recuperar-senha e /redefinir-senha | Público | Pedido do link e troca da senha |
| /termos | Público | Termos de Uso |
| /privacidade | Público | Política de Privacidade e direitos do titular |
| /consentimento | Autenticado | Aceite dos termos de uso |
| /inicio | Autenticado | Redireciona para a página inicial do perfil |
| /conta/seguranca | Autenticado | Ativar/desativar verificação em duas etapas |
| /aluno/dashboard | ALUNO | Painel com a situação do estágio, a carga horária e os avisos de prazo |
| /estagio | ALUNO | Formulário de cadastro ou reenvio |
| /aluno/trilha | ALUNO | Trilha de tarefas, entregas e histórico de versões |
| /aluno/solicitacoes | ALUNO | Troca de modalidade, trancamento e reabertura |
| /orientador/validacoes | ORIENTADOR | Estágios para validar e estágios ativos |
| /orientador/correcoes | ORIENTADOR | Painel de correção |
| /orientador/correcoes/{id} | ORIENTADOR | Avaliação de uma entrega com o checklist |
| /orientador/historico | ORIENTADOR | Histórico das correções |
| /orientador/acompanhamento/{id} | ORIENTADOR | Acompanhamento do aluno, conclusão e reprovação |
| /coordenador/dashboard | COORDENADOR | Painel de indicadores |
| /coordenador/cursos, /turmas, /tipos-estagio, /trilhas | COORDENADOR | Cadastros da coordenação |
| /coordenador/pre-cadastros | COORDENADOR | Lista de pré-cadastro |
| /coordenador/estagios | COORDENADOR | Estágios em aberto e desligamento |
| /coordenador/solicitacoes | COORDENADOR | Decisão das solicitações |
| /coordenador/convites | COORDENADOR | Convites de secretarias |
| /secretaria/dashboard | SECRETARIA | Painel da secretaria |
| /secretaria/enviar-convite | SECRETARIA | Envio de convites |
| /secretaria/convites | SECRETARIA | Convites enviados |
| /secretaria/solicitacoes | SECRETARIA | Pedidos de trancamento e de reabertura |
| /admin/auditoria | ADMINISTRADOR | Consulta da auditoria |
| /admin/convites | ADMINISTRADOR | Convites de coordenadores |
| /admin/configuracoes | ADMINISTRADOR | Parâmetros do sistema |
| /admin/correcoes | ADMINISTRADOR | Correções excepcionais |

---

## Trabalhos Futuros

- Relatório de aderência aos critérios, que mostraria ao coordenador os critérios que mais levam à devolução das entregas
- Recuperação do 2FA pelo próprio usuário, sem depender do administrador

## Projeto Final de Curso (PFC)

Este projeto é desenvolvido como Projeto Final de Conclusão de Curso (PFC) do curso de Engenharia de Software da Universidade de Mogi das Cruzes (UMC).

**Orientador:** Prof. Leonardo Torres  
**Co-orientador:** Prof. Alessandro Silva

**Alunos:**
- Isabela Tamie Shihara
- Leonardo Valdir Silva
