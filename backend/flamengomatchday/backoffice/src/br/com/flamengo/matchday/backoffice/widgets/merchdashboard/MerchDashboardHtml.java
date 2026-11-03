package br.com.flamengo.matchday.backoffice.widgets.merchdashboard;

import br.com.flamengo.matchday.backoffice.widgets.report.MatchdayReportHtml;
import br.com.flamengo.matchday.report.merch.MerchDashboard;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;


/**
 * HTML do painel do gestor de merchandising: cartões de números, regras publicadas e
 * cupons mais usados (mesmo visual dos relatórios do Matchday e do Sócio-Torcedor).
 */
public class MerchDashboardHtml
{
	private static final String RED = "#c8102e";
	private static final String MUTED = "#556b82";
	private static final TimeZone TZ = TimeZone.getTimeZone("America/Sao_Paulo");
	private static final int MAX_RULES = 12;

	private static final String CSS = "<style>"
			+ ".fmd{color:#1d2d3e;max-width:1320px;font-size:13px}"
			+ ".fmd *{box-sizing:border-box}"
			+ ".fmd-sub{font-size:13px;color:" + MUTED + ";margin:-6px 0 14px}"
			+ ".fmd-kpis{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin-bottom:12px}"
			+ ".fmd-kpi{background:#fff;border:1px solid #e1e5e9;border-radius:12px;padding:14px 16px;border-top:4px solid #1d2d3e}"
			+ ".fmd-kpi.red{border-top-color:" + RED + "}.fmd-kpi.amber{border-top-color:#e9a23b}.fmd-kpi.grey{border-top-color:#c3cbd3}"
			+ ".fmd-kpi-label{font-size:11px;color:" + MUTED + ";text-transform:uppercase;letter-spacing:.05em;font-weight:600}"
			+ ".fmd-kpi-value{font-size:26px;font-weight:700;margin-top:6px;font-variant-numeric:tabular-nums;white-space:nowrap}"
			+ ".fmd-kpi-sub{font-size:12px;color:" + MUTED + ";margin-top:4px}"
			+ ".fmd-row{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(0,1fr);gap:12px;margin-bottom:12px}"
			+ ".fmd-card{background:#fff;border:1px solid #e1e5e9;border-radius:12px;padding:16px 18px}"
			+ ".fmd-h2{font-size:15px;font-weight:700;margin:0}"
			+ ".fmd-h2-sub{font-size:12px;color:" + MUTED + ";margin:2px 0 12px}"
			+ "table.fmd-t{width:100%;border-collapse:collapse;font-size:13px}"
			+ ".fmd-t th{text-align:left;font-weight:600;color:" + MUTED
			+ ";font-size:12px;padding:8px 10px;border-bottom:1px solid #e1e5e9;white-space:nowrap}"
			+ ".fmd-t td{padding:9px 10px;border-bottom:1px solid #eef1f3;vertical-align:top}"
			+ ".fmd-t .num{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}"
			+ ".fmd-code{font-size:11px;color:" + MUTED + "}"
			+ ".fmd-tag{display:inline-block;font-size:11px;font-weight:600;border-radius:10px;padding:1px 8px;margin-left:6px}"
			+ ".fmd-tag.amber{background:#fdf1dc;color:#8a5a00}.fmd-tag.green{background:#e5f4e4;color:#256f3a}"
			+ ".fmd-tag.grey{background:#eef1f3;color:" + MUTED + "}"
			+ ".fmd-empty{color:" + MUTED + ";font-size:12px;margin-top:6px}"
			+ "@media (max-width:1180px){.fmd-kpis{grid-template-columns:repeat(3,minmax(0,1fr))}.fmd-row{grid-template-columns:1fr}}"
			+ "</style>";

	private final MerchDashboard d;
	private final Function<String, String> labels;
	private final boolean portuguese;
	private final NumberFormat amount;
	private final NumberFormat integer;
	private final SimpleDateFormat day;
	private final SimpleDateFormat dateTime;

	public MerchDashboardHtml(final MerchDashboard dashboard, final Function<String, String> labels, final Locale uiLocale)
	{
		this.d = dashboard;
		this.labels = labels;
		this.portuguese = uiLocale != null && "pt".equals(uiLocale.getLanguage());
		final Locale locale = portuguese ? new Locale("pt", "BR") : Locale.US;
		this.amount = NumberFormat.getNumberInstance(locale);
		this.amount.setMinimumFractionDigits(2);
		this.amount.setMaximumFractionDigits(2);
		this.integer = NumberFormat.getIntegerInstance(locale);
		this.day = new SimpleDateFormat(portuguese ? "dd/MM/yyyy" : "MMM d, yyyy", locale);
		this.day.setTimeZone(TZ);
		this.dateTime = new SimpleDateFormat(portuguese ? "dd/MM/yyyy HH:mm" : "MMM d, yyyy h:mm a", locale);
		this.dateTime.setTimeZone(TZ);
	}

	public String render()
	{
		final StringBuilder html = new StringBuilder(16_000);
		html.append("<div class=\"fmd\">").append(CSS);
		html.append("<div class=\"fmd-sub\">")
				.append(esc(fmt("sub", StringUtils.defaultIfBlank(d.promotionGroup, "-"), date(dateTime, d.generatedAt))))
				.append("</div>");
		kpis(html);
		html.append("<div class=\"fmd-row\">");
		rules(html);
		coupons(html);
		html.append("</div></div>");
		return html.toString();
	}

	protected void kpis(final StringBuilder html)
	{
		html.append("<div class=\"fmd-kpis\">");
		kpi(html, "red", label("kpi.published"), integer.format(d.rulesPublished),
				d.rulesExpiring > 0 ? fmt("kpi.published.sub", integer.format(d.rulesExpiring), String.valueOf(d.expiringDays))
						: fmt("kpi.published.none", String.valueOf(d.expiringDays)));
		kpi(html, "amber", label("kpi.draft"), integer.format(d.rulesDraft), label("kpi.draft.sub"));
		kpi(html, "", label("kpi.coupons"), integer.format(d.couponsActive), fmt("kpi.coupons.sub", integer.format(d.couponsTotal)));
		kpi(html, "grey", label("kpi.uses"), integer.format(d.redemptions), fmt("kpi.uses.sub", String.valueOf(d.days)));
		kpi(html, "red", label("kpi.discount"), money(d.discount), fmt("kpi.discount.sub", integer.format(d.ordersWithDiscount),
				integer.format(d.orders), integer.format(d.ordersWithCoupon), String.valueOf(d.days)));
		html.append("</div>");
	}

	protected void rules(final StringBuilder html)
	{
		html.append("<section class=\"fmd-card\"><div class=\"fmd-h2\">").append(esc(label("rules.title")))
				.append("</div><div class=\"fmd-h2-sub\">").append(esc(label("rules.sub"))).append("</div>");
		if (d.published.isEmpty())
		{
			html.append("<div class=\"fmd-empty\">").append(esc(label("rules.empty"))).append("</div></section>");
			return;
		}
		html.append("<table class=\"fmd-t\"><tr><th>").append(esc(label("rules.name"))).append("</th><th class=\"num\">")
				.append(esc(label("rules.priority"))).append("</th><th>").append(esc(label("rules.period"))).append("</th><th>")
				.append(esc(label("rules.group"))).append("</th></tr>");
		for (final MerchDashboard.Rule rule : d.published.subList(0, Math.min(MAX_RULES, d.published.size())))
		{
			html.append("<tr><td>").append(esc(StringUtils.defaultIfBlank(rule.name, rule.code)));
			if (rule.expiring)
			{
				html.append("<span class=\"fmd-tag amber\">").append(esc(label("rules.expiring"))).append("</span>");
			}
			html.append("<div class=\"fmd-code\">").append(esc(rule.code)).append("</div></td><td class=\"num\">")
					.append(rule.priority == null ? "" : integer.format(rule.priority)).append("</td><td>")
					.append(esc(period(rule))).append("</td><td>").append(esc(StringUtils.defaultString(rule.ruleGroup)))
					.append("</td></tr>");
		}
		html.append("</table></section>");
	}

	protected void coupons(final StringBuilder html)
	{
		html.append("<section class=\"fmd-card\"><div class=\"fmd-h2\">").append(esc(label("coupons.title")))
				.append("</div><div class=\"fmd-h2-sub\">").append(esc(fmt("coupons.sub", String.valueOf(d.days)))).append("</div>");
		if (d.topCoupons.isEmpty())
		{
			html.append("<div class=\"fmd-empty\">").append(esc(fmt("coupons.empty", String.valueOf(d.days))))
					.append("</div></section>");
			return;
		}
		html.append("<table class=\"fmd-t\"><tr><th>").append(esc(label("coupons.coupon"))).append("</th><th>")
				.append(esc(label("coupons.kind"))).append("</th><th>").append(esc(label("coupons.status")))
				.append("</th><th class=\"num\">").append(esc(label("coupons.uses"))).append("</th></tr>");
		for (final MerchDashboard.CouponUse use : d.topCoupons)
		{
			html.append("<tr><td>").append(esc(use.couponId));
			if (StringUtils.isNotBlank(use.name) && !use.name.equals(use.couponId))
			{
				html.append("<div class=\"fmd-code\">").append(esc(use.name)).append("</div>");
			}
			final String kind = label("kind." + use.kind);
			html.append("</td><td>").append(esc(kind.startsWith("kind.") ? use.kind : kind)).append("</td><td><span class=\"fmd-tag ")
					.append(use.active ? "green" : "grey").append("\" style=\"margin-left:0\">")
					.append(esc(label(use.active ? "coupons.active" : "coupons.inactive"))).append("</span></td><td class=\"num\">")
					.append(integer.format(use.uses)).append("</td></tr>");
		}
		html.append("</table></section>");
	}

	protected void kpi(final StringBuilder html, final String color, final String name, final String value, final String sub)
	{
		html.append("<div class=\"fmd-kpi ").append(color).append("\"><div class=\"fmd-kpi-label\">").append(esc(name))
				.append("</div><div class=\"fmd-kpi-value\">").append(esc(value)).append("</div><div class=\"fmd-kpi-sub\">")
				.append(esc(sub)).append("</div></div>");
	}

	protected String period(final MerchDashboard.Rule rule)
	{
		if (rule.start != null && rule.end != null)
		{
			return date(day, rule.start) + " – " + date(day, rule.end);
		}
		if (rule.end != null)
		{
			return fmt("rules.until", date(day, rule.end));
		}
		if (rule.start != null)
		{
			return fmt("rules.from", date(day, rule.start)) + " (" + label("rules.noEnd") + ")";
		}
		return label("rules.noEnd");
	}

	protected String money(final double value)
	{
		final String symbol = d.currency == null || "BRL".equals(d.currency) ? "R$" : d.currency;
		return symbol + (portuguese ? " " : "") + amount.format(value);
	}

	protected static String date(final SimpleDateFormat format, final Date date)
	{
		return date == null ? "" : format.format(date);
	}

	protected String label(final String key)
	{
		return labels.apply(key);
	}

	/** Rótulo com {0}, {1}... trocados pelos valores */
	protected String fmt(final String key, final String... values)
	{
		String text = label(key);
		for (int i = 0; i < values.length; i++)
		{
			text = text.replace("{" + i + "}", values[i] == null ? "" : values[i]);
		}
		return text;
	}

	protected static String esc(final String text)
	{
		return MatchdayReportHtml.esc(text);
	}
}
