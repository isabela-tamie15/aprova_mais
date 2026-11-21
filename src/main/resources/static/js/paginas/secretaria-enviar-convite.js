// Página secretaria/enviar-convite.html

function conviteForm() {
    return {
        form: { emailDestino: '', perfil: '' },
        preCadastros: [],
        busca: '',
        carregandoLista: true,
        enviando: false,
        erro: '',
        erros: {},
        sucesso: '',

        // Pré-cadastrados sem conta, vindos de /api/v1/secretaria/pre-cadastros
        async carregarPreCadastros() {
            this.carregandoLista = true;
            try {
                const resposta = await fetch('/api/v1/secretaria/pre-cadastros', { credentials: 'include' });
                if (resposta.ok) {
                    this.preCadastros = await resposta.json();
                } else {
                    this.erro = 'Não foi possível carregar a lista de pré-cadastro.';
                }
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregandoLista = false;
            }
        },

        // Busca por nome, RGM ou matrícula e e-mail, sem diferenciar maiúsculas
        preCadastrosFiltrados() {
            const termo = this.busca.trim().toLowerCase();
            if (!termo) {
                return this.preCadastros;
            }
            return this.preCadastros.filter(item =>
                item.nome.toLowerCase().includes(termo) ||
                item.identificadorInstitucional.toLowerCase().includes(termo) ||
                item.email.toLowerCase().includes(termo));
        },

        podoEnviar() {
            return this.form.emailDestino && this.form.perfil;
        },

        // Conferência simples do formato, a validação completa é feita no backend
        validar() {
            const erros = {};
            const email = this.form.emailDestino.trim();
            if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
                erros.emailDestino = 'Informe um e-mail válido.';
            } else if (email.length > 254) {
                erros.emailDestino = 'O e-mail deve ter no máximo 254 caracteres.';
            }
            if (!this.form.perfil) {
                erros.perfil = 'Selecione o perfil de acesso.';
            }
            this.erros = erros;
            return Object.keys(erros).length === 0;
        },

        // Convite para uma pessoa da lista, com o e-mail e o perfil do pré-cadastro
        async convidar(item) {
            const enviado = await this.enviarConvite({ emailDestino: item.email, perfil: item.perfil });
            if (enviado) {
                this.sucesso = 'Convite enviado para ' + item.nome + '. O e-mail chega em instantes.';
            }
        },

        // Convite pelo formulário, com o e-mail digitado
        async enviar() {
            this.erros = {};
            if (!this.validar()) {
                return;
            }
            const enviado = await this.enviarConvite({
                emailDestino: this.form.emailDestino.trim(),
                perfil: this.form.perfil
            });
            if (enviado) {
                this.sucesso = 'Convite enviado com sucesso! O destinatário receberá um e-mail em instantes.';
                this.form = { emailDestino: '', perfil: '' };
            }
        },

        // Envia o convite e atualiza a lista. Devolve true quando o convite foi gravado
        async enviarConvite(corpo) {
            this.erro = '';
            this.sucesso = '';
            this.enviando = true;

            try {
                const resposta = await fetch('/api/v1/convites', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify(corpo)
                });

                // Lemos o corpo da resposta como JSON. Se vier vazio ou não for JSON, ficamos com um objeto vazio
                let dados = {};
                try {
                    dados = await resposta.json();
                } catch (erroDeLeitura) {
                    dados = {};
                }

                if (!resposta.ok) {
                    if (resposta.status >= 500) {
                        this.erro = 'Erro interno no servidor. Tente novamente.';
                    } else {
                        if (dados.campos) {
                            this.erros = dados.campos;
                        }
                        this.erro = dados.mensagem || 'Erro ao enviar convite.';
                    }
                    return false;
                }

                await this.carregarPreCadastros();
                return true;

            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
                return false;
            } finally {
                this.enviando = false;
            }
        }
    }
}
