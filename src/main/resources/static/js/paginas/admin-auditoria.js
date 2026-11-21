// Script da página admin/auditoria.html

const FILTROS_VAZIOS = { email: '', acao: '', sucesso: '', inicio: '', fim: '' };
const TAMANHO_PAGINA = 20;

function auditoria() {
    return {
        filtros: { ...FILTROS_VAZIOS },
        // Filtros da última consulta feita, usados na troca de página
        filtrosAplicados: { ...FILTROS_VAZIOS },
        acoes: [],
        registros: [],
        pagina: 0,
        totalPaginas: 0,
        totalItens: 0,
        carregando: false,
        mensagemErro: '',
        erros: {},

        async iniciar() {
            await Promise.all([this.carregarAcoes(), this.consultar(0)]);
        },

        async carregarAcoes() {
            try {
                const resposta = await fetch('/api/v1/admin/auditoria/acoes', { credentials: 'include' });
                if (resposta.ok) this.acoes = await resposta.json();
            } catch (erro) {
                // Sem a lista, o select fica só com "Todas" e a consulta continua funcionando
            }
        },

        // Mesmas regras do backend, para avisar antes de enviar
        validar() {
            this.erros = {};
            if (this.filtros.email.length > 254) {
                this.erros.email = 'O e-mail deve ter no máximo 254 caracteres.';
            }
            if (this.filtros.inicio && this.filtros.fim && this.filtros.inicio > this.filtros.fim) {
                this.erros.periodo = 'A data inicial deve ser anterior ou igual à data final.';
            }
            return Object.keys(this.erros).length === 0;
        },

        filtrar() {
            if (!this.validar()) return;
            this.filtrosAplicados = { ...this.filtros, email: this.filtros.email.trim() };
            this.consultar(0);
        },

        limpar() {
            this.filtros = { ...FILTROS_VAZIOS };
            this.erros = {};
            this.filtrosAplicados = { ...FILTROS_VAZIOS };
            this.consultar(0);
        },

        irPara(pagina) {
            if (pagina < 0 || pagina >= this.totalPaginas) return;
            this.consultar(pagina);
        },

        async consultar(pagina) {
            this.carregando = true;
            this.mensagemErro = '';

            // Só vai para a URL o filtro que tem valor
            const parametros = new URLSearchParams({ pagina, tamanho: TAMANHO_PAGINA });
            for (const nomeDoFiltro in this.filtrosAplicados) {
                const valorDoFiltro = this.filtrosAplicados[nomeDoFiltro];
                if (valorDoFiltro !== '' && valorDoFiltro !== null) {
                    parametros.append(nomeDoFiltro, valorDoFiltro);
                }
            }

            try {
                const resposta = await fetch('/api/v1/admin/auditoria?' + parametros, { credentials: 'include' });
                // Lemos o corpo da resposta como JSON. Se vier vazio ou não for JSON, ficamos com um objeto vazio
                let dados = {};
                try {
                    dados = await resposta.json();
                } catch (erroDeLeitura) {
                    dados = {};
                }
                if (!resposta.ok) {
                    this.mensagemErro = this.mensagemDeErro(dados);
                    return;
                }
                this.registros = dados.itens;
                this.pagina = dados.pagina;
                this.totalPaginas = dados.totalPaginas;
                this.totalItens = dados.totalItens;
            } catch (erro) {
                this.mensagemErro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        },

        // O 400 de validação traz o mapa de campos, os outros erros mandam só a mensagem
        mensagemDeErro(dados) {
            if (dados.campos) {
                if (dados.campos.periodoValido) this.erros.periodo = dados.campos.periodoValido;
                if (dados.campos.email) this.erros.email = dados.campos.email;
                return Object.values(dados.campos).join(' ');
            }
            return dados.mensagem || 'Não foi possível consultar a auditoria.';
        },

        /*
           Abre o registro sem máscara, buscando no servidor, que registra a visualização na auditoria.
           Ocultar volta para a versão mascarada que já estava na tela
        */
        async alternarDados(indice) {
            const registro = this.registros[indice];
            if (!registro.mascarado) {
                this.registros[indice] = registro.versaoMascarada;
                return;
            }
            try {
                const resposta = await fetch('/api/v1/admin/auditoria/' + registro.id, { credentials: 'include' });
                if (!resposta.ok) {
                    this.mensagemErro = 'Não foi possível abrir o registro.';
                    return;
                }
                const completo = await resposta.json();
                completo.versaoMascarada = registro;
                this.registros[indice] = completo;
            } catch (erro) {
                this.mensagemErro = 'Não foi possível conectar ao servidor.';
            }
        },

        // Uma linha para cada parte dos detalhes, e o e-mail que vem depois de "para:" na linha de baixo
        linhasDetalhes(texto) {
            if (!texto) return ['-'];
            const linhas = [];
            for (const parte of texto.split(' | ')) {
                const comEmail = parte.match(/^(.*:)\s+(\S+@\S+)$/);
                if (comEmail) {
                    linhas.push(comEmail[1], comEmail[2]);
                } else {
                    linhas.push(parte);
                }
            }
            return linhas;
        },

        resumo() {
            if (this.totalItens === 0) return 'Nenhum registro';
            return this.totalItens === 1 ? '1 registro' : this.totalItens.toLocaleString('pt-BR') + ' registros';
        },

        formatarData(valor) {
            if (!valor) return '-';
            return new Date(valor).toLocaleString('pt-BR', {
                day: '2-digit', month: '2-digit', year: 'numeric',
                hour: '2-digit', minute: '2-digit', second: '2-digit'
            });
        }
    };
}
