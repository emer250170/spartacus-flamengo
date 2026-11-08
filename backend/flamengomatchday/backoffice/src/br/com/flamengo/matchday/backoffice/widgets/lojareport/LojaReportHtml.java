package br.com.flamengo.matchday.backoffice.widgets.lojareport;

import br.com.flamengo.matchday.report.loja.LojaReport;
import br.com.flamengo.matchday.report.loja.LojaReport.Line;
import br.com.flamengo.matchday.report.loja.LojaReport.Point;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;


/**
 * HTML do relatório de vendas da Loja Flamengo, no mesmo visual dos relatórios de ingressos, do sócio-torcedor e do
 * resumo do administrador (cartões, tabelas e gráfico em SVG gerados no servidor).
 */
public class LojaReportHtml
{
	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
	private static final String RED = "#c8102e";
	private static final String GREY = "#c3cbd3";
	private static final String MUTED = "#556b82";

	private static final String CSS = "<style>"
			+ ".flr{color:#1d2d3e;max-width:1320px;font-size:13px}"
			+ ".flr *{box-sizing:border-box}"
			+ ".flr-head{margin:0 0 14px}"
			+ ".flr-h1{font-size:18px;font-weight:700}"
			+ ".flr-sub{font-size:13px;color:" + MUTED + ";margin-top:2px}"
			+ ".flr-kpis{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin-bottom:12px}"
			+ ".flr-kpi{background:#fff;border:1px solid #e1e5e9;border-radius:12px;padding:14px 16px;border-top:4px solid #1d2d3e}"
			+ ".flr-kpi.red{border-top-color:" + RED + "}.flr-kpi.grey{border-top-color:" + GREY + "}"
			+ ".flr-kpi-label{font-size:11px;color:" + MUTED + ";text-transform:uppercase;letter-spacing:.05em;font-weight:600}"
			+ ".flr-kpi-value{font-size:26px;font-weight:700;margin-top:6px;font-variant-numeric:tabular-nums;white-space:nowrap}"
			+ ".flr-kpi-sub{font-size:12px;color:" + MUTED + ";margin-top:4px}"
			+ ".flr-row{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:12px;margin-bottom:12px}"
			+ ".flr-card{background:#fff;border:1px solid #e1e5e9;border-radius:12px;padding:16px 18px;margin-bottom:12px}"
			+ ".flr-row .flr-card{margin-bottom:0}"
			+ ".flr-h2{font-size:15px;font-weight:700;margin:0}"
			+ ".flr-h2-sub{font-size:12px;color:" + MUTED + ";margin:2px 0 12px}"
			+ "table.flr-t{width:100%;border-collapse:collapse;font-size:13px;color:#1d2d3e}"
			+ ".flr-t th{text-align:right;font-weight:600;color:" + MUTED
			+ ";font-size:12px;padding:8px 10px;border-bottom:1px solid #e1e5e9;white-space:nowrap}"
			+ ".flr-t td{text-align:right;padding:9px 10px;border-bottom:1px solid #eef1f3;font-variant-numeric:tabular-nums;white-space:nowrap}"
			+ ".flr-t th:first-child,.flr-t td:first-child{text-align:left}"
			+ ".flr-t td.flr-name{white-space:normal}"
			+ ".flr-t tr.flr-total td{font-weight:700;border-top:1px solid #c9d0d6;border-bottom:none}"
			+ ".flr-t tr.flr-zero td{color:#97a5b3}"
			+ ".flr-rank{display:inline-block;width:20px;color:" + MUTED + "}"
			+ ".flr-bar{display:inline-block;width:64px;height:6px;background:#eef1f3;border-radius:3px;margin-right:8px;vertical-align:middle;overflow:hidden}"
			+ ".flr-bar span{display:block;height:100%;background:" + RED + "}"
			+ ".flr-pct{display:inline-block;min-width:42px;color:" + MUTED + "}"
			+ ".flr-code{font-family:ui-monospace,Menlo,Consolas,monospace;font-weight:600}"
			+ ".flr-note{font-size:12px;color:" + MUTED + ";margin:2px 2px 24px;line-height:1.5}"
			+ ".flr-empty{color:" + MUTED + ";font-size:12px;margin-top:6px}"
			+ "@media (max-width:1180px){.flr-kpis{grid-template-columns:repeat(3,minmax(0,1fr))}.flr-row{grid-template-columns:1fr}}"
			+ "</style>";

	private final LojaReport report;
	private final Function<String, String> labels;
	private final boolean portuguese;
	private final Locale locale;
	private final NumberFormat amount;
	private final NumberFormat integer;
	private final DateTimeFormatter dateTime;
	private final DateTimeFormatter dayMonth;

	public LojaReportHtml(final LojaReport report, final Function<String, String> labels, final Locale uiLocale)
	{
		this.report = report;
		this.labels = labels;
		this.portuguese = uiLocale != null && "pt".equals(uiLocale.getLanguage());
		this.locale = portuguese ? new Locale("pt", "BR") : Locale.US;
		this.amount = NumberFormat.getNumberInstance(locale);
		this.amount.setMinimumFractionDigits(2);
		this.amount.setMaximumFractionDigits(2);
		this.integer = NumberFormat.getIntegerInstance(locale);
		this.dateTime = DateTimeFormatter.ofPattern(portuguese ? "dd/MM/yyyy HH:mm" : "MMM d, yyyy h:mm a", locale);
		this.dayMonth = DateTimeFormatter.ofPattern(portuguese ? "dd/MM" : "MM/dd", locale);
	}

	public String render(final String title, final String subtitle)
	{
		final StringBuilder html = new StringBuilder(32_000);
		html.append("<div class=\"flr\">").append(CSS);
		html.append("<div class=\"flr-head\"><div class=\"flr-h1\">").append(esc(title)).append("</div>");
		if (subtitle != null && !subtitle.isEmpty())
		{
			html.append("<div class=\"flr-sub\">").append(esc(subtitle)).append("</div>");
		}
		html.append("</div>");
		kpis(html);
		series(html);
		html.append("<div class=\"flr-row\">");
		categories(html);
		products(html);
		html.append("</div><div class=\"flr-row\">");
		shares(html, "pay", report.payments);
		shares(html, "dlv", report.deliveries);
		html.append("</div><div class=\"flr-row\">");
		promotions(html);
		coupons(html);
		html.append("</div>");
		html.append("<div class=\"flr-note\">").append(esc(label("note.rules"))).append("<br/>")
				.append(esc(fmt("note.updated", dateTime.format(report.generatedAt.toInstant().atZone(ZONE))))).append("</div>");
		html.append("</div>");
		return html.toString();
	}

	// ------------------------------------------------------------ blocos

	protected void kpis(final StringBuilder html)
	{
		html.append("<div class=\"flr-kpis\">");
		kpi(html, "red", label("kpi.revenue"), money(report.revenue), label("period." + report.period.name()));
		kpi(html, "", label("kpi.orders"), integer.format(report.orders),
				report.customers == 1 ? label("kpi.orders.one") : fmt("kpi.orders.sub", integer.format(report.customers)));
		kpi(html, "", label("kpi.average"), money(report.getAverageTicket()), label("kpi.average.sub"));
		kpi(html, "", label("kpi.units"), integer.format(report.storeUnits), label("kpi.units.sub"));
		kpi(html, "grey", label("kpi.awaiting"), integer.format(report.awaitingOrders),
				report.awaitingOrders == 0 ? label("kpi.awaiting.none") : fmt("kpi.awaiting.sub", money(report.awaitingAmount)));
		html.append("</div>");
	}

	protected void kpi(final StringBuilder html, final String color, final String name, final String value, final String sub)
	{
		html.append("<div class=\"flr-kpi ").append(color).append("\"><div class=\"flr-kpi-label\">").append(esc(name))
				.append("</div><div class=\"flr-kpi-value\">").append(esc(value)).append("</div><div class=\"flr-kpi-sub\">")
				.append(esc(sub)).append("</div></div>");
	}

	/** Vendas por dia (mês, 30 dias) ou por mês (ano, desde o início), em barras */
	protected void series(final StringBuilder html)
	{
		final List<Point> points = report.series;
		double sum = 0;
		double max = 0;
		int orders = 0;
		for (final Point point : points)
		{
			sum += point.revenue;
			orders += point.orders;
			max = Math.max(max, point.revenue);
		}
		final String prefix = report.monthly ? "mo." : "dy.";
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label(prefix + "title")))
				.append("</div><div class=\"flr-h2-sub\">")
				.append(esc(fmt(prefix + "sub", money(sum), integer.format(orders)))).append("</div>");
		final int width = 1260;
		final int height = 270;
		final int left = 70;
		final int right = 14;
		final int top = 18;
		final int bottom = 34;
		final double plotWidth = width - left - right;
		final double plotHeight = height - top - bottom;
		final double step = niceStep(max);
		final double ceiling = Math.max(step * 4, Math.ceil(max / step) * step);
		html.append("<svg viewBox=\"0 0 ").append(width).append(' ').append(height)
				.append("\" width=\"100%\" style=\"display:block;max-height:300px\" role=\"img\" aria-label=\"")
				.append(esc(label(prefix + "title"))).append("\">");
		for (double value = 0; value <= ceiling + 0.001; value += step)
		{
			final double y = top + plotHeight - plotHeight * value / ceiling;
			html.append("<line x1=\"").append(left).append("\" x2=\"").append(width - right).append("\" y1=\"").append(num(y))
					.append("\" y2=\"").append(num(y)).append("\" stroke=\"").append(value == 0 ? "#c9d0d6" : "#eef1f3")
					.append("\"/><text x=\"").append(left - 8).append("\" y=\"").append(num(y + 4))
					.append("\" text-anchor=\"end\" font-size=\"11\" fill=\"").append(MUTED).append("\">").append(esc(shortMoney(value)))
					.append("</text>");
		}
		final int count = points.size();
		final double slot = plotWidth / Math.max(1, count);
		final double bar = Math.min(46, slot * 0.62);
		// rótulo do eixo: todos os meses; nos dias, um a cada 2 a 3 (sempre o primeiro e o último)
		final int every = report.monthly || count <= 16 ? 1 : count <= 31 ? 2 : 3;
		for (int i = 0; i < count; i++)
		{
			final Point point = points.get(i);
			final ZonedDateTime start = point.start.toInstant().atZone(ZONE);
			final double h = ceiling <= 0 ? 0 : plotHeight * point.revenue / ceiling;
			final double x = left + slot * i + (slot - bar) / 2;
			final String name = report.monthly
					? start.getMonth().getDisplayName(TextStyle.SHORT, locale).replace(".", "") + "/"
							+ String.valueOf(start.getYear()).substring(2)
					: dayMonth.format(start);
			final String tip = fmt("chart.tooltip", name, money(point.revenue), integer.format(point.orders));
			if (h > 0)
			{
				html.append("<rect x=\"").append(num(x)).append("\" y=\"").append(num(top + plotHeight - h)).append("\" width=\"")
						.append(num(bar)).append("\" height=\"").append(num(h)).append("\" rx=\"3\" fill=\"").append(RED)
						.append("\"><title>").append(esc(tip)).append("</title></rect>");
				if (report.monthly || count <= 16)
				{
					html.append("<text x=\"").append(num(x + bar / 2)).append("\" y=\"").append(num(top + plotHeight - h - 5))
							.append("\" text-anchor=\"middle\" font-size=\"11\" font-weight=\"600\" fill=\"#1d2d3e\">")
							.append(esc(shortMoney(point.revenue))).append("</text>");
				}
			}
			html.append("<rect x=\"").append(num(left + slot * i)).append("\" y=\"").append(top).append("\" width=\"").append(num(slot))
					.append("\" height=\"").append(num(plotHeight)).append("\" fill=\"transparent\"><title>").append(esc(tip))
					.append("</title></rect>");
			if (i % every == 0 || i == count - 1)
			{
				html.append("<text x=\"").append(num(x + bar / 2)).append("\" y=\"").append(height - 12)
						.append("\" text-anchor=\"middle\" font-size=\"11\" fill=\"").append(MUTED).append("\">").append(esc(name))
						.append("</text>");
			}
		}
		html.append("</svg>");
		if (sum == 0)
		{
			html.append("<div class=\"flr-empty\">").append(esc(label("chart.empty"))).append("</div>");
		}
		html.append("</section>");
	}

	/** Receita por tipo: produtos da loja, Estúdio, experiências... frete e descontos */
	protected void categories(final StringBuilder html)
	{
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label("cat.title")))
				.append("</div><div class=\"flr-h2-sub\">").append(esc(label("cat.sub"))).append("</div>");
		html.append("<table class=\"flr-t\"><thead><tr><th>").append(esc(label("col.type"))).append("</th><th>")
				.append(esc(label("col.qty"))).append("</th><th>").append(esc(label("col.orders"))).append("</th><th>")
				.append(esc(label("col.revenue"))).append("</th><th>").append(esc(label("col.share"))).append("</th></tr></thead><tbody>");
		for (final Line line : report.categories)
		{
			final boolean noQty = "FREIGHT".equals(line.key) || "DISCOUNTS".equals(line.key);
			html.append(line.orders == 0 ? "<tr class=\"flr-zero\">" : "<tr>").append("<td class=\"flr-name\">")
					.append(esc(label("cat." + line.key))).append("</td><td>")
					.append(noQty ? "&ndash;" : integer.format(line.quantity)).append("</td><td>").append(integer.format(line.orders))
					.append("</td><td>").append(esc(money(line.amount))).append("</td><td>").append(share(line.amount, report.revenue))
					.append("</td></tr>");
		}
		html.append("<tr class=\"flr-total\"><td>").append(esc(label("total"))).append("</td><td></td><td>")
				.append(integer.format(report.orders)).append("</td><td>").append(esc(money(report.revenue)))
				.append("</td><td></td></tr></tbody></table></section>");
	}

	/** Dez produtos da loja mais vendidos (tamanhos somados) */
	protected void products(final StringBuilder html)
	{
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label("prd.title")))
				.append("</div><div class=\"flr-h2-sub\">").append(esc(label("prd.sub"))).append("</div>");
		if (report.products.isEmpty())
		{
			html.append("<div class=\"flr-empty\">").append(esc(label("prd.empty"))).append("</div></section>");
			return;
		}
		html.append("<table class=\"flr-t\"><thead><tr><th>").append(esc(label("col.product"))).append("</th><th>")
				.append(esc(label("col.units"))).append("</th><th>").append(esc(label("col.revenue"))).append("</th></tr></thead><tbody>");
		int rank = 1;
		for (final Line line : report.products)
		{
			html.append("<tr><td class=\"flr-name\"><span class=\"flr-rank\">").append(rank++).append("</span>").append(esc(line.name))
					.append("</td><td>").append(integer.format(line.quantity)).append("</td><td>").append(esc(money(line.amount)))
					.append("</td></tr>");
		}
		html.append("</tbody></table></section>");
	}

	/** Formas de pagamento (pay) ou como o torcedor recebe (dlv): pedidos, receita e % dos pedidos */
	protected void shares(final StringBuilder html, final String prefix, final List<Line> lines)
	{
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label(prefix + ".title")))
				.append("</div><div class=\"flr-h2-sub\">").append(esc(label(prefix + ".sub"))).append("</div>");
		html.append("<table class=\"flr-t\"><thead><tr><th>").append(esc(label(prefix + ".col"))).append("</th><th>")
				.append(esc(label("col.orders"))).append("</th><th>").append(esc(label("col.ordersShare"))).append("</th><th>")
				.append(esc(label("col.revenue"))).append("</th></tr></thead><tbody>");
		for (final Line line : lines)
		{
			html.append(line.orders == 0 ? "<tr class=\"flr-zero\">" : "<tr>").append("<td class=\"flr-name\">")
					.append(esc(label(prefix + "." + line.key))).append("</td><td>").append(integer.format(line.orders)).append("</td><td>")
					.append(share(line.orders, report.orders)).append("</td><td>").append(esc(money(line.amount))).append("</td></tr>");
		}
		html.append("</tbody></table></section>");
	}

	protected void promotions(final StringBuilder html)
	{
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label("prm.title")))
				.append("</div><div class=\"flr-h2-sub\">").append(esc(label("prm.sub"))).append("</div>");
		if (report.promotions.isEmpty())
		{
			html.append("<div class=\"flr-empty\">").append(esc(label("prm.empty"))).append("</div></section>");
			return;
		}
		html.append("<table class=\"flr-t\"><thead><tr><th>").append(esc(label("col.promotion"))).append("</th><th>")
				.append(esc(label("col.orders"))).append("</th><th>").append(esc(label("col.discount"))).append("</th></tr></thead><tbody>");
		for (final Line line : report.promotions)
		{
			html.append("<tr><td class=\"flr-name\">").append(esc(line.name)).append("</td><td>").append(integer.format(line.orders))
					.append("</td><td>").append(esc(money(line.amount))).append("</td></tr>");
		}
		html.append("</tbody></table></section>");
	}

	protected void coupons(final StringBuilder html)
	{
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label("cpn.title")))
				.append("</div><div class=\"flr-h2-sub\">").append(esc(label("cpn.sub"))).append("</div>");
		if (report.coupons.isEmpty())
		{
			html.append("<div class=\"flr-empty\">").append(esc(label("cpn.empty"))).append("</div></section>");
			return;
		}
		html.append("<table class=\"flr-t\"><thead><tr><th>").append(esc(label("col.coupon"))).append("</th><th>")
				.append(esc(label("col.orders"))).append("</th><th>").append(esc(label("col.ordersRevenue"))).append("</th></tr></thead><tbody>");
		for (final Line line : report.coupons)
		{
			html.append("<tr><td><span class=\"flr-code\">").append(esc(line.name)).append("</span></td><td>")
					.append(integer.format(line.orders)).append("</td><td>").append(esc(money(line.amount))).append("</td></tr>");
		}
		html.append("</tbody></table></section>");
	}

	// ------------------------------------------------------------ apoio

	protected String share(final double part, final double whole)
	{
		final double share = whole <= 0 ? 0 : part * 100 / whole;
		// desconto (valor negativo): "− 0,3%", com a barra vazia
		final String text = share < -0.0001 ? "− " + percent(-share) : percent(share);
		return "<span class=\"flr-bar\"><span style=\"width:" + num(Math.max(0, Math.min(100, share)))
				+ "%\"></span></span><span class=\"flr-pct\">" + esc(text) + "</span>";
	}

	protected String label(final String key)
	{
		final String value = labels.apply(key);
		return value == null || value.isEmpty() ? key : value;
	}

	/** Troca {0}, {1}... (sem MessageFormat, para não mexer em apóstrofos) */
	protected String fmt(final String key, final String... args)
	{
		String text = label(key);
		for (int i = 0; i < args.length; i++)
		{
			text = text.replace("{" + i + "}", args[i]);
		}
		return text;
	}

	protected String money(final double value)
	{
		return (value < -0.004 ? "− " : "") + (portuguese ? "R$ " : "R$") + amount.format(Math.abs(value));
	}

	/** Eixo: "R$ 12 mil" / "R$ 1,5 mil" / "R$ 800" */
	protected String shortMoney(final double value)
	{
		if (value >= 1000)
		{
			final NumberFormat f = NumberFormat.getNumberInstance(locale);
			f.setMaximumFractionDigits(value >= 10_000 ? 0 : 1);
			return (portuguese ? "R$ " : "R$") + f.format(value / 1000) + (portuguese ? " mil" : "k");
		}
		return (portuguese ? "R$ " : "R$") + integer.format(Math.round(value));
	}

	protected String percent(final double value)
	{
		final NumberFormat format = NumberFormat.getNumberInstance(locale);
		format.setMaximumFractionDigits(value > 0 && value < 10 ? 1 : 0);
		return format.format(value) + "%";
	}

	/** Passo do eixo (1, 2, 5 x 10^n) para ~4 linhas */
	protected static double niceStep(final double max)
	{
		if (max <= 0)
		{
			return 250;
		}
		final double raw = max / 4.0;
		final double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
		final double normalized = raw / magnitude;
		final double nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
		return Math.max(1, nice * magnitude);
	}

	protected static String num(final double value)
	{
		return String.format(Locale.ROOT, "%.2f", value);
	}

	public static String esc(final String text)
	{
		if (text == null)
		{
			return "";
		}
		final StringBuilder out = new StringBuilder(text.length());
		for (final char c : text.toCharArray())
		{
			switch (c)
			{
				case '<':
					out.append("&lt;");
					break;
				case '>':
					out.append("&gt;");
					break;
				case '&':
					out.append("&amp;");
					break;
				case '"':
					out.append("&quot;");
					break;
				default:
					out.append(c);
			}
		}
		return out.toString();
	}
}
