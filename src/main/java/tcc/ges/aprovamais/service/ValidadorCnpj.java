package tcc.ges.aprovamais.service;

import java.util.Locale;

/*
   Confere os dígitos verificadores do CNPJ, sem consultar nada fora
   Já aceita o CNPJ alfanumérico da Receita. No cálculo, cada caractere vale o código ASCII menos 48: os números continuam 0 a 9 e as
   letras vão de A = 17 até Z = 42
   Para o CNPJ só com números, a conta é a mesma de sempre
*/
public final class ValidadorCnpj {

    private static final int[] PESOS_PRIMEIRO = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_SEGUNDO = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private ValidadorCnpj() {
    }

    // Tira pontos, barra, traço e espaços e deixa as letras maiúsculas
    public static String normalizar(String cnpj) {
        if (cnpj == null) {
            return null;
        }
        return cnpj.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean valido(String cnpj) {
        String cnpjSemPontuacao = normalizar(cnpj);
        if (cnpjSemPontuacao == null || !cnpjSemPontuacao.matches("[0-9A-Z]{12}[0-9]{2}")) {
            return false;
        }

        // Todos os caracteres iguais (ex.: 00000000000000) passam na conta dos dígitos, então recusamos.
        // chars().distinct().count() conta quantos caracteres diferentes existem; se for 1, todos são iguais
        if (cnpjSemPontuacao.chars().distinct().count() == 1) {
            return false;
        }

        int primeiroDigitoEsperado = calcularDigito(cnpjSemPontuacao, PESOS_PRIMEIRO);
        int segundoDigitoEsperado = calcularDigito(cnpjSemPontuacao, PESOS_SEGUNDO);
        return cnpjSemPontuacao.charAt(12) - '0' == primeiroDigitoEsperado && cnpjSemPontuacao.charAt(13) - '0' == segundoDigitoEsperado;
    }

    // Formata para exibir no formato 11.222.333/0001-81
    public static String formatar(String cnpj) {
        String cnpjSemPontuacao = normalizar(cnpj);
        if (cnpjSemPontuacao == null || cnpjSemPontuacao.length() != 14) {
            return cnpj;
        }
        return cnpjSemPontuacao.substring(0, 2) + "." + cnpjSemPontuacao.substring(2, 5) + "." + cnpjSemPontuacao.substring(5, 8)
                + "/" + cnpjSemPontuacao.substring(8, 12) + "-" + cnpjSemPontuacao.substring(12);
    }

    private static int calcularDigito(String cnpjSemPontuacao, int[] pesos) {
        int soma = 0;
        for (int posicao = 0; posicao < pesos.length; posicao++) {
            soma += (cnpjSemPontuacao.charAt(posicao) - '0') * pesos[posicao];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
