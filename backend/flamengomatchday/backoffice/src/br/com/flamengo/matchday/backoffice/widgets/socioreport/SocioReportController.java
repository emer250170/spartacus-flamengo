package br.com.flamengo.matchday.backoffice.widgets.socioreport;

import br.com.flamengo.matchday.report.socio.SocioReport;
import br.com.flamengo.matchday.report.socio.SocioReportService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Html;

import com.hybris.cockpitng.annotations.SocketEvent;
import com.hybris.cockpitng.annotations.ViewEvent;
import com.hybris.cockpitng.i18n.CockpitLocaleService;
import com.hybris.cockpitng.util.DefaultWidgetController;


/**
 * Relatórios do sócio-torcedor (perspectiva Sócio-Torcedor › Relatórios): painel
 * no modelo do "Dashboard - Base" do clube, com "Atualizar" e "Gerar PDF". Os
 * números vêm do {@link SocioReportService}.
 */
public class SocioReportController extends DefaultWidgetController
{
	private static final long serialVersionUID = 1L;

	@Wire
	private Html reportContent;

	@WireVariable
	private transient SocioReportService socioReportService;
	@WireVariable
	private transient CockpitLocaleService cockpitLocaleService;

	private String lastHtml;

	@Override
	public void initialize(final Component comp)
	{
		super.initialize(comp);
		render();
	}

	/** Ao clicar em "Relatórios" na árvore */
	@SocketEvent(socketId = "refresh")
	public void refresh(final Object data)
	{
		render();
	}

	@ViewEvent(componentID = "refreshButton", eventName = Events.ON_CLICK)
	public void onRefreshClick()
	{
		render();
	}

	/**
	 * "Gerar PDF": abre a impressão do navegador só com o painel (A4 deitado), já
	 * com o nome do arquivo; o usuário escolhe "Salvar como PDF".
	 */
	@ViewEvent(componentID = "pdfButton", eventName = Events.ON_CLICK)
	public void onPdfClick()
	{
		if (lastHtml == null)
		{
			render();
		}
		final String fileName = label("pdfFile") + " - " + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		final String document = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + SocioReportHtml.esc(fileName)
				+ "</title><style>"
				+ "@page{size:A4 landscape;margin:8mm}"
				+ "html,body{margin:0;background:#fff;font-family:'72','72full',Arial,Helvetica,sans-serif;"
				+ "-webkit-print-color-adjust:exact;print-color-adjust:exact}"
				+ ".fla-print-head{display:flex;justify-content:space-between;align-items:baseline;border-bottom:3px solid #c8102e;"
				+ "padding-bottom:6px;margin-bottom:10px;color:#1d2d3e}"
				+ ".fla-print-head b{font-size:16px}.fla-print-head span{font-size:11px;color:#556b82}"
				+ ".fsr{max-width:none}"
				+ "</style></head><body><div class=\"fla-print-head\"><b>" + SocioReportHtml.esc(label("title"))
				+ "</b><span>Flamengo · Sócio-Torcedor</span></div>" + lastHtml + "</body></html>";
		Clients.evalJavaScript("(function(h,t){var f=document.getElementById('flaReportPrint');if(f){f.parentNode.removeChild(f);}"
				+ "f=document.createElement('iframe');f.id='flaReportPrint';"
				+ "f.style.cssText='position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden';"
				+ "document.body.appendChild(f);var d=f.contentWindow.document;d.open();d.write(h);d.close();"
				+ "setTimeout(function(){var old=document.title;document.title=t;try{f.contentWindow.focus();f.contentWindow.print();}"
				+ "finally{setTimeout(function(){document.title=old;},500);}},400);})(" + js(document) + "," + js(fileName) + ");");
	}

	protected void render()
	{
		final SocioReport report = socioReportService.getReport(new Date(), locale());
		if (report.byPlan.isEmpty())
		{
			lastHtml = "<div style=\"color:#556b82;padding:8px 0\">" + SocioReportHtml.esc(label("empty")) + "</div>";
		}
		else
		{
			lastHtml = new SocioReportHtml(report, this::label, locale()).render();
		}
		reportContent.setContent(lastHtml);
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
