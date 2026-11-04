package br.com.flamengo.matchday.report.summary.impl;

import br.com.flamengo.matchday.model.MatchdayGameModel;
import br.com.flamengo.matchday.report.MatchdayReportLine;
import br.com.flamengo.matchday.report.MatchdayReportService;
import br.com.flamengo.matchday.report.summary.SummaryReport;
import br.com.flamengo.matchday.report.summary.SummaryReport.CategoryLine;
import br.com.flamengo.matchday.report.summary.SummaryReport.GameLine;
import br.com.flamengo.matchday.report.summary.SummaryReport.MonthLine;
import br.com.flamengo.matchday.report.summary.SummaryReport.TicketLine;
import br.com.flamengo.matchday.report.summary.SummaryReportService;
import de.hybris.platform.core.HybrisEnumValue;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Resumo de vendas (painel inicial do administrador). Pedidos da loja (site), sem os cancelados,
 * recusados na análise de fraude e os que aguardam o Pix ou boleto (esses aparecem à parte).
 * Ingressos, plano, experiência, estacionamento, comida e personalização são separados pelo
 * prefixo do código do produto (os mesmos de flamengo.loja.operacao.semRemessa); o resto é
 * produto da loja. Frete e descontos do pedido entram como linhas próprias, para a soma das
 * linhas fechar com a receita.
 */
public class DefaultSummaryReportService implements SummaryReportService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSummaryReportService.class);
	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
	private static final int MONTHS = 12;
	private static final int NEXT_GAMES = 5;
	private static final String SITE = "electronics-spa";
	private static final String DISCOUNTS = "DISCOUNTS";
	private static final Set<OrderStatus> EXCLUDED = new HashSet<>(Arrays.asList(OrderStatus.CANCELLED, OrderStatus.CHECKED_INVALID));
	private static final List<String> ORDER = Arrays.asList(TICKETS, MEMBERSHIP, EXPERIENCES, PARKING, FOOD, CUSTOMIZATION, STORE,
			FREIGHT, DISCOUNTS);
	/** Prefixo do código do produto -> tipo (o primeiro que casar) */
	private static final Map<String, String> PREFIXES = new LinkedHashMap<>();

	static
	{
		PREFIXES.put("JOGO-", TICKETS);
		PREFIXES.put("NACAO-", MEMBERSHIP);
		PREFIXES.put("EXP-", EXPERIENCES);
		PREFIXES.put("FLA-EST-", PARKING);
		PREFIXES.put("FLA-FOOD-", FOOD);
		PREFIXES.put("FLA-PERSONALIZACAO", CUSTOMIZATION);
	}

	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private MatchdayReportService matchdayReportService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;

	@Override
	public SummaryReport getReport(final SummaryReport.Period period, final Date now)
	{
		final Date reference = now == null ? new Date() : now;
		final SummaryReport.Period chosen = period == null ? SummaryReport.Period.MONTH : period;
		return unrestricted(() -> build(chosen, reference));
	}

	protected SummaryReport build(final SummaryReport.Period period, final Date now)
	{
		final SummaryReport report = new SummaryReport();
		report.setPeriod(period);
		report.setGeneratedAt(now);
		final ZonedDateTime current = now.toInstant().atZone(ZONE);
		final Date from = start(period, current);
		report.setFrom(from);
		final YearMonth thisMonth = YearMonth.from(current);
		final YearMonth firstMonth = thisMonth.minusMonths(MONTHS - 1L);
		final Date monthsFrom = Date.from(firstMonth.atDay(1).atStartOfDay(ZONE).toInstant());

		final Map<String, CategoryLine> categories = new LinkedHashMap<>();
		ORDER.forEach(key -> categories.put(key, new CategoryLine(key)));
		final Map<YearMonth, MonthLine> months = new LinkedHashMap<>();
		for (YearMonth m = firstMonth; !m.isAfter(thisMonth); m = m.plusMonths(1))
		{
			months.put(m, new MonthLine(m));
		}

		final Date queryFrom = from == null ? null : from.before(monthsFrom) ? from : monthsFrom;
		double revenue = 0;
		int orders = 0;
		int awaiting = 0;
		double awaitingAmount = 0;
		for (final OrderModel order : orders(queryFrom))
		{
			if (order.getStatus() != null && EXCLUDED.contains(order.getStatus()))
			{
				continue;
			}
			final double total = value(order.getTotalPrice());
			final Date placed = order.getDate() != null ? order.getDate() : order.getCreationtime();
			final boolean inPeriod = from == null || placed != null && !placed.before(from);
			if (order.getStatus() == OrderStatus.PAYMENT_NOT_CAPTURED)
			{
				if (inPeriod)
				{
					awaiting++;
					awaitingAmount += total;
				}
				continue;
			}
			if (placed != null)
			{
				final MonthLine month = months.get(YearMonth.from(placed.toInstant().atZone(ZONE)));
				if (month != null)
				{
					month.add(total);
				}
			}
			if (!inPeriod)
			{
				continue;
			}
			orders++;
			revenue += total;
			double lines = 0;
			final Map<String, Boolean> seen = new HashMap<>();
			for (final AbstractOrderEntryModel entry : order.getEntries())
			{
				final long qty = entry.getQuantity() == null ? 0 : entry.getQuantity().longValue();
				if (qty <= 0 || entry.getProduct() == null)
				{
					continue;
				}
				final CategoryLine line = categories.get(category(entry.getProduct().getCode()));
				final double price = value(entry.getTotalPrice());
				line.addQuantity(qty);
				line.addRevenue(price);
				lines += price;
				if (seen.put(line.getKey(), Boolean.TRUE) == null)
				{
					line.addOrder();
				}
			}
			final double freight = value(order.getDeliveryCost());
			if (freight > 0)
			{
				final CategoryLine line = categories.get(FREIGHT);
				line.addQuantity(1);
				line.addRevenue(freight);
				line.addOrder();
			}
			final double difference = total - lines - freight;
			if (Math.abs(difference) >= 0.005)
			{
				final CategoryLine line = categories.get(DISCOUNTS);
				line.addRevenue(difference);
				line.addOrder();
			}
		}
		report.setRevenue(revenue);
		report.setOrders(orders);
		report.setAwaitingOrders(awaiting);
		report.setAwaitingAmount(awaitingAmount);
		report.getCategories().addAll(categories.values().stream()
				.filter(line -> !DISCOUNTS.equals(line.getKey()) || line.getOrders() > 0).collect(Collectors.toList()));
		report.getMonths().addAll(months.values());
		tickets(report);
		games(report, now);
		return report;
	}

	protected String category(final String code)
	{
		final String value = StringUtils.defaultString(code);
		for (final Map.Entry<String, String> prefix : PREFIXES.entrySet())
		{
			if (value.startsWith(prefix.getKey()))
			{
				return prefix.getValue();
			}
		}
		return STORE;
	}

	protected Date start(final SummaryReport.Period period, final ZonedDateTime now)
	{
		final LocalDate today = now.toLocalDate();
		switch (period)
		{
			case LAST_30_DAYS:
				return Date.from(now.minusDays(30).toInstant());
			case YEAR:
				return Date.from(today.withDayOfYear(1).atStartOfDay(ZONE).toInstant());
			case ALL:
				return null;
			case MONTH:
			default:
				return Date.from(today.withDayOfMonth(1).atStartOfDay(ZONE).toInstant());
		}
	}

	protected List<OrderModel> orders(final Date from)
	{
		final StringBuilder q = new StringBuilder(
				"SELECT {o.pk} FROM {Order AS o JOIN BaseSite AS s ON {o.site}={s.pk}} WHERE {o.versionID} IS NULL AND {s.uid}=?site");
		final Map<String, Object> params = new HashMap<>();
		params.put("site", SITE);
		if (from != null)
		{
			q.append(" AND {o.date}>=?from");
			params.put("from", from);
		}
		return flexibleSearchService.<OrderModel> search(new FlexibleSearchQuery(q.toString(), params)).getResult();
	}

	/** Chamados que não estão fechados, por tipo (sem dependência de compilação do ticketsystem) */
	protected void tickets(final SummaryReport report)
	{
		final Map<String, TicketLine> lines = new LinkedHashMap<>();
		int open = 0;
		try
		{
			for (final ItemModel ticket : flexibleSearchService.<ItemModel> search("SELECT {pk} FROM {CsTicket}").getResult())
			{
				final String state = code(modelService.getAttributeValue(ticket, "state"));
				if ("Closed".equalsIgnoreCase(state))
				{
					continue;
				}
				final String headline = StringUtils.defaultString((String) modelService.getAttributeValue(ticket, "headline"));
				final String key = headline.startsWith("Cancelamento do plano") ? TICKET_PLAN_CANCEL
						: StringUtils.defaultIfBlank(code(modelService.getAttributeValue(ticket, "category")), "OTHER");
				lines.computeIfAbsent(key, TicketLine::new).add("New".equalsIgnoreCase(state), ticket.getCreationtime());
				open++;
			}
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Resumo: chamados não lidos: {}", e.getMessage());
		}
		report.setOpenTickets(open);
		report.getTickets().addAll(lines.values().stream().sorted(Comparator.comparingInt(TicketLine::getTotal).reversed())
				.collect(Collectors.toList()));
	}

	/** Próximos jogos com os números do relatório de vendas (inclui a ocupação simulada da demonstração) */
	protected void games(final SummaryReport report, final Date now)
	{
		final List<MatchdayGameModel> next = matchdayReportService.getGames().stream()
				.filter(game -> game.getKickoff() != null && !game.getKickoff().before(now))
				.sorted(Comparator.comparing(MatchdayGameModel::getKickoff)).limit(NEXT_GAMES).collect(Collectors.toList());
		for (final MatchdayGameModel game : next)
		{
			final MatchdayReportLine total = matchdayReportService.getReport(Collections.singletonList(game), now).getTotal();
			report.getGames().add(new GameLine(game.getCode(), game.getOpponent(), game.getKickoff(), game.getCompetition(),
					total.getOnSale(), total.getSold(), total.getAvailable(), total.getRevenue()));
		}
	}

	protected <T> T unrestricted(final Supplier<T> body)
	{
		return sessionService.executeInLocalView(new SessionExecutionBody()
		{
			@Override
			public Object execute()
			{
				searchRestrictionService.disableSearchRestrictions();
				return body.get();
			}
		});
	}

	private static String code(final Object value)
	{
		return value instanceof HybrisEnumValue ? ((HybrisEnumValue) value).getCode() : value == null ? null : value.toString();
	}

	private static double value(final Double d)
	{
		return d == null ? 0 : d.doubleValue();
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setMatchdayReportService(final MatchdayReportService matchdayReportService)
	{
		this.matchdayReportService = matchdayReportService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setSearchRestrictionService(final SearchRestrictionService searchRestrictionService)
	{
		this.searchRestrictionService = searchRestrictionService;
	}
}
