// Página primeiro-acesso.html

/*
   Guarda o token do convite e o tira da barra de endereço, para ele não ficar no histórico do navegador
   nem aparecer numa captura de tela
*/
const TOKEN_CONVITE = new URLSearchParams(window.location.search).get('token');
window.history.replaceState(null, '', window.location.pathname);

function primeiroAcessoForm() {
    return {
        aceitou: false,
        senha: '',
        confirmacaoSenha: '',
        enviando: false,
        erro: '',

        requisitos() {
            return {
                tamanho: this.senha.length >= 8 && this.senha.length <= 72,
                letra: /\p{L}/u.test(this.senha),
                numero: /\d/.test(this.senha)
            };
        },

        podeConcluir() {
            const requisitosDaSenha = this.requisitos();
            return this.aceitou && !this.enviando &&
                   requisitosDaSenha.tamanho && requisitosDaSenha.letra && requisitosDaSenha.numero &&
                   this.senha === this.confirmacaoSenha;
        },
         async aceitar() {
            this.erro = '';
            this.enviando = true;

            try {
                const resposta = await fetch('/primeiro-acesso/aceitar?token=' + encodeURIComponent(TOKEN_CONVITE), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        senha: this.senha,
                        confirmacaoSenha: this.confirmacaoSenha
                    })
                });

                if (resposta.ok) {
                    window.location.href = '/login?cadastro=concluido';
                } else {
                    // O erro de validação traz a mensagem do campo, e os outros ficam com a genérica
                    let dados = {};
                    try {
                        dados = await resposta.json();
                    } catch (erroDeLeitura) {
                        dados = {};
                    }
                    this.erro = dados.mensagem || 'Erro ao concluir cadastro. Tente novamente.';
                }
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.enviando = false;
            }
        },

        async recusar() {
            this.enviando = true;
            try {
                await fetch('/primeiro-acesso/recusar?token=' + encodeURIComponent(TOKEN_CONVITE), {
                    method: 'POST'
                });
                window.location.href = '/login?convite=recusado';
            } catch (erro) {
                window.location.href = '/login?convite=recusado';
            }
        }
    }
}
