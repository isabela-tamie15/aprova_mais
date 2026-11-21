// Página aluno/trilha.html, com o envio das entregas e o histórico de versões

function trilhaAluno() {
    return {
        tarefaId: null,
        detalhe: null,
        carregando: false,
        enviando: false,
        comentario: '',
        erro: '',
        sucesso: '',
        erros: {},
        // Status atualizado depois de um envio. As linhas sem envio nesta visita usam o status do servidor
        statusTarefas: {},

        statusDe(id, statusServidor) {
            return this.statusTarefas[id] || statusServidor;
        },

        // A tarefa aceita envio quando ainda não foi enviada ou quando o orientador devolveu
        podeEntregar(status) {
            return status === 'PENDENTE' || status === 'DEVOLVIDA';
        },

        // Já existe versão quando o servidor contou alguma ou quando houve envio nesta visita
        temVersao(id, versoesServidor) {
            return Number(versoesServidor) > 0 || Boolean(this.statusTarefas[id]);
        },
        // Versão com o formulário de pedido de exclusão aberto
        excluindoId: null,
        motivoExclusao: '',
        anexoDisponivel: anexoDisponivel,
        textoSituacaoAnexo: textoSituacaoAnexo,
        nomeStatus: nomeStatusEntrega,
        classeStatus: classeStatusEntrega,
        nomeResultado: nomeResultadoCriterio,
        classeResultado: classeResultadoCriterio,
        formatarDataHora: formatarDataHoraEntrega,
        formatarTamanho: formatarTamanhoArquivo,

        // Entregar leva ao formulário de envio; Ver leva ao histórico de versões
        async abrir(id, destino = 'entregar') {
            this.tarefaId = id;
            this.detalhe = null;
            this.erro = '';
            this.sucesso = '';
            this.erros = {};
            this.comentario = '';
            this.carregando = true;
            try {
                const resposta = await fetch(`/api/v1/aluno/tarefas/${id}/entrega`, { credentials: 'include' });
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
            // Espera a tela atualizar antes de rolar até o painel da entrega
            const alvo = destino === 'ver' ? 'historico-versoes' : 'painel-entrega';
            this.$nextTick(() => document.getElementById(alvo).scrollIntoView({ behavior: 'smooth' }));
        },

        async cancelarEnvio(versao) {
            if (!confirm('Cancelar o envio da versão ' + versao.numero + '? O arquivo será apagado.')) {
                return;
            }
            this.erro = '';
            this.sucesso = '';
            try {
                const resposta = await fetch(`/api/v1/aluno/entregas/versoes/${versao.id}`, {
                    method: 'DELETE',
                    credentials: 'include'
                });
                if (resposta.ok) {
                    this.detalhe = await resposta.json();
                    this.statusTarefas[this.tarefaId] = this.detalhe.status;
                    this.sucesso = 'Envio cancelado e arquivo apagado. Você já pode enviar o arquivo correto.';
                } else {
                    this.erro = (await mensagemErroApi(resposta, 'Não foi possível cancelar o envio.')).mensagem;
                }
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            }
        },

        abrirExclusao(versao) {
            this.excluindoId = versao.id;
            this.motivoExclusao = '';
            this.erro = '';
        },

        async solicitarExclusao(versao) {
            if (!this.motivoExclusao.trim()) {
                this.erro = 'Informe o motivo da exclusão.';
                return;
            }
            this.erro = '';
            this.sucesso = '';
            try {
                const resposta = await fetch(`/api/v1/aluno/entregas/versoes/${versao.id}/exclusao`, {
                    method: 'POST',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ motivo: this.motivoExclusao.trim() })
                });
                if (resposta.ok) {
                    this.excluindoId = null;
                    this.sucesso = 'Pedido de exclusão enviado. O anexo fica bloqueado até a decisão da secretaria ou da coordenação.';
                    await this.abrir(this.tarefaId, 'ver');
                    this.sucesso = 'Pedido de exclusão enviado. O anexo fica bloqueado até a decisão da secretaria ou da coordenação.';
                } else {
                    this.erro = (await mensagemErroApi(resposta, 'Não foi possível enviar o pedido.')).mensagem;
                }
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            }
        },

        // Mesmas regras do ValidadorArquivoEntrega. O servidor confere de novo, inclusive o conteúdo
        validar() {
            const erros = {};
            const arquivo = this.$refs.arquivo.files[0];
            if (!arquivo) {
                erros.arquivo = 'Selecione um arquivo para enviar.';
            } else {
                // A extensão é o que vem depois do último ponto do nome (relatorio.pdf vira "pdf")
                let extensao = '';
                if (arquivo.name.includes('.')) {
                    const partesDoNome = arquivo.name.split('.');
                    extensao = partesDoNome[partesDoNome.length - 1].toLowerCase();
                }
                if (!FORMATOS_ENTREGA.includes(extensao)) {
                    erros.arquivo = 'Formato não aceito. Envie um arquivo PDF, DOCX, XLSX, PPTX, JPG, JPEG ou PNG.';
                } else if (arquivo.size > TAMANHO_MAXIMO_ENTREGA) {
                    erros.arquivo = 'O arquivo deve ter no máximo 10 MB.';
                } else if (arquivo.size === 0) {
                    erros.arquivo = 'O arquivo está vazio.';
                }
            }
            if (this.comentario.trim().length > 1000) {
                erros.comentario = 'O comentário deve ter no máximo 1000 caracteres.';
            }
            this.erros = erros;
            return Object.keys(erros).length === 0;
        },

        async enviar() {
            this.erro = '';
            this.sucesso = '';
            if (!this.validar()) {
                return;
            }
            const dados = new FormData();
            dados.append('arquivo', this.$refs.arquivo.files[0]);
            if (this.comentario.trim()) {
                dados.append('comentario', this.comentario.trim());
            }
            this.enviando = true;
            try {
                const resposta = await fetch(`/api/v1/aluno/tarefas/${this.tarefaId}/entregas`, {
                    method: 'POST',
                    credentials: 'include',
                    body: dados
                });
                if (resposta.ok) {
                    this.detalhe = await resposta.json();
                    this.statusTarefas[this.tarefaId] = this.detalhe.status;
                    this.comentario = '';
                    this.$refs.arquivo.value = '';
                    this.sucesso = 'Versão ' + this.detalhe.versoes.length + ' enviada. O orientador já pode analisar.';
                } else {
                    const falha = await mensagemErroApi(resposta, 'Não foi possível enviar o arquivo.');
                    this.erros = falha.campos;
                    if (resposta.status === 400) {
                        this.erros.arquivo = falha.mensagem;
                    } else {
                        this.erro = falha.mensagem;
                    }
                }
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.enviando = false;
            }
        }
    };
}
