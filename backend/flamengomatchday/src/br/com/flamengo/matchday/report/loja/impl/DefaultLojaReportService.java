package br.com.flamengo.matchday.report.loja.impl;

import br.com.flamengo.matchday.estudio.MengoEstudioCartService;
import br.com.flamengo.matchday.report.loja.LojaReport;
import br.com.flamengo.matchday.report.loja.LojaReport.Line;
import br.com.flamengo.matchday.report.loja.LojaReport.Point;
import br.com.flamengo.matchday.report.loja.LojaReportService;
import br.com.flamengo.matchday.report.summary.SummaryReport;
import br.com.flamengo.matchday.report.summary.SummaryReportService;
import de.hybris.platform.core.HybrisEnumValue;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.CreditCardPaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.promotions.model.AbstractPromotionActionModel;
import de.hybris.platform.promotions.model.PromotionResultModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.variants.model.VariantProductModel;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Relatório de vendas da Loja Flamengo (08/11). Mesmas regras e tipos do resumo de vendas do administrador
 * ({@code DefaultSummaryReportService}): pedidos do site, sem cancelados e recusados na análise de fraude; os que
 * aguardam Pix ou boleto ficam à parte. Camisetas do Mengo Estúdio viram um tipo próprio. Produtos mais vendidos:
 * produtos da loja somados pelo produto base (tamanhos juntos). Promoções: só as que valeram (certeza 100%), com o
 * desconto das ações.
 */
public class DefaultLojaReportService implements LojaReportService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultLojaReportService.class);
	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
	private static final String SITE = "electronics-spa";
	private static final Locale PT = new Locale("pt");
	private static final int TOP_PRODUCTS = 10;
	private static final int TOP_PROMOTIONS = 8;
	private static final Set<OrderStatus> EXCLUDED = new HashSet<>(Arrays.asList(OrderStatus.CANCELLED, OrderStatus.CHECKED_INVALID));
	private static final List<String> CATEGORY_ORDER = Arrays.asList(SummaryReportService.STORE, ESTUDIO,
			SummaryReportService.EXPERIENCES, SummaryReportService.CUSTOMIZATION, SummaryReportService.TICKETS,
			SummaryReportService.MEMBERSHIP, SummaryReportService.PARKING, SummaryReportService.FOOD, SummaryReportService.FREIGHT,
			DISCOUNTS);
	private static final Map<String, String> PREFIXES = new LinkedHashMap<>();

	static
	{
		PREFIXES.put("JOGO-", SummaryReportService.TICKETS);
		PREFIXES.put("NACAO-", SummaryReportService.MEMBERSHIP);
		PREFIXES.put("EXP-", SummaryReportService.EXPERIENCES);
		PREFIXES.put("FLA-EST-", SummaryReportService.PARKING);
		PREFIXES.put("FLA-FOOD-", SummaryReportService.FOOD);
		PREFIXES.put("FLA-PERSONALIZACAO", SummaryReportService.CUSTOMIZATION);
		PREFIXES.put("FLA-PERS-", SummaryReportService.CUSTOMIZATION);
	}

	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;
	private MengoEstudioCartService mengoEstudioCartService;

	@Override
	public LojaReport getReport(final SummaryReport.Period period, final Date now)
	{
		final Date reference = now == null ? new Date() : now;
		final SummaryReport.Period chosen = period == null ? SummaryReport.Period.MONTH : period;
		return unrestricted(() -> build(chosen, reference));
	}

	protected LojaReport build(final SummaryReport.Period period, final Date now)
	{
		final LojaReport report = new LojaReport();
		report.period = period;
		report.generatedAt = now;
		final ZonedDateTime current = now.toInstant().atZone(ZONE);
		report.from = start(period, current);
		report.monthly = period == SummaryReport.Period.YEAR || period == SummaryReport.Period.ALL;
		final Map<String, Point> points = points(report, current);

		final Map<String, Line> categories = new LinkedHashMap<>();
		CATEGORY_ORDER.forEach(key -> categories.put(key, new Line(key, null)));
		final Map<String, Line> products = new HashMap<>();
		final Map<String, Line> payments = new LinkedHashMap<>();
		Arrays.asList(PAY_CARD, PAY_PIX, PAY_BOLETO, PAY_OTHER).forEach(key -> payments.put(key, new Line(key, null)));
		final Map<String, Line> deliveries = new LinkedHashMap<>();
		Arrays.asList(DELIVERY_HOME, DELIVERY_PICKUP, DELIVERY_DIGITAL).forEach(key -> deliveries.put(key, new Line(key, null)));
		final Map<String, Line> promotions = new HashMap<>();
		final Map<String, Line> coupons = new HashMap<>();
		final Map<String, String> ruleNames = new HashMap<>();
		final Set<String> customers = new HashSet<>();

		for (final OrderModel order : orders(report.from))
		{
			if (order.getStatus() != null && EXCLUDED.contains(order.getStatus()))
			{
				continue;
			}
			final double total = value(order.getTotalPrice());
			if (order.getStatus() == OrderStatus.PAYMENT_NOT_CAPTURED)
			{
				report.awaitingOrders++;
				report.awaitingAmount += total;
				continue;
			}
			report.orders++;
			report.revenue += total;
			if (order.getUser() != null)
			{
				customers.add(order.getUser().getUid());
			}
			final Date placed = order.getDate() != null ? order.getDate() : order.getCreationtime();
			final Point point = placed == null ? null : points.get(key(placed, report.monthly));
			if (point != null)
			{
				point.revenue += total;
				point.orders++;
			}
			entries(order, report, categories, products);
			add(payments.get(payment(order)), 1, total);
			add(deliveries.get(delivery(order)), 1, total);
			promotions(order, promotions, ruleNames);
			final Collection<String> codes = order.getAppliedCouponCodes();
			if (codes != null)
			{
				for (final String code : new HashSet<>(codes))
				{
					add(coupons.computeIfAbsent(code, c -> new Line(c, c)), 1, total);
				}
			}
		}
		report.customers = customers.size();
		final Line discounts = categories.get(DISCOUNTS);
		report.discounts = -discounts.amount;
		report.series.addAll(points.values());
		report.categories.addAll(categories.values().stream()
				.filter(line -> !DISCOUNTS.equals(line.key) || line.orders > 0).collect(Collectors.toList()));
		report.products.addAll(products.values().stream()
				.sorted(Comparator.comparingLong((Line l) -> l.quantity).reversed().thenComparing(l -> -l.amount)).limit(TOP_PRODUCTS)
				.collect(Collectors.toList()));
		report.payments.addAll(payments.values().stream().filter(l -> l.orders > 0 || !PAY_OTHER.equals(l.key)).collect(Collectors.toList()));
		report.deliveries.addAll(deliveries.values());
		report.promotions.addAll(promotions.values().stream().sorted(Comparator.comparingDouble((Line l) -> l.amount).reversed())
				.limit(TOP_PROMOTIONS).collect(Collectors.toList()));
		report.coupons.addAll(coupons.values().stream().sorted(Comparator.comparingInt((Line l) -> l.orders).reversed())
				.limit(TOP_PROMOTIONS).collect(Collectors.toList()));
		return report;
	}

	/** Linhas do pedido por tipo; frete e a diferença (descontos do pedido) como linhas próprias */
	protected void entries(final OrderModel order, final LojaReport report, final Map<String, Line> categories,
			final Map<String, Line> products)
	{
		double lines = 0;
		final Set<String> seen = new HashSet<>();
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final long qty = entry.getQuantity() == null ? 0 : entry.getQuantity().longValue();
			if (qty <= 0 || entry.getProduct() == null)
			{
				continue;
			}
			final String type = category(entry);
			final Line line = categories.get(type);
			final double price = value(entry.getTotalPrice());
			line.quantity += qty;
			line.amount += price;
			lines += price;
			if (seen.add(type))
			{
				line.orders++;
			}
			if (SummaryReportService.STORE.equals(type) || ESTUDIO.equals(type))
			{
				report.storeUnits += qty;
			}
			if (SummaryReportService.STORE.equals(type))
			{
				final ProductModel base = base(entry.getProduct());
				final Line product = products.computeIfAbsent(base.getCode(), code -> new Line(code, name(base)));
				product.quantity += qty;
				product.amount += price;
				product.orders++;
			}
		}
		final double freight = value(order.getDeliveryCost());
		if (freight > 0)
		{
			add(categories.get(SummaryReportService.FREIGHT), 1, freight);
		}
		final double difference = value(order.getTotalPrice()) - lines - freight;
		if (Math.abs(difference) >= 0.005)
		{
			final Line line = categories.get(DISCOUNTS);
			line.amount += difference;
			line.orders++;
		}
	}

	protected String category(final AbstractOrderEntryModel entry)
	{
		if (mengoEstudioCartService != null && mengoEstudioCartService.isEstudioEntry(entry))
		{
			return ESTUDIO;
		}
		final String code = StringUtils.defaultString(entry.getProduct().getCode());
		for (final Map.Entry<String, String> prefix : PREFIXES.entrySet())
		{
			if (code.startsWith(prefix.getKey()))
			{
				return prefix.getValue();
			}
		}
		return SummaryReportService.STORE;
	}

	/** Mesma regra do atributo "analise" da Integration API (analise-pedido.groovy) */
	protected String payment(final OrderModel order)
	{
		final String socio = code(attr(order, "socioPaymentMethod"));
		final String adyen = StringUtils.defaultString(StringUtils.lowerCase(String.valueOf(attr(order, "adyenPaymentMethod"))));
		final String info = order.getPaymentInfo() == null ? "" : StringUtils.lowerCase(StringUtils.defaultString(order.getPaymentInfo().getCode()));
		if ("PIX".equals(socio) || "PIX_AUTOMATICO".equals(socio) || adyen.startsWith("pix") || info.startsWith("pix"))
		{
			return PAY_PIX;
		}
		if ("BOLETO".equals(socio) || adyen.contains("boleto") || info.startsWith("boleto"))
		{
			return PAY_BOLETO;
		}
		if ("CARTAO_MENSAL".equals(socio) || "CARTAO_ANUAL".equals(socio) || "scheme".equals(adyen)
				|| order.getPaymentInfo() instanceof CreditCardPaymentInfoModel)
		{
			return PAY_CARD;
		}
		return PAY_OTHER;
	}

	/** Retirada na loja (itens com loja), digital (ingresso, plano, experiência) ou entrega em casa */
	protected String delivery(final OrderModel order)
	{
		final String mode = order.getDeliveryMode() == null ? "" : StringUtils.defaultString(order.getDeliveryMode().getCode());
		if ("pickup".equals(mode) || order.getEntries().stream().anyMatch(e -> e.getDeliveryPointOfService() != null))
		{
			return DELIVERY_PICKUP;
		}
		if (mode.startsWith("matchday") || mode.isEmpty())
		{
			return DELIVERY_DIGITAL;
		}
		return DELIVERY_HOME;
	}

	protected void promotions(final OrderModel order, final Map<String, Line> promotions, final Map<String, String> ruleNames)
	{
		try
		{
			final Set<PromotionResultModel> results = order.getAllPromotionResults();
			if (results == null)
			{
				return;
			}
			for (final PromotionResultModel result : results)
			{
				if (result.getPromotion() == null || result.getCertainty() != null && result.getCertainty().floatValue() < 1.0f)
				{
					continue;
				}
				double amount = 0;
				if (result.getActions() != null)
				{
					for (final AbstractPromotionActionModel action : result.getActions())
					{
						final Object value = attr(action, "amount");
						amount += value instanceof Number ? ((Number) value).doubleValue() : 0;
					}
				}
				final String code = result.getPromotion().getCode();
				final Line line = promotions.computeIfAbsent(code,
						c -> new Line(c, ruleNames.computeIfAbsent(c, this::ruleName)));
				line.orders++;
				line.amount += amount;
			}
		}
		catch (final RuntimeException e)
		{
			LOG.debug("Pedido {}: promoções não lidas: {}", order.getCode(), e.getMessage());
		}
	}

	/** Nome da regra no construtor de promoções (PromotionSourceRule); sem nome, o código */
	protected String ruleName(final String code)
	{
		try
		{
			final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {PromotionSourceRule} WHERE {code}=?c");
			q.addQueryParameter("c", code);
			final List<ItemModel> rules = flexibleSearchService.<ItemModel> search(q).getResult();
			if (!rules.isEmpty())
			{
				final Object name = modelService.getAttributeValue(rules.get(0), "name", PT);
				if (name != null && StringUtils.isNotBlank(name.toString()))
				{
					return name.toString();
				}
			}
		}
		catch (final RuntimeException e)
		{
			LOG.debug("Promoção {}: nome não lido: {}", code, e.getMessage());
		}
		return code;
	}

	/** Pontos vazios do gráfico: dias do período (mês, 30 dias) ou meses (ano: janeiro até hoje; tudo: 12 meses) */
	protected Map<String, Point> points(final LojaReport report, final ZonedDateTime now)
	{
		final Map<String, Point> points = new LinkedHashMap<>();
		if (report.monthly)
		{
			final YearMonth last = YearMonth.from(now);
			YearMonth first = report.period == SummaryReport.Period.YEAR ? YearMonth.of(now.getYear(), 1) : last.minusMonths(11);
			for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1))
			{
				final Date start = Date.from(m.atDay(1).atStartOfDay(ZONE).toInstant());
				points.put(key(start, true), new Point(start));
			}
		}
		else
		{
			final LocalDate last = now.toLocalDate();
			for (LocalDate d = report.from.toInstant().atZone(ZONE).toLocalDate(); !d.isAfter(last); d = d.plusDays(1))
			{
				final Date start = Date.from(d.atStartOfDay(ZONE).toInstant());
				points.put(key(start, false), new Point(start));
			}
		}
		return points;
	}

	protected String key(final Date date, final boolean monthly)
	{
		final LocalDate day = date.toInstant().atZone(ZONE).toLocalDate();
		return monthly ? YearMonth.from(day).toString() : day.toString();
	}

	protected Date start(final SummaryReport.Period period, final ZonedDateTime now)
	{
		final LocalDate today = now.toLocalDate();
		switch (period)
		{
			case LAST_30_DAYS:
				return Date.from(today.minusDays(29).atStartOfDay(ZONE).toInstant());
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
		return new ArrayList<>(flexibleSearchService.<OrderModel> search(new FlexibleSearchQuery(q.toString(), params)).getResult());
	}

	protected ProductModel base(final ProductModel product)
	{
		ProductModel current = product;
		while (current instanceof VariantProductModel && ((VariantProductModel) current).getBaseProduct() != null)
		{
			current = ((VariantProductModel) current).getBaseProduct();
		}
		return current;
	}

	protected String name(final ProductModel product)
	{
		String name = null;
		try
		{
			name = product.getName(PT);
		}
		catch (final RuntimeException e)
		{
			name = null;
		}
		return StringUtils.defaultIfBlank(name, StringUtils.defaultIfBlank(product.getName(), product.getCode()));
	}

	protected Object attr(final ItemModel item, final String attribute)
	{
		try
		{
			return modelService.getAttributeValue(item, attribute);
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	protected static void add(final Line line, final int orders, final double amount)
	{
		if (line != null)
		{
			line.orders += orders;
			line.quantity += orders;
			line.amount += amount;
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

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setSearchRestrictionService(final SearchRestrictionService searchRestrictionService)
	{
		this.searchRestrictionService = searchRestrictionService;
	}

	public void setMengoEstudioCartService(final MengoEstudioCartService mengoEstudioCartService)
	{
		this.mengoEstudioCartService = mengoEstudioCartService;
	}
}
