package br.com.flamengo.matchday.customer;

/**
 * CPF informado ao criar a conta (07/11), guardado durante a chamada POST /users da API de
 * comércio: o {@code SignupCpfInterceptor} (OCC) confere e guarda; o
 * {@link CustomerCpfPrepareInterceptor} grava no torcedor novo criado nessa mesma chamada.
 */
public final class SignupCpfHolder
{
	/** Liga/desliga o CPF no cadastro (padrão ligado) */
	public static final String ENABLED_PROPERTY = "flamengo.cadastro.cpf.ativo";

	private static final ThreadLocal<String> CPF = new ThreadLocal<>();

	private SignupCpfHolder()
	{
	}

	public static void set(final String digits)
	{
		CPF.set(digits);
	}

	public static String get()
	{
		return CPF.get();
	}

	public static void clear()
	{
		CPF.remove();
	}
}
