package br.com.flamengo.matchday.socio;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Sócio (ou ex-sócio) na tela Sócio-Torcedor › Sócios do Backoffice: resumo da
 * lista e, quando aberto, a ficha completa. Montado pelo {@link SocioMemberService}.
 */
public class SocioMember
{
	/** Situação do sócio */
	public enum Status
	{
		/** Sócio em dia */
		ACTIVE,
		/** Benefícios suspensos por mensalidade em atraso */
		SUSPENDED,
		/** Pediu cancelamento; o atendimento ainda não confirmou */
		CANCELLING,
		/** Ex-sócio (cancelamento confirmado) */
		INACTIVE
	}

	// ------------------------------------------------------------ lista
	public String uid;
	public String name;
	public String planCode;
	public String planName;
	public Status status;
	/** Benefícios suspensos (pode valer junto com CANCELLING) */
	public boolean suspended;
	/** Primeira compra do plano (ou adesão registrada) */
	public Date since;

	// ------------------------------------------------------------ ficha
	public String family;
	/** Fim da vigência (12 meses a partir de since) */
	public Date until;
	public double monthlyPrice;
	public int guests;
	public double guestPrice;
	/** Onda de compra (0 = venda geral) */
	public int wave;
	public int ticketDiscount;
	public int storeDiscount;
	/** Próxima mensalidade (mesmo dia do mês da adesão) */
	public Date nextCharge;
	/** "VISA •••• 1111" */
	public String card;
	/** Chamado de cancelamento ainda aberto (ou null) */
	public String openCancelTicket;
	public final List<Order> orders = new ArrayList<>();
	public final List<Ticket> tickets = new ArrayList<>();
	public final List<Event> events = new ArrayList<>();

	public static class Order
	{
		public String code;
		public Date date;
		public double total;
		public String items;
	}

	public static class Ticket
	{
		public String id;
		public String headline;
		public String state;
		public Date date;
	}

	public static class Event
	{
		public Date date;
		/** Código do tipo (SUSPENSAO...) */
		public String type;
		public String origin;
		public String author;
		public String note;
		public String result;
	}
}
