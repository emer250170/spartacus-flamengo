package br.com.flamengo.matchday.ticket;

import java.util.Date;


/** Quem vai usar a poltrona do carrinho (ingresso adicional de quem não é sócio) */
public class TicketHolder
{
	public String seatCode;
	public String gameCode;
	/** Ingresso adicional: precisa dos dados de quem vai usar para fechar o pedido */
	public boolean required;
	public String name;
	/** Só dígitos */
	public String cpf;
	public Date birthDate;
	public String email;
}
