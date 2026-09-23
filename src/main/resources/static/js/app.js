/* =============================================
   Aprova+ - Funções Alpine.js
   ============================================= */

/**
 * Realiza o logout do usuário invalidando o cookie JWT no backend
 * e redirecionando para a tela de login.
 * Usada no botão de logout da navbar em todas as páginas autenticadas.
 */
async function logout() {
    await fetch('/api/v1/auth/logout', {
        method: 'POST',
        credentials: 'include'
    });
    window.location.href = '/login';
}

/**
 * Componente Alpine.js do formulário de login
 * Gerencia estado do formulário, chamada à API e redirecionamento por perfil
 */
function loginForm() {
    return {
        email: '',
        senha: '',
        erro: '',
        carregando: false,

        async entrar() {
            this.erro = '';
            this.carregando = true;

            try {
                const resposta = await fetch('/api/v1/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    // credentials: 'include' envia o cookie JWT automaticamente
                    // nas requisições seguintes — funciona porque o backend
                    // usa cookie HttpOnly com SameSite=Strict
                    credentials: 'include',
                    body: JSON.stringify({
                        email: this.email,
                        senha: this.senha
                    })
                });

                // .catch(() => ({})) evita exceção se o backend retornar
                // resposta sem corpo (ex: 204, 401 sem body, HTML de erro)
                const dados = await resposta.json().catch(() => ({}));

                if (!resposta.ok) {
                    if (resposta.status >= 500) {
                        this.erro = 'Erro interno no servidor. Tente novamente em instantes.';
                    } else {
                        this.erro = dados.mensagem || 'E-mail ou senha inválidos.';
                    }
                    return;
                }

                const rotas = {
                    'COORDENADOR': '/coordenador/dashboard',
                    'COORDENADOR_CURSO': '/coordenador/dashboard',
                    'ORIENTADOR': '/orientador/validacoes',
                    'ALUNO': '/aluno/dashboard'
                };

                const destino = rotas[dados.perfil];
                if (!destino) {
                    this.erro = 'Perfil de usuário não reconhecido. Contate o suporte.';
                    return;
                }

                window.location.href = destino;

            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        }
    }
}

/**
 * Componente Alpine.js do formulário de cadastro de estágio.
 * Carrega tipos de estágio, pré-preenche com dados de estágio rejeitado
 * e gerencia o envio do formulário.
 */
function estagioForm() {
    return {
        tipos: [],
        enviando: false,
        erro: '',
        form: {
            tipoEstagioId: '',
            nomeEmpresa: '',
            dataInicio: ''
        },

        async init() {
            await this.carregarTipos();

            // Lê os dados do estágio rejeitado dos atributos data- da div
            // para pré-preencher o formulário sem que o aluno precise redigitar tudo
            const el = this.$el;
            const nomeEmpresa = el.dataset.nomeEmpresa;
            const dataInicio = el.dataset.dataInicio;
            const tipoEstagioNome = el.dataset.tipoEstagioNome;

            if (nomeEmpresa) this.form.nomeEmpresa = nomeEmpresa;
            if (dataInicio) this.form.dataInicio = dataInicio;

            // Encontra o ID do tipo de estágio pelo nome após carregar a lista
            if (tipoEstagioNome) {
                const tipo = this.tipos.find(t => t.nome === tipoEstagioNome);
                if (tipo) this.form.tipoEstagioId = tipo.id;
            }
        },

        async carregarTipos() {
            try {
                const resp = await fetch('/api/v1/aluno/estagio/tipos', {
                    credentials: 'include'
                });
                if (resp.ok) {
                    this.tipos = await resp.json();
                }
            } catch (e) {
                console.error('Erro ao carregar tipos de estágio:', e);
            }
        },

        async enviar() {
            this.erro = '';
            this.enviando = true;
            try {
                const resp = await fetch('/api/v1/aluno/estagio', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({
                        tipoEstagioId: Number(this.form.tipoEstagioId),
                        nomeEmpresa: this.form.nomeEmpresa,
                        dataInicio: this.form.dataInicio
                    })
                });

                const dados = await resp.json().catch(() => ({}));

                if (!resp.ok) {
                    this.erro = dados.mensagem || 'Erro ao cadastrar estágio.';
                    return;
                }

                window.location.href = '/aluno/dashboard';

            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.enviando = false;
            }
        }
    }
}

/**
 * Componente Alpine.js para aprovação e rejeição de estágios.
 * Usado na tela de validações do orientador, um componente por estágio listado.
 * @param {number} id - ID do estágio a ser validado
 */
function validacao(id) {
    return {
        justificativa: '',
        mensagem: '',
        processando: false,

        async aprovar() {
            this.processando = true;
            try {
                const resp = await fetch(`/api/v1/orientador/validacoes/${id}/aprovar`, {
                    method: 'POST',
                    credentials: 'include'
                });
                if (resp.ok) {
                    window.location.reload();
                } else {
                    const dados = await resp.json().catch(() => ({}));
                    this.mensagem = dados.mensagem || 'Erro ao aprovar estágio.';
                }
            } catch (e) {
                this.mensagem = 'Não foi possível conectar ao servidor.';
            } finally {
                this.processando = false;
            }
        },

        async rejeitar() {
            if (!this.justificativa.trim()) {
                this.mensagem = 'Informe a justificativa antes de rejeitar.';
                return;
            }
            this.processando = true;
            try {
                const resp = await fetch(`/api/v1/orientador/validacoes/${id}/rejeitar`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ justificativa: this.justificativa })
                });
                if (resp.ok) {
                    window.location.reload();
                } else {
                    const dados = await resp.json().catch(() => ({}));
                    this.mensagem = dados.mensagem || 'Erro ao rejeitar estágio.';
                }
            } catch (e) {
                this.mensagem = 'Não foi possível conectar ao servidor.';
            } finally {
                this.processando = false;
            }
        }
    }
}