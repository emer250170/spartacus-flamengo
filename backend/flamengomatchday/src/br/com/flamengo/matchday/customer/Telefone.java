package br.com.flamengo.matchday.customer;

/**
 * Telefone do torcedor (07/11): só números, DDD + número. Celular tem 11 números com o 9 depois do DDD;
 * fixo tem 10, começando por 2 a 5. DDD com dois dígitos de 1 a 9 (11 a 99, sem zero).
 */
public final class Telefone
{
	private Telefone()
	{
	}

	/** Só os números (sem +55 na frente, se vier) */
	public static String digitos(final String telefone)
	{
		String d = telefone == null ? "" : telefone.replaceAll("\\D", "");
		if ((d.length() == 12 || d.length() == 13) && d.startsWith("55"))
		{
			d = d.substring(2);
		}
		return d;
	}

	public static boolean valido(final String telefone)
	{
		final String d = digitos(telefone);
		if (d.length() != 10 && d.length() != 11)
		{
			return false;
		}
		if (d.charAt(0) == '0' || d.charAt(1) == '0')
		{
			return false;
		}
		if (d.length() == 11)
		{
			return d.charAt(2) == '9';
		}
		return d.charAt(2) >= '2' && d.charAt(2) <= '5';
	}

	/** DDD (2 números) */
	public static String ddd(final String telefone)
	{
		return digitos(telefone).substring(0, 2);
	}

	/** Número sem o DDD */
	public static String numero(final String telefone)
	{
		return digitos(telefone).substring(2);
	}

	/** (21) 98765-4321 */
	public static String formatar(final String telefone)
	{
		final String d = digitos(telefone);
		if (d.length() < 10)
		{
			return d;
		}
		final String n = d.substring(2);
		final int corte = n.length() - 4;
		return "(" + d.substring(0, 2) + ") " + n.substring(0, corte) + "-" + n.substring(corte);
	}
}
