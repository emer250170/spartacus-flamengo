package br.com.flamengo.matchday.socio;

import java.util.Date;


/** Ingresso de um convidado: poltrona do titular marcada para o convidado (carrinho ou pedido). */
public class SocioGuestTicket
{
	/** PK do convidado */
	public String guestId;
	/** Nome gravado no ingresso */
	public String holder;
	/** Código da poltrona (JOGO-20261115-BAH-SUL-H13) */
	public String seatCode;
	/** Nome da poltrona na loja */
	public String seatName;
	/** Código do jogo */
	public String gameCode;
	/** Nome do jogo (Flamengo x Bahia) */
	public String gameName;
	public Date kickoff;
	/** Pedido (null quando ainda está no carrinho) */
	public String orderCode;
	/** Carrinho (null quando já virou pedido) */
	public String cartCode;
}
