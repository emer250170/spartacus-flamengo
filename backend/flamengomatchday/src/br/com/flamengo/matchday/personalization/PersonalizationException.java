package br.com.flamengo.matchday.personalization;

/** Recusa na personalização do manto; a mensagem, em português, vai para o torcedor. */
public class PersonalizationException extends Exception
{
	private static final long serialVersionUID = 1L;

	public PersonalizationException(final String message)
	{
		super(message);
	}
}
