package br.com.flamengo.matchday.backoffice.widgets.report;

import br.com.flamengo.matchday.model.MatchdayGameModel;
import br.com.flamengo.matchday.report.MatchdayReport;
import br.com.flamengo.matchday.report.MatchdayReportService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Comboitem;
import org.zkoss.zul.Html;

import com.hybris.cockpitng.annotations.SocketEvent;
import com.hybris.cockpitng.annotations.ViewEvent;
import com.hybris.cockpitng.i18n.CockpitLocaleService;
import com.hybris.cockpitng.util.DefaultWidgetController;


/**
 * Relatórios da perspectiva Matchday Flamengo: escolhe o jogo (ou todos) e mostra
 * totais, rosca de vendidos x disponíveis, setores, vendas por dia e tipos de
 * ingresso. Os números vêm do {@link MatchdayReportService}.
 */
public class MatchdayReportController extends DefaultWidgetController
{
	private static final long serialVersionUID = 1L;
	private static final String ALL_GAMES = "__ALL__";
	private static final TimeZone TZ = TimeZone.getTimeZone("America/Sao_Paulo");

	@Wire
	private Combobox gameSelector;
	@Wire
	private Html reportContent;

	@WireVariable
	private transient MatchdayReportService matchdayReportService;
	@WireVariable
	private transient CockpitLocaleService cockpitLocaleService;

	private transient List<MatchdayGameModel> games = Collections.emptyList();
	private String selected;
	/** Último relatório mostrado, para o PDF sair igual à tela */
	private String lastHtml;
	private String lastTitle;

	@Override
	public void initialize(final Component comp)
	{
		super.initialize(comp);
		load();
	}

	/** Ao clicar em "Relatórios" na árvore: recarrega jogos e números */
	@SocketEvent(socketId = "refresh")
	public void refresh(final Object data)
	{
		load();
	}

	@ViewEvent(componentID = "refreshButton", eventName = Events.ON_CLICK)
	public void onRefreshClick()
	{
		load();
	}

	/**
	 * "Gerar PDF": abre a impressão do navegador só com o relatório (A4 deitado),
	 * já com o nome do arquivo; o usuário escolhe "Salvar como PDF". Os gráficos
	 * são SVG, então saem nítidos no PDF.
	 */
	@ViewEvent(componentID = "pdfButton", eventName = Events.ON_CLICK)
	public void onPdfClick()
	{
		if (lastHtml == null)
		{
			render();
		}
		final String fileName = label("pdfFile") + " - " + lastTitle + " - "
				+ new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		final String document = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + MatchdayReportHtml.esc(fileName)
				+ "</title><style>"
				+ "@page{size:A4 landscape;margin:10mm}"
				+ "html,body{margin:0;background:#fff;font-family:'72','72full',Arial,Helvetica,sans-serif;"
				+ "-webkit-print-color-adjust:exact;print-color-adjust:exact}"
				+ ".fla-print-head{display:flex;justify-content:space-between;align-items:baseline;border-bottom:3px solid #c8102e;"
				+ "padding-bottom:6px;margin-bottom:12px;color:#1d2d3e}"
				+ ".fla-print-head b{font-size:16px}.fla-print-head span{font-size:11px;color:#556b82}"
				+ ".flr{max-width:none}"
				+ ".flr .flr-kpis{grid-template-columns:repeat(5,minmax(0,1fr))!important;gap:8px}"
				+ ".flr .flr-row{grid-template-columns:minmax(220px,1fr) minmax(0,2.2fr)!important;gap:8px}"
				+ ".flr .flr-kpi-value{font-size:21px}"
				+ ".flr .flr-card,.flr .flr-kpi,.flr .flr-row{break-inside:avoid;page-break-inside:avoid}"
				+ ".flr .flr-card,.flr .flr-kpi{border-color:#d5dadd}"
				+ ".flr .flr-card svg[width='100%']{max-height:250px!important}"
				+ "</style></head><body><div class=\"fla-print-head\"><b>" + MatchdayReportHtml.esc(label("title"))
				+ "</b><span>Flamengo · Matchday</span></div>" + lastHtml + "</body></html>";
		Clients.evalJavaScript("(function(h,t){var f=document.getElementById('flaReportPrint');if(f){f.parentNode.removeChild(f);}"
				+ "f=document.createElement('iframe');f.id='flaReportPrint';"
				+ "f.style.cssText='position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden';"
				+ "document.body.appendChild(f);var d=f.contentWindow.document;d.open();d.write(h);d.close();"
				+ "setTimeout(function(){var old=document.title;document.title=t;try{f.contentWindow.focus();f.contentWindow.print();}"
				+ "finally{setTimeout(function(){document.title=old;},500);}},400);})(" + js(document) + "," + js(fileName) + ");");
	}

	@ViewEvent(componentID = "gameSelector", eventName = Events.ON_SELECT)
	public void onGameSelected()
	{
		final Comboitem item = gameSelector.getSelectedItem();
		selected = item == null ? null : (String) item.getValue();
		render();
	}

	protected void load()
	{
		games = new ArrayList<>(matchdayReportService.getGames());
		gameSelector.getChildren().clear();
		final Comboitem all = new Comboitem(label("allGames"));
		all.setValue(ALL_GAMES);
		gameSelector.appendChild(all);
		Comboitem current = null;
		for (final MatchdayGameModel game : games)
		{
			final Comboitem item = new Comboitem(title(game) + "  ·  " + format(game.getKickoff(), true));
			item.setValue(game.getCode());
			gameSelector.appendChild(item);
			if (game.getCode().equals(selected))
			{
				current = item;
			}
		}
		if (ALL_GAMES.equals(selected))
		{
			current = all;
		}
		if (current == null)
		{
			final MatchdayGameModel next = defaultGame();
			selected = next == null ? ALL_GAMES : next.getCode();
			current = next == null ? all
					: gameSelector.getItems().stream().filter(item -> selected.equals(item.getValue())).findFirst().orElse(all);
		}
		gameSelector.setSelectedItem(current);
		render();
	}

	protected void render()
	{
		if (games.isEmpty())
		{
			reportContent.setContent("<div style=\"color:#556b82;padding:8px 0\">" + label("empty") + "</div>");
			return;
		}
		final List<MatchdayGameModel> chosen = ALL_GAMES.equals(selected) ? games
				: games.stream().filter(game -> game.getCode().equals(selected)).collect(Collectors.toList());
		final MatchdayReport report = matchdayReportService.getReport(chosen, new Date());
		final String title;
		final String subtitle;
		if (chosen.size() == 1)
		{
			final MatchdayGameModel game = chosen.get(0);
			title = title(game);
			final List<String> parts = new ArrayList<>();
			parts.add(format(game.getKickoff(), false));
			if (StringUtils.isNotBlank(game.getCompetition()))
			{
				parts.add(game.getCompetition().trim());
			}
			parts.add(StringUtils.defaultIfBlank(game.getVenue(), "Maracanã").trim());
			subtitle = String.join("  ·  ", parts);
		}
		else
		{
			title = label("allGames");
			subtitle = label("allGames.sub").replace("{0}", String.valueOf(chosen.size()));
		}
		lastTitle = title;
		lastHtml = new MatchdayReportHtml(report, this::label, locale()).render(title, subtitle);
		reportContent.setContent(lastHtml);
	}

	/** Próximo jogo; sem jogo futuro, o mais recente */
	protected MatchdayGameModel defaultGame()
	{
		final Date now = new Date();
		MatchdayGameModel next = null;
		for (final MatchdayGameModel game : games)
		{
			if (game.getKickoff() != null && !game.getKickoff().before(now))
			{
				next = game; // lista do mais recente para o mais antigo: o último futuro é o próximo
			}
		}
		return next != null ? next : games.isEmpty() ? null : games.get(0);
	}

	protected String title(final MatchdayGameModel game)
	{
		return "Flamengo x " + StringUtils.defaultString(game.getOpponent()).trim();
	}

	protected String format(final Date date, final boolean dateOnly)
	{
		if (date == null)
		{
			return "";
		}
		final boolean pt = "pt".equals(locale().getLanguage());
		final String pattern = dateOnly ? (pt ? "dd/MM/yyyy" : "MMM d, yyyy") : (pt ? "dd/MM/yyyy HH'h'mm" : "MMM d, yyyy h:mm a");
		final SimpleDateFormat format = new SimpleDateFormat(pattern, pt ? new Locale("pt", "BR") : Locale.US);
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
				case '\u2028':
					out.append("\\u2028");
					break;
				case '\u2029':
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
