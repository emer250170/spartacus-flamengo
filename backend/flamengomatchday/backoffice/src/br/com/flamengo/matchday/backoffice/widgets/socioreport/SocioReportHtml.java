package br.com.flamengo.matchday.backoffice.widgets.socioreport;

import br.com.flamengo.matchday.report.socio.SocioReport;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;


/**
 * Monta o HTML do painel do sócio-torcedor, no modelo do "Dashboard - Base" do
 * clube: cartões, tabelas e gráficos em SVG gerados no servidor (sem biblioteca
 * de gráficos no navegador).
 * <p>
 * Layout: linhas de dois cards (coluna estreita + coluna larga, como no Power BI).
 * Abaixo de 1100px as linhas "somem" (display:contents) e os cards se reorganizam
 * numa grade de duas colunas; abaixo de 700px, uma coluna só. Na impressão (PDF
 * em A4 deitado) vale sempre o layout largo, sem quebrar card entre páginas.
 */
public class SocioReportHtml
{
	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
	private static final String RED = "#c8102e";
	private static final String BLACK = "#1d1d1b";
	private static final String INK = "#1d2d3e";
	private static final String GREY = "#c3cbd3";
	private static final String DARK = "#5b6670";
	private static final String MUTED = "#556b82";
	private static final String RULE = "#e1e5e9";
	private static final String SOFT = "#eef1f3";
	private static final String GREEN = "#1e8e3e";
	/** Cores das linhas por programa (gráfico por horário) */
	private static final String[] SERIES = { RED, BLACK, "#8c96a0", "#e9a23b", "#2f6f9f" };

	/** Largura do viewBox dos gráficos da coluna larga e da estreita (≈ largura real em 1400px) */
	private static final int WIDE = 940;
	private static final int NARROW = 380;

	private static final String CSS = "<style>"
			+ ".fsr{color:" + INK + ";max-width:1400px;font-size:13px;line-height:1.35;"
			+ "-webkit-print-color-adjust:exact;print-color-adjust:exact}"
			+ ".fsr *{box-sizing:border-box}"
			+ ".fsr svg{display:block;overflow:visible}"
			+ ".fsr-row{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,2.4fr);gap:12px;margin-bottom:12px}"
			+ ".fsr-stack{display:flex;flex-direction:column;gap:12px;min-width:0}"
			+ ".fsr-stack>.fsr-card{flex:1 1 auto}"
			+ ".fsr-fams{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px;min-width:0}"
			+ ".fsr-card{background:#fff;border:1px solid " + RULE + ";border-radius:12px;padding:14px 18px;min-width:0}"
			+ ".fsr-head{display:flex;justify-content:space-between;align-items:flex-start;gap:8px 18px;flex-wrap:wrap;margin-bottom:10px}"
			+ ".fsr-h2{font-size:15px;font-weight:700;margin:0}"
			+ ".fsr-sub{font-size:12px;color:" + MUTED + ";margin-top:2px}"
			+ ".fsr-legend{display:flex;gap:4px 14px;flex-wrap:wrap;font-size:12px;color:" + MUTED + ";align-items:center}"
			+ ".fsr-sw{display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:6px;vertical-align:-1px}"
			+ ".fsr-ln{display:inline-block;width:16px;height:3px;border-radius:2px;margin-right:6px;vertical-align:3px}"
			+ ".fsr-delta{font-size:11px;font-weight:700;white-space:nowrap}"
			+ ".fsr-up{color:" + GREEN + "}.fsr-down{color:" + RED + "}.fsr-flat{color:" + MUTED + "}"
			// cartão "Total da base ativa"
			+ ".fsr-base{border-top:4px solid " + RED + "}"
			+ ".fsr-base-grid{display:grid;grid-template-columns:minmax(0,1.12fr) minmax(0,1fr);gap:8px}"
			+ ".fsr-col{display:flex;flex-direction:column;gap:8px;min-width:0}"
			+ ".fsr-tile{border:1px solid " + RULE + ";border-radius:10px;padding:8px 12px;flex:1 1 auto}"
			+ ".fsr-v{font-size:28px;font-weight:700;font-variant-numeric:tabular-nums;white-space:nowrap;line-height:1.1}"
			+ ".fsr-tile.sm .fsr-v{font-size:21px}"
			+ ".fsr-tile .fsr-l{flex-direction:column;align-items:flex-start;gap:2px;margin-top:4px}"
			+ ".fsr-l{display:flex;justify-content:space-between;align-items:baseline;gap:2px 8px;flex-wrap:wrap;margin-top:5px}"
			+ ".fsr-cap{font-size:11px;color:" + MUTED + ";text-transform:uppercase;letter-spacing:.04em;font-weight:600}"
			// cartões dos programas
			+ ".fsr-fam{border-top:4px solid " + BLACK + ";display:flex;flex-direction:column}"
			+ ".fsr-fam-top{display:flex;gap:10px 22px;align-items:flex-end;flex-wrap:wrap}"
			+ ".fsr-fam-sep{border-left:1px solid " + RULE + ";padding-left:22px}"
			+ ".fsr-fam-sep .fsr-v{font-size:21px;color:" + DARK + "}"
			+ ".fsr-bar{display:flex;height:8px;border-radius:4px;overflow:hidden;background:" + SOFT + ";margin:16px 0 10px}"
			+ ".fsr-bar span{display:block;height:100%}"
			+ ".fsr-split{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}"
			+ ".fsr-split b{font-size:17px;font-variant-numeric:tabular-nums;white-space:nowrap}"
			+ ".fsr-split b{display:block;line-height:1.15}"
			+ ".fsr-split i{display:block;font-style:normal;font-size:12px;font-weight:700;color:" + DARK + ";margin-top:1px}"
			+ ".fsr-split .fsr-cap{display:block;margin-top:2px;text-transform:none;letter-spacing:0;font-weight:400}"
			// números do cabeçalho (receita, acumulado do dia)
			+ ".fsr-nums{display:flex;gap:6px 26px;flex-wrap:wrap;text-align:right}"
			+ ".fsr-num b{display:block;font-size:19px;font-variant-numeric:tabular-nums;white-space:nowrap}"
			+ ".fsr-num span{font-size:11px;color:" + MUTED + "}"
			+ ".fsr-num.red b{color:" + RED + "}"
			+ ".fsr-acc{text-align:right}.fsr-acc>.fsr-cap{display:block;margin-bottom:2px}"
			// tabelas
			+ "table.fsr-t{width:100%;border-collapse:collapse;font-size:12.5px;color:" + INK + "}"
			+ ".fsr-t th{text-align:right;font-weight:600;color:" + MUTED + ";font-size:11.5px;padding:6px 8px;"
			+ "border-bottom:1px solid #c9d0d6;vertical-align:bottom}"
			+ ".fsr-t td{text-align:right;padding:6px 8px;border-bottom:1px solid " + SOFT
			+ ";font-variant-numeric:tabular-nums;white-space:nowrap}"
			+ ".fsr-t th:first-child,.fsr-t td:first-child{text-align:left;padding-left:0}"
			+ ".fsr-t td:first-child{white-space:normal}"
			+ ".fsr-t th:last-child,.fsr-t td:last-child{padding-right:0}"
			+ ".fsr-t tr.fsr-total td{font-weight:700;border-top:1px solid #c9d0d6;border-bottom:none}"
			+ ".fsr-t td.fsr-code{font-size:12px;letter-spacing:.02em}"
			+ ".fsr-t .fsr-strong{font-weight:700}"
			+ ".fsr-empty{color:" + MUTED + ";font-size:12px;padding:6px 0}"
			// cancelamentos pedidos na loja
			+ ".fsr-one{margin-bottom:12px}"
			+ ".fsr-cx{border-top:4px solid " + RED + "}"
			+ ".fsr-cx-body{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,2.4fr);gap:18px;align-items:start}"
			+ ".fsr-cx-tiles{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-bottom:14px}"
			+ ".fsr-rs{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(0,1fr) 26px;align-items:center;gap:6px 10px;font-size:12px}"
			+ ".fsr-rs-bar{height:8px;background:" + SOFT + ";border-radius:4px;overflow:hidden}"
			+ ".fsr-rs-bar span{display:block;height:100%;background:" + RED + "}"
			+ ".fsr-rs b{text-align:right;font-variant-numeric:tabular-nums}"
			+ ".fsr-tl th,.fsr-tl td{text-align:left}"
			+ ".fsr-t td.fsr-wrap{white-space:normal;min-width:160px}"
			+ ".fsr-pill{display:inline-block;padding:1px 8px;border-radius:999px;font-size:11px;font-weight:700;white-space:nowrap}"
			+ ".fsr-pill.open{background:#fff2d9;color:#9a5b00}.fsr-pill.closed{background:" + SOFT + ";color:" + MUTED + "}"
			+ ".fsr-who{display:block;font-size:11px;color:" + MUTED + "}"
			+ ".fsr-nw{white-space:nowrap}"
			// rodapé
			+ ".fsr-foot{display:flex;justify-content:space-between;align-items:baseline;gap:6px 24px;flex-wrap:wrap;"
			+ "font-size:12px;color:" + MUTED + ";margin:2px 2px 24px;padding-top:10px;border-top:3px solid " + RED + "}"
			+ ".fsr-foot b{color:" + INK + "}"
			// telas menores: os cards viram itens de uma grade de duas colunas
			+ "@media screen and (max-width:1100px){"
			+ ".fsr-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:12px;margin-bottom:12px;"
			+ "grid-template-areas:\"base base\" \"fam fam\" \"hourly hourly\" \"revenue revenue\" \"wave wave\" \"plan plan\""
			+ " \"ops churn\" \"coupons states\" \"joins joins\"}"
			+ ".fsr-row,.fsr-stack{display:contents}"
			+ ".fsr-a-base{grid-area:base}.fsr-a-fam{grid-area:fam}.fsr-a-ops{grid-area:ops}.fsr-a-hourly{grid-area:hourly}"
			+ ".fsr-a-coupons{grid-area:coupons}.fsr-a-revenue{grid-area:revenue}.fsr-a-wave{grid-area:wave}"
			+ ".fsr-a-states{grid-area:states}.fsr-a-plan{grid-area:plan}.fsr-a-churn{grid-area:churn}.fsr-a-joins{grid-area:joins}"
			+ ".fsr-base-grid{grid-template-columns:repeat(auto-fit,minmax(200px,1fr))}"
			+ ".fsr-cx-body{grid-template-columns:minmax(0,1fr)}"
			+ "}"
			+ "@media screen and (max-width:700px){"
			+ ".fsr-grid{grid-template-columns:minmax(0,1fr);grid-template-areas:none}"
			+ ".fsr-grid .fsr-card,.fsr-grid .fsr-fams{grid-area:auto}"
			+ ".fsr-nums,.fsr-acc{text-align:left}"
			+ "}"
			// impressão (A4 deitado): layout largo, cards inteiros na página
			+ "@media print{"
			+ ".fsr{max-width:none;font-size:12px}"
			+ ".fsr-row{gap:8px;margin-bottom:8px}"
			+ ".fsr-stack,.fsr-fams{gap:8px}"
			+ ".fsr-row,.fsr-stack,.fsr-fams,.fsr-card,.fsr-foot{break-inside:avoid;page-break-inside:avoid}"
			+ ".fsr-card{border-color:#d5dadd;padding:10px 14px}"
			+ ".fsr-v{font-size:22px}.fsr-tile.sm .fsr-v,.fsr-fam-sep .fsr-v{font-size:17px}"
			+ ".fsr-tile{padding:7px 10px}"
			+ ".fsr-num b{font-size:16px}"
			+ ".fsr-t td,.fsr-t th{padding-top:4px;padding-bottom:4px}"
			+ "}"
			+ "</style>";

	private final SocioReport report;
	private final Function<String, String> labels;
	private final boolean portuguese;
	private final Locale locale;
	/** Valor com 2 casas; o "R$" vem em money() (a JVM do servidor mostra "BRL" em inglês) */
	private final NumberFormat amount;
	private final NumberFormat integer;
	private final DateTimeFormatter shortDay;
	private final DateTimeFormatter dateTime;
	private final ZonedDateTime now;

	public SocioReportHtml(final SocioReport report, final Function<String, String> labels, final Locale uiLocale)
	{
		this.report = report;
		this.labels = labels;
		this.portuguese = uiLocale != null && "pt".equals(uiLocale.getLanguage());
		this.locale = portuguese ? new Locale("pt", "BR") : Locale.US;
		this.amount = NumberFormat.getNumberInstance(locale);
		this.amount.setMinimumFractionDigits(2);
		this.amount.setMaximumFractionDigits(2);
		this.integer = NumberFormat.getIntegerInstance(locale);
		this.shortDay = DateTimeFormatter.ofPattern(portuguese ? "dd/MM" : "MMM d", locale);
		this.dateTime = DateTimeFormatter.ofPattern(portuguese ? "dd/MM/yyyy HH:mm" : "MMM d, yyyy h:mm a", locale);
		final Date generated = report.generatedAt == null ? new Date() : report.generatedAt;
		this.now = generated.toInstant().atZone(ZONE);
	}

	public String render()
	{
		final StringBuilder html = new StringBuilder(64_000);
		html.append("<div class=\"fsr\">").append(CSS);
		if (report.families.isEmpty() && report.byPlan.isEmpty())
		{
			html.append("<div class=\"fsr-empty\">").append(esc(label("empty"))).append("</div></div>");
			return html.toString();
		}
		html.append("<div class=\"fsr-grid\">");

		html.append("<div class=\"fsr-row\">");
		baseCard(html);
		html.append("<div class=\"fsr-fams fsr-a-fam\">");
		for (final SocioReport.Family family : report.families)
		{
			familyCard(html, family);
		}
		html.append("</div></div>");

		html.append("<div class=\"fsr-row\">");
		operations(html);
		hourly(html);
		html.append("</div>");

		html.append("<div class=\"fsr-row\">");
		coupons(html);
		html.append("<div class=\"fsr-stack\">");
		revenue(html);
		byWave(html);
		html.append("</div></div>");

		html.append("<div class=\"fsr-row\">");
		states(html);
		byPlan(html);
		html.append("</div>");

		html.append("<div class=\"fsr-row\">");
		churn(html);
		joins(html);
		html.append("</div>");

		html.append("</div>");
		cancellations(html);
		footer(html);
		html.append("</div>");
		return html.toString();
	}

	// ------------------------------------------------------------ cartões

	/** Total da base ativa: ativos e contratos (grandes) + contratos de cada programa */
	protected void baseCard(final StringBuilder html)
	{
		html.append("<section class=\"fsr-card fsr-base fsr-a-base\"><div class=\"fsr-head\"><div class=\"fsr-h2\">")
				.append(esc(label("base.title"))).append("</div></div><div class=\"fsr-base-grid\"><div class=\"fsr-col\">");
		tile(html, "", integer.format(report.activeTotal), label("base.active"), report.activeTotalDelta);
		tile(html, "", integer.format(report.contracts), label("base.contracts"), report.contractsDelta);
		html.append("</div><div class=\"fsr-col\">");
		for (final SocioReport.Family family : report.families)
		{
			tile(html, "sm", integer.format(family.contracts), family.name, family.delta);
		}
		html.append("</div></div></section>");
	}

	protected void tile(final StringBuilder html, final String size, final String value, final String caption, final double delta)
	{
		html.append("<div class=\"fsr-tile ").append(size).append("\"><div class=\"fsr-v\">").append(esc(value))
				.append("</div><div class=\"fsr-l\"><span class=\"fsr-cap\">").append(esc(caption)).append("</span>");
		delta(html, delta);
		html.append("</div></div>");
	}

	/** "↓ -0,06%" colorido (queda em vermelho, alta em verde) */
	protected void delta(final StringBuilder html, final double fraction)
	{
		final double rounded = Math.round(fraction * 10_000) / 10_000.0;
		final String css = rounded > 0 ? "fsr-up" : rounded < 0 ? "fsr-down" : "fsr-flat";
		final String arrow = rounded > 0 ? "↑ " : rounded < 0 ? "↓ " : "";
		final String text = signed(fraction * 100, 2) + "%";
		html.append("<span class=\"fsr-delta ").append(css).append("\" title=\"").append(esc(fmt("delta.tooltip", text)))
				.append("\">").append(arrow).append(esc(text)).append("</span>");
	}

	/** Um programa: contratos, inativos e a divisão adimplentes / inadimplentes / não renovado */
	protected void familyCard(final StringBuilder html, final SocioReport.Family family)
	{
		html.append("<section class=\"fsr-card fsr-fam\"><div class=\"fsr-head\"><div class=\"fsr-h2\">").append(esc(family.name))
				.append("</div></div><div class=\"fsr-fam-top\"><div><div class=\"fsr-v\">").append(integer.format(family.contracts))
				.append("</div><div class=\"fsr-l\"><span class=\"fsr-cap\">").append(esc(label("family.contracts"))).append("</span>");
		delta(html, family.delta);
		html.append("</div></div><div class=\"fsr-fam-sep\"><div class=\"fsr-v\">").append(integer.format(family.inactive))
				.append("</div><div class=\"fsr-l\"><span class=\"fsr-cap\">").append(esc(label("family.inactive")))
				.append("</span></div></div></div>");

		final int[] values = { family.upToDate, family.overdue, family.notRenewed };
		final String[] names = { label("family.upToDate"), label("family.overdue"), label("family.notRenewed") };
		final String[] colors = { BLACK, RED, GREY };
		final int whole = family.contracts > 0 ? family.contracts : values[0] + values[1] + values[2];
		html.append("<div class=\"fsr-bar\">");
		for (int i = 0; i < values.length; i++)
		{
			if (values[i] > 0 && whole > 0)
			{
				html.append("<span style=\"width:").append(num(Math.min(100, values[i] * 100.0 / whole))).append("%;background:")
						.append(colors[i]).append("\" title=\"").append(esc(names[i] + ": " + integer.format(values[i])))
						.append("\"></span>");
			}
		}
		html.append("</div><div class=\"fsr-split\">");
		for (int i = 0; i < values.length; i++)
		{
			html.append("<div><b>").append(integer.format(values[i])).append("</b><i>")
					.append(esc(percent(whole <= 0 ? 0 : values[i] / (double) whole, 1))).append("</i><span class=\"fsr-cap\">")
					.append("<span class=\"fsr-sw\" style=\"background:").append(colors[i]).append(";width:8px;height:8px;margin-right:5px\"></span>")
					.append(esc(names[i])).append("</span></div>");
		}
		html.append("</div></section>");
	}

	// ------------------------------------------------------------ novos contratos

	/** Cascata de hoje: renovação + adesão + upgrade = total */
	protected void operations(final StringBuilder html)
	{
		html.append("<section class=\"fsr-card fsr-a-ops\"><div class=\"fsr-head\"><div><div class=\"fsr-h2\">")
				.append(esc(label("ops.title"))).append("</div><div class=\"fsr-sub\">").append(esc(label("ops.sub")))
				.append("</div></div></div>");
		final int total = report.renewals + report.joins + report.upgrades;
		final String[] names = { label("ops.renewals"), label("ops.joins"), label("ops.upgrades"), label("ops.total") };
		final int[] values = { report.renewals, report.joins, report.upgrades, total };
		final int width = NARROW;
		final int height = 230;
		final int top = 24;
		final int bottom = 30;
		final double plotHeight = height - top - bottom;
		final double slot = (width - 8) / 4.0;
		final double bar = Math.min(62, slot * 0.68);
		final double ceiling = Math.max(1, total);
		svgOpen(html, width, height, label("ops.title"));
		final double base = top + plotHeight;
		line(html, 4, width - 4, base, base, "#c9d0d6", false);
		double running = 0;
		double previousTop = base;
		for (int i = 0; i < 4; i++)
		{
			final boolean isTotal = i == 3;
			final double from = isTotal ? 0 : running;
			final double to = isTotal ? total : running + values[i];
			final double yTop = top + plotHeight - plotHeight * to / ceiling;
			final double yBottom = top + plotHeight - plotHeight * from / ceiling;
			final double x = 4 + slot * i + (slot - bar) / 2;
			if (i > 0)
			{
				// ligação tracejada do topo do degrau anterior
				html.append("<line x1=\"").append(num(x - (slot - bar))).append("\" x2=\"").append(num(x)).append("\" y1=\"")
						.append(num(previousTop)).append("\" y2=\"").append(num(previousTop))
						.append("\" stroke=\"#9aa5b1\" stroke-dasharray=\"3 3\"/>");
			}
			final double h = Math.max(values[i] > 0 ? 1.5 : 0, yBottom - yTop);
			html.append("<rect x=\"").append(num(x)).append("\" y=\"").append(num(yBottom - h)).append("\" width=\"").append(num(bar))
					.append("\" height=\"").append(num(h)).append("\" rx=\"2\" fill=\"").append(isTotal ? RED : DARK).append("\"><title>")
					.append(esc(names[i] + ": " + integer.format(values[i]))).append("</title></rect>");
			text(html, x + bar / 2, yBottom - h - 7, "middle", 12, INK, true, integer.format(values[i]));
			text(html, x + bar / 2, height - 10, "middle", 12, MUTED, false, names[i]);
			previousTop = isTotal ? yTop : yTop;
			running = to;
		}
		html.append("</svg></section>");
	}

	/** Linhas por hora de hoje, uma por programa, até a hora atual */
	protected void hourly(final StringBuilder html)
	{
		final List<SocioReport.Hourly> lines = report.hourly;
		final int lastHour = Math.max(0, Math.min(23, report.currentHour));
		html.append("<section class=\"fsr-card fsr-a-hourly\"><div class=\"fsr-head\"><div><div class=\"fsr-h2\">")
				.append(esc(label("hourly.title"))).append("</div><div class=\"fsr-sub\">")
				.append(esc(fmt("hourly.sub", hour(lastHour)))).append("</div>");
		html.append("<div class=\"fsr-legend\" style=\"margin-top:6px\">");
		for (int i = 0; i < lines.size(); i++)
		{
			html.append("<span><span class=\"fsr-ln\" style=\"background:").append(SERIES[i % SERIES.length]).append("\"></span>")
					.append(esc(lines.get(i).family)).append("</span>");
		}
		html.append("</div></div><div class=\"fsr-acc\"><span class=\"fsr-cap\">").append(esc(label("hourly.accum")))
				.append("</span><div class=\"fsr-nums\">");
		for (final SocioReport.Hourly line : lines)
		{
			html.append("<div class=\"fsr-num\"><span>").append(esc(line.family)).append("</span><b>").append(integer.format(line.total))
					.append("</b></div>");
		}
		html.append("</div></div></div>");
		if (lines.isEmpty())
		{
			html.append("<div class=\"fsr-empty\">").append(esc(label("noData"))).append("</div></section>");
			return;
		}

		int max = 0;
		for (final SocioReport.Hourly line : lines)
		{
			for (int hour = 0; hour <= lastHour && hour < line.perHour.length; hour++)
			{
				max = Math.max(max, line.perHour[hour]);
			}
		}
		final int width = WIDE;
		final int height = 210;
		final int left = 40;
		final int right = 12;
		final int top = 10;
		final int bottom = 28;
		final double plotWidth = width - left - right;
		final double plotHeight = height - top - bottom;
		final double step = niceStep(max);
		final double ceiling = ceiling(max, step);
		svgOpen(html, width, height, label("hourly.title"));
		yAxis(html, left, width - right, top, plotHeight, step, ceiling, false);
		for (int hour = 0; hour < 24; hour++)
		{
			final double x = left + plotWidth * hour / 23;
			if (hour % 3 == 0 || hour == 23)
			{
				text(html, x, height - 8, hour == 0 ? "start" : hour == 23 ? "end" : "middle", 11, MUTED, false, hour(hour));
			}
		}
		// faixa das horas que ainda não chegaram
		if (lastHour < 23)
		{
			final double x = left + plotWidth * lastHour / 23;
			html.append("<rect x=\"").append(num(x)).append("\" y=\"").append(top).append("\" width=\"")
					.append(num(left + plotWidth - x)).append("\" height=\"").append(num(plotHeight)).append("\" fill=\"")
					.append("#f7f8f9\"/>");
		}
		for (int i = lines.size() - 1; i >= 0; i--)
		{
			final SocioReport.Hourly line = lines.get(i);
			final String color = SERIES[i % SERIES.length];
			final StringBuilder path = new StringBuilder();
			final StringBuilder dots = new StringBuilder();
			for (int hour = 0; hour <= lastHour; hour++)
			{
				final int value = hour < line.perHour.length ? line.perHour[hour] : 0;
				final double x = left + plotWidth * hour / 23;
				final double y = top + plotHeight - plotHeight * value / ceiling;
				path.append(hour == 0 ? "M" : "L").append(num(x)).append(',').append(num(y));
				dots.append("<circle cx=\"").append(num(x)).append("\" cy=\"").append(num(y)).append("\" r=\"")
						.append(hour == lastHour ? "3.5" : "2.5").append("\" fill=\"#fff\" stroke=\"").append(color)
						.append("\" stroke-width=\"1.8\"><title>")
						.append(esc(fmt("hourly.tooltip", line.family, hour(hour), integer.format(value)))).append("</title></circle>");
			}
			html.append("<path d=\"").append(path).append("\" fill=\"none\" stroke=\"").append(color)
					.append("\" stroke-width=\"2.2\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>").append(dots);
		}
		html.append("</svg></section>");
	}

	// ------------------------------------------------------------ receita

	/** Receita paga por dia do mês, marcador de hoje e os três números do mês */
	protected void revenue(final StringBuilder html)
	{
		html.append("<section class=\"fsr-card fsr-a-revenue\"><div class=\"fsr-head\"><div><div class=\"fsr-h2\">")
				.append(esc(label("revenue.title"))).append("</div><div class=\"fsr-legend\" style=\"margin-top:6px\">")
				.append("<span><span class=\"fsr-sw\" style=\"background:").append(RED).append("\"></span>")
				.append(esc(label("revenue.paid"))).append("</span><span><span class=\"fsr-sw\" style=\"background:#e3e7eb\"></span>")
				.append(esc(label("revenue.future"))).append("</span></div></div><div class=\"fsr-nums\">");
		number(html, "red", money(report.revenuePaid), label("revenue.paid"));
		number(html, "", money(report.revenueFuture), label("revenue.future"));
		number(html, "", money(report.revenueForecast), label("revenue.forecast"));
		html.append("</div></div>");

		final List<Double> days = report.revenueByDay;
		final int count = Math.max(Math.max(1, report.daysInMonth), days.size());
		final int today = Math.max(1, Math.min(count, report.dayOfMonth));
		final LocalDate first = now.toLocalDate().withDayOfMonth(1);
		double max = 0;
		for (final Double value : days)
		{
			if (value != null)
			{
				max = Math.max(max, value);
			}
		}
		final int width = WIDE;
		final int height = 200;
		final int left = 58;
		final int right = 14;
		final int top = 18;
		final int bottom = 26;
		final double plotWidth = width - left - right;
		final double plotHeight = height - top - bottom;
		final double step = niceStep(max);
		final double ceiling = ceiling(max, step);
		svgOpen(html, width, height, label("revenue.title"));

		// resto do mês (recorrências futuras)
		final double todayX = x(left, plotWidth, today, count);
		if (today < count)
		{
			html.append("<rect x=\"").append(num(todayX)).append("\" y=\"").append(top).append("\" width=\"")
					.append(num(left + plotWidth - todayX)).append("\" height=\"").append(num(plotHeight))
					.append("\" fill=\"#f1f3f5\"><title>").append(esc(label("revenue.future") + ": " + money(report.revenueFuture)))
					.append("</title></rect>");
		}
		yAxis(html, left, width - right, top, plotHeight, step, ceiling, true);

		final StringBuilder path = new StringBuilder();
		int firstDay = -1;
		int lastDay = -1;
		for (int day = 1; day <= count; day++)
		{
			final Double value = day <= days.size() ? days.get(day - 1) : null;
			if (value == null)
			{
				continue;
			}
			final double x = x(left, plotWidth, day, count);
			final double y = top + plotHeight - plotHeight * value / ceiling;
			path.append(firstDay < 0 ? "M" : "L").append(num(x)).append(',').append(num(y));
			if (firstDay < 0)
			{
				firstDay = day;
			}
			lastDay = day;
		}
		final double base = top + plotHeight;
		if (firstDay > 0)
		{
			html.append("<path d=\"").append(path).append("L").append(num(x(left, plotWidth, lastDay, count))).append(',')
					.append(num(base)).append("L").append(num(x(left, plotWidth, firstDay, count))).append(',').append(num(base))
					.append("Z\" fill=\"").append(RED).append("\" fill-opacity=\"0.16\"/>");
			html.append("<path d=\"").append(path).append("\" fill=\"none\" stroke=\"").append(RED)
					.append("\" stroke-width=\"2\" stroke-linejoin=\"round\"/>");
		}
		// marcador de hoje
		html.append("<line x1=\"").append(num(todayX)).append("\" x2=\"").append(num(todayX)).append("\" y1=\"").append(top - 4)
				.append("\" y2=\"").append(num(base)).append("\" stroke=\"").append(BLACK)
				.append("\" stroke-width=\"1.5\" stroke-dasharray=\"4 3\"/>");
		final String todayText = shortDay.format(first.plusDays(today - 1L));
		text(html, todayX + (today > count * 0.9 ? -6 : 6), top + 6, today > count * 0.9 ? "end" : "start", 11, INK, true,
				fmt("revenue.today", todayText));
		// dias (rótulos e dicas)
		final double column = plotWidth / count;
		for (int day = 1; day <= count; day++)
		{
			final double x = x(left, plotWidth, day, count);
			final Double value = day <= days.size() ? days.get(day - 1) : null;
			final String dayText = shortDay.format(first.plusDays(day - 1L));
			if (value != null)
			{
				html.append("<rect x=\"").append(num(x - column / 2)).append("\" y=\"").append(top).append("\" width=\"")
						.append(num(column)).append("\" height=\"").append(num(plotHeight)).append("\" fill=\"transparent\"><title>")
						.append(esc(dayText + ": " + money(value))).append("</title></rect>");
			}
			if (day == 1 || day % 5 == 0 && count - day >= 2 || day == count)
			{
				text(html, x, height - 7, day == 1 ? "start" : day == count ? "end" : "middle", 11, MUTED, false, dayText);
			}
		}
		html.append("</svg></section>");
	}

	protected void number(final StringBuilder html, final String css, final String value, final String caption)
	{
		html.append("<div class=\"fsr-num ").append(css).append("\"><b>").append(esc(value)).append("</b><span>").append(esc(caption))
				.append("</span></div>");
	}

	protected static double x(final int left, final double plotWidth, final int day, final int count)
	{
		return left + (count <= 1 ? plotWidth / 2 : plotWidth * (day - 1) / (count - 1));
	}

	// ------------------------------------------------------------ base ativa

	protected void byWave(final StringBuilder html)
	{
		final List<String> groups = new ArrayList<>();
		for (final SocioReport.Base base : report.byWave)
		{
			final String wave = base.label == null ? "" : base.label.trim();
			groups.add("0".equals(wave) ? label("wave.none") : wave.matches("\\d+") ? fmt("wave.n", wave) : wave);
		}
		baseBars(html, "fsr-a-wave", label("wave.title"), report.byWave, groups, true);
	}

	protected void byPlan(final StringBuilder html)
	{
		final List<String> groups = new ArrayList<>();
		for (final SocioReport.Base base : report.byPlan)
		{
			groups.add(base.label);
		}
		baseBars(html, "fsr-a-plan", label("plan.title"), report.byPlan, groups, false);
	}

	/** Barras agrupadas: titulares (vermelho), convidados (preto, opcional) e ativos total (cinza) */
	protected void baseBars(final StringBuilder html, final String area, final String title, final List<SocioReport.Base> rows,
			final List<String> groups, final boolean withGuests)
	{
		final String[] names = withGuests ? new String[] { label("series.holders"), label("series.guests"), label("series.total") }
				: new String[] { label("series.holders"), label("series.total") };
		final String[] colors = withGuests ? new String[] { RED, BLACK, GREY } : new String[] { RED, GREY };
		html.append("<section class=\"fsr-card ").append(area).append("\"><div class=\"fsr-head\"><div class=\"fsr-h2\">")
				.append(esc(title)).append("</div><div class=\"fsr-legend\">");
		for (int s = 0; s < names.length; s++)
		{
			html.append("<span><span class=\"fsr-sw\" style=\"background:").append(colors[s]).append("\"></span>").append(esc(names[s]))
					.append("</span>");
		}
		html.append("</div></div>");
		if (rows.isEmpty())
		{
			html.append("<div class=\"fsr-empty\">").append(esc(label("noData"))).append("</div></section>");
			return;
		}
		final int[][] values = new int[names.length][rows.size()];
		int max = 0;
		for (int g = 0; g < rows.size(); g++)
		{
			final SocioReport.Base base = rows.get(g);
			values[0][g] = base.holders;
			if (withGuests)
			{
				values[1][g] = base.guests;
			}
			values[names.length - 1][g] = base.getTotal();
			max = Math.max(max, base.getTotal());
		}

		final int width = WIDE;
		final int height = 232;
		final int top = 22;
		final int bottom = 40;
		final double plotHeight = height - top - bottom;
		final double slot = (width - 8) / (double) rows.size();
		final int series = names.length;
		final double bar = Math.min(64, slot * 0.8 / series);
		final double gap = Math.min(4, bar * 0.08);
		final double ceiling = Math.max(1, max);
		svgOpen(html, width, height, title);
		final double base = top + plotHeight;
		for (int g = 0; g < rows.size(); g++)
		{
			final double groupWidth = bar * series + gap * (series - 1);
			final double start = 4 + slot * g + (slot - groupWidth) / 2;
			for (int s = 0; s < series; s++)
			{
				final int value = values[s][g];
				final double h = Math.max(value > 0 ? 1.5 : 0, plotHeight * value / ceiling);
				final double x = start + s * (bar + gap);
				html.append("<rect x=\"").append(num(x)).append("\" y=\"").append(num(base - h)).append("\" width=\"").append(num(bar))
						.append("\" height=\"").append(num(h)).append("\" rx=\"2\" fill=\"").append(colors[s]).append("\"><title>")
						.append(esc(groups.get(g) + " · " + names[s] + ": " + integer.format(value))).append("</title></rect>");
				text(html, x + bar / 2, base - h - 6, "middle", bar < 40 ? 10.5 : 11.5, INK, s == series - 1, integer.format(value));
			}
			final List<String> lines = wrap(groups.get(g), (int) Math.max(8, slot / 6.6));
			for (int l = 0; l < lines.size(); l++)
			{
				text(html, 4 + slot * g + slot / 2, base + 17 + l * 14, "middle", 12, MUTED, false, lines.get(l));
			}
		}
		line(html, 4, width - 4, base, base, "#c9d0d6", false);
		html.append("</svg></section>");
	}

	// ------------------------------------------------------------ tabelas

	protected void coupons(final StringBuilder html)
	{
		html.append("<section class=\"fsr-card fsr-a-coupons\"><div class=\"fsr-head\"><div class=\"fsr-h2\">")
				.append(esc(label("coupons.title"))).append("</div></div>");
		if (report.coupons.isEmpty())
		{
			html.append("<div class=\"fsr-empty\">").append(esc(label("noData"))).append("</div></section>");
			return;
		}
		html.append("<table class=\"fsr-t\"><thead><tr><th>").append(esc(label("coupons.code"))).append("</th><th>")
				.append(esc(label("coupons.month"))).append("</th><th>").append(esc(label("coupons.today"))).append("</th><th>")
				.append(esc(label("coupons.total"))).append("</th></tr></thead><tbody>");
		int month = 0;
		int today = 0;
		int total = 0;
		for (final SocioReport.Coupon coupon : report.coupons)
		{
			month += coupon.month;
			today += coupon.today;
			total += coupon.total;
			html.append("<tr><td class=\"fsr-code\">").append(esc(coupon.code)).append("</td><td>").append(integer.format(coupon.month))
					.append("</td><td>").append(integer.format(coupon.today)).append("</td><td>").append(integer.format(coupon.total))
					.append("</td></tr>");
		}
		html.append("<tr class=\"fsr-total\"><td>").append(esc(label("total"))).append("</td><td>").append(integer.format(month))
				.append("</td><td>").append(integer.format(today)).append("</td><td>").append(integer.format(total))
				.append("</td></tr></tbody></table></section>");
	}

	protected void churn(final StringBuilder html)
	{
		html.append("<section class=\"fsr-card fsr-a-churn\"><div class=\"fsr-head\"><div class=\"fsr-h2\">")
				.append(esc(label("churn.title"))).append("</div></div>");
		if (report.churn.isEmpty())
		{
			html.append("<div class=\"fsr-empty\">").append(esc(label("noData"))).append("</div></section>");
			return;
		}
		html.append("<table class=\"fsr-t\"><thead><tr><th>").append(esc(label("churn.plan"))).append("</th><th>")
				.append(esc(label("churn.rate"))).append("</th><th>").append(esc(label("churn.vsAverage")))
				.append("</th></tr></thead><tbody>");
		for (final SocioReport.Churn row : report.churn)
		{
			final double diff = Math.round(row.vsAverage * 10) / 10.0;
			// churn acima da média é ruim (vermelho); abaixo é bom (verde)
			final String css = diff > 0 ? "fsr-down" : diff < 0 ? "fsr-up" : "fsr-flat";
			final String arrow = diff > 0 ? "↑ " : diff < 0 ? "↓ " : "";
			html.append("<tr><td>").append(esc(row.plan)).append("</td><td>").append(esc(percent(row.rate, 1)))
					.append("</td><td class=\"fsr-strong ").append(css).append("\">").append(arrow)
					.append(esc(fmt("churn.pp", signed(row.vsAverage, 1)))).append("</td></tr>");
		}
		html.append("</tbody></table></section>");
	}

	protected void joins(final StringBuilder html)
	{
		html.append("<section class=\"fsr-card fsr-a-joins\"><div class=\"fsr-head\"><div class=\"fsr-h2\">")
				.append(esc(label("joins.title"))).append("</div></div>");
		if (report.joinsByPlan.isEmpty())
		{
			html.append("<div class=\"fsr-empty\">").append(esc(label("noData"))).append("</div></section>");
			return;
		}
		html.append("<table class=\"fsr-t\"><thead><tr><th>").append(esc(label("joins.plan"))).append("</th><th>")
				.append(esc(label("joins.abandoned"))).append("</th><th>").append(esc(label("joins.today"))).append("</th><th>")
				.append(esc(label("joins.month"))).append("</th><th>").append(esc(label("joins.lastMonth"))).append("</th><th>")
				.append(esc(label("joins.vsLastMonth"))).append("</th></tr></thead><tbody>");
		final SocioReport.Join total = new SocioReport.Join();
		for (final SocioReport.Join row : report.joinsByPlan)
		{
			total.abandoned += row.abandoned;
			total.today += row.today;
			total.month += row.month;
			total.lastMonth += row.lastMonth;
			joinRow(html, "<tr>", row.plan, row);
		}
		joinRow(html, "<tr class=\"fsr-total\">", label("total"), total);
		html.append("</tbody></table></section>");
	}

	protected void joinRow(final StringBuilder html, final String tr, final String name, final SocioReport.Join row)
	{
		final double vs = row.getVsLastMonth();
		final double rounded = Math.round(vs * 10_000) / 10_000.0;
		final String css = row.lastMonth == 0 ? "fsr-flat" : rounded > 0 ? "fsr-up" : rounded < 0 ? "fsr-down" : "fsr-flat";
		html.append(tr).append("<td>").append(esc(name)).append("</td><td>").append(integer.format(row.abandoned)).append("</td><td>")
				.append(integer.format(row.today)).append("</td><td>").append(integer.format(row.month)).append("</td><td>")
				.append(integer.format(row.lastMonth)).append("</td><td class=\"fsr-strong ").append(css).append("\">")
				.append(row.lastMonth == 0 ? "&ndash;" : esc(signed(vs * 100, 2) + "%")).append("</td></tr>");
	}

	// ------------------------------------------------------------ estados

	/** Barras horizontais: top 10 estados das adesões Nação Sem Fronteiras */
	protected void states(final StringBuilder html)
	{
		html.append("<section class=\"fsr-card fsr-a-states\"><div class=\"fsr-head\"><div><div class=\"fsr-h2\">")
				.append(esc(label("states.title"))).append("</div><div class=\"fsr-sub\">").append(esc(label("states.sub")))
				.append("</div></div></div>");
		final List<SocioReport.State> rows = new ArrayList<>(report.states);
		rows.sort(Comparator.comparingInt((SocioReport.State state) -> state.count).reversed());
		if (rows.size() > 10)
		{
			rows.subList(10, rows.size()).clear();
		}
		if (rows.isEmpty())
		{
			html.append("<div class=\"fsr-empty\">").append(esc(label("noData"))).append("</div></section>");
			return;
		}
		int max = 1;
		for (final SocioReport.State state : rows)
		{
			max = Math.max(max, state.count);
		}
		final int width = NARROW;
		final int rowHeight = 23;
		final int height = rows.size() * rowHeight + 4;
		final int labelWidth = 34;
		final int valueWidth = 46;
		final double barMax = width - labelWidth - valueWidth;
		svgOpen(html, width, height, label("states.title"));
		for (int i = 0; i < rows.size(); i++)
		{
			final SocioReport.State state = rows.get(i);
			final double y = 2 + i * rowHeight;
			final double w = Math.max(state.count > 0 ? 2 : 0, barMax * state.count / max);
			text(html, labelWidth - 8, y + rowHeight / 2.0 + 4, "end", 12, INK, true, state.uf);
			html.append("<rect x=\"").append(labelWidth).append("\" y=\"").append(num(y + 4)).append("\" width=\"").append(num(w))
					.append("\" height=\"").append(rowHeight - 8).append("\" rx=\"2\" fill=\"").append(i == 0 ? RED : "#d9475c")
					.append("\"><title>").append(esc(fmt("states.tooltip", state.uf, integer.format(state.count))))
					.append("</title></rect>");
			text(html, labelWidth + w + 6, y + rowHeight / 2.0 + 4, "start", 12, INK, false, integer.format(state.count));
		}
		line(html, labelWidth, labelWidth, 2, height - 2, "#c9d0d6", false);
		html.append("</svg></section>");
	}

	// ------------------------------------------------------------ cancelamentos (reais)

	/** Pedidos de cancelamento feitos na loja: em aberto, no mês, por motivo e os últimos pedidos */
	protected void cancellations(final StringBuilder html)
	{
		html.append("<div class=\"fsr-one\"><section class=\"fsr-card fsr-cx\"><div class=\"fsr-head\"><div><div class=\"fsr-h2\">")
				.append(esc(label("cancel.title"))).append("</div><div class=\"fsr-sub\">").append(esc(label("cancel.sub")))
				.append("</div></div></div>");
		if (report.cancelTotal == 0)
		{
			html.append("<div class=\"fsr-empty\">").append(esc(label("cancel.none"))).append("</div></section></div>");
			return;
		}
		html.append("<div class=\"fsr-cx-body\"><div><div class=\"fsr-cx-tiles\">");
		cancelTile(html, report.cancelOpen, label("cancel.open"));
		cancelTile(html, report.cancelMonth, label("cancel.month"));
		cancelTile(html, report.cancelTotal, label("cancel.total"));
		html.append("</div><div class=\"fsr-cap\" style=\"margin-bottom:8px\">").append(esc(label("cancel.byReason")))
				.append("</div><div class=\"fsr-rs\">");
		int max = 1;
		for (final Integer count : report.cancelReasons.values())
		{
			max = Math.max(max, count.intValue());
		}
		for (final java.util.Map.Entry<String, Integer> entry : report.cancelReasons.entrySet())
		{
			final int count = entry.getValue().intValue();
			html.append("<span>").append(esc(reasonLabel(entry.getKey(), entry.getKey()))).append("</span><span class=\"fsr-rs-bar\"><span style=\"width:")
					.append(num(count * 100.0 / max)).append("%\"></span></span><b>").append(integer.format(count)).append("</b>");
		}
		html.append("</div></div><div><div class=\"fsr-cap\" style=\"margin-bottom:4px\">").append(esc(label("cancel.latest")))
				.append("</div><table class=\"fsr-t fsr-tl\"><thead><tr><th>").append(esc(label("cancel.date"))).append("</th><th>")
				.append(esc(label("cancel.member"))).append("</th><th>").append(esc(label("cancel.plan"))).append("</th><th>")
				.append(esc(label("cancel.reason"))).append("</th><th>").append(esc(label("cancel.comment"))).append("</th><th>")
				.append(esc(label("cancel.status"))).append("</th></tr></thead><tbody>");
		final DateTimeFormatter day = DateTimeFormatter.ofPattern(portuguese ? "dd/MM/yyyy HH:mm" : "MMM d, h:mm a", locale);
		for (final SocioReport.Cancellation c : report.cancellations)
		{
			html.append("<tr><td><span class=\"fsr-nw\">").append(c.created == null ? "" : esc(day.format(c.created.toInstant().atZone(ZONE))))
					.append("</span>")
					.append("<span class=\"fsr-who\">").append(esc(fmt("cancel.ticket", c.ticketId))).append("</span></td><td>")
					.append(esc(c.customerName)).append("<span class=\"fsr-who\">").append(esc(c.customerUid)).append("</span></td><td>")
					.append(esc(c.plan)).append("</td><td>").append(esc(reasonLabel(c.reasonKey, c.reason))).append("</td><td class=\"fsr-wrap\">")
					.append(esc(c.comment == null ? "—" : c.comment)).append("</td><td><span class=\"fsr-pill ")
					.append(c.open ? "open" : "closed").append("\">").append(esc(label(c.open ? "cancel.statusOpen" : "cancel.statusClosed")))
					.append("</span></td></tr>");
		}
		html.append("</tbody></table></div></div></section></div>");
	}

	protected void cancelTile(final StringBuilder html, final int value, final String caption)
	{
		html.append("<div class=\"fsr-tile sm\"><div class=\"fsr-v\">").append(integer.format(value))
				.append("</div><div class=\"fsr-l\"><span class=\"fsr-cap\">").append(esc(caption)).append("</span></div></div>");
	}

	/** Motivo conhecido no idioma do Backoffice; texto livre como veio */
	protected String reasonLabel(final String key, final String fallback)
	{
		if (key != null && key.matches("price|games|moving|service|other"))
		{
			return label("cancel.reason." + key);
		}
		return fallback == null ? "—" : fallback;
	}

	// ------------------------------------------------------------ rodapé

	protected void footer(final StringBuilder html)
	{
		html.append("<div class=\"fsr-foot\"><span>")
				.append(esc(fmt("footer.demo", integer.format(report.realMembers), integer.format(report.realJoinsMonth),
						integer.format(report.realAbandoned))))
				.append("</span><span>").append(esc(label("footer.updated"))).append(" <b>").append(esc(dateTime.format(now)))
				.append("</b></span></div>");
	}

	// ------------------------------------------------------------ SVG

	protected void svgOpen(final StringBuilder html, final int width, final int height, final String aria)
	{
		html.append("<svg viewBox=\"0 0 ").append(width).append(' ').append(height).append("\" width=\"100%\" role=\"img\" aria-label=\"")
				.append(esc(aria)).append("\">");
	}

	protected void text(final StringBuilder html, final double x, final double y, final String anchor, final double size,
			final String color, final boolean bold, final String content)
	{
		html.append("<text x=\"").append(num(x)).append("\" y=\"").append(num(y)).append("\" text-anchor=\"").append(anchor)
				.append("\" font-size=\"").append(num(size)).append("\" fill=\"").append(color).append('"')
				.append(bold ? " font-weight=\"700\"" : "").append('>').append(esc(content)).append("</text>");
	}

	protected void line(final StringBuilder html, final double x1, final double x2, final double y1, final double y2,
			final String color, final boolean dashed)
	{
		html.append("<line x1=\"").append(num(x1)).append("\" x2=\"").append(num(x2)).append("\" y1=\"").append(num(y1))
				.append("\" y2=\"").append(num(y2)).append("\" stroke=\"").append(color).append('"')
				.append(dashed ? " stroke-dasharray=\"4 3\"" : "").append("/>");
	}

	/** Linhas de grade com os valores à esquerda (compactos: "500 mil", "1,5 mi") */
	protected void yAxis(final StringBuilder html, final int left, final int right, final int top, final double plotHeight,
			final double step, final double ceiling, final boolean compact)
	{
		for (double value = 0; value <= ceiling + step / 2; value += step)
		{
			final double y = top + plotHeight - plotHeight * value / ceiling;
			line(html, left, right, y, y, value == 0 ? "#c9d0d6" : SOFT, false);
			text(html, left - 8, y + 4, "end", 11, MUTED, false, compact ? compact(value) : integer.format(value));
		}
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
		return (portuguese ? "R$ " : "R$") + amount.format(value);
	}

	/** Fração em porcentagem: 0.126 → "12,6%" */
	protected String percent(final double fraction, final int decimals)
	{
		final NumberFormat format = NumberFormat.getNumberInstance(locale);
		format.setMinimumFractionDigits(decimals);
		format.setMaximumFractionDigits(decimals);
		return format.format(fraction * 100) + "%";
	}

	/** Número com sinal: "+1,8" / "-0,06" / "0,0" */
	protected String signed(final double value, final int decimals)
	{
		final NumberFormat format = NumberFormat.getNumberInstance(locale);
		format.setMinimumFractionDigits(decimals);
		format.setMaximumFractionDigits(decimals);
		final double factor = Math.pow(10, decimals);
		final double rounded = Math.round(value * factor) / factor;
		return (rounded > 0 ? "+" : rounded < 0 ? "-" : "") + format.format(Math.abs(rounded));
	}

	protected String compact(final double value)
	{
		final NumberFormat format = NumberFormat.getNumberInstance(locale);
		format.setMaximumFractionDigits(1);
		if (value >= 1_000_000)
		{
			return fmt("axis.million", format.format(value / 1_000_000));
		}
		if (value >= 1_000)
		{
			return fmt("axis.thousand", format.format(value / 1_000));
		}
		return integer.format(value);
	}

	protected static String hour(final int hour)
	{
		return (hour < 10 ? "0" : "") + hour + ":00";
	}

	/** Quebra um rótulo em até duas linhas de ~maxChars caracteres */
	protected static List<String> wrap(final String text, final int maxChars)
	{
		final List<String> lines = new ArrayList<>();
		if (text == null || text.isEmpty())
		{
			lines.add("");
			return lines;
		}
		StringBuilder current = new StringBuilder();
		for (final String word : text.trim().split("\\s+"))
		{
			if (current.length() > 0 && current.length() + 1 + word.length() > maxChars && lines.isEmpty())
			{
				lines.add(current.toString());
				current = new StringBuilder();
			}
			if (current.length() > 0)
			{
				current.append(' ');
			}
			current.append(word);
		}
		lines.add(current.toString());
		return lines;
	}

	/** Passo do eixo: 1, 2, 2,5, 5, 10, 20, 25, 50... para 4 ou 5 linhas */
	protected static double niceStep(final double max)
	{
		if (max <= 4)
		{
			return 1;
		}
		final double raw = max / 5.0;
		final double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
		final double normalized = raw / magnitude;
		final double nice = normalized <= 1 ? 1
				: normalized <= 2 ? 2 : normalized <= 2.5 && magnitude >= 10 ? 2.5 : normalized <= 5 ? 5 : 10;
		return Math.max(1, Math.round(nice * magnitude));
	}

	/** Topo do eixo: o múltiplo do passo logo acima do maior valor (mínimo de 4 passos quando é tudo pequeno) */
	protected static double ceiling(final double max, final double step)
	{
		final double top = Math.ceil(max / step) * step;
		return max <= 4 ? Math.max(4, top) : Math.max(step, top);
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
