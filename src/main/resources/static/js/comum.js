/* =============================================
   Aprova+ - Funções usadas em várias páginas
   ============================================= */

// Faz logout do usuário, invalida o cookie JWT no backend e volta pra tela de login
async function logout() {
    await fetch('/api/v1/auth/logout', {
        method: 'POST',
        credentials: 'include'
    });
    window.location.href = '/login';
}
