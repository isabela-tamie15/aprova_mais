// Página redefinir-senha.html, que troca a senha com o token do link do e-mail

function redefinirSenhaForm() {
    return {
        senha: '',
        confirmacaoSenha: '',
        enviando: false,
        erro: '',

        // Mesmos requisitos do RedefinicaoSenhaRequest, que o backend confere de novo
        requisitos() {
            return {
                tamanho: this.senha.length >= 8 && this.senha.length <= 72,
                letra: /\p{L}/u.test(this.senha),
                numero: /\d/.test(this.senha)
            };
        },

        podeSalvar() {
            const requisitosDaSenha = this.requisitos();
            return !this.enviando && requisitosDaSenha.tamanho && requisitosDaSenha.letra && requisitosDaSenha.numero && this.senha === this.confirmacaoSenha;
        },

        async salvar() {
            this.erro = '';
            if (!this.podeSalvar()) {
                return;
            }

            const token = new URLSearchParams(window.location.search).get('token');
            this.enviando = true;
            try {
                const resposta = await fetch('/api/v1/auth/recuperacao/redefinir', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        token: token,
                        senha: this.senha,
                        confirmacaoSenha: this.confirmacaoSenha
                    })
                });

                if (resposta.ok) {
                    window.location.href = '/login?senha=redefinida';
                    return;
                }
                // Lemos o corpo da resposta como JSON. Se vier vazio ou não for JSON, ficamos com um objeto vazio
                let dados = {};
                try {
                    dados = await resposta.json();
                } catch (erroDeLeitura) {
                    dados = {};
                }
                this.erro = resposta.status >= 500
                    ? 'Erro interno no servidor. Tente novamente.'
                    : (dados.mensagem || 'Não foi possível alterar a senha.');
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.enviando = false;
            }
        }
    }
}
