package br.com.flamengo.matchday.socio;

/** Recusa no cadastro de convidado; a mensagem, em português, vai para o torcedor. */
public class SocioGuestException extends Exception
{
	private static final long serialVersionUID = 1L;

	public SocioGuestException(final String message)
	{
		super(message);
	}
}
