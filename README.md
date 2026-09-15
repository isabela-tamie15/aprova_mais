# Aprova+ - Sistema de Gestão de Estágios Supervisionados

## Sobre o Projeto

O Aprova+ é um sistema web desenvolvido como Projeto Final de Curso (PFC) para a gestão de estágios supervisionados da Universidade de Mogi das Cruzes (UMC).

O sistema permite que alunos cadastrem seus estágios, orientadores validem as solicitações e acompanhem o progresso, promovendo uma gestão mais eficiente e digitalizada do processo de estágio.

---

## Equipe

| Nome | RGM | Função |
|------|-----|--------|
| Leonardo Valdir Silva | 11232100617 | Desenvolvedor - Feature: Trilha Personalizada |
| Isabela Tamie Shihara | 11231203887 | Desenvolvedora - Feature: Cadastro e Validação de Estágio |

Turma: 8B - Engenharia de Software - UMC

---

## Tecnologias Utilizadas

- Java 25
- Spring Boot 4.1.1
- PostgreSQL 18
- Redis (reservado para blocklist de logout - implementação futura)
- Thymeleaf - renderização server-side
- Alpine.js - interatividade leve no frontend
- Bootstrap 5 - estilização
- JWT via cookie HttpOnly com SameSite=Strict
- Docker + Docker Compose
- Prometheus + Grafana (monitoramento)

---

## Funcionalidades Implementadas

### Feature 1 - Cadastro e Validação de Estágio (Isabela)
- Aluno cadastra estágio escolhendo tipo, empresa e data de início
- Estágio enviado com status PENDENTE para aprovação
- Orientador visualiza estágios PENDENTE e REJEITADO da sua turma
- Orientador aprova - status muda para ATIVO
- Orientador rejeita com justificativa - status muda para REJEITADO
- Aluno pode reenviar após rejeição

### Feature 2 - Trilha Personalizada (Leonardo)
- Aluno com estágio ATIVO acessa sua trilha personalizada
- Trilha determinada pelo tipo de estágio escolhido
- Tarefas listadas em ordem definida pelo coordenador
- Diferentes tipos de estágio possuem trilhas diferentes

---

## Segurança

- Autenticação via JWT armazenado em cookie HttpOnly com SameSite=Strict
- O browser envia o cookie automaticamente em todas as requisições
- Não é necessário enviar header Authorization manualmente nas páginas
- Defense in depth: regras de acesso no SecurityConfig e @PreAuthorize nos controllers
- Bloqueio automático de conta após 5 tentativas de login falhas
- Desbloqueio automático após 5 minutos
- Estrutura preparada para autenticação de dois fatores (2FA)

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

JWT_SECRET=aprova_mais_chave_secreta_super_longa_2026

ENCRYPTION_SECRET=MinhaChaveSecreta

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

Após subir o Docker, conecte no pgAdmin em localhost:5433 e rode o script de seed disponível em docs/seed.sql.

Usuários disponíveis após o seed:

| Perfil | Email | Senha |
|--------|-------|-------|
| COORDENADOR | coordenador@teste.com | senha123 |
| ORIENTADOR | orientador@teste.com | senha123 |
| ALUNO (com estágio ATIVO) | aluno@teste.com | senha123 |
| ALUNO (sem estágio) | aluno2@teste.com | senha123 |

---

## Estrutura do Projeto

**src/main/java/tcc/ges/aprovamais/**
```
auth/        - Autenticação: AuthController, AuthService, UserDetailsServiceImpl
config/      - Configurações: SecurityConfig, AsyncConfig
controller/  - Controllers de página: PaginaController
dto/         - Objetos de transferência de dados
entity/      - Entidades JPA e enums
exception/   - Tratamento global de exceções
repository/  - Repositórios Spring Data JPA
security/    - JwtService, JwtFilter, AesEncryptor
service/     - Lógica de negócio: EstagioService, TrilhaService
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

### Autenticação
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /api/v1/auth/login | Realiza login e cria cookie JWT |
| POST | /api/v1/auth/logout | Invalida o cookie JWT |

### Aluno
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /api/v1/aluno/estagio | Busca o estágio atual do aluno |
| GET | /api/v1/aluno/estagio/tipos | Lista os tipos de estágio disponíveis |
| POST | /api/v1/aluno/estagio | Cadastra ou reenvia o estágio |
| GET | /api/v1/aluno/trilha | Busca a trilha personalizada do aluno |

### Orientador
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /api/v1/orientador/validacoes | Lista estágios pendentes e rejeitados da turma |
| POST | /api/v1/orientador/validacoes/{id}/aprovar | Aprova um estágio |
| POST | /api/v1/orientador/validacoes/{id}/rejeitar | Rejeita um estágio com justificativa |

---

## Páginas

| Rota | Perfil | Descrição |
|------|--------|-----------|
| /login | Público | Tela de login |
| /aluno/dashboard | ALUNO | Dashboard com situação do estágio |
| /estagio | ALUNO | Formulário de cadastro ou reenvio |
| /aluno/trilha | ALUNO | Trilha personalizada de tarefas |
| /orientador/validacoes | ORIENTADOR | Lista de estágios para validar |

## Projeto Final de Curso (PFC)

Este projeto é desenvolvido como Projeto Final de Curso (PFC) do curso de Engenharia de Software da Universidade de Mogi das Cruzes (UMC).

**Orientador:** Prof. Leonardo Torres  
**Co-orientador:** Prof. Alessandro Silva

**Alunos:**
- Isabela Tamie Shihara
- Leonardo Valdir Silva
