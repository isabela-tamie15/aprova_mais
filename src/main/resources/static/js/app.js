/* =============================================
   Aprova+ - Funções Alpine.js
   ============================================= */

// Faz logout do usuário, invalida o cookie JWT no backend e volta pra tela de login
async function logout() {
    await fetch('/api/v1/auth/logout', {
        method: 'POST',
        credentials: 'include'
    });
    window.location.href = '/login';
}

/*
   Desenha o QR Code do 2fa dentro do elemento passado. A geração roda
   toda no navegador com o qrcodejs, o segredo não vai pra nenhum
   serviço externo
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

// Converte a resposta de erro da API numa mensagem amigável pro usuário
function mensagemDeErro(resposta, dados, mensagemPadrao) {

    // Erro 5xx é problema do servidor, então não faz sentido expor o detalhe real
    if (resposta.status >= 500) {
        return 'Erro interno no servidor. Tente novamente em instantes.';
    }
    // 423 é conta bloqueada, então mostra a mensagem específica do backend
    if (resposta.status === 423) {
        return dados.mensagem || 'Conta bloqueada. Tente novamente em alguns minutos.';
    }
    return dados.mensagem || mensagemPadrao;
}

/*
   Componente Alpine do formulário de login. Passa por até três etapas,
   primeiro credenciais, depois código do 2fa pra quem já tem ativado,
   e por último configuração com QR Code pra quem tem perfil que exige
   mas ainda não configurou. O token de pré-autenticação fica só na
   memória, nunca vai pra localStorage
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

        // Esse é o passo 1, envia email e senha e decide pra onde ir depois
        async entrar() {
            this.erro = '';
            this.carregando = true;

            try {
                const resposta = await fetch('/api/v1/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    /*
                       O credentials include faz o navegador mandar o cookie
                       JWT automaticamente nas próximas requisições. Funciona
                       porque o backend usa HttpOnly com SameSite=Strict
                    */
                    credentials: 'include',
                    body: JSON.stringify({
                        email: this.email,
                        senha: this.senha
                    })
                });

                /*
                   O catch vazio serve pra quando o backend devolve resposta
                   sem corpo, tipo 204 ou um 401 sem body. Sem isso o .json()
                   estouraria exceção
                */
                const dados = await resposta.json().catch(() => ({}));

                if (!resposta.ok) {
                    this.erro = mensagemDeErro(resposta, dados, 'E-mail ou senha inválidos.');
                    return;
                }

                // Usuário já tem 2fa, então vai pra etapa do código
                if (dados.requer2FA) {
                    this.preAuthToken = dados.token;
                    this.senha = '';
                    this.etapa = 'codigo';
                    this.$nextTick(() => this.$refs.campoCodigo.focus());
                    return;
                }

                // Perfil exige 2fa mas ainda não configurou, então vai pra etapa de configuração
                if (dados.requerConfiguracao2FA) {
                    this.preAuthToken = dados.token;
                    this.senha = '';
                    await this.iniciarConfiguracao();
                    return;
                }

                // Login completo, sem 2fa pendente, redireciona
                this.redirecionar();

            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        },

        // Esse é o passo da configuração obrigatória, busca o QR Code e o segredo no backend
        async iniciarConfiguracao() {
            const resposta = await fetch('/api/v1/auth/2fa/configuracao/iniciar', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ token: this.preAuthToken })
            });
            const dados = await resposta.json().catch(() => ({}));

            if (!resposta.ok) {
                // Se falhou, reinicia o fluxo e volta pra etapa de credenciais
                this.reiniciar();
                this.erro = 'Não foi possível iniciar a verificação em duas etapas. Faça login novamente.';
                return;
            }

            this.segredo = dados.segredo;
            this.etapa = 'configuracao';

            // O $nextTick espera o DOM atualizar antes de desenhar o QR Code
            this.$nextTick(() => desenharQrCode(this.$refs.qrcode, dados.otpauthUri));
        },

        // Esse método serve tanto pra etapa de código quanto pra de configuração, o endpoint muda
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

                    /*
                       Conta bloqueada por excesso de tentativas, então
                       reinicia o fluxo todo, a pessoa precisa esperar
                       o tempo de bloqueio e logar de novo
                    */
                    if (resposta.status === 423) {
                        this.reiniciar();
                        this.erro = mensagemDeErro(resposta, dados, '');
                        return;
                    }

                    /*
                       401 é código errado OU token de pré-autenticação expirado,
                       o backend responde genérico pra não dar pista, então a
                       mensagem cobre os dois casos
                    */
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

        // Volta tudo pro estado inicial, usado quando dá erro ou quando a pessoa desiste
        reiniciar() {
            this.etapa = 'credenciais';
            this.senha = '';
            this.codigo = '';
            this.preAuthToken = '';
            this.segredo = '';
            this.erro = '';
        },

        /*
           Manda pro endpoint /inicio em vez de decidir aqui pra onde ir.
           Assim o mapeamento de perfil pra página inicial fica em um só
           lugar, no backend, sem duplicar essa regra no frontend
        */
        redirecionar() {
            window.location.href = '/inicio';
        }
    }
}

/*
   Componente Alpine do formulário de cadastro de estágio. Carrega a
   lista de tipos, pré-preenche com dados de um estágio rejeitado
   (se houver) e envia o formulário
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

            /*
               Lê os dados do estágio rejeitado direto dos atributos data-
               da div onde o componente foi montado. Assim o aluno não
               precisa redigitar tudo quando vai reenviar
            */
            const el = this.$el;
            const nomeEmpresa = el.dataset.nomeEmpresa;
            const dataInicio = el.dataset.dataInicio;
            const tipoEstagioNome = el.dataset.tipoEstagioNome;

            if (nomeEmpresa) this.form.nomeEmpresa = nomeEmpresa;
            if (dataInicio) this.form.dataInicio = dataInicio;

            /*
               O select de tipo usa ID, mas os data-attributes só tem o nome.
               Então precisa procurar o ID na lista de tipos que já foi carregada
            */
            if (tipoEstagioNome) {
                const tipo = this.tipos.find(t => t.nome === tipoEstagioNome);
                if (tipo) this.form.tipoEstagioId = tipo.id;
            }
        },

        // Carrega a lista de tipos de estágio disponíveis pro select
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

        // Envia o formulário de cadastro ou atualização de estágio
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

/*
   Componente Alpine da tela de validações do orientador. Um componente
   por estágio listado, cada um recebe o id do estágio pra montar as
   chamadas de aprovar e rejeitar
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
                    // Recarrega a página pra atualizar a lista de pendentes
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
            // Justificativa é obrigatória, então valida antes de mandar
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

/*
   Componente Alpine da tela de segurança da conta. Deixa o usuário
   ativar o 2fa (gerando QR Code e confirmando o primeiro código) e,
   pros perfis em que ele é opcional, desativar mediante um código
   válido. As regras de perfil e limite de tentativas ficam no backend
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

        // Busca o status atual do 2fa e se o perfil exige
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

        // Chama o backend pra gerar o QR Code e entra no modo de ativação
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

        // Só troca o modo, a confirmação em si acontece no confirmar()
        iniciarDesativacao() {
            this.limparMensagens();
            this.modo = 'desativando';
        },

        // Confirma a operação, tanto faz se é ativação ou desativação, o endpoint muda pelo modo
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

                // Recarrega o status pra atualizar a tela com o novo estado
                await this.carregarStatus();
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.processando = false;
            }
        },

        // Sai do modo atual e limpa os campos temporários
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