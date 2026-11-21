package tcc.ges.aprovamais.dto;

/*
   Resultado da consulta do CNPJ na BrasilAPI
   - ENCONTRADO quando a Receita conhece o CNPJ, com a razão social e a situação cadastral
   - NAO_ENCONTRADO quando os dígitos estão certos, mas o CNPJ não existe na Receita
   - INDISPONIVEL quando a API demorou, caiu ou respondeu algo inesperado. O cadastro segue
     e o orientador confere o CNPJ no termo de compromisso
*/
public record ConsultaCnpjResponse(Status status, String razaoSocial, String nomeFantasia, String situacao) {

    public enum Status { ENCONTRADO, NAO_ENCONTRADO, INDISPONIVEL }

    public static ConsultaCnpjResponse encontrado(String razaoSocial, String nomeFantasia, String situacao) {
        return new ConsultaCnpjResponse(Status.ENCONTRADO, razaoSocial, nomeFantasia, situacao);
    }

    public static ConsultaCnpjResponse naoEncontrado() {
        return new ConsultaCnpjResponse(Status.NAO_ENCONTRADO, null, null, null);
    }

    public static ConsultaCnpjResponse indisponivel() {
        return new ConsultaCnpjResponse(Status.INDISPONIVEL, null, null, null);
    }
}
