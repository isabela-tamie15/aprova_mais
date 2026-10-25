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
                const resp = await fetch('/api/v1/admin/auditoria/acoes', { credentials: 'include' });
                if (resp.ok) this.acoes = await resp.json();
            } catch (e) {
                // Sem a lista, o select fica só com "Todas" e a consulta continua funcionando
            }
        },

        // Mesmas regras do backend, para avisar antes de mandar
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
            Object.entries(this.filtrosAplicados).forEach(([chave, valor]) => {
                if (valor !== '' && valor !== null) parametros.append(chave, valor);
            });

            try {
                const resp = await fetch('/api/v1/admin/auditoria?' + parametros, { credentials: 'include' });
                const dados = await resp.json().catch(() => ({}));
                if (!resp.ok) {
                    this.mensagemErro = this.mensagemDeErro(dados);
                    return;
                }
                this.registros = dados.itens;
                this.pagina = dados.pagina;
                this.totalPaginas = dados.totalPaginas;
                this.totalItens = dados.totalItens;
            } catch (e) {
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
