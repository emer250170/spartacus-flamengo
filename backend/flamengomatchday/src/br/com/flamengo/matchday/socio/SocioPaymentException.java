package br.com.flamengo.matchday.socio;

/** Forma de pagamento recusada (mensagem em português para a loja, o Backoffice e o Claude) */
public class SocioPaymentException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	public SocioPaymentException(final String message)
	{
		super(message);
	}
}
