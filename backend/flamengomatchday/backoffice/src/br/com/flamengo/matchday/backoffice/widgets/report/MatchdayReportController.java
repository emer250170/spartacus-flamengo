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
		reportContent.setContent(new MatchdayReportHtml(report, this::label, locale()).render(title, subtitle));
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

	protected String label(final String key)
	{
		final String value = getLabel(key);
		return StringUtils.isBlank(value) ? key : value;
	}
}
