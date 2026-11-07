package br.com.flamengo.matchday.customer;

/**
 * CPF e telefone informados ao criar a conta (07/11), guardados durante a chamada POST /users da API
 * de comércio: o {@code SignupCpfInterceptor} (OCC) confere e guarda; o
 * {@link CustomerCpfPrepareInterceptor} grava no torcedor novo criado nessa mesma chamada.
 */
public final class SignupCpfHolder
{
	/** Liga/desliga o CPF no cadastro (padrão ligado) */
	public static final String ENABLED_PROPERTY = "flamengo.cadastro.cpf.ativo";

	/** Liga/desliga o telefone no cadastro (padrão ligado) */
	public static final String PHONE_ENABLED_PROPERTY = "flamengo.cadastro.telefone.ativo";

	private static final ThreadLocal<String> CPF = new ThreadLocal<>();
	private static final ThreadLocal<String> PHONE = new ThreadLocal<>();

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
		PHONE.remove();
	}

	/** Telefone conferido (só números, DDD + número) */
	public static void setPhone(final String digits)
	{
		PHONE.set(digits);
	}

	public static String getPhone()
	{
		return PHONE.get();
	}
}
