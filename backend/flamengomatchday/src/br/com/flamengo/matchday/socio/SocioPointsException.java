package br.com.flamengo.matchday.socio;

/** Resgate recusado (saldo, plano, esgotada, situação do sócio); a mensagem vai para o torcedor */
public class SocioPointsException extends RuntimeException
{
	public SocioPointsException(final String message)
	{
		super(message);
	}
}
