package br.com.flamengo.matchday.ticket;

/** Ação no ingresso recusada (situação, prazo, poltrona ocupada); a mensagem vai para o gestor */
public class MatchdayTicketException extends RuntimeException
{
	public MatchdayTicketException(final String message)
	{
		super(message);
	}
}
