package br.com.flamengo.matchday.socio;

/** Recusa de uma escolha de vigência (fora do prazo, plano inválido, não é sócio) */
public class SocioContractException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	public SocioContractException(final String message)
	{
		super(message);
	}
}
