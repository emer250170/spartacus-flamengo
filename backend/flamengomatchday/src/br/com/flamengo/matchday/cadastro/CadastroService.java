package br.com.flamengo.matchday.cadastro;

import de.hybris.platform.core.model.user.CustomerModel;


/**
 * Criação de conta da loja com CPF (07/11).
 * <p>
 * Faz o mesmo que o cadastro padrão da API ({@code POST /users} → {@code CustomerFacade.register}):
 * nome "nome sobrenome", e-mail em minúsculas como login, idioma e moeda da sessão e
 * {@code CustomerAccountService.register}, que grava a senha e dispara o e-mail de boas-vindas. A diferença
 * é o CPF no cliente (atributo {@code Customer.cpf}) e não pedir o "Tratamento".
 * <p>
 * Chave {@code flamengo.cadastro.cpfObrigatorio} (padrão true): false = CPF opcional (validado se vier).
 */
public interface CadastroService
{
	String PROPRIEDADE_CPF_OBRIGATORIO = "flamengo.cadastro.cpfObrigatorio";

	/** Customer.cpf (só dígitos), lido e gravado pelo nome do atributo, como os outros campos novos */
	String ATRIBUTO_CPF = "cpf";

	/**
	 * Cria a conta.
	 *
	 * @param email login (vai em minúsculas)
	 * @param cpf com ou sem pontuação; grava só os dígitos
	 * @throws CadastroException dado inválido, e-mail já cadastrado ou CPF já usado em outra conta
	 */
	CustomerModel cadastrar(String nome, String sobrenome, String email, String senha, String cpf) throws CadastroException;

	/** Só os dígitos do CPF, ou "" */
	static String digitos(final String cpf)
	{
		return cpf == null ? "" : cpf.replaceAll("\\D", "");
	}
}
