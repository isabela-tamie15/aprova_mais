// Script da página admin/convites.html, convida coordenadores e lista os convites enviados

const NOMES_PERFIL_CONVITE = { COORDENADOR: 'Coordenador', SECRETARIA: 'Secretaria', ORIENTADOR: 'Orientador', ALUNO: 'Aluno' };
const NOMES_STATUS_CONVITE = { PENDENTE: 'Pendente', ACEITO: 'Aceito', RECUSADO: 'Recusado', EXPIRADO: 'Expirado' };

function convitesAdmin() {
    return {
        form: { nome: '', emailDestino: '' },
        convites: [],
        carregando: true,
        enviando: false,
        erro: '',
        sucesso: '',
        erros: {},

        async iniciar() {
            await this.carregar();
        },

        async carregar() {
            this.carregando = true;
            try {
                const resp = await fetch('/api/v1/convites', { credentials: 'include' });
                if (resp.ok) {
                    this.convites = await resp.json();
                } else {
                    this.erro = 'Não foi possível carregar os convites enviados.';
                }
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        },

        // Mesmas regras do ConviteRequest; o backend valida de novo
        validar() {
            const erros = {};
            const nome = this.form.nome.trim();
            const email = this.form.emailDestino.trim();
            if (nome.length < 3 || nome.length > 150) {
                erros.nome = 'Informe o nome completo (3 a 150 caracteres).';
            }
            if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
                erros.emailDestino = 'Informe um e-mail válido.';
            } else if (email.length > 254) {
                erros.emailDestino = 'O e-mail deve ter no máximo 254 caracteres.';
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
            this.enviando = true;
            try {
                const resp = await fetch('/api/v1/convites', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({
                        nome: this.form.nome.trim(),
                        emailDestino: this.form.emailDestino.trim(),
                        perfil: 'COORDENADOR'
                    })
                });
                if (!resp.ok) {
                    const dados = await resp.json().catch(() => ({}));
                    if (resp.status >= 500) {
                        this.erro = 'Erro interno no servidor. Tente novamente.';
                    } else {
                        if (dados.campos) {
                            this.erros = dados.campos;
                        }
                        this.erro = dados.mensagem || 'Não foi possível enviar o convite.';
                    }
                    return;
                }
                this.sucesso = 'Convite enviado. O link vale por 24 horas.';
                this.form = { nome: '', emailDestino: '' };
                await this.carregar();
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.enviando = false;
            }
        },

        nomePerfil(perfil) {
            return NOMES_PERFIL_CONVITE[perfil] || perfil;
        },

        nomeStatus(status) {
            return NOMES_STATUS_CONVITE[status] || status;
        },

        formatarData(valor) {
            if (!valor) return '-';
            return new Date(valor).toLocaleString('pt-BR', {
                day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
            });
        }
    };
}
