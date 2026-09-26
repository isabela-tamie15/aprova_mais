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
 * Desenha o QR Code do 2FA dentro do elemento informado.
 * A geração é feita no navegador (qrcodejs), sem enviar o segredo a serviços externos.
 */
function desenharQrCode(elemento, texto) {
    elemento.innerHTML = '';
    new QRCode(elemento, {
        text: texto,
        width: 180,
        height: 180,
        correctLevel: QRCode.CorrectLevel.M
    });
}

/**
 * Converte a resposta de erro da API em mensagem para o usuário.
 */
function mensagemDeErro(resposta, dados, mensagemPadrao) {
    if (resposta.status >= 500) {
        return 'Erro interno no servidor. Tente novamente em instantes.';
    }
    if (resposta.status === 423) {
        return dados.mensagem || 'Conta bloqueada. Tente novamente em alguns minutos.';
    }
    return dados.mensagem || mensagemPadrao;
}

/**
 * Componente Alpine.js do formulário de login.
 * Etapas:
 *  - 'credenciais': e-mail e senha;
 *  - 'codigo': conta com 2FA ativo informa o código do aplicativo autenticador;
 *  - 'configuracao': perfil que exige 2FA e ainda não configurou escaneia o QR Code
 *    e confirma o primeiro código.
 * O token de pré-autenticação fica apenas em memória (nunca em localStorage).
 */
function loginForm() {
    return {
        etapa: 'credenciais',
        email: '',
        senha: '',
        codigo: '',
        preAuthToken: '',
        segredo: '',
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
                    this.erro = mensagemDeErro(resposta, dados, 'E-mail ou senha inválidos.');
                    return;
                }

                if (dados.requer2FA) {
                    this.preAuthToken = dados.token;
                    this.senha = '';
                    this.etapa = 'codigo';
                    this.$nextTick(() => this.$refs.campoCodigo.focus());
                    return;
                }

                if (dados.requerConfiguracao2FA) {
                    this.preAuthToken = dados.token;
                    this.senha = '';
                    await this.iniciarConfiguracao();
                    return;
                }

                this.redirecionar();

            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        },

        async iniciarConfiguracao() {
            const resposta = await fetch('/api/v1/auth/2fa/configuracao/iniciar', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ token: this.preAuthToken })
            });
            const dados = await resposta.json().catch(() => ({}));

            if (!resposta.ok) {
                this.reiniciar();
                this.erro = 'Não foi possível iniciar a verificação em duas etapas. Faça login novamente.';
                return;
            }

            this.segredo = dados.segredo;
            this.etapa = 'configuracao';
            this.$nextTick(() => desenharQrCode(this.$refs.qrcode, dados.otpauthUri));
        },

        async confirmarCodigo() {
            this.erro = '';
            this.carregando = true;

            const url = this.etapa === 'configuracao'
                ? '/api/v1/auth/2fa/configuracao/confirmar'
                : '/api/v1/auth/2fa/verificar';

            try {
                const resposta = await fetch(url, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ token: this.preAuthToken, codigo: this.codigo })
                });
                const dados = await resposta.json().catch(() => ({}));

                if (!resposta.ok) {
                    this.codigo = '';
                    if (resposta.status === 423) {
                        // Conta bloqueada por excesso de tentativas: recomeça do login
                        this.reiniciar();
                        this.erro = mensagemDeErro(resposta, dados, '');
                        return;
                    }
                    // 401: código errado ou token de pré-autenticação expirado.
                    // O backend responde de forma genérica, então a mensagem cobre os dois casos.
                    this.erro = resposta.status === 401
                        ? 'Código inválido ou verificação expirada. Se o problema persistir, volte e faça login novamente.'
                        : mensagemDeErro(resposta, dados, 'Não foi possível verificar o código.');
                    return;
                }

                this.redirecionar();

            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        },

        reiniciar() {
            this.etapa = 'credenciais';
            this.senha = '';
            this.codigo = '';
            this.preAuthToken = '';
            this.segredo = '';
            this.erro = '';
        },

        // O servidor redireciona para a página inicial do perfil (/inicio, PerfilUsuario.getRotaInicial),
        // mantendo o mapeamento perfil -> página em um único lugar
        redirecionar() {
            window.location.href = '/inicio';
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

/**
 * Componente Alpine.js da tela de segurança da conta (/conta/seguranca).
 * Permite ativar o 2FA (QR Code + confirmação do primeiro código) e, para perfis
 * em que ele é opcional, desativá-lo mediante um código válido.
 * As regras (perfil obrigatório, limite de tentativas) são aplicadas pelo backend.
 */
function segurancaConta() {
    return {
        carregado: false,
        ativo: false,
        obrigatorio: false,
        modo: '',          // '' | 'ativando' | 'desativando'
        segredo: '',
        codigo: '',
        erro: '',
        sucesso: '',
        processando: false,

        async init() {
            await this.carregarStatus();
        },

        async carregarStatus() {
            try {
                const resposta = await fetch('/api/v1/conta/2fa', { credentials: 'include' });
                if (!resposta.ok) {
                    this.erro = 'Não foi possível carregar a configuração de segurança.';
                    return;
                }
                const dados = await resposta.json();
                this.ativo = dados.ativo;
                this.obrigatorio = dados.obrigatorio;
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregado = true;
            }
        },

        async iniciarAtivacao() {
            this.limparMensagens();
            this.processando = true;
            try {
                const resposta = await fetch('/api/v1/conta/2fa/configurar', {
                    method: 'POST',
                    credentials: 'include'
                });
                const dados = await resposta.json().catch(() => ({}));
                if (!resposta.ok) {
                    this.erro = mensagemDeErro(resposta, dados, 'Não foi possível gerar o QR Code.');
                    return;
                }
                this.segredo = dados.segredo;
                this.modo = 'ativando';
                this.$nextTick(() => desenharQrCode(this.$refs.qrcode, dados.otpauthUri));
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.processando = false;
            }
        },

        iniciarDesativacao() {
            this.limparMensagens();
            this.modo = 'desativando';
        },

        async confirmar() {
            this.limparMensagens();
            this.processando = true;
            const acao = this.modo === 'ativando' ? 'ativar' : 'desativar';
            try {
                const resposta = await fetch('/api/v1/conta/2fa/' + acao, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ codigo: this.codigo })
                });
                const dados = await resposta.json().catch(() => ({}));
                if (!resposta.ok) {
                    this.codigo = '';
                    this.erro = mensagemDeErro(resposta, dados, 'Não foi possível concluir a operação.');
                    return;
                }
                this.sucesso = acao === 'ativar'
                    ? 'Verificação em duas etapas ativada.'
                    : 'Verificação em duas etapas desativada.';
                this.cancelar();
                await this.carregarStatus();
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.processando = false;
            }
        },

        cancelar() {
            this.modo = '';
            this.segredo = '';
            this.codigo = '';
        },

        limparMensagens() {
            this.erro = '';
            this.sucesso = '';
        }
    }
}