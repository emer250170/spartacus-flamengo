package br.com.flamengo.matchday.cadastro;

/** Recusa do cadastro, com a mensagem em português para a loja mostrar ao torcedor. */
public class CadastroException extends Exception
{
	private static final long serialVersionUID = 1L;

	public CadastroException(final String message)
	{
		super(message);
	}
}
