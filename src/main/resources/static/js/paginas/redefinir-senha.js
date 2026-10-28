// Script da página redefinir-senha.html: troca a senha usando o token do link recebido por e-mail

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
            const r = this.requisitos();
            return !this.enviando && r.tamanho && r.letra && r.numero && this.senha === this.confirmacaoSenha;
        },

        async salvar() {
            this.erro = '';
            if (!this.podeSalvar()) {
                return;
            }

            const token = new URLSearchParams(window.location.search).get('token');
            this.enviando = true;
            try {
                const resp = await fetch('/api/v1/auth/recuperacao/redefinir', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        token: token,
                        senha: this.senha,
                        confirmacaoSenha: this.confirmacaoSenha
                    })
                });

                if (resp.ok) {
                    window.location.href = '/login?senha=redefinida';
                    return;
                }
                const dados = await resp.json().catch(() => ({}));
                this.erro = resp.status >= 500
                    ? 'Erro interno no servidor. Tente novamente.'
                    : (dados.mensagem || 'Não foi possível alterar a senha.');
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.enviando = false;
            }
        }
    }
}
