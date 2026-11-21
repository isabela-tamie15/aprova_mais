// Botão de mostrar e ocultar a senha em todos os campos de senha da página
const ICONE_OLHO = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>';
const ICONE_OLHO_RISCADO = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>';

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('input[type="password"]').forEach((campo) => {
        // O botão fica dentro do campo, à direita. Na tela de login o campo já tem um envoltório posicionado
        let envoltorio = campo.parentElement;
        if (!envoltorio.classList.contains('input-wrapper')) {
            envoltorio = document.createElement('div');
            envoltorio.className = 'position-relative';
            campo.parentElement.insertBefore(envoltorio, campo);
            envoltorio.appendChild(campo);
        }
        campo.style.paddingRight = '2.75rem';

        const botao = document.createElement('button');
        botao.type = 'button';
        botao.className = 'btn btn-link position-absolute top-50 end-0 translate-middle-y text-secondary px-2 py-0';
        botao.style.zIndex = '5';
        botao.innerHTML = ICONE_OLHO;
        botao.title = 'Mostrar senha';
        botao.setAttribute('aria-label', 'Mostrar senha');
        botao.addEventListener('click', () => {
            const visivel = campo.type === 'text';
            campo.type = visivel ? 'password' : 'text';
            botao.innerHTML = visivel ? ICONE_OLHO : ICONE_OLHO_RISCADO;
            botao.title = visivel ? 'Mostrar senha' : 'Ocultar senha';
            botao.setAttribute('aria-label', botao.title);
        });
        envoltorio.appendChild(botao);
    });
});
