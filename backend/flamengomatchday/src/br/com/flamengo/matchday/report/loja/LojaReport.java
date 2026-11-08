package br.com.flamengo.matchday.report.loja;

import br.com.flamengo.matchday.report.summary.SummaryReport;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Relatório de vendas da perspectiva Loja Flamengo (gestor da loja, 08/11): indicadores do período, vendas por dia
 * (ou por mês), receita por tipo, produtos mais vendidos, formas de pagamento, como o torcedor recebe e promoções e
 * cupons. Mesmas regras do resumo de vendas do administrador: sem cancelados, recusados na análise de fraude e
 * pedidos aguardando o Pix ou o boleto (esses aparecem à parte).
 */
public class LojaReport
{
	public SummaryReport.Period period;
	public Date from;
	public Date generatedAt;
	public double revenue;
	public int orders;
	public int customers;
	/** Unidades de produtos da loja e do Mengo Estúdio */
	public long storeUnits;
	public double discounts;
	public int awaitingOrders;
	public double awaitingAmount;
	/** true: a série é por mês (ano, desde o início); false: por dia */
	public boolean monthly;
	public final List<Point> series = new ArrayList<>();
	public final List<Line> categories = new ArrayList<>();
	public final List<Line> products = new ArrayList<>();
	public final List<Line> payments = new ArrayList<>();
	public final List<Line> deliveries = new ArrayList<>();
	public final List<Line> promotions = new ArrayList<>();
	public final List<Line> coupons = new ArrayList<>();

	public double getAverageTicket()
	{
		return orders == 0 ? 0 : revenue / orders;
	}

	/** Um ponto do gráfico: dia ou mês */
	public static class Point
	{
		public final Date start;
		public double revenue;
		public int orders;

		public Point(final Date start)
		{
			this.start = start;
		}
	}

	/** Uma linha de tabela: chave (ou código), nome, quantidade, pedidos e valor */
	public static class Line
	{
		public final String key;
		public String name;
		public long quantity;
		public int orders;
		public double amount;

		public Line(final String key, final String name)
		{
			this.key = key;
			this.name = name;
		}
	}
}
