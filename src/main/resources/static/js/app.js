/* =============================================
   Aprova+ - Funções Alpine.js
   ============================================= */

/*
   Desenha o QR Code do 2fa dentro do elemento passado. A geração roda
   toda no navegador com o qrcodejs, o segredo não vai para nenhum
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

// Converte a resposta de erro da API numa mensagem amigável para o usuário
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
   primeiro credenciais, depois código do 2fa para quem já tem ativado,
   e por último configuração com QR Code para quem tem perfil que exige
   mas ainda não configurou. O token de pré-autenticação fica só na
   memória, nunca vai para localStorage
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

        // Esse é o passo 1, envia email e senha e decide para onde ir depois
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
                   O catch vazio serve para quando o backend devolve resposta
                   sem corpo, tipo 204 ou um 401 sem body. Sem isso o .json()
                   estouraria exceção
                */
                // Lemos o corpo da resposta como JSON. Se vier vazio ou não for JSON, ficamos com um objeto vazio
                let dados = {};
                try {
                    dados = await resposta.json();
                } catch (erroDeLeitura) {
                    dados = {};
                }

                if (!resposta.ok) {
                    this.erro = mensagemDeErro(resposta, dados, 'E-mail ou senha inválidos.');
                    return;
                }

                // Usuário já tem 2fa, então vai para a etapa do código
                if (dados.requer2FA) {
                    this.preAuthToken = dados.token;
                    this.senha = '';
                    this.etapa = 'codigo';
                    // O $nextTick espera a tela atualizar e só depois roda o que está dentro de () =>
                    this.$nextTick(() => this.$refs.campoCodigo.focus());
                    return;
                }

                // Perfil exige 2fa mas ainda não configurou, então vai para a etapa de configuração
                if (dados.requerConfiguracao2FA) {
                    this.preAuthToken = dados.token;
                    this.senha = '';
                    await this.iniciarConfiguracao();
                    return;
                }

                // Login completo, sem 2fa pendente, redireciona
                this.redirecionar();

            } catch (erro) {
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
            // Lemos o corpo da resposta como JSON. Se vier vazio ou não for JSON, ficamos com um objeto vazio
            let dados = {};
            try {
                dados = await resposta.json();
            } catch (erroDeLeitura) {
                dados = {};
            }

            if (!resposta.ok) {
                // Se falhou, reinicia o fluxo e volta para a etapa de credenciais
                this.reiniciar();
                this.erro = 'Não foi possível iniciar a verificação em duas etapas. Faça login novamente.';
                return;
            }

            this.segredo = dados.segredo;
            this.etapa = 'configuracao';

            // O $nextTick espera o DOM atualizar antes de desenhar o QR Code
            this.$nextTick(() => desenharQrCode(this.$refs.qrcode, dados.otpauthUri));
        },

        // Esse método serve tanto para a etapa de código quanto para de configuração, o endpoint muda
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
                // Lemos o corpo da resposta como JSON. Se vier vazio ou não for JSON, ficamos com um objeto vazio
                let dados = {};
                try {
                    dados = await resposta.json();
                } catch (erroDeLeitura) {
                    dados = {};
                }

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
                       o backend responde genérico para não dar pista, então a
                       mensagem cobre os dois casos
                    */
                    this.erro = resposta.status === 401
                        ? 'Código inválido ou verificação expirada. Se o problema persistir, volte e faça login novamente.'
                        : mensagemDeErro(resposta, dados, 'Não foi possível verificar o código.');
                    return;
                }

                this.redirecionar();

            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        },

        // Volta tudo para o estado inicial, usado quando dá erro ou quando a pessoa desiste
        reiniciar() {
            this.etapa = 'credenciais';
            this.senha = '';
            this.codigo = '';
            this.preAuthToken = '';
            this.segredo = '';
            this.erro = '';
        },

        /*
           Manda para o endpoint /inicio em vez de decidir aqui para onde ir.
           Assim o mapeamento de perfil para página inicial fica em um só
           lugar, no backend, sem duplicar essa regra no frontend
        */
        redirecionar() {
            window.location.href = '/inicio';
        }
    }
}


/*
   Componente Alpine da tela de segurança da conta. Deixa o usuário
   ativar o 2fa (gerando QR Code e confirmando o primeiro código) e,
   para os perfis em que ele é opcional, desativar mediante um código
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
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregado = true;
            }
        },

        // Chama o backend para gerar o QR Code e entra no modo de ativação
        async iniciarAtivacao() {
            this.limparMensagens();
            this.processando = true;
            try {
                const resposta = await fetch('/api/v1/conta/2fa/configurar', {
                    method: 'POST',
                    credentials: 'include'
                });
                // Lemos o corpo da resposta como JSON. Se vier vazio ou não for JSON, ficamos com um objeto vazio
                let dados = {};
                try {
                    dados = await resposta.json();
                } catch (erroDeLeitura) {
                    dados = {};
                }
                if (!resposta.ok) {
                    this.erro = mensagemDeErro(resposta, dados, 'Não foi possível gerar o QR Code.');
                    return;
                }
                this.segredo = dados.segredo;
                this.modo = 'ativando';
                // O $nextTick espera a tela atualizar e só depois roda o que está dentro de () =>
                this.$nextTick(() => desenharQrCode(this.$refs.qrcode, dados.otpauthUri));
            } catch (erro) {
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
                // Lemos o corpo da resposta como JSON. Se vier vazio ou não for JSON, ficamos com um objeto vazio
                let dados = {};
                try {
                    dados = await resposta.json();
                } catch (erroDeLeitura) {
                    dados = {};
                }
                if (!resposta.ok) {
                    this.codigo = '';
                    this.erro = mensagemDeErro(resposta, dados, 'Não foi possível concluir a operação.');
                    return;
                }
                this.sucesso = acao === 'ativar'
                    ? 'Verificação em duas etapas ativada.'
                    : 'Verificação em duas etapas desativada.';
                this.cancelar();

                // Recarrega o status para atualizar a tela com o novo estado
                await this.carregarStatus();
            } catch (erro) {
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