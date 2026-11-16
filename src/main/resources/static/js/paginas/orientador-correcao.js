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
        // Respostas do checklist, uma por critério: { idDoCriterio: { resultado, observacao } }
        respostas: {},

        nomeStatus: nomeStatusEntrega,
        classeStatus: classeStatusEntrega,
        nomeResultado: nomeResultadoCriterio,
        classeResultado: classeResultadoCriterio,
        formatarDataHora: formatarDataHoraEntrega,
        formatarTamanho: formatarTamanhoArquivo,

        // Abrir a entrega já coloca em EM_REVISAO a que estava ENVIADA
        async carregar() {
            try {
                const resposta = await fetch(`/api/v1/orientador/entregas/${entregaId}`, { credentials: 'include' });
                if (resposta.ok) {
                    const dados = await resposta.json();
                    // As respostas vazias são criadas antes de mostrar a tela, para cada critério já ter onde guardar a marcação
                    this.respostas = this.respostasVazias(dados.criterios);
                    this.detalhe = dados;
                } else {
                    this.erro = (await mensagemErroApi(resposta, 'Não foi possível carregar a entrega.')).mensagem;
                }
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        },

        // Uma resposta em branco para cada critério da tarefa
        respostasVazias(criterios) {
            const respostas = {};
            for (const criterio of criterios) {
                respostas[criterio.id] = { resultado: '', observacao: '' };
            }
            return respostas;
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

        // Verdadeiro quando algum critério foi marcado como insuficiente. Nesse caso só dá para devolver
        algumCriterioInsuficiente() {
            if (!this.detalhe) {
                return false;
            }
            for (const criterio of this.detalhe.criterios) {
                if (this.respostas[criterio.id].resultado === 'INSUFICIENTE') {
                    return true;
                }
            }
            return false;
        },

        // Verdadeiro quando algum critério ainda não foi marcado
        algumCriterioSemResposta() {
            for (const criterio of this.detalhe.criterios) {
                if (!this.respostas[criterio.id].resultado) {
                    return true;
                }
            }
            return false;
        },

        // Mesmas regras do CorrecaoService e do ChecklistService, que conferem de novo
        validar(decisao) {
            const erros = {};
            if (decisao === 'DEVOLVER' && !this.feedback.trim()) {
                erros.feedback = 'Escreva o feedback para o aluno saber o que corrigir.';
            }
            if (this.algumCriterioSemResposta()) {
                erros.checklist = 'Marque suficiente ou insuficiente em todos os critérios.';
            } else if (decisao === 'APROVAR' && this.algumCriterioInsuficiente()) {
                erros.checklist = 'Há critério insuficiente. Devolva a entrega para o aluno corrigir.';
            }
            this.erros = erros;
            return Object.keys(erros).length === 0;
        },

        // Monta a lista que vai para a API, um item por critério, na ordem do checklist
        respostasParaEnviar() {
            const lista = [];
            for (const criterio of this.detalhe.criterios) {
                const resposta = this.respostas[criterio.id];
                lista.push({
                    criterioId: criterio.id,
                    resultado: resposta.resultado,
                    observacao: resposta.observacao.trim() || null
                });
            }
            return lista;
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
                    body: JSON.stringify({
                        decisao,
                        feedback: this.feedback.trim() || null,
                        respostas: this.respostasParaEnviar()
                    })
                });
                if (resposta.ok) {
                    const dados = await resposta.json();
                    this.respostas = this.respostasVazias(dados.criterios);
                    this.detalhe = dados;
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
