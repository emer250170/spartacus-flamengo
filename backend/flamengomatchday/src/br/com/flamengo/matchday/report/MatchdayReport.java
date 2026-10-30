package br.com.flamengo.matchday.report;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Relatório de ingressos de um jogo (ou de vários somados): totais, setores,
 * tipos de ingresso e vendas por dia.
 */
public class MatchdayReport
{
	private final MatchdayReportLine total = new MatchdayReportLine("TOTAL");
	private final List<MatchdayReportLine> sectors = new ArrayList<>();
	private final List<MatchdayReportLine> ticketTypes = new ArrayList<>();
	private final List<MatchdayReportDay> days = new ArrayList<>();
	private int games;
	/** Vendas que vieram de pedidos da loja */
	private int storeSales;
	/** Vendas da ocupação simulada (sem pedido): tipo, preço e data gerados para a demonstração */
	private int simulatedSales;
	private LocalDate firstDay;
	private LocalDate lastDay;
	private Date generatedAt;

	public MatchdayReportLine getTotal()
	{
		return total;
	}

	public List<MatchdayReportLine> getSectors()
	{
		return sectors;
	}

	public List<MatchdayReportLine> getTicketTypes()
	{
		return ticketTypes;
	}

	public List<MatchdayReportDay> getDays()
	{
		return days;
	}

	public int getGames()
	{
		return games;
	}

	public void setGames(final int games)
	{
		this.games = games;
	}

	public int getStoreSales()
	{
		return storeSales;
	}

	public void setStoreSales(final int storeSales)
	{
		this.storeSales = storeSales;
	}

	public int getSimulatedSales()
	{
		return simulatedSales;
	}

	public void setSimulatedSales(final int simulatedSales)
	{
		this.simulatedSales = simulatedSales;
	}

	public LocalDate getFirstDay()
	{
		return firstDay;
	}

	public void setFirstDay(final LocalDate firstDay)
	{
		this.firstDay = firstDay;
	}

	public LocalDate getLastDay()
	{
		return lastDay;
	}

	public void setLastDay(final LocalDate lastDay)
	{
		this.lastDay = lastDay;
	}

	public Date getGeneratedAt()
	{
		return generatedAt;
	}

	public void setGeneratedAt(final Date generatedAt)
	{
		this.generatedAt = generatedAt;
	}
}
