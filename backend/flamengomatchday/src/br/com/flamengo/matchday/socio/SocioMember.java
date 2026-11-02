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
		INACTIVE,
		/** Pix ou boleto do plano ainda não pago (item 7): ainda não é sócio */
		AWAITING_PAYMENT
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
	/** Biometria facial validada (grupo biometria-validada) */
	public boolean biometry;
	/** Termo do Nação: versão atual (null = nenhuma publicada), se aceitou e o último aceite */
	public String termVersion;
	public boolean termAccepted;
	public String termAcceptedVersion;
	public java.util.Date termAcceptedAt;
	public String termAcceptedOrigin;
	public String termAcceptedOrder;
	/**
	 * Regras de saída (item 5): simulação do cancelamento (sócio; com pedido aberto,
	 * na data do pedido) ou o valor do cancelamento confirmado (ex-sócio). Null com a
	 * chave desligada ou sem dados.
	 */
	public SocioExitQuote exit;
	/** Uma linha: "Reembolso de R$ 67,10 (arrependimento até 09/11/2026)" */
	public String exitSummary;
	/**
	 * Pagamento do plano (item 7): Pix/boleto aguardando ou a forma do último pedido do
	 * plano; null sem forma gravada (cartão com a mensalidade, como antes)
	 */
	public SocioPayment payment;
	/** Vigência (item 6): contrato gravado ou calculado do pedido; null com a chave desligada */
	public SocioContractStatus contract;
	/** Últimas tentativas de pôr ingresso no carrinho (SocioEligibilityDecision), mais nova primeiro */
	public final List<Attempt> attempts = new ArrayList<>();
	/** Convidados nominais (SocioGuest): ativos primeiro, depois os encerrados mais novos */
	public final List<Guest> guestList = new ArrayList<>();
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

	public static class Attempt
	{
		public Date date;
		public String game;
		public String seat;
		public boolean eligible;
		public boolean blocked;
		/** Motivos da negativa, um por linha */
		public String reasons;
	}

	public static class Guest
	{
		public String name;
		/** ***.456.789-** */
		public String cpf;
		public Date birthDate;
		public String email;
		public Date since;
		public boolean active;
		public Date until;
		public String endReason;
		/** Ingressos para jogos ainda não realizados ("Flamengo x Bahia 15/11 · SUL-H13, pedido 00013002") */
		public final List<String> tickets = new ArrayList<>();
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
