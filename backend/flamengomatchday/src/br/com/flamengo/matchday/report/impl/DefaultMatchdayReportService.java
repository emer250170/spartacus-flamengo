package br.com.flamengo.matchday.report.impl;

import br.com.flamengo.matchday.enums.MatchdayGameStatus;
import br.com.flamengo.matchday.enums.MatchdayTicketStatus;
import br.com.flamengo.matchday.game.MatchdayGameService;
import br.com.flamengo.matchday.model.MatchdayGameModel;
import br.com.flamengo.matchday.model.MatchdayTicketModel;
import br.com.flamengo.matchday.report.MatchdayReport;
import br.com.flamengo.matchday.report.MatchdayReportDay;
import br.com.flamengo.matchday.report.MatchdayReportLine;
import br.com.flamengo.matchday.report.MatchdayReportService;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.ordersplitting.model.StockLevelModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;


/**
 * Monta o relatório de ingressos a partir do estoque das poltronas (vendida =
 * estoque zerado, livre = estoque sem reserva) e dos pedidos da loja.
 * <p>
 * Poltrona vendida com pedido: tipo, preço e data vêm do pedido (preço abaixo da
 * inteira = sócio; preço zero = cortesia). Poltrona vendida sem pedido vem da
 * ocupação simulada da demonstração: tipo, preço e data são gerados de forma
 * determinística a partir do código da poltrona (o relatório sai sempre igual),
 * com datas concentradas na abertura de cada onda e perto do jogo.
 */
public class DefaultMatchdayReportService implements MatchdayReportService
{
	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
	private static final long DAY = 86_400_000L;
	/** Gráfico por dia limitado aos últimos 120 dias */
	private static final int MAX_DAYS = 120;
	/** Desconto na inteira: Nação Maraca 1, 2 e 3 */
	private static final int[] MEMBER_DISCOUNT = { 75, 65, 60 };

	private FlexibleSearchService flexibleSearchService;
	private MatchdayGameService matchdayGameService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;

	@Override
	public List<MatchdayGameModel> getGames()
	{
		return unrestricted(() -> {
			final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {MatchdayGame} ORDER BY {kickoff} DESC");
			return flexibleSearchService.<MatchdayGameModel> search(query).getResult().stream()
					.filter(game -> game.getStatus() != MatchdayGameStatus.DRAFT).collect(Collectors.toList());
		});
	}

	@Override
	public MatchdayReport getReport(final Collection<MatchdayGameModel> games, final Date now)
	{
		final Date reference = now == null ? new Date() : now;
		return unrestricted(() -> build(games == null ? Collections.emptyList() : games, reference));
	}

	/** Mesmos números para o admin e para o Gestor de jogos */
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

	protected MatchdayReport build(final Collection<MatchdayGameModel> games, final Date now)
	{
		final MatchdayReport report = new MatchdayReport();
		report.setGeneratedAt(now);
		final Map<String, MatchdayReportLine> sectors = new LinkedHashMap<>();
		SECTORS.forEach(sector -> sectors.put(sector, new MatchdayReportLine(sector)));
		final Map<String, MatchdayReportLine> types = new LinkedHashMap<>();
		TICKET_TYPES.forEach(type -> types.put(type, new MatchdayReportLine(type)));
		final Map<LocalDate, MatchdayReportDay> days = new TreeMap<>();
		Map<String, Double> templatePrices = null;
		LocalDate first = null;
		LocalDate last = null;
		int count = 0;
		int store = 0;
		int simulated = 0;

		for (final MatchdayGameModel game : games)
		{
			if (game == null || game.getCode() == null)
			{
				continue;
			}
			count++;
			final Map<String, AbstractOrderEntryModel> orders = storeSales(game.getCode());
			final Date[] window = salesWindow(game, orders.values(), now);
			final LocalDate start = day(window[0]);
			final LocalDate end = day(window[1]);
			first = first == null || start.isBefore(first) ? start : first;
			last = last == null || end.isAfter(last) ? end : last;

			for (final StockLevelModel level : seats(game.getCode()))
			{
				final String seat = level.getProductCode();
				final String sector = seat.substring(game.getCode().length() + 1).split("-")[0];
				final boolean sold = level.getAvailable() <= 0;
				final boolean free = !sold && level.getAvailable() - level.getReserved() > 0;
				final MatchdayReportLine sectorLine = sectors.computeIfAbsent(sector, MatchdayReportLine::new);
				report.getTotal().addSeat(sold, free);
				sectorLine.addSeat(sold, free);
				if (!sold)
				{
					continue;
				}
				if (templatePrices == null && sectorPriceOf(game, sector) == null)
				{
					templatePrices = matchdayGameService.getTemplatePrices();
				}
				final double listPrice = listPrice(game, sector, templatePrices);
				final Sale sale;
				final AbstractOrderEntryModel entry = orders.get(seat);
				if (entry != null)
				{
					sale = storeSale(entry, listPrice);
					store++;
				}
				else
				{
					sale = simulatedSale(game, seat, listPrice, window);
					simulated++;
				}
				report.getTotal().addSale(sale.price);
				sectorLine.addSale(sale.price);
				types.get(sale.type).addTypedSale(sale.price);
				days.computeIfAbsent(day(sale.date), MatchdayReportDay::new).add(sale.price);
			}
		}

		if (first != null)
		{
			LocalDate from = first;
			if (ChronoUnit.DAYS.between(from, last) > MAX_DAYS)
			{
				from = last.minusDays(MAX_DAYS);
			}
			for (LocalDate day = from; !day.isAfter(last); day = day.plusDays(1))
			{
				final MatchdayReportDay sales = days.get(day);
				report.getDays().add(sales == null ? new MatchdayReportDay(day) : sales);
			}
			report.setFirstDay(from);
			report.setLastDay(last);
		}
		report.getSectors().addAll(sectors.values());
		report.getTicketTypes().addAll(types.values());
		report.setGames(count);
		report.setStoreSales(store);
		report.setSimulatedSales(simulated);
		return report;
	}

	// ------------------------------------------------------------ vendas

	protected static class Sale
	{
		final String type;
		final double price;
		final Date date;

		Sale(final String type, final double price, final Date date)
		{
			this.type = type;
			this.price = Math.round(price * 100) / 100.0;
			this.date = date;
		}
	}

	/** Venda de um pedido da loja: abaixo da inteira = sócio, zero = cortesia */
	protected Sale storeSale(final AbstractOrderEntryModel entry, final double listPrice)
	{
		final AbstractOrderModel order = entry.getOrder();
		final long quantity = entry.getQuantity() == null || entry.getQuantity().longValue() < 1 ? 1 : entry.getQuantity().longValue();
		final double price = entry.getTotalPrice() != null ? entry.getTotalPrice().doubleValue() / quantity
				: entry.getBasePrice() == null ? listPrice : entry.getBasePrice().doubleValue();
		final String type;
		if (price <= 0.004)
		{
			type = TYPE_COURTESY;
		}
		else if (listPrice > 0 && price < listPrice * 0.995)
		{
			type = TYPE_MEMBER;
		}
		else
		{
			type = TYPE_FULL;
		}
		final Date date = order.getDate() != null ? order.getDate() : order.getCreationtime();
		return new Sale(type, price, date);
	}

	/**
	 * Venda da ocupação simulada: 40% inteira, 22% meia, 26% sócio, 7% gratuidade e
	 * 5% cortesia. Sócio compra a partir da onda do plano; inteira e meia a partir da
	 * venda geral. Dois terços das vendas logo na abertura, um terço perto do jogo.
	 */
	protected Sale simulatedSale(final MatchdayGameModel game, final String seat, final double listPrice, final Date[] window)
	{
		final int typeBucket = bucket(seat + "#tipo", 100);
		final String type;
		double price;
		Date from;
		if (typeBucket < 40)
		{
			type = TYPE_FULL;
			price = listPrice;
			from = game.getGeneralSaleStart();
		}
		else if (typeBucket < 62)
		{
			type = TYPE_HALF;
			price = listPrice / 2;
			from = game.getGeneralSaleStart();
		}
		else if (typeBucket < 88)
		{
			type = TYPE_MEMBER;
			final int level = bucket(seat + "#nivel", 3);
			price = listPrice * (100 - MEMBER_DISCOUNT[level]) / 100.0;
			from = level == 0 ? game.getWave1Start() : level == 1 ? game.getWave2Start() : game.getWave3Start();
		}
		else if (typeBucket < 95)
		{
			type = TYPE_FREE;
			price = 0;
			from = null;
		}
		else
		{
			type = TYPE_COURTESY;
			price = 0;
			from = null;
		}
		final Date start = window[0];
		final Date end = window[1];
		if (from == null || from.before(start))
		{
			from = start;
		}
		if (from.after(end))
		{
			from = end;
		}
		final double u = bucket(seat + "#dia", 10000) / 10000.0;
		final double share = bucket(seat + "#curva", 3) == 0 ? 1 - Math.pow(1 - u, 3) : Math.pow(u, 3);
		final Date date = new Date(from.getTime() + Math.round(share * (end.getTime() - from.getTime())));
		return new Sale(type, price, date);
	}

	/**
	 * Período de vendas do jogo: da abertura (primeira onda, ou o cadastro do jogo)
	 * até hoje ou o jogo. Jogo sem ondas e com período muito curto (jogos de
	 * demonstração cadastrados depois da data) ganha os 10 dias anteriores.
	 */
	protected Date[] salesWindow(final MatchdayGameModel game, final Collection<AbstractOrderEntryModel> orders, final Date now)
	{
		Date end = game.getKickoff() != null && game.getKickoff().before(now) ? game.getKickoff() : now;
		final List<Date> waves = new ArrayList<>();
		for (final Date wave : new Date[] { game.getWave1Start(), game.getWave2Start(), game.getWave3Start(),
				game.getGeneralSaleStart() })
		{
			if (wave != null)
			{
				waves.add(wave);
			}
		}
		Date start = waves.isEmpty() ? game.getCreationtime() : Collections.min(waves);
		if (start == null)
		{
			start = end;
		}
		for (final AbstractOrderEntryModel entry : orders)
		{
			final Date date = entry.getOrder().getDate();
			if (date != null && date.before(start))
			{
				start = date;
			}
			if (date != null && date.after(end))
			{
				end = date;
			}
		}
		if (waves.isEmpty() && end.getTime() - start.getTime() < 3 * DAY)
		{
			start = new Date(end.getTime() - 10 * DAY);
		}
		if (start.after(end))
		{
			start = end;
		}
		return new Date[] { start, end };
	}

	// ------------------------------------------------------------ consultas

	protected List<StockLevelModel> seats(final String gameCode)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {StockLevel} WHERE {productCode} LIKE ?p ORDER BY {productCode}");
		query.addQueryParameter("p", gameCode + "-%");
		return flexibleSearchService.<StockLevelModel> search(query).getResult();
	}

	/** Poltrona vendida -> item do pedido mais recente (pedidos cancelados ficam de fora) */
	protected Map<String, AbstractOrderEntryModel> storeSales(final String gameCode)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {e.pk} FROM {OrderEntry AS e JOIN Order AS o ON {e.order}={o.pk} JOIN Product AS p ON {e.product}={p.pk}}"
						+ " WHERE {p.code} LIKE ?p AND {o.versionID} IS NULL");
		query.addQueryParameter("p", gameCode + "-%");
		final Map<String, AbstractOrderEntryModel> sales = new HashMap<>();
		final Map<String, AbstractOrderEntryModel> lines = new HashMap<>();
		for (final AbstractOrderEntryModel entry : flexibleSearchService.<AbstractOrderEntryModel> search(query).getResult())
		{
			final AbstractOrderModel order = entry.getOrder();
			if (order == null || order.getStatus() == OrderStatus.CANCELLED || entry.getProduct() == null)
			{
				continue;
			}
			lines.put(order.getCode() + "-" + entry.getEntryNumber(), entry);
			final AbstractOrderEntryModel current = sales.get(entry.getProduct().getCode());
			if (current == null || date(order).after(date(current.getOrder())))
			{
				sales.put(entry.getProduct().getCode(), entry);
			}
		}
		movedSeats(gameCode, sales, lines);
		return sales;
	}

	/**
	 * Item 10: poltrona trocada pelo gestor de jogos. A venda passa para a poltrona nova (senão
	 * ela apareceria como venda simulada) e sai da antiga, que voltou para a venda.
	 */
	protected void movedSeats(final String gameCode, final Map<String, AbstractOrderEntryModel> sales,
			final Map<String, AbstractOrderEntryModel> lines)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {t.pk} FROM {MatchdayTicket AS t JOIN MatchdayGame AS g ON {t.game}={g.pk}} WHERE {g.code}=?g AND {t.status}<>?c");
		query.addQueryParameter("g", gameCode);
		query.addQueryParameter("c", MatchdayTicketStatus.CANCELADO);
		for (final MatchdayTicketModel ticket : flexibleSearchService.<MatchdayTicketModel> search(query).getResult())
		{
			final AbstractOrderEntryModel entry = lines.get(ticket.getCode());
			if (entry == null || ticket.getSeat() == null || ticket.getSeat().getCode().equals(entry.getProduct().getCode()))
			{
				continue;
			}
			sales.remove(entry.getProduct().getCode(), entry);
			sales.put(ticket.getSeat().getCode(), entry);
		}
	}

	// ------------------------------------------------------------ apoio

	protected Double sectorPriceOf(final MatchdayGameModel game, final String sector)
	{
		switch (sector)
		{
			case MatchdayGameService.SECTOR_NORTE:
				return game.getPriceNorte();
			case MatchdayGameService.SECTOR_SUL:
				return game.getPriceSul();
			case MatchdayGameService.SECTOR_LESTE:
				return game.getPriceLeste();
			case MatchdayGameService.SECTOR_OESTE:
				return game.getPriceOeste();
			default:
				return null;
		}
	}

	protected double listPrice(final MatchdayGameModel game, final String sector, final Map<String, Double> templatePrices)
	{
		final Double price = sectorPriceOf(game, sector);
		if (price != null)
		{
			return price.doubleValue();
		}
		final Double template = templatePrices == null ? null : templatePrices.get(sector);
		return template == null ? 0 : template.doubleValue();
	}

	protected static Date date(final AbstractOrderModel order)
	{
		final Date date = order.getDate() != null ? order.getDate() : order.getCreationtime();
		return date == null ? new Date(0) : date;
	}

	protected static LocalDate day(final Date date)
	{
		return date.toInstant().atZone(ZONE).toLocalDate();
	}

	/** Número estável de 0 a mod-1 a partir do texto (mesma poltrona, mesmo resultado) */
	protected static int bucket(final String text, final int mod)
	{
		int h = text.hashCode();
		h ^= h >>> 16;
		h *= 0x85ebca6b;
		h ^= h >>> 13;
		h *= 0xc2b2ae35;
		h ^= h >>> 16;
		return Math.floorMod(h, mod);
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setMatchdayGameService(final MatchdayGameService matchdayGameService)
	{
		this.matchdayGameService = matchdayGameService;
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
