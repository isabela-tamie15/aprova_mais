// Script da página aluno/trilha.html: envio das entregas e histórico de versões

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
        // Status atualizado depois de um envio; as linhas sem envio nesta visita usam o status do servidor
        statusTarefas: {},

        statusDe(id, statusServidor) {
            return this.statusTarefas[id] || statusServidor;
        },
        nomeStatus: nomeStatusEntrega,
        classeStatus: classeStatusEntrega,
        formatarDataHora: formatarDataHoraEntrega,
        formatarTamanho: formatarTamanhoArquivo,

        async abrir(id) {
            this.tarefaId = id;
            this.detalhe = null;
            this.erro = '';
            this.sucesso = '';
            this.erros = {};
            this.comentario = '';
            this.carregando = true;
            try {
                const resp = await fetch(`/api/v1/aluno/tarefas/${id}/entrega`, { credentials: 'include' });
                if (resp.ok) {
                    this.detalhe = await resp.json();
                } else {
                    this.erro = (await mensagemErroApi(resp, 'Não foi possível carregar a entrega.')).mensagem;
                }
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
            this.$nextTick(() => document.getElementById('painel-entrega').scrollIntoView({ behavior: 'smooth' }));
        },

        // Mesmas regras do backend (ValidadorArquivoEntrega); o servidor confere de novo, inclusive o conteúdo
        validar() {
            const erros = {};
            const arquivo = this.$refs.arquivo.files[0];
            if (!arquivo) {
                erros.arquivo = 'Selecione um arquivo para enviar.';
            } else {
                const extensao = arquivo.name.includes('.') ? arquivo.name.split('.').pop().toLowerCase() : '';
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
                const resp = await fetch(`/api/v1/aluno/tarefas/${this.tarefaId}/entregas`, {
                    method: 'POST',
                    credentials: 'include',
                    body: dados
                });
                if (resp.ok) {
                    this.detalhe = await resp.json();
                    this.statusTarefas[this.tarefaId] = this.detalhe.status;
                    this.comentario = '';
                    this.$refs.arquivo.value = '';
                    this.sucesso = 'Versão ' + this.detalhe.versoes.length + ' enviada. O orientador já pode analisar.';
                } else {
                    const falha = await mensagemErroApi(resp, 'Não foi possível enviar o arquivo.');
                    this.erros = falha.campos;
                    if (resp.status === 400) {
                        this.erros.arquivo = falha.mensagem;
                    } else {
                        this.erro = falha.mensagem;
                    }
                }
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.enviando = false;
            }
        }
    };
}
