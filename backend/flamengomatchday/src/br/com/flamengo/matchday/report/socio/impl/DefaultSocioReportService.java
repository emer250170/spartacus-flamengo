package br.com.flamengo.matchday.report.socio.impl;

import br.com.flamengo.matchday.enums.SocioPlanStatus;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.report.socio.SocioReport;
import br.com.flamengo.matchday.report.socio.SocioReportService;
import br.com.flamengo.matchday.socio.SocioPlanService;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Monta o painel do sócio-torcedor.
 * <p>
 * Base simulada (demonstração): cada plano tem "titulares simulados" no cadastro
 * (demoBase). A partir dela saem, de forma determinística (mesmo resultado no
 * mesmo dia), convidados, adimplência, adesões de hoje e do mês, receita, churn,
 * cupons e estados, nas proporções do painel real do clube. Somam-se os dados
 * reais da loja: sócios no grupo do plano, pedidos com plano (adesões e receita) e
 * carrinhos com plano sem pedido (abandono).
 */
public class DefaultSocioReportService implements SocioReportService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSocioReportService.class);
	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
	/** Assunto dos chamados de cancelamento abertos na loja (SocioAccountService no Spartacus) */
	private static final String CANCEL_SUBJECT = "Cancelamento do plano";
	/** Quantos pedidos de cancelamento listar no painel */
	private static final int CANCEL_LIST = 10;
	/** Motivos do formulário de cancelamento da loja (pt e en) → chave */
	private static final Map<String, String> CANCEL_REASONS = new HashMap<>();
	static
	{
		CANCEL_REASONS.put("o valor ficou alto", "price");
		CANCEL_REASONS.put("it got too expensive", "price");
		CANCEL_REASONS.put("não estou indo aos jogos", "games");
		CANCEL_REASONS.put("i am not going to the games", "games");
		CANCEL_REASONS.put("vou me mudar", "moving");
		CANCEL_REASONS.put("i am moving", "moving");
		CANCEL_REASONS.put("insatisfeito com o atendimento", "service");
		CANCEL_REASONS.put("unhappy with the service", "service");
		CANCEL_REASONS.put("outro motivo", "other");
		CANCEL_REASONS.put("other reason", "other");
	}
	/** Convidados por titular, pelo máximo de convidados do plano (proporções do painel do clube) */
	private static final double[] GUEST_FILL = { 0, 0.18, 0.685, 0.873 };
	/** Peso de cada hora do dia nas adesões (pico de manhã, com campanha às 9h, e à noite) */
	private static final double[] HOUR_WEIGHT = { 0.4, 0.2, 0.1, 0.1, 0.1, 0.2, 0.6, 1.4, 2.6, 6.5, 3.6, 3.2, 2.8, 2.6, 2.7, 2.9, 3.0,
			3.1, 3.4, 3.9, 4.2, 3.6, 2.4, 1.2 };
	private static final String[] COUPONS = { "SEMFRONTEIRAS130", "NACAO10", "VOLTAMARACA", "MENGO2026", "FLAPRIMEIRAONDA",
			"MARACA130", "NSFANUAL", "MENGOBRASIL", "NACAOFAMILIA", "BLACKNACAO", "NACAOJOVEM", "CAMISA10" };
	private static final double[] COUPON_WEIGHT = { 0.115, 0.06, 0.058, 0.055, 0.051, 0.047, 0.043, 0.042, 0.035, 0.033, 0.032, 0.03 };
	private static final String[] UF = { "RJ", "SP", "MG", "DF", "SC", "BA", "PR", "RS", "MS", "PE" };
	private static final double[] UF_WEIGHT = { 10, 7, 5, 4, 4, 3, 3, 3, 2, 2 };
	/** Parte das adesões que usa cupom */
	private static final double COUPON_SHARE = 0.42;

	private SocioPlanService socioPlanService;
	private FlexibleSearchService flexibleSearchService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;

	@Override
	public SocioReport getReport(final Date now, final Locale locale)
	{
		final Date reference = now == null ? new Date() : now;
		return sessionService.executeInLocalView(new SessionExecutionBody()
		{
			@Override
			public Object execute()
			{
				searchRestrictionService.disableSearchRestrictions();
				return build(reference, locale == null ? new Locale("pt") : locale);
			}
		});
	}

	/** Números de um plano no painel */
	protected static class PlanNumbers
	{
		SocioPlanModel plan;
		String name;
		String family;
		int wave;
		int holders;
		int guests;
		double price;
		double guestPrice;
		int lastMonth;
		int month;
		int today;
		int abandoned;
		double churn;
		double churnVsAverage;
		/** Sócios reais da loja com os benefícios suspensos (inadimplentes) */
		int suspended;
	}

	protected SocioReport build(final Date now, final Locale locale)
	{
		final SocioReport report = new SocioReport();
		report.generatedAt = now;
		final LocalDateTime local = LocalDateTime.ofInstant(now.toInstant(), ZONE);
		final LocalDate today = local.toLocalDate();
		final String seed = today.toString();
		report.dayOfMonth = today.getDayOfMonth();
		report.daysInMonth = today.lengthOfMonth();
		report.currentHour = local.getHour();
		final double dayFraction = (local.getHour() + local.getMinute() / 60.0) / 24.0;
		final double profileSoFar = profileUntil(local.getHour(), local.getMinute());

		final List<PlanNumbers> plans = new ArrayList<>();
		for (final SocioPlanModel plan : socioPlanService.getPlans())
		{
			final int real = socioPlanService.countMembers(plan);
			final int demo = plan.getDemoBase() == null ? 0 : plan.getDemoBase().intValue();
			if (demo <= 0 && real <= 0 && plan.getStatus() != SocioPlanStatus.ON_SALE)
			{
				continue;
			}
			final PlanNumbers n = new PlanNumbers();
			n.plan = plan;
			n.name = StringUtils.defaultIfBlank(plan.getName(locale), StringUtils.defaultIfBlank(plan.getName(new Locale("pt")), plan.getCode()));
			n.family = StringUtils.defaultIfBlank(plan.getFamily(), "Nação Maraca");
			n.wave = plan.getWave() == null ? 0 : Math.max(0, plan.getWave().intValue());
			n.holders = demo + real;
			n.suspended = Math.min(real, socioPlanService.countSuspended(plan));
			final int maxGuests = plan.getMaxGuests() == null ? 0 : plan.getMaxGuests().intValue();
			n.guests = (int) Math.round(demo * maxGuests * GUEST_FILL[Math.min(maxGuests, GUEST_FILL.length - 1)]);
			n.price = plan.getMonthlyPrice() == null ? 0 : plan.getMonthlyPrice().doubleValue();
			n.guestPrice = plan.getGuestPrice() == null ? 0 : plan.getGuestPrice().doubleValue();
			// adesões simuladas: taxa mensal de 3% a 23% da base, mês atual com tendência de 30% a 110% do anterior
			final double monthlyRate = 0.03 + bucket(plan.getCode() + "#taxa", 200) / 1000.0;
			final double trend = 0.3 + bucket(plan.getCode() + "#tendencia#" + today.getMonthValue(), 80) / 100.0;
			n.lastMonth = (int) Math.round(demo * monthlyRate);
			n.month = (int) Math.round(n.lastMonth * (report.dayOfMonth - 1 + dayFraction) / report.daysInMonth * trend);
			final double perDay = n.lastMonth * trend / report.daysInMonth;
			n.today = (int) Math.round(perDay * profileSoFar * (0.75 + bucket(plan.getCode() + "#hoje#" + seed, 50) / 100.0));
			n.month = Math.max(n.month, n.today);
			n.abandoned = demo == 0 ? 0 : (int) Math.round(demo * 0.0018) + bucket(plan.getCode() + "#abandono#" + seed, 10);
			n.churn = 0.02 + bucket(plan.getCode() + "#churn#" + today.getMonthValue(), 190) / 1000.0;
			n.churnVsAverage = (bucket(plan.getCode() + "#media#" + today.getMonthValue(), 40) - 10) / 10.0;
			report.realMembers += real;
			plans.add(n);
		}
		final RealData real = addRealStoreData(report, plans, local);

		// ---------------- cartões
		final Map<String, SocioReport.Family> families = new LinkedHashMap<>();
		final Map<String, Integer> realOverdue = new LinkedHashMap<>();
		int churnedToday = 0;
		for (final PlanNumbers n : plans)
		{
			report.contracts += n.holders;
			report.activeTotal += n.holders + n.guests;
			final SocioReport.Family family = families.computeIfAbsent(n.family, name -> {
				final SocioReport.Family f = new SocioReport.Family();
				f.name = name;
				return f;
			});
			family.contracts += n.holders;
			realOverdue.merge(n.family, n.suspended, Integer::sum);
			final int churned = (int) Math.round(n.holders * n.churn / report.daysInMonth * profileSoFar);
			churnedToday += churned;
			family.delta += n.today - churned; // ajustado para fração abaixo
		}
		for (final SocioReport.Family family : families.values())
		{
			final boolean borderless = family.name.toLowerCase(Locale.ROOT).contains("fronteira");
			final double upToDate = borderless ? 0.767 : 0.868;
			final double overdue = borderless ? 0.228 : 0.127;
			// base simulada pelas taxas + suspensos reais da loja (sempre inadimplentes)
			final int suspended = realOverdue.getOrDefault(family.name, 0);
			final int simulated = Math.max(0, family.contracts - suspended);
			family.upToDate = (int) Math.round(simulated * upToDate);
			family.overdue = (int) Math.round(simulated * overdue) + suspended;
			family.notRenewed = Math.max(0, family.contracts - family.upToDate - family.overdue);
			family.inactive = (int) Math.round(family.contracts * (borderless ? 0.359 : 0.368));
			family.delta = family.contracts == 0 ? 0 : family.delta / family.contracts;
			report.families.add(family);
		}

		// ---------------- novos contratos de hoje
		for (final PlanNumbers n : plans)
		{
			report.joins += n.today;
		}
		report.renewals = (int) Math.round(report.joins * 1.53);
		report.upgrades = (int) Math.round(report.joins * 0.03);
		final int newToday = report.joins + report.renewals + report.upgrades;
		report.contractsDelta = report.contracts == 0 ? 0 : (report.joins - churnedToday) / (double) report.contracts;
		report.activeTotalDelta = report.activeTotal == 0 ? 0
				: (report.joins - churnedToday) * (report.activeTotal / (double) Math.max(1, report.contracts)) / report.activeTotal;
		for (final SocioReport.Family family : report.families)
		{
			final SocioReport.Hourly hourly = new SocioReport.Hourly();
			hourly.family = family.name;
			int familyToday = 0;
			for (final PlanNumbers n : plans)
			{
				if (n.family.equals(family.name))
				{
					familyToday += n.today;
				}
			}
			final int familyNew = report.joins == 0 ? 0 : (int) Math.round(newToday * familyToday / (double) report.joins);
			distributeByHour(hourly, familyNew, local, seed + family.name);
			report.hourly.add(hourly);
		}

		// ---------------- receita do mês
		double forecast = 0;
		for (final PlanNumbers n : plans)
		{
			forecast += n.holders * n.price + n.guests * n.guestPrice;
		}
		final double done = collected((report.dayOfMonth - 1 + dayFraction) / report.daysInMonth);
		final double paid = forecast * done + real.revenue;
		report.revenueForecast = Math.max(forecast + real.revenue, paid);
		report.revenuePaid = paid;
		report.revenueFuture = Math.max(0, report.revenueForecast - paid);
		final double[] perDay = new double[report.dayOfMonth];
		double sum = 0;
		for (int day = 1; day <= report.dayOfMonth; day++)
		{
			final double from = collected((day - 1) / (double) report.daysInMonth);
			final double to = day == report.dayOfMonth ? done : collected(day / (double) report.daysInMonth);
			perDay[day - 1] = Math.max(0, (to - from) * forecast * (0.85 + bucket("receita#" + today.withDayOfMonth(day), 30) / 100.0));
			sum += perDay[day - 1];
		}
		for (int day = 1; day <= report.daysInMonth; day++)
		{
			report.revenueByDay.add(day <= report.dayOfMonth ? Double.valueOf(sum == 0 ? 0 : perDay[day - 1] * paid / sum) : null);
		}

		// ---------------- base por onda e por plano
		final Map<Integer, SocioReport.Base> waves = new LinkedHashMap<>();
		for (final int wave : new int[] { 1, 2, 3, 0 })
		{
			for (final PlanNumbers n : plans)
			{
				if (n.wave == wave)
				{
					final SocioReport.Base base = waves.computeIfAbsent(Integer.valueOf(wave), w -> {
						final SocioReport.Base b = new SocioReport.Base();
						b.label = String.valueOf(w);
						return b;
					});
					base.holders += n.holders;
					base.guests += n.guests;
				}
			}
		}
		report.byWave.addAll(waves.values());
		for (final PlanNumbers n : plans)
		{
			final SocioReport.Base base = new SocioReport.Base();
			base.label = n.name;
			base.holders = n.holders;
			base.guests = n.guests;
			report.byPlan.add(base);

			final SocioReport.Churn churn = new SocioReport.Churn();
			churn.plan = n.name;
			churn.rate = n.churn;
			churn.vsAverage = n.churnVsAverage;
			report.churn.add(churn);

			final SocioReport.Join join = new SocioReport.Join();
			join.plan = n.name;
			join.abandoned = n.abandoned;
			join.today = n.today;
			join.month = n.month;
			join.lastMonth = n.lastMonth;
			report.joinsByPlan.add(join);
		}

		// ---------------- cupons e estados
		int monthJoins = 0;
		int borderlessMonth = 0;
		for (final PlanNumbers n : plans)
		{
			monthJoins += n.month;
			if (n.family.toLowerCase(Locale.ROOT).contains("fronteira"))
			{
				borderlessMonth += n.month;
			}
		}
		// n.month já inclui as adesões reais; a parte simulada é o restante, e as reais entram pelo estado de entrega
		final int simulatedBorderlessMonth = Math.max(0, borderlessMonth - real.borderlessJoins);
		for (int i = 0; i < COUPONS.length; i++)
		{
			final SocioReport.Coupon coupon = new SocioReport.Coupon();
			coupon.code = COUPONS[i];
			coupon.month = (int) Math.round(monthJoins * COUPON_SHARE * COUPON_WEIGHT[i]);
			coupon.today = (int) Math.round(report.joins * COUPON_SHARE * COUPON_WEIGHT[i]
					* (0.5 + bucket(COUPONS[i] + "#hoje#" + seed, 100) / 100.0));
			coupon.total = coupon.month * (3 + bucket(COUPONS[i] + "#total", 10));
			if (coupon.month > 0 || coupon.today > 0)
			{
				report.coupons.add(coupon);
			}
		}
		report.coupons.sort((a, b) -> Integer.compare(b.month, a.month));
		double ufWeights = 0;
		for (final double weight : UF_WEIGHT)
		{
			ufWeights += weight;
		}
		for (int i = 0; i < UF.length; i++)
		{
			final SocioReport.State state = new SocioReport.State();
			state.uf = UF[i];
			state.count = (int) Math.round(simulatedBorderlessMonth * UF_WEIGHT[i] / ufWeights
					* (0.8 + bucket(UF[i] + "#mes#" + today.getYear() + "-" + today.getMonthValue(), 40) / 100.0)) + real.states.getOrDefault(UF[i], Integer.valueOf(0)).intValue();
			if (state.count > 0)
			{
				report.states.add(state);
			}
		}
		// adesão real de um estado fora da lista simulada também aparece
		final Set<String> listed = new HashSet<>(Arrays.asList(UF));
		for (final Map.Entry<String, Integer> entry : real.states.entrySet())
		{
			if (!listed.contains(entry.getKey()))
			{
				final SocioReport.State state = new SocioReport.State();
				state.uf = entry.getKey();
				state.count = entry.getValue().intValue();
				report.states.add(state);
			}
		}
		report.states.sort((a, b) -> Integer.compare(b.count, a.count));
		addCancellations(report, local);
		return report;
	}

	// ------------------------------------------------------------ cancelamentos (chamados de atendimento)

	/**
	 * Pedidos de cancelamento feitos na loja: chamados "Cancelamento do plano X"
	 * (sistema de tickets). A 1ª mensagem do chamado traz o motivo
	 * ("Motivo: ..."), o comentário opcional e o plano. Se o sistema de tickets
	 * não responder, o painel segue sem o quadro.
	 */
	protected void addCancellations(final SocioReport report, final LocalDateTime now)
	{
		try
		{
			final FlexibleSearchQuery tickets = new FlexibleSearchQuery(
					"SELECT {t.ticketID}, {t.headline}, {t.creationtime}, {s.code}, {u.uid}, {u.name} FROM {CsTicket AS t"
							+ " JOIN CsTicketState AS s ON {t.state}={s.pk} LEFT JOIN User AS u ON {t.customer}={u.pk}}"
							+ " WHERE {t.headline} LIKE ?subject ORDER BY {t.creationtime} DESC");
			tickets.addQueryParameter("subject", CANCEL_SUBJECT + "%");
			tickets.setResultClassList(Arrays.asList(String.class, String.class, Date.class, String.class, String.class, String.class));
			final Date monthStart = toDate(now.toLocalDate().withDayOfMonth(1));
			final Map<String, Integer> reasons = new HashMap<>();
			for (final List<Object> row : flexibleSearchService.<List<Object>> search(tickets).getResult())
			{
				final SocioReport.Cancellation c = new SocioReport.Cancellation();
				c.ticketId = (String) row.get(0);
				c.plan = StringUtils.trimToNull(StringUtils.removeStart(StringUtils.defaultString((String) row.get(1)), CANCEL_SUBJECT));
				c.created = (Date) row.get(2);
				c.open = !"closed".equalsIgnoreCase((String) row.get(3));
				c.customerUid = (String) row.get(4);
				c.customerName = StringUtils.defaultIfBlank((String) row.get(5), c.customerUid);
				readCancelMessage(c);

				report.cancelTotal++;
				if (c.open)
				{
					report.cancelOpen++;
				}
				if (c.created != null && !c.created.before(monthStart))
				{
					report.cancelMonth++;
				}
				final String reasonKey = c.reasonKey != null ? c.reasonKey : StringUtils.defaultIfBlank(c.reason, "other");
				reasons.merge(reasonKey, Integer.valueOf(1), (a, b) -> Integer.valueOf(a.intValue() + b.intValue()));
				if (report.cancellations.size() < CANCEL_LIST)
				{
					report.cancellations.add(c);
				}
			}
			reasons.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue().intValue(), a.getValue().intValue()))
					.forEach(e -> report.cancelReasons.put(e.getKey(), e.getValue()));
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Relatório do sócio: não foi possível ler os pedidos de cancelamento: {}", e.getMessage());
		}
	}

	/** Motivo, comentário e plano a partir da 1ª mensagem do chamado */
	protected void readCancelMessage(final SocioReport.Cancellation c)
	{
		final FlexibleSearchQuery events = new FlexibleSearchQuery(
				"SELECT {e.text} FROM {CsTicketEvent AS e JOIN CommentItemRelation AS r ON {r.source}={e.pk}"
						+ " JOIN CsTicket AS t ON {r.target}={t.pk}} WHERE {t.ticketID}=?id"
						+ " ORDER BY {e.creationtime} ASC");
		events.addQueryParameter("id", c.ticketId);
		events.setResultClassList(Arrays.asList(String.class));
		events.setCount(1);
		final List<String> texts = flexibleSearchService.<String> search(events).getResult();
		final String text = texts.isEmpty() ? null : texts.get(0);
		if (StringUtils.isBlank(text))
		{
			return;
		}
		final List<String> lines = new ArrayList<>();
		for (final String line : text.split("\\r?\\n"))
		{
			if (StringUtils.isNotBlank(line))
			{
				lines.add(line.trim());
			}
		}
		if (lines.isEmpty())
		{
			return;
		}
		int first = 0;
		final String head = lines.get(0);
		if (head.matches("(?i)^(motivo|reason)\\s*:.*"))
		{
			c.reason = StringUtils.trimToNull(StringUtils.substringAfter(head, ":"));
			c.reasonKey = c.reason == null ? null : CANCEL_REASONS.get(c.reason.toLowerCase(new Locale("pt")));
			first = 1;
		}
		// última linha: "Plano (CÓDIGO) · Sócio desde dd/mm/aaaa"; o que fica no meio é o comentário
		int last = lines.size();
		if (last > first && lines.get(last - 1).contains("·"))
		{
			last--;
		}
		if (last > first)
		{
			c.comment = String.join(" ", lines.subList(first, last));
		}
	}

	// ------------------------------------------------------------ dados reais da loja

	/** Dados reais da loja usados num painel */
	protected static class RealData
	{
		double revenue;
		/** adesões reais Sem Fronteiras no mês, com ou sem estado no endereço */
		int borderlessJoins;
		/** adesões reais Sem Fronteiras no mês por estado de entrega */
		final Map<String, Integer> states = new HashMap<>();
	}

	/**
	 * Pedidos com plano (adesões de hoje, do mês e do mês passado; receita do mês;
	 * estado de entrega das adesões Sem Fronteiras) e carrinhos com plano sem
	 * pedido nos últimos 30 dias (abandono).
	 */
	protected RealData addRealStoreData(final SocioReport report, final List<PlanNumbers> plans, final LocalDateTime now)
	{
		final RealData real = new RealData();
		if (plans.isEmpty())
		{
			return real;
		}
		final Map<String, PlanNumbers> byCode = new HashMap<>();
		for (final PlanNumbers n : plans)
		{
			byCode.put(n.plan.getCode(), n);
		}
		final LocalDate today = now.toLocalDate();
		final LocalDate monthStart = today.withDayOfMonth(1);
		final LocalDate lastMonthStart = monthStart.minusMonths(1);

		final FlexibleSearchQuery orders = new FlexibleSearchQuery(
				"SELECT {e.pk} FROM {OrderEntry AS e JOIN Order AS o ON {e.order}={o.pk} JOIN Product AS p ON {e.product}={p.pk}}"
						+ " WHERE {o.versionID} IS NULL AND {o.date} >= ?from AND ({p.code} IN (?codes) OR {p.code} LIKE 'NACAO-CONVIDADO-%')");
		orders.addQueryParameter("from", toDate(lastMonthStart));
		orders.addQueryParameter("codes", new ArrayList<>(byCode.keySet()));
		final Set<String> countedOrders = new HashSet<>();
		for (final AbstractOrderEntryModel entry : flexibleSearchService.<AbstractOrderEntryModel> search(orders).getResult())
		{
			final AbstractOrderModel order = entry.getOrder();
			if (order == null || order.getDate() == null || order.getStatus() == OrderStatus.CANCELLED)
			{
				continue;
			}
			final LocalDate day = LocalDateTime.ofInstant(order.getDate().toInstant(), ZONE).toLocalDate();
			final String code = entry.getProduct().getCode();
			if (!day.isBefore(monthStart) && entry.getTotalPrice() != null)
			{
				real.revenue += entry.getTotalPrice().doubleValue();
			}
			final PlanNumbers n = byCode.get(code);
			if (n == null || !countedOrders.add(order.getCode() + "#" + code))
			{
				continue;
			}
			if (day.isBefore(monthStart))
			{
				n.lastMonth++;
				continue;
			}
			n.month++;
			report.realJoinsMonth++;
			if (day.equals(today))
			{
				n.today++;
			}
			// estados: adesões do mês inteiro, como diz o gráfico ("Adesões no mês")
			if (n.family.toLowerCase(Locale.ROOT).contains("fronteira"))
			{
				real.borderlessJoins++;
				final AddressModel address = order.getDeliveryAddress() != null ? order.getDeliveryAddress() : order.getPaymentAddress();
				final String uf = address == null || address.getRegion() == null ? null
						: StringUtils.substringAfterLast(StringUtils.defaultString(address.getRegion().getIsocode()), "-");
				if (StringUtils.isNotBlank(uf))
				{
					real.states.merge(uf, Integer.valueOf(1), (a, b) -> Integer.valueOf(a.intValue() + b.intValue()));
				}
			}
		}

		final FlexibleSearchQuery carts = new FlexibleSearchQuery(
				"SELECT DISTINCT {c.code}, {p.code} FROM {CartEntry AS e JOIN Cart AS c ON {e.order}={c.pk} JOIN Product AS p ON {e.product}={p.pk}"
						+ " JOIN User AS u ON {c.user}={u.pk}} WHERE {p.code} IN (?codes) AND {c.modifiedtime} >= ?since AND {u.uid} <> 'anonymous'");
		carts.addQueryParameter("codes", new ArrayList<>(byCode.keySet()));
		carts.addQueryParameter("since", toDate(today.minusDays(30)));
		carts.setResultClassList(Arrays.asList(String.class, String.class));
		for (final List<Object> row : flexibleSearchService.<List<Object>> search(carts).getResult())
		{
			final PlanNumbers n = byCode.get(row.get(1));
			if (n != null)
			{
				n.abandoned++;
				report.realAbandoned++;
			}
		}
		return real;
	}

	// ------------------------------------------------------------ apoio

	/** Espalha os novos contratos de hoje pelas horas até agora, com o perfil do dia */
	protected void distributeByHour(final SocioReport.Hourly hourly, final int total, final LocalDateTime now, final String seed)
	{
		final int hour = now.getHour();
		final double[] weights = new double[24];
		double sum = 0;
		for (int h = 0; h <= hour; h++)
		{
			weights[h] = HOUR_WEIGHT[h] * (h == hour ? Math.max(0.05, now.getMinute() / 60.0) : 1)
					* (0.7 + bucket(seed + "#" + h, 60) / 100.0);
			sum += weights[h];
		}
		int assigned = 0;
		for (int h = 0; h <= hour; h++)
		{
			hourly.perHour[h] = sum == 0 ? 0 : (int) Math.floor(total * weights[h] / sum);
			assigned += hourly.perHour[h];
		}
		// o que sobrou do arredondamento vai para a hora de maior peso
		int top = 0;
		for (int h = 1; h <= hour; h++)
		{
			if (weights[h] > weights[top])
			{
				top = h;
			}
		}
		hourly.perHour[top] += Math.max(0, total - assigned);
		hourly.total = total;
	}

	/** Parte do dia (pelo perfil das horas) que já passou */
	protected static double profileUntil(final int hour, final int minute)
	{
		double sum = 0;
		double done = 0;
		for (int h = 0; h < 24; h++)
		{
			sum += HOUR_WEIGHT[h];
			if (h < hour)
			{
				done += HOUR_WEIGHT[h];
			}
			else if (h == hour)
			{
				done += HOUR_WEIGHT[h] * minute / 60.0;
			}
		}
		return done / sum;
	}

	/** Parte da receita do mês já recebida: as mensalidades caem mais no começo do mês */
	protected static double collected(final double monthFraction)
	{
		final double x = Math.min(1, Math.max(0, monthFraction));
		return 1 - Math.pow(1 - x, 2.5);
	}

	protected static Date toDate(final LocalDate day)
	{
		return Date.from(day.atStartOfDay(ZONE).toInstant());
	}

	/** Número estável de 0 a mod-1 a partir do texto */
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

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
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
