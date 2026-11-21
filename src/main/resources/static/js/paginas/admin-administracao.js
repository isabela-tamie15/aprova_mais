// Páginas admin/configuracoes.html e admin/correcoes.html

// Chama a API do administrador e devolve { ok, dados }, com a mensagem de erro pronta para a tela
async function chamarApiAdmin(url, metodo = 'GET', corpo = null) {
    const opcoes = { method: metodo, credentials: 'include', headers: {} };
    if (corpo !== null) {
        opcoes.headers['Content-Type'] = 'application/json';
        opcoes.body = JSON.stringify(corpo);
    }
    let resposta;
    try {
        resposta = await fetch(url, opcoes);
    } catch (erro) {
        return { ok: false, dados: { mensagem: 'Não foi possível conectar ao servidor.' } };
    }
    if (resposta.status === 204) {
        return { ok: true, dados: null };
    }
    let dados = {};
    try {
        dados = await resposta.json();
    } catch (erroDeLeitura) {
        dados = {};
    }
    if (resposta.status >= 500) {
        dados = { mensagem: 'Erro interno no servidor. Tente novamente.' };
    }
    return { ok: resposta.ok, dados: dados };
}

function formatarDataHora(valor) {
    return valor ? new Date(valor).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '';
}

function configuracoesAdmin() {
    return {
        configuracoes: [],
        valores: {},
        carregando: true,
        salvando: false,
        erro: '',
        sucesso: '',
        formatarDataHora: formatarDataHora,

        async carregar() {
            const resposta = await chamarApiAdmin('/api/v1/admin/configuracoes');
            if (resposta.ok) {
                this.configuracoes = resposta.dados;
                this.valores = {};
                for (const item of this.configuracoes) {
                    this.valores[item.parametro] = item.valor;
                }
            } else {
                this.erro = resposta.dados.mensagem || 'Erro ao carregar as configurações.';
            }
            this.carregando = false;
        },

        async salvar(item) {
            this.erro = '';
            this.sucesso = '';
            const valor = this.valores[item.parametro];
            if (!Number.isInteger(valor) || valor < item.minimo || valor > item.maximo) {
                this.erro = item.descricao + ' deve ficar entre ' + item.minimo + ' e ' + item.maximo + ' ' + item.unidade + '.';
                return;
            }
            this.salvando = true;
            const resposta = await chamarApiAdmin('/api/v1/admin/configuracoes/' + item.parametro, 'PUT', { valor: valor });
            this.salvando = false;
            if (resposta.ok) {
                this.sucesso = item.descricao + ' alterado para ' + valor + ' ' + item.unidade + '.';
                await this.carregar();
            } else {
                this.erro = resposta.dados.mensagem || 'Não foi possível salvar.';
            }
        }
    };
}

function correcoesAdmin() {
    return {
        erro: '',
        sucesso: '',
        salvando: false,
        doisFatores: { email: '', justificativa: '' },
        estagioId: '',
        estagio: null,
        correcao: { status: '', motivoEncerramento: '', justificativa: '' },
        statusPossiveis: ['PENDENTE', 'ATIVO', 'REJEITADO', 'SUSPENSO', 'CONCLUIDO', 'ENCERRADO'],
        motivos: ['TROCA_MODALIDADE', 'TRANCAMENTO', 'REPROVACAO', 'DESLIGAMENTO'],

        limparMensagens() {
            this.erro = '';
            this.sucesso = '';
        },

        async desativarDoisFatores() {
            this.limparMensagens();
            if (!this.doisFatores.email.trim() || !this.doisFatores.justificativa.trim()) {
                this.erro = 'Informe o e-mail do usuário e a justificativa.';
                return;
            }
            this.salvando = true;
            const resposta = await chamarApiAdmin('/api/v1/admin/correcoes/dois-fatores', 'POST', {
                email: this.doisFatores.email.trim(),
                justificativa: this.doisFatores.justificativa.trim()
            });
            this.salvando = false;
            if (resposta.ok) {
                this.sucesso = '2FA desativado. A correção ficou registrada na auditoria.';
                this.doisFatores = { email: '', justificativa: '' };
            } else {
                this.erro = resposta.dados.mensagem || 'Não foi possível desativar o 2FA.';
            }
        },

        async buscarEstagio() {
            this.limparMensagens();
            this.estagio = null;
            if (!this.estagioId) {
                this.erro = 'Informe o número do estágio.';
                return;
            }
            const resposta = await chamarApiAdmin('/api/v1/admin/correcoes/estagios/' + this.estagioId);
            if (resposta.ok) {
                this.estagio = resposta.dados;
                this.correcao = { status: '', motivoEncerramento: '', justificativa: '' };
            } else {
                this.erro = resposta.dados.mensagem || 'Estágio não encontrado.';
            }
        },

        async corrigirEstagio() {
            this.limparMensagens();
            if (!this.correcao.status || !this.correcao.justificativa.trim()) {
                this.erro = 'Escolha o novo status e informe a justificativa.';
                return;
            }
            if (this.correcao.status === 'ENCERRADO' && !this.correcao.motivoEncerramento) {
                this.erro = 'Escolha o motivo do encerramento.';
                return;
            }
            this.salvando = true;
            const resposta = await chamarApiAdmin(`/api/v1/admin/correcoes/estagios/${this.estagio.id}/status`, 'POST', {
                status: this.correcao.status,
                motivoEncerramento: this.correcao.status === 'ENCERRADO' ? this.correcao.motivoEncerramento : null,
                justificativa: this.correcao.justificativa.trim()
            });
            this.salvando = false;
            if (resposta.ok) {
                this.estagio = resposta.dados;
                this.correcao = { status: '', motivoEncerramento: '', justificativa: '' };
                this.sucesso = 'Status corrigido. A correção ficou registrada na auditoria.';
            } else {
                this.erro = resposta.dados.mensagem || 'Não foi possível corrigir o status.';
            }
        }
    };
}
