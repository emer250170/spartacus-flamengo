package br.com.flamengo.matchday.socio.impl;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.model.SocioEventModel;
import br.com.flamengo.matchday.model.SocioExperienceModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.model.SocioRedemptionModel;
import br.com.flamengo.matchday.service.SeatHoldService;
import br.com.flamengo.matchday.socio.SocioExperienceService;
import br.com.flamengo.matchday.socio.SocioPaymentService;
import br.com.flamengo.matchday.socio.SocioPayments;
import br.com.flamengo.matchday.socio.SocioPlanService;
import br.com.flamengo.matchday.socio.SocioPoints;
import br.com.flamengo.matchday.socio.SocioPointsException;
import br.com.flamengo.matchday.socio.SocioPointsService;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.c2l.LanguageModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;

import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Pontos e resgates do sócio (item 8). O saldo é sempre calculado: pedidos fechados
 * desde a adesão menos os resgates não cancelados. Ver {@link SocioPointsService}.
 */
public class DefaultSocioPointsService implements SocioPointsService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSocioPointsService.class);
	private static final Locale PT = new Locale("pt");
	private static final Locale BR = new Locale("pt", "BR");
	private static final String GUEST_PREFIX = "NACAO-CONVIDADO-";
	private static final String VOUCHER_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	private ConfigurationService configurationService;
	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private SocioPlanService socioPlanService;
	private SocioPaymentService socioPaymentService;
	private SeatHoldService seatHoldService;
	private CommonI18NService commonI18NService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;
	private SocioExperienceService socioExperienceService;

	@Override
	public boolean isEnabled()
	{
		return configurationService.getConfiguration().getBoolean(ENABLED_PROPERTY, true);
	}

	protected int perTicket()
	{
		return Math.max(0, configurationService.getConfiguration().getInt(PER_TICKET_PROPERTY, 100));
	}

	protected int perReal()
	{
		return Math.max(0, configurationService.getConfiguration().getInt(PER_REAL_PROPERTY, 1));
	}

	protected int bonus()
	{
		return Math.max(0, configurationService.getConfiguration().getInt(BONUS_PROPERTY, 500));
	}

	// ------------------------------------------------------------ leitura

	@Override
	public SocioPoints status(final CustomerModel customer)
	{
		return unrestricted(() -> build(customer, sessionLocale()));
	}

	protected SocioPoints build(final CustomerModel customer, final Locale locale)
	{
		final SocioPoints p = new SocioPoints();
		p.enabled = isEnabled();
		p.perTicket = perTicket();
		p.perReal = perReal();
		p.bonus = bonus();
		if (!p.enabled || customer == null)
		{
			p.reason = "Os pontos do sócio estão desligados.";
			return p;
		}
		final SocioPlanModel plan = socioPlanService.memberPlan(customer);
		if (plan != null)
		{
			p.planCode = plan.getCode();
			p.planName = name(plan, locale);
		}
		p.since = since(customer);
		p.member = p.since != null;

		if (p.member)
		{
			earnings(customer, p);
		}
		for (final SocioRedemptionModel r : redemptions(customer))
		{
			final SocioPoints.Redemption x = new SocioPoints.Redemption();
			x.voucher = r.getVoucher();
			x.experienceCode = r.getExperience() == null ? null : r.getExperience().getCode();
			x.experienceName = r.getExperience() == null ? null : name(r.getExperience(), locale);
			x.points = r.getPoints() == null ? 0 : r.getPoints().intValue();
			x.date = r.getDate() != null ? r.getDate() : r.getCreationtime();
			x.origin = r.getOrigin();
			x.cancelledAt = r.getCancelledAt();
			x.orderCode = r.getOrderCode();
			x.amount = r.getAmount();
			x.usedAt = r.getUsedAt();
			if (x.cancelledAt == null)
			{
				p.redeemed += x.points;
			}
			p.redemptions.add(x);
		}
		p.balance = p.earned - p.redeemed;
		p.reason = blockReason(customer, plan, p.member);
		p.canRedeem = p.reason == null;

		for (final SocioExperienceModel e : experiences())
		{
			final SocioPoints.Experience x = new SocioPoints.Experience();
			x.code = e.getCode();
			x.name = name(e, locale);
			x.description = e.getDescription(locale) != null ? e.getDescription(locale) : e.getDescription(PT);
			x.points = e.getPoints() == null ? 0 : e.getPoints().intValue();
			x.available = available(e);
			if (e.getMinPlan() != null)
			{
				x.minPlanCode = e.getMinPlan().getCode();
				x.minPlanName = name(e.getMinPlan(), locale);
			}
			x.reason = experienceReason(e, x, plan, p);
			x.redeemable = x.reason == null;
			sale(e, x);
			p.experiences.add(x);
		}
		return p;
	}

	/** Pedidos fechados desde a adesão: ingressos e produtos (plano e convidados não contam) */
	protected void earnings(final CustomerModel customer, final SocioPoints p)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {Order} WHERE {user}=?c AND {versionID} IS NULL"
				+ " AND {date}>=?since ORDER BY {date} DESC");
		q.addQueryParameter("c", customer);
		q.addQueryParameter("since", startOfDay(p.since));
		for (final OrderModel order : flexibleSearchService.<OrderModel> search(q).getResult())
		{
			if (order.getStatus() == OrderStatus.CANCELLED || SocioPayments.isAwaiting(modelService, order)
					|| order.getEntries() == null)
			{
				continue;
			}
			int tickets = 0;
			double products = 0;
			for (final AbstractOrderEntryModel entry : order.getEntries())
			{
				final ProductModel product = entry.getProduct();
				if (product == null)
				{
					continue;
				}
				if (seatHoldService.isSeat(product))
				{
					tickets += entry.getQuantity() == null ? 1 : entry.getQuantity().intValue();
				}
				else if (!isPlanOrGuest(product))
				{
					products += entry.getTotalPrice() == null ? 0 : entry.getTotalPrice().doubleValue();
				}
			}
			final int points = tickets * p.perTicket + (int) Math.floor(products * p.perReal);
			if (points <= 0)
			{
				continue;
			}
			final SocioPoints.Earning e = new SocioPoints.Earning();
			e.orderCode = order.getCode();
			e.date = order.getDate();
			e.tickets = tickets;
			e.productsAmount = Math.round(products * 100) / 100.0;
			e.points = points;
			p.earnings.add(e);
			p.earned += points;
			p.tickets += tickets;
			p.productsAmount += e.productsAmount;
		}
		p.productsAmount = Math.round(p.productsAmount * 100) / 100.0;
		if (p.bonus > 0)
		{
			final SocioPoints.Earning e = new SocioPoints.Earning();
			e.date = p.since;
			e.points = p.bonus;
			p.earnings.add(e); // a adesão é a mais antiga: fim da lista
			p.earned += p.bonus;
		}
	}

	/** Primeiro pedido fechado com plano ou convidado; sem pedido, a primeira Adesão na linha do tempo */
	protected Date since(final CustomerModel customer)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {user}=?c AND {versionID} IS NULL ORDER BY {date} ASC");
		q.addQueryParameter("c", customer);
		for (final OrderModel order : flexibleSearchService.<OrderModel> search(q).getResult())
		{
			if (order.getStatus() == OrderStatus.CANCELLED || SocioPayments.isAwaiting(modelService, order)
					|| order.getEntries() == null)
			{
				continue;
			}
			if (order.getEntries().stream().anyMatch(e -> isPlanOrGuest(e.getProduct())))
			{
				return order.getDate();
			}
		}
		final FlexibleSearchQuery ev = new FlexibleSearchQuery(
				"SELECT {pk} FROM {SocioEvent} WHERE {customer}=?c AND {type}=?t ORDER BY {date} ASC");
		ev.addQueryParameter("c", customer);
		ev.addQueryParameter("t", SocioEventType.ADESAO);
		ev.setCount(1);
		final List<SocioEventModel> events = flexibleSearchService.<SocioEventModel> search(ev).getResult();
		if (events.isEmpty())
		{
			return null;
		}
		return events.get(0).getDate() != null ? events.get(0).getDate() : events.get(0).getCreationtime();
	}

	/** Por que o torcedor não pode resgatar (null = pode) */
	protected String blockReason(final CustomerModel customer, final SocioPlanModel plan, final boolean member)
	{
		if (plan == null)
		{
			if (socioPaymentService != null && socioPaymentService.awaitingOrder(customer) != null)
			{
				return "O plano aguarda a confirmação do pagamento; o resgate libera quando ele for confirmado.";
			}
			return member ? "Só sócios resgatam experiências. Volte a ser sócio para usar os seus pontos."
					: "Só sócios resgatam experiências.";
		}
		if (socioPlanService.isSuspended(customer))
		{
			return "Seus benefícios estão suspensos por mensalidade em atraso; regularize para resgatar.";
		}
		return null;
	}

	protected String experienceReason(final SocioExperienceModel e, final SocioPoints.Experience x, final SocioPlanModel plan,
			final SocioPoints p)
	{
		if (!p.canRedeem)
		{
			return p.reason;
		}
		if (x.available != null && x.available.intValue() <= 0)
		{
			return "Esgotada.";
		}
		if (e.getMinPlan() != null && price(plan) < price(e.getMinPlan()))
		{
			return "A partir do " + x.minPlanName + ".";
		}
		if (p.balance < x.points)
		{
			return "Faltam " + number(x.points - p.balance) + " pontos.";
		}
		return null;
	}

	/** Venda na loja (item 9): preço e produto quando a experiência está à venda */
	protected void sale(final SocioExperienceModel e, final SocioPoints.Experience x)
	{
		if (socioExperienceService == null || !socioExperienceService.isSaleEnabled() || e.getProduct() == null
				|| e.getPrice() == null || e.getPrice().doubleValue() <= 0)
		{
			return;
		}
		x.price = e.getPrice();
		x.productCode = e.getProduct().getCode();
		x.buyable = x.available == null || x.available.intValue() > 0;
	}

	// ------------------------------------------------------------ resgate

	@Override
	public SocioRedemptionModel redeem(final CustomerModel customer, final String experienceCode, final String origin)
	{
		if (!isEnabled())
		{
			throw new SocioPointsException("Os pontos do sócio estão desligados.");
		}
		if (customer == null)
		{
			throw new SocioPointsException("Entre na sua conta para resgatar.");
		}
		return unrestricted(() -> {
			final SocioPoints p = build(customer, PT);
			final SocioPoints.Experience x = p.experiences.stream().filter(e -> e.code.equals(experienceCode)).findFirst()
					.orElse(null);
			if (x == null)
			{
				throw new SocioPointsException("Experiência não encontrada na vitrine.");
			}
			if (!x.redeemable)
			{
				throw new SocioPointsException(x.name + ": " + x.reason);
			}
			final SocioExperienceModel experience = experience(experienceCode);
			final SocioRedemptionModel r = modelService.create(SocioRedemptionModel.class);
			r.setVoucher(newVoucher());
			r.setCustomer(customer);
			r.setExperience(experience);
			r.setPoints(Integer.valueOf(x.points));
			r.setDate(new Date());
			r.setPlan(socioPlanService.memberPlan(customer));
			r.setOrigin(StringUtils.defaultIfBlank(origin, "Loja"));
			modelService.save(r);
			if (socioExperienceService != null)
			{
				socioExperienceService.syncStock(experience); // vaga única: o produto da loja perde a vaga também
			}
			LOG.info("Resgate {}: {} usou {} pontos em {}", r.getVoucher(), customer.getUid(), x.points, experienceCode);
			record(customer, r, x, p.balance - x.points);
			return r;
		});
	}

	/** Linha do tempo: evento Resgate de pontos (falha aqui não desfaz o resgate) */
	protected void record(final CustomerModel customer, final SocioRedemptionModel r, final SocioPoints.Experience x,
			final int balance)
	{
		try
		{
			final SocioEventModel event = socioPlanService.newEvent(customer, SocioEventType.RESGATE_PONTOS, r.getOrigin(),
					x.name + ": " + number(x.points) + " pontos, voucher " + r.getVoucher());
			event.setResult("Saldo: " + number(balance) + " pontos");
			modelService.save(event);
		}
		catch (final Exception e)
		{
			LOG.warn("Resgate {}: não gravou o evento de {}: {}", r.getVoucher(), customer.getUid(), e.getMessage());
		}
	}

	protected String newVoucher()
	{
		for (int attempt = 0; attempt < 20; attempt++)
		{
			final StringBuilder code = new StringBuilder("NACAO-");
			for (int i = 0; i < 8; i++)
			{
				if (i == 4)
				{
					code.append('-');
				}
				code.append(VOUCHER_CHARS.charAt(RANDOM.nextInt(VOUCHER_CHARS.length())));
			}
			final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {SocioRedemption} WHERE {voucher}=?v");
			q.addQueryParameter("v", code.toString());
			if (flexibleSearchService.search(q).getResult().isEmpty())
			{
				return code.toString();
			}
		}
		throw new SocioPointsException("Não foi possível gerar o voucher agora. Tente de novo.");
	}

	// ------------------------------------------------------------ consultas

	protected List<SocioExperienceModel> experiences()
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {SocioExperience} WHERE {active}=?a OR {active} IS NULL ORDER BY {sortOrder}, {points}, {code}");
		q.addQueryParameter("a", Boolean.TRUE);
		return flexibleSearchService.<SocioExperienceModel> search(q).getResult();
	}

	protected SocioExperienceModel experience(final String code)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {SocioExperience} WHERE {code}=?c");
		q.addQueryParameter("c", code);
		final List<SocioExperienceModel> result = flexibleSearchService.<SocioExperienceModel> search(q).getResult();
		if (result.isEmpty())
		{
			throw new SocioPointsException("Experiência não encontrada na vitrine.");
		}
		return result.get(0);
	}

	protected List<SocioRedemptionModel> redemptions(final CustomerModel customer)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {SocioRedemption} WHERE {customer}=?c ORDER BY {date} DESC, {creationtime} DESC");
		q.addQueryParameter("c", customer);
		return flexibleSearchService.<SocioRedemptionModel> search(q).getResult();
	}

	/** Vagas que sobram (null = sem limite) */
	protected Integer available(final SocioExperienceModel e)
	{
		if (e.getStock() == null)
		{
			return null;
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT COUNT({pk}) FROM {SocioRedemption} WHERE {experience}=?e AND {cancelledAt} IS NULL");
		q.addQueryParameter("e", e);
		q.setResultClassList(List.of(Integer.class));
		final List<Integer> used = flexibleSearchService.<Integer> search(q).getResult();
		final int count = used.isEmpty() || used.get(0) == null ? 0 : used.get(0).intValue();
		return Integer.valueOf(Math.max(0, e.getStock().intValue() - count));
	}

	// ------------------------------------------------------------ apoio

	protected boolean isPlanOrGuest(final ProductModel product)
	{
		return product != null && (socioPlanService.findForProduct(product) != null
				|| (product.getCode() != null && product.getCode().startsWith(GUEST_PREFIX)));
	}

	protected static double price(final SocioPlanModel plan)
	{
		return plan == null || plan.getMonthlyPrice() == null ? 0 : plan.getMonthlyPrice().doubleValue();
	}

	protected static String name(final SocioPlanModel plan, final Locale locale)
	{
		final String name = plan.getName(locale) != null ? plan.getName(locale) : plan.getName(PT);
		return StringUtils.defaultIfBlank(name, plan.getCode());
	}

	protected static String name(final SocioExperienceModel e, final Locale locale)
	{
		final String name = e.getName(locale) != null ? e.getName(locale) : e.getName(PT);
		return StringUtils.defaultIfBlank(name, e.getCode());
	}

	protected static String number(final int value)
	{
		return String.format(BR, "%,d", Integer.valueOf(value));
	}

	protected static Date startOfDay(final Date date)
	{
		final Calendar c = Calendar.getInstance();
		c.setTime(date);
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c.getTime();
	}

	protected Locale sessionLocale()
	{
		try
		{
			final LanguageModel language = commonI18NService.getCurrentLanguage();
			return language == null ? PT : commonI18NService.getLocaleForLanguage(language);
		}
		catch (final RuntimeException e)
		{
			return PT;
		}
	}

	protected <T> T unrestricted(final Supplier<T> body)
	{
		if (sessionService == null || searchRestrictionService == null)
		{
			return body.get();
		}
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

	// ------------------------------------------------------------ dependências

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}

	public void setSocioPaymentService(final SocioPaymentService socioPaymentService)
	{
		this.socioPaymentService = socioPaymentService;
	}

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}

	public void setCommonI18NService(final CommonI18NService commonI18NService)
	{
		this.commonI18NService = commonI18NService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setSearchRestrictionService(final SearchRestrictionService searchRestrictionService)
	{
		this.searchRestrictionService = searchRestrictionService;
	}

	public void setSocioExperienceService(final SocioExperienceService socioExperienceService)
	{
		this.socioExperienceService = socioExperienceService;
	}
}
