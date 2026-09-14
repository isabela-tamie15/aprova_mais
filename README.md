# Aprova+

**Sistema de Gestão de Estágio Supervisionado**

Aprova+ é um sistema para gestão de estágio obrigatório universitário, que centraliza o acompanhamento do aluno desde o cadastro do estágio até a correção final dos documentos exigidos, com cálculo automático de prazos e checklist de critérios configurável por curso.

---

## Stack

- **Backend:** Java, Spring Boot (Spring Data JPA, Spring Security, Spring Mail)
- **Banco de dados:** PostgreSQL
- **Cache/sessão:** Redis
- **Autenticação:** JWT (token via header `Authorization` e cookie HttpOnly)
- **Frontend:** Thymeleaf, Bootstrap 5, Alpine.js — páginas servidas pelo backend, com dados carregados via chamadas à própria API REST
- **Infraestrutura:** Docker / Docker Compose
- **Documentação de API:** springdoc-openapi (Swagger)

---

## Como rodar o projeto

### Pré-requisitos
- Docker e Docker Compose
- Java 21+ e Maven, caso deseje rodar fora de container

### Passos

1. Copie `.env.example` para `.env` e preencha as variáveis (banco de dados, JWT, e-mail, Redis). O `.env` não é versionado — cada desenvolvedor mantém o próprio, localmente.
2. Suba os containers:
   ```bash
   docker-compose up
   ```
3. A aplicação estará disponível em `http://localhost:8080`.
4. Documentação da API: `http://localhost:8080/swagger-ui.html`

---

## Perfis do sistema

| Perfil | Responsabilidades |
|---|---|
| **Aluno** | Cadastra o próprio estágio, acompanha prazos, executa a trilha de tarefas do seu perfil, envia documentos e acompanha o histórico e o resultado das correções |
| **Orientador** | Aprova ou rejeita o cadastro de estágio dos seus alunos, corrige entregas, preenche o checklist de critérios |
| **Coordenador** | Configura os parâmetros gerais do sistema: carga horária de cada perfil de estágio, tarefas de cada trilha e critérios de checklist por tipo de documento |

---

## Funcionalidades

### Cadastro e Validação do Estágio

O aluno cadastra o estágio uma única vez, informando data de início e perfil (CLT, Estagiário ou Empreendedor); a carga horária semanal é derivada automaticamente do perfil escolhido. O cadastro permanece pendente até aprovação do orientador, que pode rejeitá-lo mediante justificativa obrigatória. Alterações de perfil após a aprovação seguem o mesmo fluxo de validação.

### Cálculo Automático de Progresso e Alerta de Prazo

A partir da data de início e da carga horária do perfil, o sistema calcula a data prevista de término e a data limite de entrega dos documentos, sinalizando visualmente quando o aluno se aproxima do prazo ou já está em atraso.

### Trilha Personalizada por Perfil de Estágio

Cada perfil de estágio possui sua própria lista de tarefas e documentos obrigatórios, definida pelo coordenador. O aluno visualiza somente as tarefas correspondentes ao seu perfil, podendo enviá-las em qualquer ordem.

### Painel de Correção Centralizado

Ao corrigir uma entrega, o orientador visualiza a versão anterior — com o feedback já registrado — lado a lado com a nova versão enviada, facilitando a verificação das correções solicitadas.

### Histórico de Versões de Entregas

Cada reenvio de documento gera uma nova versão, imutável: versões anteriores nunca são sobrescritas. O histórico completo — arquivo, data, comentário do aluno e feedback do orientador — fica disponível tanto para o aluno quanto para o orientador.

### Checklist de Critérios de Correção Configurável

O coordenador cadastra critérios de avaliação por tipo de documento. Em cada correção, o orientador indica quais critérios foram atendidos; o resultado fica associado à versão específica da entrega.

---

## Dashboard

Cada perfil possui um dashboard próprio:

- **Aluno:** progresso do estágio, status do cadastro, próximas tarefas pendentes
- **Orientador:** estágios aguardando aprovação, entregas aguardando correção
- **Coordenador:** visão geral dos estágios ativos, alertas de prazo agregados

---

## Estrutura do projeto

```
src/main/java/tcc/ges/aprovamais/
├── auth/              # Autenticação (login, JWT, 2FA)
├── config/            # Configurações do Spring (segurança, CORS, etc.)
├── controller/        # Endpoints REST e páginas Thymeleaf
├── dto/                # Objetos de entrada e saída da API
├── entity/             # Entidades JPA
├── exception/          # Tratamento centralizado de erros
├── repository/         # Acesso a dados (Spring Data JPA)
├── security/           # Filtros e utilitários de segurança
└── service/             # Regras de negócio

src/main/resources/
├── templates/                     # Páginas HTML (Thymeleaf + Alpine.js)
├── application.properties         # Configuração comum
├── application-dev.properties     # Configuração de desenvolvimento
└── application-prod.properties    # Configuração de produção
```

---

## Projeto Final de Curso (PFC)

Este projeto é desenvolvido como Projeto Final de Curso (PFC) do curso de Engenharia de Software da Universidade de Mogi das Cruzes (UMC).

**Orientador:** Prof. Leonardo Torres  
**Co-orientador:** Prof. Alessandro Silva

**Alunos:**
- Isabela Tamie Shihara
- Leonardo Valdir Silva
