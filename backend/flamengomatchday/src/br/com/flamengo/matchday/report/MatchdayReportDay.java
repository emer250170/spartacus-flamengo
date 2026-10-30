package br.com.flamengo.matchday.report;

import java.time.LocalDate;


/**
 * Vendas de um dia (horário de Brasília).
 */
public class MatchdayReportDay
{
	private final LocalDate day;
	private int sold;
	private double revenue;

	public MatchdayReportDay(final LocalDate day)
	{
		this.day = day;
	}

	public void add(final double price)
	{
		sold++;
		revenue += price;
	}

	public LocalDate getDay()
	{
		return day;
	}

	public int getSold()
	{
		return sold;
	}

	public double getRevenue()
	{
		return revenue;
	}
}
