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
- Redis (reservado para blocklist de logout - implementação futura)
- Thymeleaf - renderização server-side
- Alpine.js - interatividade leve no frontend
- Bootstrap 5 - estilização
- JWT (jjwt) via cookie HttpOnly com SameSite=Strict
- BCrypt - hash de senhas
- AES-256-GCM - criptografia de dados sensíveis em repouso
- googleauth - autenticação de dois fatores via TOTP (RFC 6238)
- qrcodejs - geração do QR Code do 2FA no navegador
- Resend - API externa de envio de e-mails (convites)
- BrasilAPI - consulta pública do CNPJ da empresa na Receita Federal
- springdoc-openapi - documentação da API (Swagger UI)
- Docker + Docker Compose
- Prometheus + Grafana (monitoramento)

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
- Carga horária cumprida calculada pelas semanas desde o início vezes a jornada semanal, e data prevista de conclusão

### Feature 2 - Trilha Personalizada
- Aluno com estágio ATIVO acessa sua trilha personalizada
- Trilha determinada pelo tipo de estágio escolhido
- Tarefas listadas em ordem definida pelo coordenador
- Diferentes tipos de estágio possuem trilhas diferentes

### Feature 3 - Segurança, Acesso e LGPD
- Acesso ao sistema somente por convite enviado pela secretaria (não há cadastro aberto)
- Primeiro acesso pelo link do convite: definição de senha e aceite dos termos de uso
- Autenticação de dois fatores (2FA), obrigatória para perfis administrativos
- Tela de segurança da conta para ativar e desativar o 2FA
- Auditoria das ações de acesso e das operações relevantes
- Páginas de Termos de Uso e Política de Privacidade
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

### Autenticação de Dois Fatores (2FA)
- Códigos TOTP de 6 dígitos, compatíveis com Google Authenticator, Microsoft Authenticator e similares
- Obrigatória para SECRETARIA e COORDENADOR: configurada no primeiro login, antes da emissão do JWT, e não pode ser desativada
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

### Autorização
- Defense in depth: regras de acesso por rota no SecurityConfig e @PreAuthorize nos controllers
- Acesso negado por padrão (anyRequest().authenticated())
- Operações sempre buscam os dados do usuário autenticado (authentication.getName())
- Orientador só aprova, rejeita ou consulta o histórico de estágios vinculados a ele: a checagem é feita na própria consulta ao banco e estágios de outros orientadores retornam 404
- Aluno só cadastra estágio com tipo do próprio curso: tipo de outro curso retorna 404, igual a um id inexistente
- HTTPS obrigatório no perfil prod (requiresSecure)
- Swagger e /v3/api-docs desativados no perfil prod

### Validação de Entrada
- Todos os formulários são validados no frontend, para avisar antes do envio, e de novo no backend com Bean Validation (@Valid), porque a validação da tela pode ser contornada chamando a API direto
- Erro de validação retorna 400 com a mensagem do primeiro erro e o mapa `campos`, com o erro de cada campo; as telas marcam o campo correspondente
- JSON inválido ou data fora do formato retorna 400, sem expor detalhes internos
- Regras: e-mail com até 254 caracteres; nome da empresa entre 2 e 150 caracteres; data de início no máximo 1 ano no futuro; CNPJ com dígito verificador válido; justificativa de rejeição com até 1000 caracteres

| Perfil | Pode acessar |
|--------|--------------|
| ALUNO | cadastro de estágio, trilha, segurança da conta, direitos LGPD |
| ORIENTADOR | validação dos estágios da sua turma, segurança da conta, direitos LGPD |
| COORDENADOR | painel da coordenação, segurança da conta, direitos LGPD |
| SECRETARIA | envio e acompanhamento de convites, segurança da conta, direitos LGPD |

### Criptografia
- Em trânsito: HTTPS (TLS) no perfil prod
- Senhas: hash BCrypt, nunca armazenadas nem registradas em log em texto puro
- Em repouso: AES-256-GCM (AesEncryptor, chave derivada de ENCRYPTION_SECRET com SHA-256) nos campos:
  - segredo do 2FA (usuarios.dois_fatores_segredo)
  - justificativa de rejeição do estágio (estagios.justificativa_rejeicao)
  - justificativa de cada decisão do orientador no histórico (validacoes_estagio.justificativa)
- A justificativa cifrada não é copiada para o log de auditoria
- RGM e matrícula institucional não são cifrados por decisão de projeto: são identificadores únicos usados em buscas por secretaria, coordenação e orientador. A cifragem AES-GCM gera um valor diferente a cada gravação, o que impediria a restrição de unicidade e a busca. Esses campos são protegidos pelo controle de acesso por perfil.

### Auditoria
Os eventos são gravados de forma assíncrona na tabela registros_auditoria, com usuário, e-mail, perfil, ação, detalhes, IP de origem, resultado e data/hora. Também são registrados no arquivo logs/auditoria.log.

| Grupo | Eventos |
|-------|---------|
| Login | LOGIN_SUCESSO, LOGIN_FALHA, LOGOUT |
| 2FA | LOGIN_2FA_REQUERIDO, LOGIN_CONFIGURACAO_2FA_REQUERIDA, LOGIN_2FA_FALHA, DOIS_FATORES_ATIVADO, DOIS_FATORES_DESATIVADO, DOIS_FATORES_FALHA |
| Acesso ao sistema | CONVITE_ENVIADO, PRIMEIRO_ACESSO_ACEITO, PRIMEIRO_ACESSO_RECUSADO, CONTA_INICIAL_CRIADA |
| Estágio | ESTAGIO_CADASTRADO, ESTAGIO_APROVADO, ESTAGIO_REJEITADO |
| LGPD | CONSENTIMENTO_ACEITO, SOLICITACAO_ACESSO_DADOS, SOLICITACAO_CORRECAO_DADOS, ANONIMIZACAO_EXECUTADA |

Consulta rápida:
```
SELECT acao, email_tentativa, ip_origem, sucesso, detalhes, criado_em
FROM registros_auditoria ORDER BY id DESC LIMIT 20;
```

### LGPD
- Termos de Uso (/termos) e Política de Privacidade (/privacidade) disponíveis sem login
- Consentimento registrado com data e versão do termo aceito (versão atual: 1.0, definida em ConsentimentoService)
- Contato do encarregado de proteção de dados (DPO) no rodapé de todas as páginas (art. 41 da LGPD)
- Direitos do titular na tela de privacidade: solicitar acesso, solicitar correção e anonimizar os dados
- Anonimização substitui nome, e-mail, senha e identificadores por valores neutros e desativa a conta; os registros acadêmicos de estágio são preservados (Lei 11.788/2008)

### Riscos Residuais e Decisões de Projeto
- Um código TOTP pode ser reutilizado dentro da sua janela de aproximadamente 30 segundos
- O logout apaga o cookie, mas não revoga o JWT no servidor (sessão stateless); mitigado pelo cookie HttpOnly e SameSite=Strict e pela validade de 8 horas. Blocklist no Redis prevista
- Conta bloqueada por tentativas falhas mantém a sessão já aberta: o bloqueio protege o login contra força bruta. A revogação imediata do acesso é feita desativando a conta (ativo = false)
- A resposta 423 (conta bloqueada) só ocorre para e-mail existente após 5 tentativas, mantida por usabilidade
- CSRF desabilitado, mitigado pelo cookie SameSite=Strict
- Perda do dispositivo autenticador: não há recuperação automática; o administrador desativa o 2FA da conta no banco após confirmar a identidade do usuário

---

## Integração com API Externa (Resend)

O envio dos e-mails de convite é feito pela API do Resend (EmailService), serviço de e-mail transacional sediado nos EUA.

| Item | Descrição |
|------|-----------|
| Finalidade | Enviar o link de primeiro acesso aos usuários convidados pela secretaria |
| Dados enviados | E-mail do destinatário e conteúdo da mensagem (link de convite, válido por 24 horas) |
| Autenticação | Chave de API na variável RESEND_API_KEY (nunca fixa no código) |
| Execução | Assíncrona (@Async): a falha no envio é registrada em log e não interrompe a requisição |
| Transferência internacional | Sim (EUA), declarada na Política de Privacidade |

Observação: com o remetente de testes do Resend (onboarding@resend.dev), os e-mails só são entregues ao endereço da conta cadastrada no Resend. Para enviar a qualquer endereço é necessário verificar um domínio próprio.

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
- Maven

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

# Secretaria inicial: criada na inicialização apenas se não existir nenhuma secretaria
SECRETARIA_INICIAL_EMAIL=secretaria@teste.com
SECRETARIA_INICIAL_SENHA=senha_com_8_ou_mais_caracteres
SECRETARIA_INICIAL_NOME=Secretaria Acadêmica

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

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

**6. Rode os testes:**
```
./mvnw test
```
Os testes usam o banco H2 em memória (perfil test), não precisam do Docker nem do .env e não chamam a BrasilAPI.

---

## Seed de Dados

Após subir o Docker, conecte no pgAdmin em localhost:5433 e rode o script de seed (docs/seed.sql).

Observação: o script de seed está sendo reestruturado e será adicionado ao repositório posteriormente.

Usuários disponíveis após o seed:

| Perfil | Email | Senha | 2FA |
|--------|-------|-------|-----|
| COORDENADOR | coordenador@teste.com | senha123 | Obrigatório (QR Code no primeiro login) |
| ORIENTADOR | orientador@teste.com | senha123 | Opcional |
| ALUNO (com estágio ATIVO) | aluno@teste.com | senha123 | Opcional |
| ALUNO (sem estágio) | aluno2@teste.com | senha123 | Opcional |
| SECRETARIA | valor de SECRETARIA_INICIAL_EMAIL | valor de SECRETARIA_INICIAL_SENHA | Obrigatório (QR Code no primeiro login) |

A secretaria não faz parte do seed: ela é criada automaticamente na inicialização a partir das variáveis
SECRETARIA_INICIAL_* do .env (ver ContaInicialService). Como o 2FA é obrigatório para SECRETARIA e
COORDENADOR, o primeiro login desses perfis exibe o QR Code para configurar o aplicativo autenticador.

Ao alterar entidades, recrie o banco com `docker compose down -v` antes de subir novamente.

Com o seed, os tipos de estágio têm carga total de 270 h e jornada semanal de 44 h (CLT), 30 h (Estagiário) e 20 h (Empreendedor). O estágio ativo de aluno@teste.com começou em 01/03/2026 e aparece no painel com 270h de 270h e conclusão prevista em 19/04/2026.

Comandos úteis durante os testes:
```
-- desbloquear uma conta sem esperar 5 minutos
UPDATE usuarios SET conta_bloqueada = false, tentativas_falhas = 0 WHERE email = '...';

-- desativar o 2FA de uma conta (ex.: perda do celular)
UPDATE usuarios SET dois_fatores_ativo = false, dois_fatores_segredo = NULL WHERE email = '...';
```

---

## Estrutura do Projeto

**src/main/java/tcc/ges/aprovamais/**
```
auth/        - Autenticação: AuthController, AuthService, UserDetailsServiceImpl
config/      - Configurações: SecurityConfig, AsyncConfig, ContaInicialRunner, OpenApiConfig
controller/  - Controllers REST e de página: PaginaController, AlunoEstagioController,
               OrientadorValidacaoController, TrilhaController, ConviteController,
               PrimeiroAcessoController, ConsentimentoController, AnonimizacaoController,
               DoisFatoresController
dto/         - Objetos de transferência de dados
entity/      - Entidades JPA e enums
exception/   - Tratamento global de exceções
repository/  - Repositórios Spring Data JPA
security/    - JwtService, JwtFilter, AesEncryptor
service/     - Lógica de negócio: EstagioService, TrilhaService, ConviteService,
               PrimeiroAcessoService, ConsentimentoService, AnonimizacaoService,
               AuditoriaService, EmailService, DoisFatoresService,
               BloqueioContaService, ContaInicialService, ConsultaCnpjService,
               CalculadoraCargaHoraria, ValidadorCnpj
```

**src/main/resources/**
```
templates/
├── login.html
├── primeiro-acesso.html
├── primeiro-acesso-erro.html
├── consentimento.html
├── termos.html
├── privacidade.html
├── aluno/
│   ├── dashboard.html
│   ├── estagio.html
│   └── trilha.html
├── orientador/
│   └── validacoes.html
├── coordenador/
│   └── dashboard.html
├── secretaria/
│   ├── dashboard.html
│   ├── enviar-convite.html
│   └── convites.html
├── conta/
│   └── seguranca.html
└── fragments/
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
| POST | /api/v1/auth/logout | Invalida o cookie JWT |
| POST | /api/v1/auth/2fa/verificar | Segunda etapa do login (conta com 2FA ativo) |
| POST | /api/v1/auth/2fa/configuracao/iniciar | Gera o QR Code no primeiro login de perfil com 2FA obrigatório |
| POST | /api/v1/auth/2fa/configuracao/confirmar | Confirma o primeiro código, ativa o 2FA e cria o cookie JWT |

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

### Secretaria
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /api/v1/convites | Envia convite de acesso por e-mail (Resend) |

### LGPD (qualquer usuário autenticado)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /api/v1/consentimento/aceitar | Registra o aceite dos termos com data e versão |
| POST | /api/v1/lgpd/solicitar-acesso | Registra solicitação de acesso aos dados |
| POST | /api/v1/lgpd/solicitar-correcao | Registra solicitação de correção dos dados |
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

### Orientador
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /api/v1/orientador/validacoes | Lista estágios pendentes e rejeitados da turma |
| POST | /api/v1/orientador/validacoes/{id}/aprovar | Aprova um estágio (somente estágios do próprio orientador), com a data de início confirmada opcional |
| GET | /api/v1/orientador/validacoes/{id}/historico | Lista as aprovações e rejeições de um estágio do orientador |
| POST | /api/v1/orientador/validacoes/{id}/rejeitar | Rejeita um estágio com justificativa (máximo de 1000 caracteres) |

---

## Páginas

| Rota | Perfil | Descrição |
|------|--------|-----------|
| /login | Público | Tela de login, com a segunda etapa do 2FA |
| /primeiro-acesso | Público (token do convite) | Definição de senha e aceite dos termos |
| /termos | Público | Termos de Uso |
| /privacidade | Público | Política de Privacidade e direitos do titular |
| /consentimento | Autenticado | Aceite dos termos de uso |
| /inicio | Autenticado | Redireciona para a página inicial do perfil |
| /conta/seguranca | Autenticado | Ativar/desativar verificação em duas etapas |
| /aluno/dashboard | ALUNO | Dashboard com situação do estágio |
| /estagio | ALUNO | Formulário de cadastro ou reenvio |
| /aluno/trilha | ALUNO | Trilha personalizada de tarefas |
| /orientador/validacoes | ORIENTADOR | Lista de estágios para validar |
| /coordenador/dashboard | COORDENADOR | Tela inicial da coordenação |
| /secretaria/dashboard | SECRETARIA | Painel da secretaria |
| /secretaria/enviar-convite | SECRETARIA | Envio de convites |
| /secretaria/convites | SECRETARIA | Convites enviados |

---

## Funcionalidades Previstas

- Perfil Administrador e hierarquia de convites: Administrador convida coordenadores, Coordenador convida secretarias, Secretaria convida alunos e orientadores
- Lista de pré-cadastro mantida pelo coordenador e matrícula criada automaticamente na turma ao aceitar o convite
- Telas do coordenador para cadastrar curso, turma, tipo de estágio, trilha e tarefa
- Envio das entregas da trilha, com histórico de versões, painel de correção e avaliação por checklist de critérios
- Progresso do estágio comparado ao ritmo esperado, com alertas de atraso
- Conclusão e encerramento do estágio e solicitações de troca de modalidade, trancamento e reabertura
- Desconto das semanas de suspensão no cálculo da carga horária
- Painel de indicadores (Chart.js) para a coordenação
- Recuperação de senha
- Blocklist de tokens JWT no Redis para revogação no logout

## Projeto Final de Curso (PFC)

Este projeto é desenvolvido como Projeto Final de Conclusão de Curso (PFC) do curso de Engenharia de Software da Universidade de Mogi das Cruzes (UMC).

**Orientador:** Prof. Leonardo Torres  
**Co-orientador:** Prof. Alessandro Silva

**Alunos:**
- Isabela Tamie Shihara
- Leonardo Valdir Silva
