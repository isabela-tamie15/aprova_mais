// Página aluno/estagio.html

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

            const elementoDaPagina = this.$el;
            const nomeEmpresa = elementoDaPagina.dataset.nomeEmpresa;
            const dataInicio = elementoDaPagina.dataset.dataInicio;
            const tipoEstagioNome = elementoDaPagina.dataset.tipoEstagioNome;
            const cnpjEmpresa = elementoDaPagina.dataset.cnpjEmpresa;
            if (cnpjEmpresa) this.form.cnpjEmpresa = cnpjEmpresa;

            if (nomeEmpresa) this.form.nomeEmpresa = nomeEmpresa;
            if (dataInicio) this.form.dataInicio = dataInicio;

            if (tipoEstagioNome) {
                // Marca no formulário o tipo de estágio com o mesmo nome
                for (const tipoEstagio of this.tipos) {
                    if (tipoEstagio.nome === tipoEstagioNome) {
                        this.form.tipoEstagioId = tipoEstagio.id;
                        break;
                    }
                }
            }
        },

        async carregarTipos() {
            try {
                const resposta = await fetch('/api/v1/aluno/estagio/tipos', {
                    credentials: 'include'
                });
                if (resposta.ok) {
                    this.tipos = await resposta.json();
                }
            } catch (erro) {
                console.error('Erro ao carregar tipos', erro);
            }
        },

        // Vai colocando pontos, barra e traço enquanto o aluno digita (aceita letras, por causa do CNPJ alfanumérico)
        mascararCnpj(valor) {
            const cnpjSemPontuacao = valor.toUpperCase().replace(/[^0-9A-Z]/g, '').slice(0, 14);
            return cnpjSemPontuacao.replace(/^(.{2})(.{0,3})/, '$1.$2')
                    .replace(/^(.{2})\.(.{3})(.{0,3})/, '$1.$2.$3')
                    .replace(/^(.{2})\.(.{3})\.(.{3})(.{0,4})/, '$1.$2.$3/$4')
                    .replace(/^(.{2})\.(.{3})\.(.{3})\/(.{4})(.{0,2})/, '$1.$2.$3/$4-$5')
                    .replace(/[./-]+$/, '');
        },

        // Mesma conta do ValidadorCnpj do backend, em que cada caractere vale o código ASCII menos 48
        cnpjValido(valor) {
            // Tira pontos, barra, traço e espaços e deixa as letras maiúsculas
            const cnpjSemPontuacao = valor.toUpperCase().replace(/[.\-\/\s]/g, '');

            // 12 letras ou números e 2 dígitos no final
            const formatoValido = /^[0-9A-Z]{12}[0-9]{2}$/.test(cnpjSemPontuacao);
            // CNPJ com todos os caracteres iguais passa na conta dos dígitos e é recusado antes
            const todosIguais = /^(.)\1+$/.test(cnpjSemPontuacao);
            if (!formatoValido || todosIguais) {
                return false;
            }

            /*
               Calcula um dígito verificador como o ValidadorCnpj. Soma cada caractere vezes o seu peso e usa o
               resto da divisão por 11, com cada caractere valendo o código ASCII menos 48
            */
            function calcularDigito(pesos) {
                let soma = 0;
                for (let posicao = 0; posicao < pesos.length; posicao++) {
                    const valorDoCaractere = cnpjSemPontuacao.charCodeAt(posicao) - 48;
                    soma = soma + valorDoCaractere * pesos[posicao];
                }
                const resto = soma % 11;
                if (resto < 2) {
                    return 0;
                }
                return 11 - resto;
            }

            const primeiroDigitoEsperado = calcularDigito([5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
            const segundoDigitoEsperado = calcularDigito([6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
            const primeiroDigitoInformado = Number(cnpjSemPontuacao[12]);
            const segundoDigitoInformado = Number(cnpjSemPontuacao[13]);
            return primeiroDigitoInformado === primeiroDigitoEsperado && segundoDigitoInformado === segundoDigitoEsperado;
        },

        /*
           Consulta o CNPJ quando o aluno sai do campo. Se a Receita conhecer, preenche o nome da empresa
           que estiver vazio e mostra a situação. Se a consulta falhar, só avisa, e o orientador confere
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
                const resposta = await fetch('/api/v1/aluno/estagio/cnpj/' + cnpj, { credentials: 'include' });
                // Lemos o corpo da resposta como JSON. Se vier vazio ou não for JSON, ficamos com um objeto vazio
                let dados = {};
                try {
                    dados = await resposta.json();
                } catch (erroDeLeitura) {
                    dados = {};
                }

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
            } catch (erro) {
                this.avisoCnpjOk = false;
                this.avisoCnpj = 'Não foi possível consultar a Receita agora. Você pode continuar, o orientador vai conferir o CNPJ.';
            } finally {
                this.consultandoCnpj = false;
            }
        },

        // A data de início pode ir no máximo 1 ano para a frente, como no backend
        dataMaxima() {
            const dataLimite = new Date();
            dataLimite.setFullYear(dataLimite.getFullYear() + 1);
            // ajusta o fuso para pegar a data local, e não a UTC
            return new Date(dataLimite.getTime() - dataLimite.getTimezoneOffset() * 60000)
                    .toISOString().slice(0, 10);
        },

        // Avisa antes de enviar. O backend valida tudo de novo
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
                const resposta = await fetch('/api/v1/aluno/estagio', {
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

                if (!resposta.ok) {
                    // Lemos o corpo da resposta como JSON. Se vier vazio ou não for JSON, ficamos com um objeto vazio
                    let dados = {};
                    try {
                        dados = await resposta.json();
                    } catch (erroDeLeitura) {
                        dados = {};
                    }
                    // Erro de validação do backend marca cada campo com a mensagem dele
                    if (dados.campos) {
                        this.erros = dados.campos;
                    }
                    this.erro = dados.mensagem || 'Erro ao cadastrar estágio.';
                    return;
                }

                window.location.href = '/aluno/dashboard';

            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.enviando = false;
            }
        }
    }
}
