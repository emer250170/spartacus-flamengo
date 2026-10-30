package br.com.flamengo.matchday.report;

/**
 * Uma linha do relatório de ingressos: o total, um setor ou um tipo de ingresso.
 */
public class MatchdayReportLine
{
	private final String key;
	/** Ingressos colocados à venda (poltronas do jogo) */
	private int onSale;
	private int sold;
	private int available;
	/** Poltronas seguras em algum carrinho (nem vendidas nem livres) */
	private int held;
	/** Vendidos pagos (sem gratuidade e cortesia), base do ticket médio */
	private int paid;
	private double revenue;

	public MatchdayReportLine(final String key)
	{
		this.key = key;
	}

	public void addSeat(final boolean isSold, final boolean isAvailable)
	{
		onSale++;
		if (isSold)
		{
			sold++;
		}
		else if (isAvailable)
		{
			available++;
		}
		else
		{
			held++;
		}
	}

	public void addSale(final double price)
	{
		if (price > 0.004)
		{
			paid++;
			revenue += price;
		}
	}

	/** Receita ÷ ingressos pagos */
	public double getAverageTicket()
	{
		return paid == 0 ? 0 : revenue / paid;
	}

	/** % vendido dos ingressos à venda (0 a 100) */
	public double getSoldPercent()
	{
		return onSale == 0 ? 0 : sold * 100.0 / onSale;
	}

	public String getKey()
	{
		return key;
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

	public int getHeld()
	{
		return held;
	}

	public int getPaid()
	{
		return paid;
	}

	public double getRevenue()
	{
		return revenue;
	}

	/** Venda contada só pelo tipo de ingresso (sem poltrona à venda associada) */
	public void addTypedSale(final double price)
	{
		sold++;
		addSale(price);
	}
}
