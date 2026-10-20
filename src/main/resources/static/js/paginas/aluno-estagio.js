// Script da página aluno/estagio.html, que antes ficava num <script> dentro dela

function estagioForm() {
    return {
        tipos: [],
        enviando: false,
        erro: '',
        erros: {},
        consultandoCnpj: false,
        avisoCnpj: '',
        avisoCnpjOk: false,
        form: {
            cnpjEmpresa: '',
            tipoEstagioId: '',
            nomeEmpresa: '',
            dataInicio: ''
        },

        async init() {
            await this.carregarTipos();

            const el = this.$el;
            const nomeEmpresa = el.dataset.nomeEmpresa;
            const dataInicio = el.dataset.dataInicio;
            const tipoEstagioNome = el.dataset.tipoEstagioNome;
            const cnpjEmpresa = el.dataset.cnpjEmpresa;
            if (cnpjEmpresa) this.form.cnpjEmpresa = cnpjEmpresa;

            if (nomeEmpresa) this.form.nomeEmpresa = nomeEmpresa;
            if (dataInicio) this.form.dataInicio = dataInicio;

            if (tipoEstagioNome) {
                const tipo = this.tipos.find(t => t.nome === tipoEstagioNome);
                if (tipo) this.form.tipoEstagioId = tipo.id;
            }
        },

        async carregarTipos() {
            try {
                const resp = await fetch('/api/v1/aluno/estagio/tipos', {
                    credentials: 'include'
                });
                if (resp.ok) {
                    this.tipos = await resp.json();
                }
            } catch (e) {
                console.error('Erro ao carregar tipos', e);
            }
        },

        // Vai colocando pontos, barra e traço enquanto o aluno digita (aceita letras, por causa do CNPJ alfanumérico)
        mascararCnpj(valor) {
            const v = valor.toUpperCase().replace(/[^0-9A-Z]/g, '').slice(0, 14);
            return v.replace(/^(.{2})(.{0,3})/, '$1.$2')
                    .replace(/^(.{2})\.(.{3})(.{0,3})/, '$1.$2.$3')
                    .replace(/^(.{2})\.(.{3})\.(.{3})(.{0,4})/, '$1.$2.$3/$4')
                    .replace(/^(.{2})\.(.{3})\.(.{3})\/(.{4})(.{0,2})/, '$1.$2.$3/$4-$5')
                    .replace(/[./-]+$/, '');
        },

        // Mesma conta do ValidadorCnpj do backend: cada caractere vale o código ASCII menos 48
        cnpjValido(valor) {
            const v = valor.toUpperCase().replace(/[.\-\/\s]/g, '');
            if (!/^[0-9A-Z]{12}[0-9]{2}$/.test(v) || /^(.)\1+$/.test(v)) {
                return false;
            }
            const digito = (pesos) => {
                let soma = 0;
                pesos.forEach((peso, i) => { soma += (v.charCodeAt(i) - 48) * peso; });
                const resto = soma % 11;
                return resto < 2 ? 0 : 11 - resto;
            };
            const primeiro = digito([5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
            const segundo = digito([6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
            return Number(v[12]) === primeiro && Number(v[13]) === segundo;
        },

        /*
           Consulta o CNPJ quando o aluno sai do campo. Se a Receita conhecer,
           preenche o nome da empresa (se ainda estiver vazio) e mostra a situação.
           Se a consulta falhar, só avisa: o cadastro continua e o orientador confere
        */
        async consultarCnpj() {
            this.avisoCnpj = '';
            delete this.erros.cnpjEmpresa;
            if (!this.form.cnpjEmpresa) {
                return;
            }
            if (!this.cnpjValido(this.form.cnpjEmpresa)) {
                this.erros = { ...this.erros, cnpjEmpresa: 'CNPJ inválido. Confira os números digitados.' };
                return;
            }

            this.consultandoCnpj = true;
            try {
                const cnpj = this.form.cnpjEmpresa.replace(/[^0-9A-Za-z]/g, '');
                const resp = await fetch('/api/v1/aluno/estagio/cnpj/' + cnpj, { credentials: 'include' });
                const dados = await resp.json().catch(() => ({}));

                if (dados.status === 'ENCONTRADO') {
                    if (!this.form.nomeEmpresa.trim() && dados.razaoSocial) {
                        this.form.nomeEmpresa = dados.razaoSocial.slice(0, 150);
                    }
                    this.avisoCnpjOk = dados.situacao === 'ATIVA';
                    this.avisoCnpj = 'Empresa encontrada na Receita Federal. Situação: ' + (dados.situacao || 'não informada') + '.';
                } else if (dados.status === 'NAO_ENCONTRADO') {
                    this.erros = { ...this.erros, cnpjEmpresa: 'CNPJ não encontrado na Receita Federal.' };
                } else {
                    this.avisoCnpjOk = false;
                    this.avisoCnpj = 'Não foi possível consultar a Receita agora. Você pode continuar, o orientador vai conferir o CNPJ.';
                }
            } catch (e) {
                this.avisoCnpjOk = false;
                this.avisoCnpj = 'Não foi possível consultar a Receita agora. Você pode continuar, o orientador vai conferir o CNPJ.';
            } finally {
                this.consultandoCnpj = false;
            }
        },

        // Mesmo limite do backend: a data de início pode ir no máximo 1 ano pra frente
        dataMaxima() {
            const data = new Date();
            data.setFullYear(data.getFullYear() + 1);
            // ajusta o fuso pra pegar a data local, e não a UTC
            return new Date(data.getTime() - data.getTimezoneOffset() * 60000)
                    .toISOString().slice(0, 10);
        },

        /*
           Validação na tela, pra avisar antes de enviar. O backend valida
           tudo de novo, porque essa parte pode ser contornada chamando a API direto
        */
        validar() {
            const erros = {};
            const nome = this.form.nomeEmpresa.trim();

            if (!this.form.tipoEstagioId) {
                erros.tipoEstagioId = 'Selecione o tipo de estágio.';
            }
            if (!this.form.cnpjEmpresa) {
                erros.cnpjEmpresa = 'Informe o CNPJ da empresa.';
            } else if (!this.cnpjValido(this.form.cnpjEmpresa)) {
                erros.cnpjEmpresa = 'CNPJ inválido. Confira os números digitados.';
            }
            if (!nome) {
                erros.nomeEmpresa = 'Informe o nome da empresa.';
            } else if (nome.length < 2 || nome.length > 150) {
                erros.nomeEmpresa = 'O nome da empresa deve ter entre 2 e 150 caracteres.';
            }
            if (!this.form.dataInicio) {
                erros.dataInicio = 'Informe a data de início do estágio.';
            } else if (this.form.dataInicio > this.dataMaxima()) {
                erros.dataInicio = 'A data de início não pode ser mais de 1 ano no futuro.';
            }

            this.erros = erros;
            return Object.keys(erros).length === 0;
        },

        async enviar() {
            this.erro = '';
            if (!this.validar()) {
                return;
            }
            this.enviando = true;
            try {
                const resp = await fetch('/api/v1/aluno/estagio', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({
                        tipoEstagioId: Number(this.form.tipoEstagioId),
                        nomeEmpresa: this.form.nomeEmpresa.trim(),
                        cnpjEmpresa: this.form.cnpjEmpresa,
                        dataInicio: this.form.dataInicio
                    })
                });

                if (!resp.ok) {
                    const dados = await resp.json().catch(() => ({}));
                    // Erro de validação do backend: marca cada campo com a mensagem dele
                    if (dados.campos) {
                        this.erros = dados.campos;
                    }
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
