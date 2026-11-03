package br.com.flamengo.matchday.report.merch.impl;

import br.com.flamengo.matchday.report.merch.MerchDashboard;
import br.com.flamengo.matchday.report.merch.MerchDashboardService;
import de.hybris.platform.core.HybrisEnumValue;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.util.DiscountValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Monta o painel do gestor de merchandising com dados reais do SAP Commerce.
 * <p>
 * Os tipos de promoção e cupom (PromotionSourceRule, AbstractCoupon, CouponRedemption)
 * são lidos por FlexibleSearch e {@code modelService.getAttributeValue}, sem dependência de
 * compilação das extensões promotionengineservices e couponservices. As consultas rodam sem
 * restrições de busca (o gestor só tem leitura de promoções e cupons, não de pedidos).
 */
public class DefaultMerchDashboardService implements MerchDashboardService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultMerchDashboardService.class);
	private static final long DAY = 24L * 60 * 60 * 1000;
	private static final Locale PT = new Locale("pt");
	private static final int TOP_COUPONS = 5;

	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;
	private ConfigurationService configurationService;

	@Override
	public MerchDashboard getDashboard(final Date now)
	{
		final Date reference = now == null ? new Date() : now;
		return sessionService.executeInLocalView(new SessionExecutionBody()
		{
			@Override
			public Object execute()
			{
				searchRestrictionService.disableSearchRestrictions();
				return build(reference);
			}
		});
	}

	protected MerchDashboard build(final Date now)
	{
		final MerchDashboard d = new MerchDashboard();
		d.generatedAt = now;
		d.promotionGroup = configurationService.getConfiguration().getString(GROUP_PROPERTY, "electronicsPromoGrp");
		d.days = Math.max(1, configurationService.getConfiguration().getInt(DAYS_PROPERTY, 30));
		d.expiringDays = Math.max(1, configurationService.getConfiguration().getInt(EXPIRING_PROPERTY, 7));
		final Date from = new Date(now.getTime() - d.days * DAY);
		final Date expiringUntil = new Date(now.getTime() + d.expiringDays * DAY);

		safely("regras", () -> rules(d, now, expiringUntil));
		safely("cupons", () -> coupons(d, now, from));
		safely("pedidos", () -> orders(d, from));
		return d;
	}

	/** Última versão de cada regra do grupo de promoção do site */
	protected void rules(final MerchDashboard d, final Date now, final Date expiringUntil)
	{
		final Map<String, ItemModel> latest = new LinkedHashMap<>();
		final Map<String, Long> versions = new HashMap<>();
		for (final ItemModel rule : search("SELECT {pk} FROM {PromotionSourceRule}", null))
		{
			if (StringUtils.isNotBlank(d.promotionGroup)
					&& !d.promotionGroup.equals(attr(attr(rule, "website"), "identifier")))
			{
				continue;
			}
			final String code = (String) attr(rule, "code");
			final Object v = attr(rule, "version");
			final long version = v instanceof Number ? ((Number) v).longValue() : 0L;
			if (code != null && (!versions.containsKey(code) || version > versions.get(code)))
			{
				versions.put(code, version);
				latest.put(code, rule);
			}
		}
		for (final ItemModel rule : latest.values())
		{
			final String status = enumCode(attr(rule, "status"));
			if ("UNPUBLISHED".equals(status) || "MODIFIED".equals(status))
			{
				d.rulesDraft++;
			}
			else if ("PUBLISHED".equals(status))
			{
				final MerchDashboard.Rule r = new MerchDashboard.Rule();
				r.code = (String) attr(rule, "code");
				r.name = localized(rule, "name");
				final Object priority = attr(rule, "priority");
				r.priority = priority instanceof Number ? ((Number) priority).intValue() : null;
				r.start = (Date) attr(rule, "startDate");
				r.end = (Date) attr(rule, "endDate");
				r.ruleGroup = (String) attr(attr(rule, "ruleGroup"), "code");
				r.expiring = r.end != null && !r.end.before(now) && !r.end.after(expiringUntil);
				if (r.expiring)
				{
					d.rulesExpiring++;
				}
				d.published.add(r);
			}
		}
		d.rulesPublished = d.published.size();
		d.published.sort(Comparator.comparing((MerchDashboard.Rule r) -> r.priority == null ? Integer.MIN_VALUE : r.priority)
				.reversed().thenComparing(r -> StringUtils.defaultString(r.code)));
	}

	/** Cupons ativos e uso na janela */
	protected void coupons(final MerchDashboard d, final Date now, final Date from)
	{
		final Map<ItemModel, MerchDashboard.CouponUse> uses = new HashMap<>();
		for (final ItemModel coupon : search("SELECT {pk} FROM {AbstractCoupon}", null))
		{
			d.couponsTotal++;
			if (isActive(coupon, now))
			{
				d.couponsActive++;
			}
		}
		final Map<String, Object> params = new HashMap<>();
		params.put("from", from);
		for (final ItemModel redemption : search("SELECT {pk} FROM {CouponRedemption} WHERE {creationtime} >= ?from", params))
		{
			d.redemptions++;
			final Object coupon = attr(redemption, "coupon");
			if (!(coupon instanceof ItemModel))
			{
				continue;
			}
			final ItemModel item = (ItemModel) coupon;
			final MerchDashboard.CouponUse use = uses.computeIfAbsent(item, c -> {
				final MerchDashboard.CouponUse u = new MerchDashboard.CouponUse();
				u.couponId = (String) attr(c, "couponId");
				u.name = localized(c, "name");
				u.kind = c.getItemtype();
				u.active = isActive(c, now);
				return u;
			});
			use.uses++;
		}
		final List<MerchDashboard.CouponUse> sorted = new ArrayList<>(uses.values());
		sorted.sort(Comparator.comparingInt((MerchDashboard.CouponUse u) -> u.uses).reversed()
				.thenComparing(u -> StringUtils.defaultString(u.couponId)));
		d.topCoupons.addAll(sorted.subList(0, Math.min(TOP_COUPONS, sorted.size())));
	}

	/** Desconto dado nos pedidos da janela (desconto do pedido + dos itens); cancelados não contam */
	protected void orders(final MerchDashboard d, final Date from)
	{
		final Map<String, Object> params = new HashMap<>();
		params.put("from", from);
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {date} >= ?from AND {versionID} IS NULL", params);
		final List<OrderModel> orders = flexibleSearchService.<OrderModel> search(query).getResult();
		for (final OrderModel order : orders)
		{
			if (OrderStatus.CANCELLED.equals(order.getStatus()))
			{
				continue;
			}
			d.orders++;
			double discount = order.getTotalDiscounts() == null ? 0 : order.getTotalDiscounts();
			if (order.getEntries() != null)
			{
				for (final AbstractOrderEntryModel entry : order.getEntries())
				{
					if (entry.getDiscountValues() != null)
					{
						for (final DiscountValue value : entry.getDiscountValues())
						{
							discount += value.getAppliedValue();
						}
					}
				}
			}
			if (discount > 0.004)
			{
				d.ordersWithDiscount++;
				d.discount += discount;
			}
			final Object coupons = attr(order, "appliedCouponCodes");
			if (coupons instanceof Collection && !((Collection<?>) coupons).isEmpty())
			{
				d.ordersWithCoupon++;
			}
			if (d.currency == null && order.getCurrency() != null)
			{
				d.currency = order.getCurrency().getIsocode();
			}
		}
	}

	protected boolean isActive(final Object coupon, final Date now)
	{
		final Date start = (Date) attr(coupon, "startDate");
		final Date end = (Date) attr(coupon, "endDate");
		return Boolean.TRUE.equals(attr(coupon, "active")) && (start == null || !start.after(now))
				&& (end == null || end.after(now));
	}

	protected List<ItemModel> search(final String text, final Map<String, Object> params)
	{
		final FlexibleSearchQuery query = params == null ? new FlexibleSearchQuery(text) : new FlexibleSearchQuery(text, params);
		return flexibleSearchService.<ItemModel> search(query).getResult();
	}

	/** Atributo por nome; null quando o item ou o atributo não existe */
	protected Object attr(final Object item, final String qualifier)
	{
		if (item == null)
		{
			return null;
		}
		try
		{
			return modelService.getAttributeValue(item, qualifier);
		}
		catch (final RuntimeException e)
		{
			LOG.debug("Atributo {} indisponível em {}", qualifier, item, e);
			return null;
		}
	}

	protected String localized(final Object item, final String qualifier)
	{
		try
		{
			final Object value = modelService.getAttributeValue(item, qualifier, PT);
			if (value instanceof String && StringUtils.isNotBlank((String) value))
			{
				return (String) value;
			}
		}
		catch (final RuntimeException e)
		{
			LOG.debug("Atributo {} (pt) indisponível em {}", qualifier, item, e);
		}
		final Object value = attr(item, qualifier);
		return value instanceof String ? (String) value : null;
	}

	protected static String enumCode(final Object value)
	{
		return value instanceof HybrisEnumValue ? ((HybrisEnumValue) value).getCode() : value == null ? null : value.toString();
	}

	/** Uma parte que falha (ex.: extensão de cupons ausente) não derruba o painel */
	protected void safely(final String part, final Runnable body)
	{
		try
		{
			body.run();
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Painel de merchandising: falha ao montar {}", part, e);
		}
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

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
