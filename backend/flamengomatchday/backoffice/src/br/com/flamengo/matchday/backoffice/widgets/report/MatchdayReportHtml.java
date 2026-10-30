package br.com.flamengo.matchday.backoffice.widgets.report;

import br.com.flamengo.matchday.report.MatchdayReport;
import br.com.flamengo.matchday.report.MatchdayReportDay;
import br.com.flamengo.matchday.report.MatchdayReportLine;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;


/**
 * Monta o HTML do relatório de ingressos (cartões, tabelas e gráficos em SVG
 * gerados no servidor, sem biblioteca de gráficos no navegador).
 */
public class MatchdayReportHtml
{
	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
	private static final String RED = "#c8102e";
	private static final String GREY = "#c3cbd3";
	private static final String AMBER = "#e9a23b";
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
			+ ".flr-row{display:grid;grid-template-columns:minmax(260px,1fr) minmax(0,2.2fr);gap:12px;margin-bottom:12px}"
			+ ".flr-card{background:#fff;border:1px solid #e1e5e9;border-radius:12px;padding:16px 18px;margin-bottom:12px}"
			+ ".flr-row .flr-card{margin-bottom:0}"
			+ ".flr-h2{font-size:15px;font-weight:700;margin:0}"
			+ ".flr-h2-sub{font-size:12px;color:" + MUTED + ";margin:2px 0 12px}"
			+ ".flr-center{text-align:center}"
			+ "table.flr-t{width:100%;border-collapse:collapse;font-size:13px;color:#1d2d3e}"
			+ ".flr-t th{text-align:right;font-weight:600;color:" + MUTED
			+ ";font-size:12px;padding:8px 10px;border-bottom:1px solid #e1e5e9;white-space:nowrap}"
			+ ".flr-t td{text-align:right;padding:9px 10px;border-bottom:1px solid #eef1f3;font-variant-numeric:tabular-nums;white-space:nowrap}"
			+ ".flr-t th:first-child,.flr-t td:first-child{text-align:left}"
			+ ".flr-t tr.flr-total td{font-weight:700;border-top:1px solid #c9d0d6;border-bottom:none}"
			+ ".flr-bar{display:inline-block;width:72px;height:6px;background:#eef1f3;border-radius:3px;margin-right:8px;vertical-align:middle;overflow:hidden}"
			+ ".flr-bar span{display:block;height:100%;background:" + RED + "}"
			+ ".flr-pct{display:inline-block;min-width:42px;color:" + MUTED + "}"
			+ ".flr-legend{display:flex;gap:14px;justify-content:center;flex-wrap:wrap;font-size:12px;margin-top:10px}"
			+ ".flr-dot{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:6px;vertical-align:-1px}"
			+ ".flr-note{font-size:12px;color:" + MUTED + ";margin:2px 2px 24px;line-height:1.5}"
			+ ".flr-empty{color:" + MUTED + ";font-size:12px;margin-top:6px}"
			+ "@media (max-width:1180px){.flr-kpis{grid-template-columns:repeat(3,minmax(0,1fr))}.flr-row{grid-template-columns:1fr}}"
			+ "</style>";

	private final MatchdayReport report;
	private final Function<String, String> labels;
	private final boolean portuguese;
	/** Valor com 2 casas; o "R$" vem em money() (a JVM do servidor mostra "BRL" em inglês) */
	private final NumberFormat amount;
	private final NumberFormat integer;
	private final DateTimeFormatter shortDay;
	private final DateTimeFormatter dateTime;

	public MatchdayReportHtml(final MatchdayReport report, final Function<String, String> labels, final Locale uiLocale)
	{
		this.report = report;
		this.labels = labels;
		this.portuguese = uiLocale != null && "pt".equals(uiLocale.getLanguage());
		final Locale locale = portuguese ? new Locale("pt", "BR") : Locale.US;
		this.amount = NumberFormat.getNumberInstance(locale);
		this.amount.setMinimumFractionDigits(2);
		this.amount.setMaximumFractionDigits(2);
		this.integer = NumberFormat.getIntegerInstance(locale);
		this.shortDay = DateTimeFormatter.ofPattern(portuguese ? "dd/MM" : "MMM d", locale);
		this.dateTime = DateTimeFormatter.ofPattern(portuguese ? "dd/MM/yyyy HH:mm" : "MMM d, yyyy h:mm a", locale);
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
		donut(html);
		sectors(html);
		html.append("</div>");
		days(html);
		ticketTypes(html);
		note(html);
		html.append("</div>");
		return html.toString();
	}

	// ------------------------------------------------------------ blocos

	protected void kpis(final StringBuilder html)
	{
		final MatchdayReportLine total = report.getTotal();
		html.append("<div class=\"flr-kpis\">");
		kpi(html, "", label("kpi.onSale"), integer.format(total.getOnSale()),
				report.getGames() > 1 ? fmt("kpi.onSale.games", integer.format(report.getGames())) : label("kpi.onSale.sub"));
		kpi(html, "red", label("kpi.sold"), integer.format(total.getSold()),
				fmt("kpi.sold.sub", percent(total.getSold(), total.getOnSale())));
		kpi(html, "grey", label("kpi.available"), integer.format(total.getAvailable()),
				total.getHeld() > 0 ? fmt("kpi.available.held", integer.format(total.getHeld()))
						: fmt("kpi.available.sub", percent(total.getAvailable(), total.getOnSale())));
		kpi(html, "", label("kpi.revenue"), money(total.getRevenue()),
				fmt("kpi.revenue.sub", integer.format(total.getPaid())));
		kpi(html, "", label("kpi.average"), money(total.getAverageTicket()), label("kpi.average.sub"));
		html.append("</div>");
	}

	protected void kpi(final StringBuilder html, final String color, final String name, final String value, final String sub)
	{
		html.append("<div class=\"flr-kpi ").append(color).append("\"><div class=\"flr-kpi-label\">").append(esc(name))
				.append("</div><div class=\"flr-kpi-value\">").append(esc(value)).append("</div><div class=\"flr-kpi-sub\">")
				.append(esc(sub)).append("</div></div>");
	}

	/** Rosca: vendidos x disponíveis (e reservados em carrinho, quando houver) */
	protected void donut(final StringBuilder html)
	{
		final MatchdayReportLine total = report.getTotal();
		final double sum = total.getOnSale();
		final double radius = 74;
		final double circumference = 2 * Math.PI * radius;
		html.append("<section class=\"flr-card flr-center\"><div class=\"flr-h2\">").append(esc(label("donut.title")))
				.append("</div><div class=\"flr-h2-sub\">").append(esc(label("donut.sub"))).append("</div>");
		html.append("<svg viewBox=\"0 0 200 200\" width=\"210\" height=\"210\" role=\"img\" aria-label=\"")
				.append(esc(label("donut.title"))).append("\">");
		html.append("<circle cx=\"100\" cy=\"100\" r=\"74\" fill=\"none\" stroke=\"#eef1f3\" stroke-width=\"28\"/>");
		double offset = 0;
		final Object[][] slices = { { total.getSold(), RED, label("kpi.sold") }, { total.getAvailable(), GREY, label("kpi.available") },
				{ total.getHeld(), AMBER, label("donut.held") } };
		for (final Object[] slice : slices)
		{
			final int value = ((Integer) slice[0]).intValue();
			if (value <= 0 || sum <= 0)
			{
				continue;
			}
			final double length = circumference * value / sum;
			html.append("<circle cx=\"100\" cy=\"100\" r=\"74\" fill=\"none\" stroke=\"").append(slice[1])
					.append("\" stroke-width=\"28\" stroke-dasharray=\"").append(num(length)).append(' ')
					.append(num(circumference - length)).append("\" stroke-dashoffset=\"").append(num(-offset))
					.append("\" transform=\"rotate(-90 100 100)\"><title>").append(esc((String) slice[2])).append(": ")
					.append(integer.format(value)).append(" (").append(percent(value, (int) sum)).append(")</title></circle>");
			offset += length;
		}
		html.append("<text x=\"100\" y=\"100\" text-anchor=\"middle\" font-size=\"28\" font-weight=\"700\" fill=\"#1d2d3e\">")
				.append(esc(percent(total.getSold(), total.getOnSale()))).append("</text>");
		html.append("<text x=\"100\" y=\"122\" text-anchor=\"middle\" font-size=\"12\" fill=\"").append(MUTED).append("\">")
				.append(esc(label("donut.center"))).append("</text></svg>");
		html.append("<div class=\"flr-legend\">");
		legend(html, RED, label("kpi.sold"), total.getSold());
		legend(html, GREY, label("kpi.available"), total.getAvailable());
		if (total.getHeld() > 0)
		{
			legend(html, AMBER, label("donut.held"), total.getHeld());
		}
		html.append("</div></section>");
	}

	protected void legend(final StringBuilder html, final String color, final String name, final int value)
	{
		html.append("<span><span class=\"flr-dot\" style=\"background:").append(color).append("\"></span>").append(esc(name))
				.append(" <b>").append(integer.format(value)).append("</b></span>");
	}

	protected void sectors(final StringBuilder html)
	{
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label("sector.title")))
				.append("</div><div class=\"flr-h2-sub\">").append(esc(label("sector.sub"))).append("</div>");
		html.append("<table class=\"flr-t\"><thead><tr><th>").append(esc(label("sector.col.sector"))).append("</th><th>")
				.append(esc(label("kpi.onSale"))).append("</th><th>").append(esc(label("kpi.sold"))).append("</th><th>")
				.append(esc(label("kpi.available"))).append("</th><th>").append(esc(label("kpi.revenue"))).append("</th><th>")
				.append(esc(label("kpi.average"))).append("</th></tr></thead><tbody>");
		for (final MatchdayReportLine line : report.getSectors())
		{
			sectorRow(html, label("sector." + line.getKey()), line, false);
		}
		sectorRow(html, label("total"), report.getTotal(), true);
		html.append("</tbody></table></section>");
	}

	protected void sectorRow(final StringBuilder html, final String name, final MatchdayReportLine line, final boolean total)
	{
		final double soldPercent = line.getSoldPercent();
		html.append(total ? "<tr class=\"flr-total\">" : "<tr>").append("<td>").append(esc(name)).append("</td><td>")
				.append(integer.format(line.getOnSale())).append("</td><td><span class=\"flr-bar\"><span style=\"width:")
				.append(num(Math.min(100, soldPercent))).append("%\"></span></span><span class=\"flr-pct\">")
				.append(esc(percent(line.getSold(), line.getOnSale()))).append("</span> ").append(integer.format(line.getSold()))
				.append("</td><td>").append(integer.format(line.getAvailable())).append("</td><td>")
				.append(esc(money(line.getRevenue()))).append("</td><td>").append(esc(money(line.getAverageTicket())))
				.append("</td></tr>");
	}

	/** Linha: ingressos vendidos por dia, da abertura das vendas até hoje (ou até o jogo) */
	protected void days(final StringBuilder html)
	{
		final List<MatchdayReportDay> days = report.getDays();
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label("days.title"))).append("</div>");
		if (days.isEmpty())
		{
			html.append("<div class=\"flr-empty\">").append(esc(label("days.empty"))).append("</div></section>");
			return;
		}
		final LocalDate today = report.getGeneratedAt().toInstant().atZone(ZONE).toLocalDate();
		final String until = report.getLastDay().equals(today) ? label("days.today") : shortDay.format(report.getLastDay());
		int sold = 0;
		int max = 0;
		for (final MatchdayReportDay day : days)
		{
			sold += day.getSold();
			max = Math.max(max, day.getSold());
		}
		html.append("<div class=\"flr-h2-sub\">")
				.append(esc(fmt("days.sub", shortDay.format(report.getFirstDay()), until, integer.format(sold)))).append("</div>");

		final int width = 960;
		final int height = 270;
		final int left = 46;
		final int right = 18;
		final int top = 14;
		final int bottom = 34;
		final double plotWidth = width - left - right;
		final double plotHeight = height - top - bottom;
		final int step = niceStep(max);
		final int ceiling = Math.max(step * 4, (int) Math.ceil(max / (double) step) * step);
		final int count = days.size();

		html.append("<svg viewBox=\"0 0 ").append(width).append(' ').append(height)
				.append("\" width=\"100%\" style=\"display:block;max-height:320px\" role=\"img\" aria-label=\"")
				.append(esc(label("days.title"))).append("\">");
		for (int value = 0; value <= ceiling; value += step)
		{
			final double y = top + plotHeight - plotHeight * value / ceiling;
			html.append("<line x1=\"").append(left).append("\" x2=\"").append(width - right).append("\" y1=\"").append(num(y))
					.append("\" y2=\"").append(num(y)).append("\" stroke=\"").append(value == 0 ? "#c9d0d6" : "#eef1f3")
					.append("\"/><text x=\"").append(left - 8).append("\" y=\"").append(num(y + 4))
					.append("\" text-anchor=\"end\" font-size=\"11\" fill=\"").append(MUTED).append("\">")
					.append(integer.format(value)).append("</text>");
		}
		final double[] xs = new double[count];
		final double[] ys = new double[count];
		for (int i = 0; i < count; i++)
		{
			xs[i] = left + (count == 1 ? plotWidth / 2 : plotWidth * i / (count - 1));
			ys[i] = top + plotHeight - plotHeight * days.get(i).getSold() / ceiling;
		}
		final StringBuilder line = new StringBuilder();
		for (int i = 0; i < count; i++)
		{
			line.append(i == 0 ? "M" : "L").append(num(xs[i])).append(',').append(num(ys[i]));
		}
		final double base = top + plotHeight;
		html.append("<path d=\"").append(line).append("L").append(num(xs[count - 1])).append(',').append(num(base)).append("L")
				.append(num(xs[0])).append(',').append(num(base)).append("Z\" fill=\"").append(RED).append("\" fill-opacity=\"0.08\"/>");
		html.append("<path d=\"").append(line).append("\" fill=\"none\" stroke=\"").append(RED)
				.append("\" stroke-width=\"2.5\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>");
		final int labelEvery = Math.max(1, (int) Math.ceil(count / 12.0));
		for (int i = 0; i < count; i++)
		{
			final MatchdayReportDay day = days.get(i);
			final String tip = fmt("days.tooltip", shortDay.format(day.getDay()), integer.format(day.getSold()),
					money(day.getRevenue()));
			html.append("<circle cx=\"").append(num(xs[i])).append("\" cy=\"").append(num(ys[i])).append("\" r=\"")
					.append(count > 45 ? "2.5" : "3.5").append("\" fill=\"#fff\" stroke=\"").append(RED)
					.append("\" stroke-width=\"2\"><title>").append(esc(tip)).append("</title></circle>");
			html.append("<rect x=\"").append(num(xs[i] - plotWidth / Math.max(1, count) / 2)).append("\" y=\"").append(top)
					.append("\" width=\"").append(num(plotWidth / Math.max(1, count))).append("\" height=\"").append(num(plotHeight))
					.append("\" fill=\"transparent\"><title>").append(esc(tip)).append("</title></rect>");
			if (i % labelEvery == 0 || i == count - 1 && (count - 1) % labelEvery >= labelEvery / 2)
			{
				html.append("<text x=\"").append(num(xs[i])).append("\" y=\"").append(height - 12)
						.append("\" text-anchor=\"middle\" font-size=\"11\" fill=\"").append(MUTED).append("\">")
						.append(esc(shortDay.format(day.getDay()))).append("</text>");
			}
		}
		html.append("</svg>");
		if (sold == 0)
		{
			html.append("<div class=\"flr-empty\">").append(esc(label("days.empty"))).append("</div>");
		}
		html.append("</section>");
	}

	protected void ticketTypes(final StringBuilder html)
	{
		final MatchdayReportLine total = report.getTotal();
		html.append("<section class=\"flr-card\"><div class=\"flr-h2\">").append(esc(label("type.title")))
				.append("</div><div class=\"flr-h2-sub\">").append(esc(label("type.sub"))).append("</div>");
		html.append("<table class=\"flr-t\"><thead><tr><th>").append(esc(label("type.col.type"))).append("</th><th>")
				.append(esc(label("type.col.qty"))).append("</th><th>").append(esc(label("type.col.revenue"))).append("</th><th>")
				.append(esc(label("type.col.share"))).append("</th><th>").append(esc(label("kpi.average"))).append("</th></tr></thead><tbody>");
		int sold = 0;
		for (final MatchdayReportLine line : report.getTicketTypes())
		{
			sold += line.getSold();
		}
		for (final MatchdayReportLine line : report.getTicketTypes())
		{
			html.append("<tr><td>").append(esc(label("type." + line.getKey()))).append("</td><td>").append(integer.format(line.getSold()))
					.append("</td><td>").append(esc(money(line.getRevenue()))).append("</td><td>")
					.append(esc(percent(line.getSold(), sold))).append("</td><td>")
					.append(line.getPaid() == 0 ? "&ndash;" : esc(money(line.getAverageTicket()))).append("</td></tr>");
		}
		html.append("<tr class=\"flr-total\"><td>").append(esc(label("total"))).append("</td><td>").append(integer.format(sold))
				.append("</td><td>").append(esc(money(total.getRevenue()))).append("</td><td>")
				.append(esc(percent(sold, sold))).append("</td><td>").append(esc(money(total.getAverageTicket())))
				.append("</td></tr></tbody></table></section>");
	}

	protected void note(final StringBuilder html)
	{
		html.append("<div class=\"flr-note\">");
		if (report.getSimulatedSales() > 0 && report.getStoreSales() == 0)
		{
			html.append(esc(fmt("note.simulated.all", integer.format(report.getSimulatedSales())))).append("<br/>");
		}
		else if (report.getSimulatedSales() > 0)
		{
			html.append(esc(fmt("note.simulated", integer.format(report.getSimulatedSales()),
					integer.format(report.getSimulatedSales() + report.getStoreSales()), integer.format(report.getStoreSales()))))
					.append("<br/>");
		}
		html.append(esc(fmt("note.updated", dateTime.format(report.getGeneratedAt().toInstant().atZone(ZONE)))));
		html.append("</div>");
	}

	// ------------------------------------------------------------ apoio

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

	/** "R$ 1.234,56" (pt) ou "R$1,234.56" (en), como na loja */
	protected String money(final double value)
	{
		return (portuguese ? "R$\u00a0" : "R$") + amount.format(value);
	}

	protected String percent(final int part, final int whole)
	{
		final double value = whole <= 0 ? 0 : part * 100.0 / whole;
		final NumberFormat format = NumberFormat.getNumberInstance(portuguese ? new Locale("pt", "BR") : Locale.US);
		format.setMaximumFractionDigits(value > 0 && value < 10 ? 1 : 0);
		return format.format(value) + "%";
	}

	/** Passo do eixo: 1, 2, 5, 10, 20, 50... para ~4 linhas */
	protected static int niceStep(final int max)
	{
		if (max <= 4)
		{
			return 1;
		}
		final double raw = max / 4.0;
		final double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
		final double normalized = raw / magnitude;
		final double nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
		return (int) Math.max(1, Math.round(nice * magnitude));
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
