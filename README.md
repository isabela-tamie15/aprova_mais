# Aprova+ - Sistema de Gestao de Estagios Supervisionados

## Sobre o Projeto

O Aprova+ e um sistema web desenvolvido como Projeto Final de Conclusão de Curso (PFC) para a gestao de estagios supervisionados da Universidade de Mogi das Cruzes (UMC).

O sistema permite que alunos cadastrem seus estagios, orientadores validem as solicitacoes e acompanhem o progresso, promovendo uma gestao mais eficiente e digitalizada do processo de estagio.

---

## Equipe

| Nome | RGM | Funcao |
|------|-----|--------|
| Leonardo Valdir Silva | 11232100617 | Desenvolvedor - Feature: Trilha Personalizada |
| Isabela Tamie Shihara | 11231203887 | Desenvolvedora - Feature: Cadastro e Validacao de Estagio |

Turma: 7B - Engenharia de Software - UMC
Orientador: Prof. Pedro

---

## Tecnologias Utilizadas

- Java 25
- Spring Boot 4.1.1
- PostgreSQL 18
- Redis (reservado para blocklist de logout - implementacao futura)
- Thymeleaf - renderizacao server-side
- Alpine.js - interatividade leve no frontend
- Bootstrap 5 - estilizacao
- JWT via cookie HttpOnly com SameSite=Strict
- Docker + Docker Compose
- Prometheus + Grafana (monitoramento)

---

## Funcionalidades Implementadas

### Feature 1 - Cadastro e Validacao de Estagio (Isabela)
- Aluno cadastra estagio escolhendo tipo, empresa e data de inicio
- Estagio enviado com status PENDENTE para aprovacao
- Orientador visualiza estagios PENDENTE e REJEITADO da sua turma
- Orientador aprova - status muda para ATIVO
- Orientador rejeita com justificativa - status muda para REJEITADO
- Aluno pode reenviar apos rejeicao

### Feature 2 - Trilha Personalizada (Leonardo)
- Aluno com estagio ATIVO acessa sua trilha personalizada
- Trilha determinada pelo tipo de estagio escolhido
- Tarefas listadas em ordem definida pelo coordenador
- Diferentes tipos de estagio possuem trilhas diferentes

---

## Seguranca

- Autenticacao via JWT armazenado em cookie HttpOnly com SameSite=Strict
- O browser envia o cookie automaticamente em todas as requisicoes
- Nao e necessario enviar header Authorization manualmente nas paginas
- Defense in depth: regras de acesso no SecurityConfig e @PreAuthorize nos controllers
- Bloqueio automatico de conta apos 5 tentativas falhas (senha ou codigo 2FA), com desbloqueio automatico apos 5 minutos
- Resposta generica para e-mail inexistente, senha incorreta ou conta inativa (evita descoberta de contas cadastradas)
- Conta desativada perde o acesso imediatamente, mesmo com token JWT ainda valido
- Autenticacao de dois fatores (2FA) via TOTP (Google Authenticator, Microsoft Authenticator e similares):
  - obrigatoria para SECRETARIA e COORDENADOR (configurada no primeiro login)
  - opcional para ALUNO e ORIENTADOR (ativada/desativada em /conta/seguranca, sempre com codigo valido)
  - token de pre-autenticacao de uso unico, valido por 2 minutos e armazenado apenas como hash SHA-256
  - QR Code gerado no navegador (o segredo nao e enviado a servicos externos)
- Criptografia AES-256-GCM em repouso do segredo do 2FA e da justificativa de rejeicao de estagio
- RGM e matricula institucional nao sao cifrados por decisao de projeto: sao identificadores unicos usados em buscas
- Orientador so aprova/rejeita estagios vinculados a ele (checagem na propria consulta ao banco)
- Auditoria de login, logout, 2FA, convites, estagios e solicitacoes LGPD (tabela registros_auditoria), com IP de origem
- Rodape com contato do encarregado de protecao de dados (DPO) em todas as paginas (art. 41 da LGPD)
- Swagger desativado no perfil prod

---

## Como Executar

### Pre-requisitos
- Docker e Docker Compose instalados
- Java 25
- Maven

### Passos

**1. Clone o repositorio:**
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

JWT_SECRET=aprova_mais_chave_secreta_super_longa_2026

ENCRYPTION_SECRET=troque_por_uma_chave_aleatoria_longa

RESEND_API_KEY=sua_chave_resend
APP_URL=http://localhost:8080

# Secretaria inicial: criada na inicializacao apenas se nao existir nenhuma secretaria
SECRETARIA_INICIAL_EMAIL=secretaria@teste.com
SECRETARIA_INICIAL_SENHA=senha_com_8_ou_mais_caracteres
SECRETARIA_INICIAL_NOME=Secretaria Academica

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

SPRING_PROFILES_ACTIVE=dev
```

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

---

## Seed de Dados

Apos subir o Docker, conecte no pgAdmin em localhost:5433 e rode o script de seed disponivel em docs/seed.sql.

Usuarios disponiveis apos o seed:

| Perfil | Email | Senha |
|--------|-------|-------|
| COORDENADOR | coordenador@teste.com | senha123 |
| ORIENTADOR | orientador@teste.com | senha123 |
| ALUNO (com estagio ATIVO) | aluno@teste.com | senha123 |
| ALUNO (sem estagio) | aluno2@teste.com | senha123 |
| SECRETARIA | valor de SECRETARIA_INICIAL_EMAIL | valor de SECRETARIA_INICIAL_SENHA |

A secretaria nao faz parte do seed: ela e criada automaticamente na inicializacao a partir das variaveis
SECRETARIA_INICIAL_* do .env (ver ContaInicialService). Como o 2FA e obrigatorio para SECRETARIA e
COORDENADOR, o primeiro login desses perfis exibe o QR Code para configurar o aplicativo autenticador.

Ao alterar entidades, recrie o banco com `docker compose down -v` antes de subir novamente.

---

## Estrutura do Projeto

**src/main/java/tcc/ges/aprovamais/**
```
auth/        - Autenticacao: AuthController, AuthService, UserDetailsServiceImpl
config/      - Configuracoes: SecurityConfig, AsyncConfig
controller/  - Controllers de pagina: PaginaController
dto/         - Objetos de transferencia de dados
entity/      - Entidades JPA e enums
exception/   - Tratamento global de excecoes
repository/  - Repositorios Spring Data JPA
security/    - JwtService, JwtFilter, AesEncryptor
service/     - Logica de negocio: EstagioService, TrilhaService
```

**src/main/resources/**
```
templates/
├── login.html
├── aluno/
│   ├── dashboard.html
│   ├── estagio.html
│   └── trilha.html
└── orientador/
    └── validacoes.html
static/
```

---
## Endpoints da API

### Autenticacao
| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| POST | /api/v1/auth/login | Realiza login e cria cookie JWT |
| POST | /api/v1/auth/logout | Invalida o cookie JWT |
| POST | /api/v1/auth/2fa/verificar | Segunda etapa do login (conta com 2FA ativo) |
| POST | /api/v1/auth/2fa/configuracao/iniciar | Gera o QR Code no primeiro login de perfil com 2FA obrigatorio |
| POST | /api/v1/auth/2fa/configuracao/confirmar | Confirma o primeiro codigo, ativa o 2FA e cria o cookie JWT |

### Conta (qualquer usuario autenticado)
| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| GET | /api/v1/conta/2fa | Situacao do 2FA (ativo, obrigatorio) |
| POST | /api/v1/conta/2fa/configurar | Gera novo segredo e QR Code |
| POST | /api/v1/conta/2fa/ativar | Ativa o 2FA com um codigo valido |
| POST | /api/v1/conta/2fa/desativar | Desativa o 2FA com um codigo valido (bloqueado para perfis obrigatorios) |

### Aluno
| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| GET | /api/v1/aluno/estagio | Busca o estagio atual do aluno |
| GET | /api/v1/aluno/estagio/tipos | Lista os tipos de estagio disponiveis |
| POST | /api/v1/aluno/estagio | Cadastra ou reenvia o estagio |
| GET | /api/v1/aluno/trilha | Busca a trilha personalizada do aluno |

### Orientador
| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| GET | /api/v1/orientador/validacoes | Lista estagios pendentes e rejeitados da turma |
| POST | /api/v1/orientador/validacoes/{id}/aprovar | Aprova um estagio |
| POST | /api/v1/orientador/validacoes/{id}/rejeitar | Rejeita um estagio com justificativa |

---

## Paginas

| Rota | Perfil | Descricao |
|------|--------|-----------|
| /login | Publico | Tela de login |
| /aluno/dashboard | ALUNO | Dashboard com situacao do estagio |
| /estagio | ALUNO | Formulario de cadastro ou reenvio |
| /aluno/trilha | ALUNO | Trilha personalizada de tarefas |
| /orientador/validacoes | ORIENTADOR | Lista de estagios para validar |
| /coordenador/dashboard | COORDENADOR | Tela inicial da coordenacao |
| /conta/seguranca | Autenticado | Ativar/desativar verificacao em duas etapas |
| /inicio | Autenticado | Redireciona para a pagina inicial do perfil |

## Projeto Final de Curso (PFC)

Este projeto é desenvolvido como Projeto Final de Conclusão de Curso (PFC) do curso de Engenharia de Software da Universidade de Mogi das Cruzes (UMC).

**Orientador:** Prof. Leonardo Torres  
**Co-orientador:** Prof. Alessandro Silva

**Alunos:**
- Isabela Tamie Shihara
- Leonardo Valdir Silva
