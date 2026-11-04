package br.com.flamengo.matchday.report.summary;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Resumo de vendas do painel inicial do administrador no Backoffice: indicadores do período,
 * vendas por produto e serviço, chamados em aberto por tipo, próximos jogos e receita mês a mês.
 */
public class SummaryReport
{
	/** Período escolhido na tela */
	public enum Period
	{
		MONTH, LAST_30_DAYS, YEAR, ALL
	}

	private Period period;
	private Date from;
	private Date generatedAt;
	private double revenue;
	private int orders;
	private int awaitingOrders;
	private double awaitingAmount;
	private int openTickets;
	private final List<CategoryLine> categories = new ArrayList<>();
	private final List<TicketLine> tickets = new ArrayList<>();
	private final List<GameLine> games = new ArrayList<>();
	private final List<MonthLine> months = new ArrayList<>();

	/** Vendas de um tipo de produto ou serviço (chave: TICKETS, MEMBERSHIP, ...; FREIGHT = frete) */
	public static class CategoryLine
	{
		private final String key;
		private long quantity;
		private double revenue;
		private int orders;

		public CategoryLine(final String key)
		{
			this.key = key;
		}

		public String getKey()
		{
			return key;
		}

		public long getQuantity()
		{
			return quantity;
		}

		public void addQuantity(final long value)
		{
			quantity += value;
		}

		public double getRevenue()
		{
			return revenue;
		}

		public void addRevenue(final double value)
		{
			revenue += value;
		}

		public int getOrders()
		{
			return orders;
		}

		public void addOrder()
		{
			orders++;
		}
	}

	/** Chamados em aberto de um tipo (categoria do chamado ou "cancelamento do plano") */
	public static class TicketLine
	{
		private final String key;
		private int newCount;
		private int openCount;
		private Date oldest;

		public TicketLine(final String key)
		{
			this.key = key;
		}

		public String getKey()
		{
			return key;
		}

		public int getNewCount()
		{
			return newCount;
		}

		public int getOpenCount()
		{
			return openCount;
		}

		public int getTotal()
		{
			return newCount + openCount;
		}

		public Date getOldest()
		{
			return oldest;
		}

		public void add(final boolean isNew, final Date created)
		{
			if (isNew)
			{
				newCount++;
			}
			else
			{
				openCount++;
			}
			if (created != null && (oldest == null || created.before(oldest)))
			{
				oldest = created;
			}
		}
	}

	/** Próximo jogo: poltronas à venda, vendidas, disponíveis e receita (mesmos números do relatório de vendas) */
	public static class GameLine
	{
		private final String code;
		private final String opponent;
		private final Date kickoff;
		private final String competition;
		private final int onSale;
		private final int sold;
		private final int available;
		private final double revenue;

		public GameLine(final String code, final String opponent, final Date kickoff, final String competition, final int onSale,
				final int sold, final int available, final double revenue)
		{
			this.code = code;
			this.opponent = opponent;
			this.kickoff = kickoff;
			this.competition = competition;
			this.onSale = onSale;
			this.sold = sold;
			this.available = available;
			this.revenue = revenue;
		}

		public String getCode()
		{
			return code;
		}

		public String getOpponent()
		{
			return opponent;
		}

		public Date getKickoff()
		{
			return kickoff;
		}

		public String getCompetition()
		{
			return competition;
		}

		public int getOnSale()
		{
			return onSale;
		}

		public int getSold()
		{
			return sold;
		}

		public int getAvailable()
		{
			return available;
		}

		public double getRevenue()
		{
			return revenue;
		}
	}

	/** Receita e pedidos de um mês */
	public static class MonthLine
	{
		private final YearMonth month;
		private double revenue;
		private int orders;

		public MonthLine(final YearMonth month)
		{
			this.month = month;
		}

		public YearMonth getMonth()
		{
			return month;
		}

		public double getRevenue()
		{
			return revenue;
		}

		public int getOrders()
		{
			return orders;
		}

		public void add(final double value)
		{
			revenue += value;
			orders++;
		}
	}

	public Period getPeriod()
	{
		return period;
	}

	public void setPeriod(final Period period)
	{
		this.period = period;
	}

	/** Início do período (null = desde o primeiro pedido) */
	public Date getFrom()
	{
		return from;
	}

	public void setFrom(final Date from)
	{
		this.from = from;
	}

	public Date getGeneratedAt()
	{
		return generatedAt;
	}

	public void setGeneratedAt(final Date generatedAt)
	{
		this.generatedAt = generatedAt;
	}

	public double getRevenue()
	{
		return revenue;
	}

	public void setRevenue(final double revenue)
	{
		this.revenue = revenue;
	}

	public int getOrders()
	{
		return orders;
	}

	public void setOrders(final int orders)
	{
		this.orders = orders;
	}

	public double getAverageTicket()
	{
		return orders == 0 ? 0 : revenue / orders;
	}

	public int getAwaitingOrders()
	{
		return awaitingOrders;
	}

	public void setAwaitingOrders(final int awaitingOrders)
	{
		this.awaitingOrders = awaitingOrders;
	}

	public double getAwaitingAmount()
	{
		return awaitingAmount;
	}

	public void setAwaitingAmount(final double awaitingAmount)
	{
		this.awaitingAmount = awaitingAmount;
	}

	public int getOpenTickets()
	{
		return openTickets;
	}

	public void setOpenTickets(final int openTickets)
	{
		this.openTickets = openTickets;
	}

	public List<CategoryLine> getCategories()
	{
		return categories;
	}

	public List<TicketLine> getTickets()
	{
		return tickets;
	}

	public List<GameLine> getGames()
	{
		return games;
	}

	public List<MonthLine> getMonths()
	{
		return months;
	}
}
