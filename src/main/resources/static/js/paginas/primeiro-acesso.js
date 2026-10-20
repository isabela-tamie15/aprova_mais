// Script da página primeiro-acesso.html, que antes ficava num <script> dentro dela

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
            const r = this.requisitos();
            return this.aceitou && !this.enviando &&
                   r.tamanho && r.letra && r.numero &&
                   this.senha === this.confirmacaoSenha;
        },
         async aceitar() {
            this.erro = '';
            this.enviando = true;

            const params = new URLSearchParams(window.location.search);
            const token = params.get('token');

            try {
                const resp = await fetch('/primeiro-acesso/aceitar?token=' + encodeURIComponent(token), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        senha: this.senha,
                        confirmacaoSenha: this.confirmacaoSenha
                    })
                });

                if (resp.ok) {
                    window.location.href = '/login?cadastro=concluido';
                } else {
                    // Erro de validação vem com a mensagem do campo, os demais ficam com a genérica
                    const dados = await resp.json().catch(() => ({}));
                    this.erro = dados.mensagem || 'Erro ao concluir cadastro. Tente novamente.';
                }
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.enviando = false;
            }
        },

        async recusar() {
            this.enviando = true;
            const params = new URLSearchParams(window.location.search);
            const token = params.get('token');

            try {
                await fetch('/primeiro-acesso/recusar?token=' + token, {
                    method: 'POST'
                });
                window.location.href = '/login?convite=recusado';
            } catch (e) {
                window.location.href = '/login?convite=recusado';
            }
        }
    }
}
