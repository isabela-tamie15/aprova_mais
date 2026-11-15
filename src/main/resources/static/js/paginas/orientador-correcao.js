// Página orientador/correcao.html, com a avaliação da entrega pelo orientador

function avaliacaoEntrega(entregaId) {
    return {
        detalhe: null,
        carregando: true,
        enviando: false,
        erro: '',
        sucesso: '',
        erros: {},
        feedback: '',

        nomeStatus: nomeStatusEntrega,
        classeStatus: classeStatusEntrega,
        formatarDataHora: formatarDataHoraEntrega,
        formatarTamanho: formatarTamanhoArquivo,

        // Abrir a entrega já coloca em EM_REVISAO a que estava ENVIADA
        async carregar() {
            try {
                const resposta = await fetch(`/api/v1/orientador/entregas/${entregaId}`, { credentials: 'include' });
                if (resposta.ok) {
                    this.detalhe = await resposta.json();
                } else {
                    this.erro = (await mensagemErroApi(resposta, 'Não foi possível carregar a entrega.')).mensagem;
                }
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        },

        atual() {
            return this.detalhe.versoes[this.detalhe.versoes.length - 1];
        },

        anterior() {
            const versoes = this.detalhe.versoes;
            return versoes.length > 1 ? versoes[versoes.length - 2] : null;
        },

        linkArquivo(versao) {
            return '/api/v1/orientador/entregas/versoes/' + versao.id + '/arquivo';
        },

        aguardando() {
            return this.detalhe && (this.detalhe.status === 'ENVIADA' || this.detalhe.status === 'EM_REVISAO');
        },

        // Mesma regra do CorrecaoService, que confere de novo
        validar(decisao) {
            const erros = {};
            if (decisao === 'DEVOLVER' && !this.feedback.trim()) {
                erros.feedback = 'Escreva o feedback para o aluno saber o que corrigir.';
            }
            this.erros = erros;
            return Object.keys(erros).length === 0;
        },

        async avaliar(decisao) {
            this.erro = '';
            this.sucesso = '';
            if (!this.validar(decisao)) {
                return;
            }
            this.enviando = true;
            try {
                const resposta = await fetch(`/api/v1/orientador/entregas/${entregaId}/avaliacao`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ decisao, feedback: this.feedback.trim() || null })
                });
                if (resposta.ok) {
                    this.detalhe = await resposta.json();
                    this.feedback = '';
                    this.sucesso = decisao === 'APROVAR'
                            ? 'Entrega aprovada. O aluno foi avisado por e-mail.'
                            : 'Entrega devolvida. O aluno foi avisado por e-mail e pode enviar uma nova versão.';
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                } else {
                    this.erro = (await mensagemErroApi(resposta, 'Não foi possível salvar a avaliação.')).mensagem;
                }
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.enviando = false;
            }
        }
    };
}
