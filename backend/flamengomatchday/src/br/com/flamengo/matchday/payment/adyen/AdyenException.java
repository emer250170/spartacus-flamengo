package br.com.flamengo.matchday.payment.adyen;

/** Recusa ou falha no pagamento pela Adyen; a mensagem, em português, vai para o torcedor. */
public class AdyenException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	private final int status;

	public AdyenException(final String message)
	{
		this(message, 400);
	}

	public AdyenException(final String message, final int status)
	{
		super(message);
		this.status = status;
	}

	/** Status HTTP da resposta da API da loja (400 recusa, 401 webhook sem autorização, 502 Adyen fora) */
	public int getStatus()
	{
		return status;
	}
}
