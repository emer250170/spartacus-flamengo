package br.com.flamengo.matchday.backoffice.widgets.summary;

import br.com.flamengo.matchday.report.summary.SummaryReport;
import br.com.flamengo.matchday.report.summary.SummaryReport.CategoryLine;
import br.com.flamengo.matchday.report.summary.SummaryReport.GameLine;
import br.com.flamengo.matchday.report.summary.SummaryReport.MonthLine;
import br.com.flamengo.matchday.report.summary.SummaryReport.TicketLine;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;


/**
 * HTML do resumo de vendas do administrador, no mesmo visual dos relatórios de vendas e do
 * sócio-torcedor (cartões, tabelas e gráfico em SVG gerados no servidor).
 */
public class SummaryReportHtml
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
			+ ".flr-row{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(0,1fr);gap:12px;margin-bottom:12px}"
			+ ".flr-card{background:#fff;border:1px solid #e1e5e9;border-radius:12px;padding:16px 18px;margin-bottom:12px}"
			+ ".flr-row .flr-card{margin-bottom:0}"
			+ ".flr-h2{font-size:15px;font-weight:700;margin:0}"
			+ ".flr-h2-sub{font-size:12px;color:" + MUTED + ";margin:2px 0 12px}"
			+ "table.flr-t{width:100%;border-collapse:collapse;font-size:13px;color:#1d2d3e}"
			+ ".flr-t th{text-align:right;font-weight:600;color:" + MUTED
			+ ";font-size:12px;padding:8px 10px;border-bottom:1px solid #e1e5e9;white-space:nowrap}"
			+ ".flr-t td{text-align:right;padding:9px 10px;border-bottom:1px solid #eef1f3;font-variant-numeric:tabular-nums;white-space:nowrap}"
			+ ".flr-t th:first-child,.flr-t td:first-child{text-align:left}"
			+ ".flr-t tr.flr-total td{font-weight:700;border-top:1px solid #c9d0d6;border-bottom:none}"
			+ ".flr-t tr.flr-zero td{color:#97a5b3}"
			+ ".flr-bar{display:inline-block;width:72px;height:6px;background:#eef1f3;border-radius:3px;margin-right:8px;vertical-align:middle;overflow:hidden}"
			+ ".flr-bar span{display:block;height:100%;background:" + RED + "}"
			+ ".flr-pct{display:inline-block;min-width:42px;color:" + MUTED + "}"
			+ ".flr-small{font-size:12px;color:" + MUTED + "}"
			+ ".flr-note{font-size:12px;color:" + MUTED + ";margin:2px 2px 24px;line-height:1.5}"
			+ ".flr-empty{color:" + MUTED + ";font-size:12px;margin-top:6px}"
			+ "@media (max-width:1180px){.flr-kpis{grid-template-columns:repeat(3,minmax(0,1fr))}.flr-row{grid-template-columns:1fr}}"
			+ "</style>";

	private final SummaryReport report;
	private final Function<String, String> labels;
	private final boolean portuguese;
	private final Locale locale;
	private final NumberFormat amount;
	private final NumberFormat integer;
	private final DateTimeFormatter dateTime;
	private final DateTimeFormatter day;

	public SummaryReportHtml(final SummaryReport report, final Function<String, String> labels, final Locale uiLocale)
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
		this.day = DateTimeFormatter.ofPattern(portuguese ? "dd/MM/yyyy" : "MMM d, yyyy", locale);
	}

	public String render(final String title, final String subtitle)
	{
		final StringBuilder html = new StringBuilder(24_000);
		html.append("<div class=\"flr\">").append(CSS);
		html.append("<div class=\"flr-head\"><div class=\"flr-h1\">").append(esc(title)).append("</div>");
		if (subtitle != null && !subtitle.isEmpty())
		{
			html.append("<div class=\"flr-sub\">").append(esc(subtitle)).append("</div>");
		}
		html.append("</div>");
		kpis(html);
		html.append("<div class=\"flr-row\">");
		categories(html);
		tickets(html);
		html.append("</div>");
		games(html);
		months(html);
		html.append("<div class=\"flr-note\">").append(esc(label("note.rules"))).append("<br/>")
				.append(esc(fmt("note.updated", dateTime.format(report.getGeneratedAt().toInstant().atZone(ZONE))))).append("</div>");
		html.append("</div>");
		return html.toString();
	}

	// ------------------------------------------------------------ blocos

	protected void kpis(final StringBuilder html)
	{
		html.append("<div class=\"flr-kpis\">");
		kpi(html, "red", label("kpi.revenue"), money(report.getRevenue()), label("period." + report.getPeriod().name()));
		kpi(html, "", label("kpi.orders"), integer.format(report.getOrders()), label("kpi.orders.sub"));
		kpi(html, "", label("kpi.average"), money(report.getAverageTicket()), label("kpi.average.sub"));
		kpi(html, "grey", label("kpi.awaiting"), integer.format(report.getAwaitingOrders()),
				report.getAwaitingOrders() == 0 ? label("kpi.awaiting.none") : fmt("kpi.awaiting.sub", money(report.getAwaitingAmount())));
		kpi(html, "", label("kpi.tickets"), integer.format(report.getOpenTickets()), label("kpi.tickets.sub"));
		html.append("</div>");
	}

	protected void kpi(final StringBuilder html, final String color, final String name, final String value, final String sub)
	{
		html.append("<div class=\"flr-kpi ").append(color).append("\"><div class=\"flr-kpi-label\">").append(esc(name))
				.append("</div><div class=\"flr-kpi-value\">").append(esc(value)).append("</div><div class=\"flr-kpi-sub\">")
				.append(esc(sub)).append("</div></div>");
	}

	/** Vendas por produto e serviço no período: quantidade, pedidos, receita e % da receita */
	protected void categories(final StringBuilder html)
	{
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label("cat.title")))
				.append("</div><div class=\"flr-h2-sub\">").append(esc(fmt("cat.sub", label("period." + report.getPeriod().name()))))
				.append("</div>");
		html.append("<table class=\"flr-t\"><thead><tr><th>").append(esc(label("cat.col.type"))).append("</th><th>")
				.append(esc(label("cat.col.qty"))).append("</th><th>").append(esc(label("cat.col.orders"))).append("</th><th>")
				.append(esc(label("cat.col.revenue"))).append("</th><th>").append(esc(label("cat.col.share"))).append("</th></tr></thead><tbody>");
		final double total = report.getRevenue();
		for (final CategoryLine line : report.getCategories())
		{
			final boolean zero = line.getOrders() == 0;
			final boolean noQty = "FREIGHT".equals(line.getKey()) || "DISCOUNTS".equals(line.getKey());
			final double share = total <= 0 ? 0 : line.getRevenue() * 100 / total;
			html.append(zero ? "<tr class=\"flr-zero\">" : "<tr>").append("<td>").append(esc(label("cat." + line.getKey())))
					.append("</td><td>").append(noQty ? "&ndash;" : integer.format(line.getQuantity())).append("</td><td>")
					.append(integer.format(line.getOrders())).append("</td><td>").append(esc(money(line.getRevenue())))
					.append("</td><td><span class=\"flr-bar\"><span style=\"width:").append(num(Math.max(0, Math.min(100, share))))
					.append("%\"></span></span><span class=\"flr-pct\">").append(esc(percent(share))).append("</span></td></tr>");
		}
		html.append("<tr class=\"flr-total\"><td>").append(esc(label("total"))).append("</td><td></td><td>")
				.append(integer.format(report.getOrders())).append("</td><td>").append(esc(money(total))).append("</td><td>")
				.append(esc(percent(total > 0 ? 100 : 0))).append("</td></tr></tbody></table></section>");
	}

	/** Chamados que não estão fechados, por tipo */
	protected void tickets(final StringBuilder html)
	{
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label("tk.title")))
				.append("</div><div class=\"flr-h2-sub\">").append(esc(label("tk.sub"))).append("</div>");
		if (report.getTickets().isEmpty())
		{
			html.append("<div class=\"flr-empty\">").append(esc(label("tk.empty"))).append("</div></section>");
			return;
		}
		html.append("<table class=\"flr-t\"><thead><tr><th>").append(esc(label("tk.col.type"))).append("</th><th>")
				.append(esc(label("tk.col.new"))).append("</th><th>").append(esc(label("tk.col.open"))).append("</th><th>")
				.append(esc(label("tk.col.total"))).append("</th><th>").append(esc(label("tk.col.oldest"))).append("</th></tr></thead><tbody>");
		int news = 0;
		int opens = 0;
		for (final TicketLine line : report.getTickets())
		{
			news += line.getNewCount();
			opens += line.getOpenCount();
			html.append("<tr><td>").append(esc(ticketType(line.getKey()))).append("</td><td>").append(integer.format(line.getNewCount()))
					.append("</td><td>").append(integer.format(line.getOpenCount())).append("</td><td>")
					.append(integer.format(line.getTotal())).append("</td><td>").append(esc(age(line))).append("</td></tr>");
		}
		html.append("<tr class=\"flr-total\"><td>").append(esc(label("total"))).append("</td><td>").append(integer.format(news))
				.append("</td><td>").append(integer.format(opens)).append("</td><td>").append(integer.format(news + opens))
				.append("</td><td></td></tr></tbody></table></section>");
	}

	/** Próximos jogos: poltronas à venda, vendidas (barra), disponíveis e receita */
	protected void games(final StringBuilder html)
	{
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label("gm.title")))
				.append("</div><div class=\"flr-h2-sub\">").append(esc(label("gm.sub"))).append("</div>");
		if (report.getGames().isEmpty())
		{
			html.append("<div class=\"flr-empty\">").append(esc(label("gm.empty"))).append("</div></section>");
			return;
		}
		html.append("<table class=\"flr-t\"><thead><tr><th>").append(esc(label("gm.col.game"))).append("</th><th>")
				.append(esc(label("gm.col.date"))).append("</th><th>").append(esc(label("gm.col.onSale"))).append("</th><th>")
				.append(esc(label("gm.col.sold"))).append("</th><th>").append(esc(label("gm.col.available"))).append("</th><th>")
				.append(esc(label("gm.col.revenue"))).append("</th></tr></thead><tbody>");
		for (final GameLine game : report.getGames())
		{
			final double sold = game.getOnSale() <= 0 ? 0 : game.getSold() * 100.0 / game.getOnSale();
			html.append("<tr><td>").append(esc("Flamengo x " + (game.getOpponent() == null ? "" : game.getOpponent().trim())));
			if (game.getCompetition() != null && !game.getCompetition().trim().isEmpty())
			{
				html.append(" <span class=\"flr-small\">· ").append(esc(game.getCompetition().trim())).append("</span>");
			}
			html.append("</td><td>").append(esc(dateTime.format(game.getKickoff().toInstant().atZone(ZONE)))).append("</td><td>")
					.append(integer.format(game.getOnSale())).append("</td><td><span class=\"flr-bar\"><span style=\"width:")
					.append(num(Math.min(100, sold))).append("%\"></span></span><span class=\"flr-pct\">").append(esc(percent(sold)))
					.append("</span> ").append(integer.format(game.getSold())).append("</td><td>").append(integer.format(game.getAvailable()))
					.append("</td><td>").append(esc(money(game.getRevenue()))).append("</td></tr>");
		}
		html.append("</tbody></table></section>");
	}

	/** Receita mês a mês (últimos 12 meses), em barras */
	protected void months(final StringBuilder html)
	{
		final List<MonthLine> months = report.getMonths();
		double sum = 0;
		double max = 0;
		for (final MonthLine month : months)
		{
			sum += month.getRevenue();
			max = Math.max(max, month.getRevenue());
		}
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label("mo.title")))
				.append("</div><div class=\"flr-h2-sub\">").append(esc(fmt("mo.sub", money(sum)))).append("</div>");
		final int width = 960;
		final int height = 270;
		final int left = 70;
		final int right = 18;
		final int top = 18;
		final int bottom = 34;
		final double plotWidth = width - left - right;
		final double plotHeight = height - top - bottom;
		final double step = niceStep(max);
		final double ceiling = Math.max(step * 4, Math.ceil(max / step) * step);
		html.append("<svg viewBox=\"0 0 ").append(width).append(' ').append(height)
				.append("\" width=\"100%\" style=\"display:block;max-height:320px\" role=\"img\" aria-label=\"")
				.append(esc(label("mo.title"))).append("\">");
		for (double value = 0; value <= ceiling + 0.001; value += step)
		{
			final double y = top + plotHeight - plotHeight * value / ceiling;
			html.append("<line x1=\"").append(left).append("\" x2=\"").append(width - right).append("\" y1=\"").append(num(y))
					.append("\" y2=\"").append(num(y)).append("\" stroke=\"").append(value == 0 ? "#c9d0d6" : "#eef1f3")
					.append("\"/><text x=\"").append(left - 8).append("\" y=\"").append(num(y + 4))
					.append("\" text-anchor=\"end\" font-size=\"11\" fill=\"").append(MUTED).append("\">").append(esc(shortMoney(value)))
					.append("</text>");
		}
		final int count = months.size();
		final double slot = plotWidth / Math.max(1, count);
		final double bar = Math.min(46, slot * 0.6);
		for (int i = 0; i < count; i++)
		{
			final MonthLine month = months.get(i);
			final double h = ceiling <= 0 ? 0 : plotHeight * month.getRevenue() / ceiling;
			final double x = left + slot * i + (slot - bar) / 2;
			final String name = month.getMonth().getMonth().getDisplayName(TextStyle.SHORT, locale).replace(".", "") + "/"
					+ String.valueOf(month.getMonth().getYear()).substring(2);
			final String tip = fmt("mo.tooltip", name, money(month.getRevenue()), integer.format(month.getOrders()));
			if (h > 0)
			{
				html.append("<rect x=\"").append(num(x)).append("\" y=\"").append(num(top + plotHeight - h)).append("\" width=\"")
						.append(num(bar)).append("\" height=\"").append(num(h)).append("\" rx=\"3\" fill=\"").append(RED)
						.append("\"><title>").append(esc(tip)).append("</title></rect>");
				html.append("<text x=\"").append(num(x + bar / 2)).append("\" y=\"").append(num(top + plotHeight - h - 5))
						.append("\" text-anchor=\"middle\" font-size=\"11\" font-weight=\"600\" fill=\"#1d2d3e\">")
						.append(esc(shortMoney(month.getRevenue()))).append("</text>");
			}
			html.append("<rect x=\"").append(num(left + slot * i)).append("\" y=\"").append(top).append("\" width=\"").append(num(slot))
					.append("\" height=\"").append(num(plotHeight)).append("\" fill=\"transparent\"><title>").append(esc(tip))
					.append("</title></rect>");
			html.append("<text x=\"").append(num(x + bar / 2)).append("\" y=\"").append(height - 12)
					.append("\" text-anchor=\"middle\" font-size=\"11\" fill=\"").append(MUTED).append("\">").append(esc(name))
					.append("</text>");
		}
		html.append("</svg>");
		if (sum == 0)
		{
			html.append("<div class=\"flr-empty\">").append(esc(label("mo.empty"))).append("</div>");
		}
		html.append("</section>");
	}

	// ------------------------------------------------------------ apoio

	protected String ticketType(final String key)
	{
		final String value = labels.apply("tk.type." + key);
		return value == null || value.isEmpty() ? key : value;
	}

	/** "há 3 dias" (dias de calendário desde o chamado mais antigo do tipo) */
	protected String age(final TicketLine line)
	{
		if (line.getOldest() == null)
		{
			return "";
		}
		final long days = ChronoUnit.DAYS.between(line.getOldest().toInstant().atZone(ZONE).toLocalDate(),
				report.getGeneratedAt().toInstant().atZone(ZONE).toLocalDate());
		final String when = day.format(line.getOldest().toInstant().atZone(ZONE));
		return days <= 0 ? fmt("tk.age.today", when) : days == 1 ? fmt("tk.age.one", when) : fmt("tk.age.days", String.valueOf(days), when);
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
		return (portuguese ? "R$ " : "R$") + amount.format(value);
	}

	/** Eixo: "R$ 12 mil" / "R$ 1,5 mil" / "R$ 800" */
	protected String shortMoney(final double value)
	{
		if (value >= 1000)
		{
			final NumberFormat f = NumberFormat.getNumberInstance(locale);
			f.setMaximumFractionDigits(value >= 10_000 ? 0 : 1);
			return (portuguese ? "R$ " : "R$") + f.format(value / 1000) + (portuguese ? " mil" : "k");
		}
		return (portuguese ? "R$ " : "R$") + integer.format(Math.round(value));
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
