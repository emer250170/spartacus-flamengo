package br.com.flamengo.matchday.backoffice.widgets.experiences;

import br.com.flamengo.matchday.socio.SocioExperienceOverview;
import br.com.flamengo.matchday.socio.SocioExperienceService;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zul.Html;

import com.hybris.cockpitng.annotations.SocketEvent;
import com.hybris.cockpitng.annotations.ViewEvent;


/**
 * Relatório do cockpit Gestão de experiências: resgates e vendas por experiência e pelos
 * últimos 6 meses, pontos usados, receita e ocupação das vagas (estilo dos relatórios do sócio).
 */
public class ExperienceReportController extends ExperienceWidgetSupport
{
	private static final long serialVersionUID = 1L;
	private static final int MONTHS = 6;
	private static final String RED = "#c8102e";
	private static final String NAVY = "#1d2d3e";
	private static final String MUTED = "#556b82";

	private static final String CSS = "<style>"
			+ ".fxr{color:#1d2d3e;max-width:1320px;font-size:13px}.fxr *{box-sizing:border-box}"
			+ ".fxr-sub{font-size:13px;color:" + MUTED + ";margin:-6px 0 14px}"
			+ ".fxr-kpis{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin-bottom:12px}"
			+ ".fxr-kpi{background:#fff;border:1px solid #e1e5e9;border-radius:12px;padding:14px 16px;border-top:4px solid " + NAVY + "}"
			+ ".fxr-kpi.red{border-top-color:" + RED + "}.fxr-kpi.grey{border-top-color:#c3cbd3}"
			+ ".fxr-kpi-label{font-size:11px;color:" + MUTED + ";text-transform:uppercase;letter-spacing:.05em;font-weight:600}"
			+ ".fxr-kpi-value{font-size:26px;font-weight:700;margin-top:6px;font-variant-numeric:tabular-nums;white-space:nowrap}"
			+ ".fxr-kpi-sub{font-size:12px;color:" + MUTED + ";margin-top:4px}"
			+ ".fxr-card{background:#fff;border:1px solid #e1e5e9;border-radius:12px;padding:16px 18px;margin-bottom:12px}"
			+ ".fxr-h2{font-size:15px;font-weight:700;margin:0}.fxr-h2-sub{font-size:12px;color:" + MUTED + ";margin:2px 0 12px}"
			+ "table.fxr-t{width:100%;border-collapse:collapse}"
			+ ".fxr-t th{text-align:right;font-weight:600;color:" + MUTED
			+ ";font-size:12px;padding:8px 10px;border-bottom:1px solid #e1e5e9;white-space:nowrap}"
			+ ".fxr-t td{text-align:right;padding:9px 10px;border-bottom:1px solid #eef1f3;font-variant-numeric:tabular-nums;white-space:nowrap}"
			+ ".fxr-t th:first-child,.fxr-t td:first-child{text-align:left;white-space:normal}"
			+ ".fxr-t tr.fxr-total td{font-weight:700;border-top:1px solid #c9d0d6;border-bottom:none}"
			+ ".fxr-bar{display:inline-block;width:72px;height:6px;background:#eef1f3;border-radius:3px;margin-right:8px;vertical-align:middle;overflow:hidden}"
			+ ".fxr-bar span{display:block;height:100%;background:" + RED + "}"
			+ ".fxr-legend{display:flex;gap:14px;font-size:12px;margin-top:8px}"
			+ ".fxr-dot{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:6px;vertical-align:-1px}"
			+ "@media (max-width:1180px){.fxr-kpis{grid-template-columns:repeat(3,minmax(0,1fr))}}"
			+ "</style>";

	@Wire
	private Html reportContent;

	@WireVariable
	private transient SocioExperienceService socioExperienceService;

	@Override
	public void initialize(final Component comp)
	{
		super.initialize(comp);
		render();
	}

	@SocketEvent(socketId = "refresh")
	public void refresh(final Object data)
	{
		render();
	}

	@ViewEvent(componentID = "refreshButton", eventName = Events.ON_CLICK)
	public void onRefresh()
	{
		render();
	}

	protected void render()
	{
		final SocioExperienceOverview o = socioExperienceService.overview(MONTHS);
		final StringBuilder h = new StringBuilder(16_000);
		h.append("<div class=\"fxr\">").append(CSS);
		h.append("<div class=\"fxr-sub\">").append(esc(label("report.sub", date(o.generatedAt, true)))).append("</div>");
		final SocioExperienceOverview.Line t = o.total;
		final int occupied = t.stock == null ? 0 : t.stock.intValue() - t.remaining.intValue();
		h.append("<div class=\"fxr-kpis\">");
		kpi(h, "", label("kpi.redemptions"), number(t.redemptions), label("kpi.redemptions.sub", number(t.pointsUsed)));
		kpi(h, "red", label("kpi.purchases"), number(t.purchases), label("kpi.purchases.sub"));
		kpi(h, "red", label("kpi.revenue"), money(Double.valueOf(t.revenue)), label("kpi.revenue.sub"));
		kpi(h, "", label("kpi.used"), number(t.used), label("kpi.used.sub", number(t.cancelled)));
		kpi(h, "grey", label("kpi.occupancy"), t.stock == null || t.stock.intValue() == 0 ? "-" : percent(occupied, t.stock.intValue()),
				t.stock == null ? label("kpi.occupancy.none") : label("kpi.occupancy.sub", number(occupied), number(t.stock.intValue())));
		h.append("</div>");

		h.append("<section class=\"fxr-card\"><div class=\"fxr-h2\">").append(esc(label("byExperience.title")))
				.append("</div><div class=\"fxr-h2-sub\">").append(esc(label("byExperience.sub"))).append("</div>");
		h.append("<table class=\"fxr-t\"><tr>");
		for (final String key : new String[] { "col.experience", "col.points", "col.price", "col.slots", "col.redemptions",
				"col.purchases", "col.used", "col.cancelled", "col.pointsUsed", "col.revenue" })
		{
			h.append("<th>").append(esc(label(key))).append("</th>");
		}
		h.append("</tr>");
		for (final SocioExperienceOverview.Line l : o.lines)
		{
			line(h, l, false);
		}
		line(h, t, true);
		h.append("</table></section>");

		months(h, o);
		h.append("</div>");
		reportContent.setContent(h.toString());
	}

	protected void line(final StringBuilder h, final SocioExperienceOverview.Line l, final boolean total)
	{
		h.append(total ? "<tr class=\"fxr-total\">" : "<tr>").append("<td>")
				.append(esc(total ? label("col.total") : l.name + (l.active ? "" : " (" + label("inactive") + ")"))).append("</td>")
				.append("<td>").append(total ? "" : number(l.points)).append("</td>")
				.append("<td>").append(total ? "" : l.price == null ? "-" : esc(money(l.price))).append("</td><td>");
		if (l.stock == null)
		{
			h.append(esc(label("unlimited")));
		}
		else
		{
			final int used = l.stock.intValue() - l.remaining.intValue();
			final int pct = l.stock.intValue() == 0 ? 0 : Math.round(used * 100f / l.stock.intValue());
			h.append("<span class=\"fxr-bar\"><span style=\"width:").append(pct).append("%\"></span></span>").append(number(used))
					.append(" / ").append(number(l.stock.intValue()));
		}
		h.append("</td><td>").append(number(l.redemptions)).append("</td><td>").append(number(l.purchases)).append("</td><td>")
				.append(number(l.used)).append("</td><td>").append(number(l.cancelled)).append("</td><td>").append(number(l.pointsUsed))
				.append("</td><td>").append(esc(money(Double.valueOf(l.revenue)))).append("</td></tr>");
	}

	/** Barras por mês: resgates (azul-marinho) e vendas (vermelho), em SVG gerado aqui */
	protected void months(final StringBuilder h, final SocioExperienceOverview o)
	{
		h.append("<section class=\"fxr-card\"><div class=\"fxr-h2\">").append(esc(label("byMonth.title")))
				.append("</div><div class=\"fxr-h2-sub\">").append(esc(label("byMonth.sub", String.valueOf(MONTHS)))).append("</div>");
		int max = 1;
		for (final SocioExperienceOverview.Month m : o.months)
		{
			max = Math.max(max, Math.max(m.redemptions, m.purchases));
		}
		final int w = 640;
		final int hgt = 180;
		final int slot = w / Math.max(1, o.months.size());
		final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(portuguese() ? "MMM/yy" : "MMM yy",
				portuguese() ? new Locale("pt", "BR") : Locale.US);
		h.append("<svg viewBox=\"0 0 ").append(w).append(' ').append(hgt + 30).append("\" width=\"100%\" style=\"max-height:240px\">");
		h.append("<line x1=\"0\" y1=\"").append(hgt).append("\" x2=\"").append(w).append("\" y2=\"").append(hgt)
				.append("\" stroke=\"#c9d0d6\"/>");
		int i = 0;
		for (final SocioExperienceOverview.Month m : o.months)
		{
			final int x = i * slot + slot / 2;
			bar(h, x - 22, m.redemptions, max, hgt, NAVY);
			bar(h, x + 2, m.purchases, max, hgt, RED);
			h.append("<text x=\"").append(x).append("\" y=\"").append(hgt + 18)
					.append("\" text-anchor=\"middle\" font-size=\"12\" fill=\"" + MUTED + "\">")
					.append(esc(YearMonth.parse(m.key).format(fmt))).append("</text>");
			i++;
		}
		h.append("</svg><div class=\"fxr-legend\"><span><span class=\"fxr-dot\" style=\"background:").append(NAVY).append("\"></span>")
				.append(esc(label("legend.redemptions"))).append("</span><span><span class=\"fxr-dot\" style=\"background:").append(RED)
				.append("\"></span>").append(esc(label("legend.purchases"))).append("</span></div>");
		h.append("<table class=\"fxr-t\" style=\"margin-top:10px\"><tr><th>").append(esc(label("col.month"))).append("</th><th>")
				.append(esc(label("col.redemptions"))).append("</th><th>").append(esc(label("col.pointsUsed"))).append("</th><th>")
				.append(esc(label("col.purchases"))).append("</th><th>").append(esc(label("col.revenue"))).append("</th></tr>");
		for (final SocioExperienceOverview.Month m : o.months)
		{
			h.append("<tr><td>").append(esc(YearMonth.parse(m.key).format(fmt))).append("</td><td>").append(number(m.redemptions))
					.append("</td><td>").append(number(m.pointsUsed)).append("</td><td>").append(number(m.purchases)).append("</td><td>")
					.append(esc(money(Double.valueOf(m.revenue)))).append("</td></tr>");
		}
		h.append("</table></section>");
	}

	protected void bar(final StringBuilder h, final int x, final int value, final int max, final int hgt, final String color)
	{
		final int height = Math.round((hgt - 24) * value / (float) max);
		h.append("<rect x=\"").append(x).append("\" y=\"").append(hgt - height).append("\" width=\"20\" height=\"").append(height)
				.append("\" rx=\"3\" fill=\"").append(color).append("\"/>");
		if (value > 0)
		{
			h.append("<text x=\"").append(x + 10).append("\" y=\"").append(hgt - height - 4)
					.append("\" text-anchor=\"middle\" font-size=\"11\" fill=\"#1d2d3e\">").append(value).append("</text>");
		}
	}

	protected void kpi(final StringBuilder h, final String color, final String name, final String value, final String sub)
	{
		h.append("<div class=\"fxr-kpi ").append(color).append("\"><div class=\"fxr-kpi-label\">").append(esc(name))
				.append("</div><div class=\"fxr-kpi-value\">").append(esc(value)).append("</div><div class=\"fxr-kpi-sub\">")
				.append(esc(sub)).append("</div></div>");
	}

	protected String percent(final int part, final int whole)
	{
		return whole <= 0 ? "-" : Math.round(part * 100f / whole) + "%";
	}
}
