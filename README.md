# Aprova+ - Sistema de Gestão de Estágios Supervisionados

## Sobre o Projeto

O Aprova+ é um sistema web desenvolvido como Projeto Final de Conclusão de Curso (PFC) para a gestão de estágios supervisionados da Universidade de Mogi das Cruzes (UMC).

O sistema centraliza o caminho do estágio em um só lugar: o acesso por convite, o cadastro e a validação do estágio, o cálculo da carga horária, a trilha de tarefas por tipo de estágio, o envio das entregas com histórico de versões, a correção pelo orientador e o acompanhamento pela coordenação, com segurança da informação e adequação à LGPD.

Situação atual: 37 dos 49 requisitos funcionais estão implementados.

---

## Equipe

| Nome | RGM | Principais frentes |
|------|-----|--------------------|
| Leonardo Valdir Silva | 11232100617 | Autenticação e 2FA, convites e pré-cadastro, trilha, entregas e correção (F3, F4 e F5), auditoria, testes |
| Isabela Tamie Shihara | 11231203887 | Cadastro e validação do estágio (F1), progresso e alertas (F2), cadastros e painel do coordenador, direitos do titular (LGPD), avaliação por checklist (F6) |

* Turma: 8B - Engenharia de Software - UMC
* Orientador: Prof. Leonardo Torres
* Coorientador: Prof. Alessandro Silva

---

## Tecnologias Utilizadas

- Java 25
- Spring Boot 4.1.1
- Spring Security 7
- PostgreSQL 18
- Redis 7 (no docker-compose; a lista de tokens revogados no logout passa para ele até a entrega final)
- Thymeleaf - renderização server-side
- Alpine.js - interatividade leve no frontend
- Bootstrap 5 e AdminLTE 4 (painel do coordenador) - estilização
- JWT (jjwt) via cookie HttpOnly com SameSite=Strict
- BCrypt - hash de senhas
- AES-256-GCM - criptografia de dados sensíveis em repouso
- googleauth - autenticação de dois fatores via TOTP (RFC 6238)
- qrcodejs - geração do QR Code do 2FA no navegador
- Resend - API externa de envio de e-mails (convites, recuperação de senha e resultado das entregas)
- BrasilAPI - consulta pública do CNPJ da empresa na Receita Federal
- Cloudflare R2 (API S3) - armazenamento opcional dos arquivos das entregas; sem ele, os arquivos ficam em disco local
- springdoc-openapi - documentação da API (Swagger UI)
- Spring Boot Actuator e Micrometer - métricas da aplicação em /actuator/prometheus
- Prometheus + Grafana - coleta e painéis das métricas (configuração em prometheus.yml)
- JUnit 5, Mockito, H2 e JaCoCo - testes automatizados e cobertura
- Docker + Docker Compose
- Render - hospedagem da aplicação (publicação prevista até a entrega final)

---

## Perfis de Acesso

O sistema tem cinco perfis. Não há cadastro aberto: cada perfil convida apenas o nível logo abaixo dele.

| Perfil | Quem convida | O que faz |
|--------|--------------|-----------|
| ADMINISTRADOR | Criado na instalação (variáveis ADMIN_INICIAL_*) | Convida coordenadores e consulta a auditoria |
| COORDENADOR | Administrador | Cadastra cursos, turmas, tipos de estágio, trilhas e o pré-cadastro; acompanha o painel de indicadores |
| SECRETARIA | Coordenador (pela API; a tela é prevista) ou variáveis SECRETARIA_INICIAL_* | Convida alunos e orientadores que estão no pré-cadastro |
| ORIENTADOR | Secretaria | Valida os estágios, corrige as entregas e acompanha os alunos |
| ALUNO | Secretaria | Cadastra o estágio, acompanha o progresso e envia as entregas da trilha |

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
- Aluno pode reenviar apenas o estágio REJEITADO, com o formulário já preenchido; estágio ATIVO só muda por solicitação de troca de modalidade (prevista)
- Histórico de todas as aprovações e rejeições, com data, orientador e justificativa
- Histórico de estágios por matrícula: só um estágio em aberto por vez, os concluídos e encerrados ficam guardados

### Feature 2 - Cálculo de Progresso e Alertas
- Carga horária cumprida calculada pelas semanas completas desde o início validado, vezes a jornada semanal, sem passar da carga total
- Extrato da carga no painel do aluno, por exemplo "7 semanas × 30 h = 210 h de 270 h"
- Data prevista de conclusão: semanas necessárias (carga total dividida pela jornada, arredondada para cima) a partir do início
- Atividades da trilha aprovadas e restantes
- Aviso de prazo a partir da data de encerramento das entregas da turma, definida pelo coordenador:
  - amarelo de 30 a 11 dias antes;
  - vermelho de 10 a 1 dia antes;
  - "PRAZO VENCE HOJE" no último dia;
  - "PRAZO VENCEU" depois da data.
- O aviso só aparece enquanto houver atividade sem aprovação, e também é exibido ao orientador
- Sem envio de e-mail: o aviso é exibido na tela

### Feature 3 - Trilha Personalizada
- Aluno com estágio ATIVO acessa sua trilha personalizada
- Trilha determinada pelo tipo de estágio do aluno
- Tarefas listadas na ordem definida pelo coordenador, com descrição e link de material de apoio opcional
- Diferentes tipos de estágio possuem trilhas diferentes
- Cada tarefa mostra a situação da entrega (não enviada, enviada, em revisão, aprovada ou devolvida)

### Feature 4 - Painel de Correção Centralizado
- Lista das entregas aguardando análise dos alunos do orientador, com filtros por aluno, tarefa, situação e período, paginada
- Tela de correção com a versão atual ao lado da anterior, o comentário do aluno e o histórico de versões
- Avaliação da entrega: aprovar ou devolver para ajustes; o feedback é obrigatório na devolução
- O aluno recebe um e-mail com o resultado (sem o feedback, que ele lê no sistema)
- Visão consolidada do aluno: estágio, progresso e todas as entregas em uma só tela
- Histórico das correções feitas pelo orientador, paginado

### Feature 5 - Histórico de Versões das Entregas
- Aluno envia a entrega de uma tarefa da trilha, com arquivo e comentário opcional
- Cada envio cria uma nova versão numerada; nenhuma versão é sobrescrita ou removida
- Nova versão só pode ser enviada quando a anterior é devolvida
- Formatos aceitos: PDF, DOCX, XLSX, PPTX, JPG, JPEG e PNG, com até 10 MB
- O formato é conferido pela assinatura dos primeiros bytes do arquivo, e não só pela extensão
- Arquivos gravados em disco local ou no Cloudflare R2, conforme a configuração do ambiente
- Download dos arquivos apenas pelo aluno dono da entrega e pelo orientador do estágio

### Cadastros e Painel do Coordenador
- Cadastro de cursos (com código e-MEC), turmas (semestre, período, modalidade e data de encerramento das entregas), tipos de estágio (carga total e jornada semanal) e trilhas com tarefas ordenadas
- Lista de pré-cadastro de alunos e orientadores: nome, e-mail, identificador institucional (RGM ou matrícula) e turma
- Painel de indicadores dos cursos do coordenador, com seletor de curso:
  - seis indicadores: sem orientador, aguardando validação, em andamento, turmas sem data de encerramento, tarefas aguardando correção e estágios na reta final com pendências;
  - três gráficos por turma: situação dos estágios, tarefas aguardando correção e prazo das entregas.

### Segurança, Acesso e LGPD
- Acesso ao sistema somente por convite, seguindo a hierarquia de perfis (não há cadastro aberto)
- Convite para aluno e orientador só para e-mail do pré-cadastro, com o mesmo perfil
- Primeiro acesso pelo link do convite: definição de senha e aceite dos termos de uso; o aluno já fica matriculado na turma do pré-cadastro
- Recusa do convite pelo próprio link
- Autenticação de dois fatores (2FA), obrigatória para os perfis administrativos
- Tela de segurança da conta para ativar e desativar o 2FA
- Recuperação de senha por e-mail, com link de uso único
- Encerramento do token JWT no logout
- Auditoria das ações de acesso e das operações relevantes, com consulta pelo administrador
- Páginas de Termos de Uso e Política de Privacidade
- Aceite da versão atual dos termos exigido antes das demais telas
- Exercício dos direitos do titular: acesso, correção e anonimização dos dados

---

## Segurança

As regras abaixo estão implementadas no backend. Ocultar botões no frontend não substitui essas verificações.

### Autenticação
- JWT assinado (HMAC) armazenado em cookie HttpOnly com SameSite=Strict e flag Secure em produção
- O JWT nunca é enviado no corpo das respostas: fica apenas no cookie, inacessível ao JavaScript da página
- O browser envia o cookie automaticamente, não é necessário enviar header Authorization nas páginas
- Senhas armazenadas com BCrypt: de 8 a 72 caracteres, com pelo menos uma letra e um número, e confirmação validada no backend (o limite de 72 é o máximo considerado pelo BCrypt)
- Resposta genérica (401) para e-mail inexistente, senha incorreta ou conta inativa, evitando a descoberta de contas cadastradas
- Bloqueio automático da conta após 5 tentativas falhas (senha ou código 2FA), com desbloqueio automático após 5 minutos
- Conta desativada perde o acesso imediatamente, mesmo com JWT ainda dentro da validade (verificação no JwtFilter)
- Validade do JWT: 8 horas
- Logout: o token entra na lista de tokens revogados e é recusado até a sua expiração, mesmo que alguém tenha copiado o cookie antes

### Autenticação de Dois Fatores (2FA)
- Códigos TOTP de 6 dígitos, compatíveis com Google Authenticator, Microsoft Authenticator e similares
- Obrigatória para ADMINISTRADOR, COORDENADOR e SECRETARIA: configurada no primeiro login, antes da emissão do JWT, e não pode ser desativada
- Opcional para ALUNO e ORIENTADOR: ativada e desativada em /conta/seguranca, sempre mediante código válido
- O JWT só é emitido depois da validação do segundo fator
- Token de pré-autenticação: aleatório, de uso único, válido por 2 minutos (jwt.pre-auth-expiration) e armazenado apenas como hash SHA-256
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

### Tokens de uso único
- Convite: válido por 24 horas; o banco guarda apenas o hash SHA-256 do token
- Recuperação de senha: válido por 30 minutos (aprova.recuperacao-senha.validade-minutos), uso único; a resposta da solicitação é a mesma para e-mail existente ou não

### Autorização
- Defense in depth: regras de acesso por rota no SecurityConfig e @PreAuthorize nos controllers
- Acesso negado por padrão (anyRequest().authenticated()): 401 sem login e 403 para perfil sem permissão
- Operações sempre buscam os dados do usuário autenticado (authentication.getName())
- Orientador só aprova, rejeita, corrige ou consulta estágios e entregas vinculados a ele: a checagem é feita na própria consulta ao banco, e registros de outros orientadores retornam 404
- Aluno só cadastra estágio com tipo do próprio curso e só acessa as próprias entregas
- Coordenador só vê e altera os cursos que coordena (RN21)
- Hierarquia de convites: perfil fora da hierarquia do remetente recebe 403
- Os controllers não acessam os repositórios: toda regra passa pela camada de serviço (RNF05)
- HTTPS obrigatório no perfil prod (requiresSecure)
- Swagger e /v3/api-docs desativados no perfil prod

### Validação de Entrada
- Todos os formulários são validados no frontend, para avisar antes do envio, e de novo no backend com Bean Validation (@Valid), porque a validação da tela pode ser contornada chamando a API direto
- Erro de validação retorna 400 com a mensagem do primeiro erro e o mapa `campos`, com o erro de cada campo; as telas marcam o campo correspondente
- JSON inválido ou data fora do formato retorna 400, sem expor detalhes internos
- Regras: e-mail com até 254 caracteres; nome da empresa entre 2 e 150 caracteres; data de início no máximo 1 ano no futuro; CNPJ com dígito verificador válido; justificativa de rejeição com até 1000 caracteres; arquivo de entrega com até 10 MB e formato conferido pela assinatura
- Dados inseridos no HTML dos e-mails passam por escape, para que um nome com marcação HTML não vire um link falso

| Perfil | Pode acessar |
|--------|--------------|
| ALUNO | painel com progresso, cadastro de estágio, trilha e entregas, segurança da conta, direitos LGPD |
| ORIENTADOR | validação dos estágios, painel de correção, visão consolidada do aluno, histórico de correções, segurança da conta, direitos LGPD |
| COORDENADOR | painel de indicadores, cursos, turmas, tipos de estágio, trilhas, pré-cadastro, segurança da conta, direitos LGPD |
| SECRETARIA | envio e acompanhamento de convites, segurança da conta, direitos LGPD |
| ADMINISTRADOR | convites de coordenadores, consulta da auditoria, segurança da conta |

### Criptografia
- Em trânsito: HTTPS (TLS) no perfil prod
- Senhas: hash BCrypt, nunca armazenadas nem registradas em log em texto puro
- Em repouso: AES-256-GCM (AesEncryptor, chave derivada de ENCRYPTION_SECRET com SHA-256) nos campos:
  - segredo do 2FA (usuarios.dois_fatores_segredo)
  - justificativa de rejeição do estágio (estagios.justificativa_rejeicao)
  - justificativa de cada decisão do orientador no histórico (validacoes_estagio.justificativa)
  - comentário do aluno e feedback do orientador em cada versão da entrega (entrega_versoes.comentario_aluno e entrega_versoes.feedback)
- Tokens de convite, de pré-autenticação e de logout guardados apenas como hash SHA-256
- A justificativa e o feedback cifrados não são copiados para o log de auditoria
- RGM e matrícula institucional não são cifrados por decisão de projeto: são identificadores únicos usados em buscas por secretaria, coordenação e orientador. A cifragem AES-GCM gera um valor diferente a cada gravação, o que impediria a restrição de unicidade e a busca. Esses campos são protegidos pelo controle de acesso por perfil.

### Auditoria
Os eventos são gravados de forma assíncrona na tabela registros_auditoria, com usuário, e-mail, perfil, ação, detalhes, IP de origem, resultado e data/hora. Também são registrados no arquivo logs/auditoria.log. O administrador consulta os registros em /admin/auditoria, com filtros por e-mail, ação, resultado e período.

| Grupo | Eventos |
|-------|---------|
| Login | LOGIN_SUCESSO, LOGIN_FALHA, LOGOUT |
| 2FA | LOGIN_2FA_REQUERIDO, LOGIN_CONFIGURACAO_2FA_REQUERIDA, LOGIN_2FA_FALHA, DOIS_FATORES_ATIVADO, DOIS_FATORES_DESATIVADO, DOIS_FATORES_FALHA |
| Senha | RECUPERACAO_SENHA_SOLICITADA, SENHA_REDEFINIDA |
| Acesso ao sistema | CONVITE_ENVIADO, PRIMEIRO_ACESSO_ACEITO, PRIMEIRO_ACESSO_RECUSADO, CONTA_INICIAL_CRIADA, MATRICULA_CRIADA |
| Coordenação | CURSO_CADASTRADO, CURSO_ALTERADO, TURMA_CADASTRADA, TURMA_ALTERADA, TIPO_ESTAGIO_CADASTRADO, TIPO_ESTAGIO_ALTERADO, TRILHA_CRIADA, TRILHA_ALTERADA, TAREFA_ADICIONADA, TAREFA_ALTERADA, TAREFA_REMOVIDA, PRE_CADASTRO_INCLUIDO, PRE_CADASTRO_ALTERADO |
| Estágio | ESTAGIO_CADASTRADO, ESTAGIO_APROVADO, ESTAGIO_REJEITADO |
| Entregas | ENTREGA_ENVIADA, ENTREGA_APROVADA, ENTREGA_DEVOLVIDA |
| LGPD | CONSENTIMENTO_ACEITO, SOLICITACAO_ACESSO_DADOS, SOLICITACAO_CORRECAO_DADOS, ANONIMIZACAO_EXECUTADA |
| Administração | AUDITORIA_CONSULTADA |

Consulta rápida:
```
SELECT acao, email_tentativa, ip_origem, sucesso, detalhes, criado_em
FROM registros_auditoria ORDER BY id DESC LIMIT 20;
```

### LGPD
- Termos de Uso (/termos) e Política de Privacidade (/privacidade) disponíveis sem login
- Consentimento registrado com data e versão do termo aceito (versão atual: 1.0, definida em ConsentimentoService); quando a versão muda, o aceite é pedido de novo antes das demais telas
- Contato do encarregado de proteção de dados (DPO) no rodapé de todas as páginas (art. 41 da LGPD)
- Direitos do titular na tela de privacidade: solicitar acesso, solicitar correção e anonimizar os dados
- Anonimização substitui nome, e-mail, senha e identificadores por valores neutros e desativa a conta; os registros acadêmicos de estágio são preservados (Lei 11.788/2008)
- Transferência internacional (Resend e Cloudflare, nos EUA) declarada na Política de Privacidade

### Riscos Residuais e Decisões de Projeto
- Um código TOTP pode ser reutilizado dentro da sua janela de aproximadamente 30 segundos
- A lista de tokens revogados no logout fica hoje na memória da aplicação, o que basta para uma instância; a migração para o Redis está prevista até a entrega final, para valer também com mais de uma instância e após reinícios
- Conta bloqueada por tentativas falhas mantém a sessão já aberta: o bloqueio protege o login contra força bruta. A revogação imediata do acesso é feita desativando a conta (ativo = false)
- A resposta 423 (conta bloqueada) só ocorre para e-mail existente após 5 tentativas, mantida por usabilidade
- CSRF desabilitado, mitigado pelo cookie SameSite=Strict
- Perda do dispositivo autenticador: não há recuperação automática; o administrador desativa o 2FA da conta no banco após confirmar a identidade do usuário
- O esquema do banco é atualizado pelo Hibernate (ddl-auto=update), sem migrações versionadas
- O registro de auditoria é assíncrono e pode perder eventos em caso de falha; quem tem acesso direto ao banco pode alterar os registros
- A chave de criptografia não tem rotação: trocá-la torna ilegíveis os dados já cifrados
- Os arquivos enviados são conferidos pela assinatura do formato, mas não passam por antivírus

---

## Integração com API Externa (Resend)

O envio dos e-mails é feito pela API do Resend (EmailService), serviço de e-mail transacional sediado nos EUA.

| Item | Descrição |
|------|-----------|
| Finalidade | Convites de primeiro acesso, recuperação de senha e resultado da avaliação das entregas |
| Dados enviados | E-mail e nome do destinatário e conteúdo da mensagem (link de convite válido por 24 horas, link de recuperação válido por 30 minutos, nome da tarefa e resultado) |
| Autenticação | Chave de API na variável RESEND_API_KEY (nunca fixa no código) |
| Execução | Assíncrona (@Async): a falha no envio é registrada em log e não interrompe a requisição |
| Transferência internacional | Sim (EUA), declarada na Política de Privacidade |

Observação: com o remetente de testes do Resend (onboarding@resend.dev), os e-mails só são entregues ao endereço da conta cadastrada no Resend. Para enviar a qualquer endereço é necessário verificar um domínio próprio. No perfil dev, o link do convite também aparece no log do backend:
```
docker compose logs backend | grep "Link de primeiro acesso"
```
(no Windows, use `findstr` no lugar de `grep`)

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

## Armazenamento dos Arquivos (disco local ou Cloudflare R2)

Os arquivos das entregas passam pela interface ArmazenamentoArquivos, com duas implementações escolhidas pela configuração do ambiente (ArmazenamentoConfig):

| Implementação | Quando é usada | Onde ficam os arquivos |
|---------------|----------------|------------------------|
| ArmazenamentoLocal | Sem as variáveis R2_* (padrão no desenvolvimento e nos testes) | Pasta definida em ARMAZENAMENTO_DIRETORIO (padrão: uploads, montada em ./uploads no docker-compose) |
| ArmazenamentoR2 | Com R2_ENDPOINT, R2_ACCESS_KEY, R2_SECRET_KEY e R2_BUCKET preenchidas | Bucket do Cloudflare R2, pela API S3 |

As regras de negócio não dependem de qual implementação está ativa.

---

## Monitoramento

- O Spring Boot Actuator expõe /actuator/health e /actuator/prometheus
- O Prometheus coleta as métricas a partir de /actuator/prometheus, conforme o arquivo prometheus.yml da raiz
- O Grafana apresenta as métricas em painéis
- Os serviços do Prometheus e do Grafana entram no docker-compose até a entrega final
- O Sentry, previsto no início do projeto, foi retirado: exigiria mais uma conta externa, e os erros enviados a ele poderiam conter dados pessoais. Os erros são acompanhados pelos logs da aplicação

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
- Docker e Docker Compose instalados
- Java 25
- Maven (ou o wrapper ./mvnw do projeto)

### Passos

**1. Clone o repositório:**
```
git clone https://github.com/isabela-tamie15/aprova_mais.git
cd aprova_mais
```

**2. Configure o arquivo .env na raiz do projeto:**
```
DB_URL=jdbc:postgresql://postgres:5432/dbaprovamais
DB_USERNAME=usuario_aprovamais
DB_PASSWORD=sua_senha

MAIL_USERNAME=seu@email.com
MAIL_PASSWORD=sua_senha_app

SSL_KEYSTORE_PASSWORD=senha123

# Mínimo de 32 caracteres (assinatura HMAC-SHA256 do JWT)
JWT_SECRET=aprova_mais_chave_secreta_super_longa_2026

# Chave da criptografia AES: use um valor aleatório e longo
ENCRYPTION_SECRET=troque_por_uma_chave_aleatoria_longa

RESEND_API_KEY=sua_chave_resend
APP_URL=http://localhost:8080

# Administrador inicial: criado na inicialização apenas se não existir nenhum administrador
ADMIN_INICIAL_EMAIL=admin@teste.com
ADMIN_INICIAL_SENHA=senha_com_8_ou_mais_caracteres
ADMIN_INICIAL_NOME=Administrador

# Secretaria inicial: criada na inicialização apenas se não existir nenhuma secretaria
SECRETARIA_INICIAL_EMAIL=secretaria@teste.com
SECRETARIA_INICIAL_SENHA=senha_com_8_ou_mais_caracteres
SECRETARIA_INICIAL_NOME=Secretaria Acadêmica

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

# Opcional: pasta dos arquivos das entregas (padrão: uploads)
# ARMAZENAMENTO_DIRETORIO=uploads

# Opcional (perfil prod): Cloudflare R2. Sem estas variáveis, os arquivos ficam em disco local
# R2_ENDPOINT=https://<conta>.r2.cloudflarestorage.com
# R2_ACCESS_KEY=
# R2_SECRET_KEY=
# R2_BUCKET=

SPRING_PROFILES_ACTIVE=dev
```

O arquivo .env está no .gitignore e nunca deve ser versionado. Alterar ENCRYPTION_SECRET depois que existirem dados cifrados torna esses dados ilegíveis.

**3. Gere o JAR:**
```
./mvnw clean package -DskipTests
```

**4. Suba os containers:**
```
docker compose up --build
```

**5. Acesse:**
http://localhost:8080/login

Documentação da API: http://localhost:8080/swagger-ui.html
Métricas: http://localhost:8080/actuator/prometheus

**6. Rode os testes:**
```
./mvnw test
```
Os testes usam o banco H2 em memória (perfil test), não precisam do Docker nem do .env e não chamam a BrasilAPI. São mais de 260 testes, entre unitários e de integração. O relatório de cobertura do JaCoCo é gerado em target/site/jacoco/index.html.

### Perfil de produção
- Porta 8443 com HTTPS (keystore.p12 e SSL_KEYSTORE_PASSWORD)
- Swagger desativado e logs em nível INFO
- Arquivos no Cloudflare R2 quando as variáveis R2_* estão preenchidas
- Hospedagem no Render prevista até a entrega final

---

## Primeiro Uso

Com o banco vazio, a primeira subida cria as tabelas (Hibernate) e as contas iniciais definidas no .env (ContaInicialRunner): o administrador e a secretaria. A partir delas, o sistema é preenchido pelas próprias telas, seguindo a hierarquia de perfis:

1. O administrador entra (configurando o 2FA no primeiro login) e convida um coordenador em /admin/convites.
2. O coordenador aceita o convite e cadastra o curso, as turmas (com a data de encerramento das entregas), os tipos de estágio e as trilhas com as tarefas.
3. O coordenador inclui os alunos e os orientadores na lista de pré-cadastro, com a turma e o identificador institucional.
4. A secretaria envia os convites para os e-mails do pré-cadastro.
5. Alunos e orientadores aceitam o convite, definem a senha e entram. O aluno já fica matriculado na turma do pré-cadastro e pode cadastrar o estágio.

| Perfil | E-mail | Senha | 2FA |
|--------|--------|-------|-----|
| ADMINISTRADOR | valor de ADMIN_INICIAL_EMAIL | valor de ADMIN_INICIAL_SENHA | Obrigatório (QR Code no primeiro login) |
| SECRETARIA | valor de SECRETARIA_INICIAL_EMAIL | valor de SECRETARIA_INICIAL_SENHA | Obrigatório (QR Code no primeiro login) |
| Demais perfis | e-mail do convite | definida no primeiro acesso | Obrigatório para COORDENADOR; opcional para ORIENTADOR e ALUNO |

Ao alterar entidades, recrie o banco com `docker compose down -v` antes de subir novamente.

Comandos úteis durante os testes:
```
-- desbloquear uma conta sem esperar 5 minutos
UPDATE usuarios SET conta_bloqueada = false, tentativas_falhas = 0, momento_bloqueio = NULL WHERE email = '...';

-- desativar o 2FA de uma conta (ex.: perda do celular)
UPDATE usuarios SET dois_fatores_ativo = false, dois_fatores_segredo = NULL WHERE email = '...';
```

---

## Estrutura do Projeto

O código é organizado em pacotes por camada. Os controllers não acessam os repositórios: toda regra de negócio passa pela camada de serviço.

**src/main/java/tcc/ges/aprovamais/**
```
config/      - Configurações: SecurityConfig, AsyncConfig, WebConfig, OpenApiConfig,
               ArmazenamentoConfig, ConsentimentoInterceptor, ContaInicialRunner
controller/  - Controllers REST e de página, um por área: autenticação, 2FA, recuperação de senha,
               convites, pré-cadastro, cursos, turmas, tipos de estágio, trilhas, painel do coordenador,
               estágio do aluno, validação, entregas do aluno, correção, acompanhamento, auditoria, LGPD
dto/         - Objetos de transferência de dados (requests e responses)
entity/      - Entidades JPA e enums
exception/   - Tratamento global de exceções
external/    - Integrações externas: EmailService (Resend) e ConsultaCnpjService (BrasilAPI)
repository/  - Repositórios Spring Data JPA
security/    - JwtService, JwtFilter, AesEncryptor, HashToken, TokenRevogadoService, UserDetailsServiceImpl
service/     - Lógica de negócio: AuthService, DoisFatoresService, BloqueioContaService,
               RecuperacaoSenhaService, ConviteService, PreCadastroService, PrimeiroAcessoService,
               ContaInicialService, CursoService, TurmaService, TipoEstagioService,
               TrilhaCoordenadorService, TrilhaService, EstagioService, CalculadoraCargaHoraria,
               PendenciasEstagio, ValidadorCnpj, EntregaService, ValidadorArquivoEntrega,
               CorrecaoService, VisaoAlunoService, PainelCoordenadorService, AuditoriaService,
               ConsultaAuditoriaService, ConsentimentoService, AnonimizacaoService
storage/     - ArmazenamentoArquivos, com as implementações ArmazenamentoLocal e ArmazenamentoR2
```

**src/main/resources/**
```
templates/
├── login.html
├── primeiro-acesso.html
├── primeiro-acesso-erro.html
├── recuperar-senha.html
├── redefinir-senha.html
├── consentimento.html
├── termos.html
├── privacidade.html
├── admin/
│   ├── auditoria.html
│   └── convites.html
├── aluno/
│   ├── dashboard.html
│   ├── estagio.html
│   └── trilha.html
├── orientador/
│   ├── validacoes.html
│   ├── correcoes.html
│   ├── correcao.html
│   ├── acompanhamento.html
│   └── historico.html
├── coordenador/
│   ├── dashboard.html
│   ├── cursos.html
│   ├── turmas.html
│   ├── tipos-estagio.html
│   ├── trilhas.html
│   └── pre-cadastros.html
├── secretaria/
│   ├── dashboard.html
│   ├── enviar-convite.html
│   └── convites.html
├── conta/
│   └── seguranca.html
└── fragments/
    ├── navbar-admin.html
    ├── navbar-coordenador.html
    ├── navbar-orientador.html
    └── rodape-lgpd.html
static/
├── css/
│   ├── main.css          estilos usados em várias páginas
│   └── paginas/          um arquivo por página (ex.: secretaria-convites.css)
└── js/
    ├── comum.js          funções usadas em várias páginas (logout)
    ├── app.js            login e segurança da conta
    └── paginas/          um arquivo por página (ex.: aluno-estagio.js)
```

As páginas não têm `<script>` nem `<style>` dentro do HTML. O nome do arquivo segue o caminho do template: `templates/aluno/estagio.html` usa `js/paginas/aluno-estagio.js`. As referências usam `th:src="@{...}"` e `th:href="@{...}"`, que colocam um hash do conteúdo no nome do arquivo: quando ele muda, o navegador baixa a versão nova em vez de usar o cache. Os scripts que definem componentes do Alpine ficam no fim do `<body>`, sem `defer`, para rodar antes do Alpine.

---

## Endpoints da API

### Autenticação
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /api/v1/auth/login | Realiza login e cria cookie JWT (ou inicia a segunda etapa) |
| POST | /api/v1/auth/logout | Apaga o cookie e revoga o token JWT |
| POST | /api/v1/auth/2fa/verificar | Segunda etapa do login (conta com 2FA ativo) |
| POST | /api/v1/auth/2fa/configuracao/iniciar | Gera o QR Code no primeiro login de perfil com 2FA obrigatório |
| POST | /api/v1/auth/2fa/configuracao/confirmar | Confirma o primeiro código, ativa o 2FA e cria o cookie JWT |
| POST | /api/v1/auth/recuperacao/solicitar | Envia o link de recuperação de senha por e-mail |
| POST | /api/v1/auth/recuperacao/redefinir | Define a nova senha com o token do link |

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
| POST | /primeiro-acesso/aceitar?token= | Define a senha, aceita os termos, ativa a conta e cria a matrícula do aluno |
| POST | /primeiro-acesso/recusar?token= | Recusa o convite |

### Convites (administrador, coordenador e secretaria)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /api/v1/convites | Envia convite de acesso por e-mail (Resend), respeitando a hierarquia de perfis |
| GET | /api/v1/convites | Lista os convites enviados pelo usuário |

### Coordenador
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /api/v1/coordenador/painel | Indicadores e gráficos do painel (curso opcional) |
| GET, POST | /api/v1/coordenador/cursos | Lista e cadastra cursos |
| PUT | /api/v1/coordenador/cursos/{id} | Altera um curso |
| GET, POST | /api/v1/coordenador/turmas | Lista e cadastra turmas |
| PUT | /api/v1/coordenador/turmas/{id} | Altera uma turma |
| GET, POST | /api/v1/coordenador/tipos-estagio | Lista e cadastra tipos de estágio |
| PUT | /api/v1/coordenador/tipos-estagio/{id} | Altera um tipo de estágio |
| GET, POST | /api/v1/coordenador/trilhas | Lista e cria trilhas |
| PUT | /api/v1/coordenador/trilhas/{id} | Altera uma trilha |
| POST | /api/v1/coordenador/trilhas/{id}/tarefas | Adiciona uma tarefa à trilha |
| PUT | /api/v1/coordenador/trilhas/tarefas/{tarefaId} | Altera uma tarefa |
| DELETE | /api/v1/coordenador/trilhas/tarefas/{tarefaId} | Remove uma tarefa |
| GET, POST | /api/v1/coordenador/pre-cadastros | Lista e inclui pré-cadastros |
| PUT | /api/v1/coordenador/pre-cadastros/{id} | Altera um pré-cadastro |

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
| POST | /api/v1/aluno/tarefas/{tarefaId}/entregas | Envia uma nova versão da entrega (arquivo e comentário) |
| GET | /api/v1/aluno/tarefas/{tarefaId}/entrega | Detalhe da entrega com o histórico de versões |
| GET | /api/v1/aluno/entregas/versoes/{versaoId}/arquivo | Baixa o arquivo de uma versão |

### Orientador
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /api/v1/orientador/validacoes | Lista os estágios do orientador para a validação |
| POST | /api/v1/orientador/validacoes/{id}/aprovar | Aprova um estágio (somente estágios do próprio orientador), com a data de início confirmada opcional |
| GET | /api/v1/orientador/validacoes/{id}/historico | Lista as aprovações e rejeições de um estágio do orientador |
| POST | /api/v1/orientador/validacoes/{id}/rejeitar | Rejeita um estágio com justificativa (máximo de 1000 caracteres) |
| GET | /api/v1/orientador/entregas | Painel de correção: entregas aguardando análise, com filtros e paginação |
| GET | /api/v1/orientador/entregas/{id} | Detalhe da entrega para a correção |
| POST | /api/v1/orientador/entregas/{id}/avaliacao | Aprova ou devolve a entrega com feedback |
| GET | /api/v1/orientador/entregas/historico | Histórico das correções do orientador, paginado |
| GET | /api/v1/orientador/entregas/versoes/{versaoId}/arquivo | Baixa o arquivo de uma versão |
| GET | /api/v1/orientador/estagios/{estagioId}/visao | Visão consolidada do aluno |

### Administrador
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /api/v1/admin/auditoria | Consulta a auditoria com filtros e paginação |
| GET | /api/v1/admin/auditoria/acoes | Lista as ações disponíveis para o filtro |

### LGPD (perfis com dados pessoais)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /api/v1/consentimento/aceitar | Registra o aceite dos termos com data e versão |
| POST | /api/v1/lgpd/solicitar-acesso | Registra solicitação de acesso aos dados |
| POST | /api/v1/lgpd/solicitar-correcao | Registra solicitação de correção dos dados |
| DELETE | /api/v1/lgpd/meus-dados | Anonimiza os dados do titular e desativa a conta |

---

## Páginas

| Rota | Perfil | Descrição |
|------|--------|-----------|
| /login | Público | Tela de login, com a segunda etapa do 2FA |
| /recuperar-senha | Público | Solicitação do link de recuperação de senha |
| /redefinir-senha | Público (token do e-mail) | Definição da nova senha |
| /primeiro-acesso | Público (token do convite) | Definição de senha e aceite dos termos |
| /termos | Público | Termos de Uso |
| /privacidade | Público | Política de Privacidade e direitos do titular |
| /consentimento | Autenticado | Aceite da versão atual dos termos |
| /inicio | Autenticado | Redireciona para a página inicial do perfil |
| /conta/seguranca | Autenticado | Ativar/desativar verificação em duas etapas |
| /aluno/dashboard | ALUNO | Painel com o estágio, a carga horária, as atividades e o aviso de prazo |
| /estagio | ALUNO | Formulário de cadastro ou reenvio |
| /aluno/trilha | ALUNO | Trilha de tarefas, envio das entregas e histórico de versões |
| /orientador/validacoes | ORIENTADOR | Estágios para validar e estágios ativos |
| /orientador/correcoes | ORIENTADOR | Painel de correção |
| /orientador/correcoes/{entregaId} | ORIENTADOR | Correção de uma entrega |
| /orientador/acompanhamento/{estagioId} | ORIENTADOR | Visão consolidada do aluno |
| /orientador/historico | ORIENTADOR | Histórico de correções |
| /coordenador/dashboard | COORDENADOR | Painel de indicadores |
| /coordenador/cursos | COORDENADOR | Cadastro de cursos |
| /coordenador/turmas | COORDENADOR | Cadastro de turmas |
| /coordenador/tipos-estagio | COORDENADOR | Cadastro de tipos de estágio |
| /coordenador/trilhas | COORDENADOR | Montagem das trilhas e tarefas |
| /coordenador/pre-cadastros | COORDENADOR | Lista de pré-cadastro |
| /secretaria/dashboard | SECRETARIA | Painel da secretaria |
| /secretaria/enviar-convite | SECRETARIA | Envio de convites |
| /secretaria/convites | SECRETARIA | Convites enviados |
| /admin/convites | ADMINISTRADOR | Convites de coordenadores |
| /admin/auditoria | ADMINISTRADOR | Consulta da auditoria |

---

## Funcionalidades Previstas (versões futuras)

- Atribuição de um orientador por turma pelo coordenador, com transferência dos estágios em aberto (RF13)
- Conclusão e encerramento do estágio, com o motivo registrado (RF23 e RF24)
- Solicitações de troca de modalidade, trancamento e reabertura, com prazo de arrependimento (RF25 a RF28)
- Desconto das semanas de suspensão no cálculo da carga horária
- Configurações do sistema editáveis pelo administrador e correção de dados em situações excepcionais (RF48 e RF49)
- E-mail ao aluno na validação do estágio e controle automático do prazo dos pedidos do titular e da eliminação da auditoria (RF19, RF43 e RF45)
- Convite de secretarias pela tela do coordenador (RF03)
- Migrações versionadas do banco com o Flyway, rotação da chave de criptografia, auditoria encadeada por hash, verificação dos arquivos por antivírus, recuperação do 2FA sem intervenção manual e domínio próprio verificado no Resend

---

## Projeto Final de Curso (PFC)

Este projeto é desenvolvido como Projeto Final de Conclusão de Curso (PFC) do curso de Engenharia de Software da Universidade de Mogi das Cruzes (UMC).

**Orientador:** Prof. Leonardo Torres  
**Co-orientador:** Prof. Alessandro Silva

**Alunos:**
- Isabela Tamie Shihara
- Leonardo Valdir Silva
