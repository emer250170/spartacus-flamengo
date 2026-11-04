package br.com.flamengo.matchday.backoffice.widgets.summary;

import br.com.flamengo.matchday.report.summary.SummaryReport;
import br.com.flamengo.matchday.report.summary.SummaryReportService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Comboitem;
import org.zkoss.zul.Html;

import com.hybris.cockpitng.annotations.ViewEvent;
import com.hybris.cockpitng.i18n.CockpitLocaleService;
import com.hybris.cockpitng.util.DefaultWidgetController;


/**
 * Resumo de vendas do painel inicial do administrador (Cockpit de administração > Principal):
 * indicadores do período, vendas por produto e serviço, chamados em aberto por tipo, próximos
 * jogos e receita mês a mês. Os números vêm do {@link SummaryReportService}.
 */
public class SummaryReportController extends DefaultWidgetController
{
	private static final long serialVersionUID = 1L;
	private static final TimeZone TZ = TimeZone.getTimeZone("America/Sao_Paulo");

	@Wire
	private Combobox periodSelector;
	@Wire
	private Html reportContent;

	@WireVariable
	private transient SummaryReportService summaryReportService;
	@WireVariable
	private transient CockpitLocaleService cockpitLocaleService;

	private SummaryReport.Period period = SummaryReport.Period.MONTH;
	/** Último resumo mostrado, para o PDF sair igual à tela */
	private String lastHtml;

	@Override
	public void initialize(final Component comp)
	{
		super.initialize(comp);
		periodSelector.getChildren().clear();
		for (final SummaryReport.Period option : SummaryReport.Period.values())
		{
			final Comboitem item = new Comboitem(label("period." + option.name()));
			item.setValue(option.name());
			periodSelector.appendChild(item);
			if (option == period)
			{
				periodSelector.setSelectedItem(item);
			}
		}
		render();
	}

	@ViewEvent(componentID = "periodSelector", eventName = Events.ON_SELECT)
	public void onPeriodSelected()
	{
		final Comboitem item = periodSelector.getSelectedItem();
		if (item != null)
		{
			period = SummaryReport.Period.valueOf((String) item.getValue());
		}
		render();
	}

	@ViewEvent(componentID = "refreshButton", eventName = Events.ON_CLICK)
	public void onRefreshClick()
	{
		render();
	}

	/** "Gerar PDF": a impressão do navegador só com o resumo (A4 deitado), como nos outros relatórios */
	@ViewEvent(componentID = "pdfButton", eventName = Events.ON_CLICK)
	public void onPdfClick()
	{
		if (lastHtml == null)
		{
			render();
		}
		final String fileName = label("pdfFile") + " - " + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		final String document = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + SummaryReportHtml.esc(fileName)
				+ "</title><style>"
				+ "@page{size:A4 landscape;margin:10mm}"
				+ "html,body{margin:0;background:#fff;font-family:'72','72full',Arial,Helvetica,sans-serif;"
				+ "-webkit-print-color-adjust:exact;print-color-adjust:exact}"
				+ ".fla-print-head{display:flex;justify-content:space-between;align-items:baseline;border-bottom:3px solid #c8102e;"
				+ "padding-bottom:6px;margin-bottom:12px;color:#1d2d3e}"
				+ ".fla-print-head b{font-size:16px}.fla-print-head span{font-size:11px;color:#556b82}"
				+ ".flr{max-width:none}"
				+ ".flr .flr-kpis{grid-template-columns:repeat(5,minmax(0,1fr))!important;gap:8px}"
				+ ".flr .flr-row{grid-template-columns:minmax(0,1.6fr) minmax(0,1fr)!important;gap:8px}"
				+ ".flr .flr-kpi-value{font-size:21px}"
				+ ".flr .flr-card,.flr .flr-kpi,.flr .flr-row{break-inside:avoid;page-break-inside:avoid}"
				+ ".flr .flr-card,.flr .flr-kpi{border-color:#d5dadd}"
				+ ".flr .flr-card svg[width='100%']{max-height:250px!important}"
				+ "</style></head><body><div class=\"fla-print-head\"><b>" + SummaryReportHtml.esc(label("title"))
				+ "</b><span>Flamengo</span></div>" + lastHtml + "</body></html>";
		Clients.evalJavaScript("(function(h,t){var f=document.getElementById('flaSummaryPrint');if(f){f.parentNode.removeChild(f);}"
				+ "f=document.createElement('iframe');f.id='flaSummaryPrint';"
				+ "f.style.cssText='position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden';"
				+ "document.body.appendChild(f);var d=f.contentWindow.document;d.open();d.write(h);d.close();"
				+ "setTimeout(function(){var old=document.title;document.title=t;try{f.contentWindow.focus();f.contentWindow.print();}"
				+ "finally{setTimeout(function(){document.title=old;},500);}},400);})(" + js(document) + "," + js(fileName) + ");");
	}

	protected void render()
	{
		final Date now = new Date();
		final SummaryReport report = summaryReportService.getReport(period, now);
		final String subtitle = report.getFrom() == null ? label("sub.all")
				: label("sub.range").replace("{0}", format(report.getFrom())).replace("{1}", format(now));
		lastHtml = new SummaryReportHtml(report, this::label, locale()).render(label("period." + period.name()), subtitle);
		reportContent.setContent(lastHtml);
	}

	protected String format(final Date date)
	{
		final boolean pt = "pt".equals(locale().getLanguage());
		final SimpleDateFormat format = new SimpleDateFormat(pt ? "dd/MM/yyyy" : "MMM d, yyyy", pt ? new Locale("pt", "BR") : Locale.US);
		format.setTimeZone(TZ);
		return format.format(date);
	}

	protected Locale locale()
	{
		final Locale locale = cockpitLocaleService == null ? null : cockpitLocaleService.getCurrentLocale();
		return locale == null ? Locale.ENGLISH : locale;
	}

	/** Texto como literal de JavaScript ("...") */
	protected static String js(final String text)
	{
		final StringBuilder out = new StringBuilder(text.length() + 16).append('"');
		for (final char c : text.toCharArray())
		{
			switch (c)
			{
				case '"':
					out.append("\\\"");
					break;
				case '\\':
					out.append("\\\\");
					break;
				case '\n':
					out.append("\\n");
					break;
				case '\r':
					out.append("\\r");
					break;
				case '<':
					out.append("\\u003c");
					break;
				case ' ':
					out.append("\\u2028");
					break;
				case ' ':
					out.append("\\u2029");
					break;
				default:
					out.append(c);
			}
		}
		return out.append('"').toString();
	}

	protected String label(final String key)
	{
		final String value = getLabel(key);
		return StringUtils.isBlank(value) ? key : value;
	}
}
