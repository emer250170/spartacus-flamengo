package br.com.flamengo.matchday.email;

import br.com.flamengo.matchday.model.SocioRedemptionModel;
import de.hybris.platform.commercefacades.order.data.AbstractOrderData;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Vouchers das experiências compradas no pedido (08/11) em {@code OrderData.flaVouchers}.
 * <p>
 * Cada unidade de experiência comprada vira um voucher NACAO-... ao fechar o pedido
 * ({@code SocioExperiencePlaceOrderMethodHook}). Sócio vê o voucher em Meus resgates; quem não é sócio não
 * tinha onde ver. Com este populador no {@code orderConverter}, o e-mail de confirmação do pedido
 * (pedido-confirmacao.vm) mostra o código para todos. Só pedido (não carrinho) com produto EXP-: os demais
 * não consultam nada. Vouchers cancelados ficam de fora.
 * <p>
 * Chave: {@value #ENABLED_PROPERTY} (false = lista vazia, e-mail como antes).
 */
public class FlaOrderVoucherPopulator implements Populator<AbstractOrderModel, AbstractOrderData>
{
	private static final Logger LOG = LoggerFactory.getLogger(FlaOrderVoucherPopulator.class);

	public static final String ENABLED_PROPERTY = "flamengo.pedido.vouchers.ativo";
	private static final Locale PT = new Locale("pt");

	private ConfigurationService configurationService;
	private FlexibleSearchService flexibleSearchService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;

	@Override
	public void populate(final AbstractOrderModel source, final AbstractOrderData target)
	{
		if (target.getFlaVouchers() == null)
		{
			target.setFlaVouchers(Collections.emptyList());
		}
		if (!(source instanceof OrderModel) || source.getCode() == null || !hasExperience(source)
				|| !configurationService.getConfiguration().getBoolean(ENABLED_PROPERTY, true))
		{
			return;
		}
		try
		{
			target.setFlaVouchers(vouchers(source.getCode()));
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Pedido {}: não leu os vouchers das experiências: {}", source.getCode(), e.getMessage());
		}
	}

	protected boolean hasExperience(final AbstractOrderModel order)
	{
		if (order.getEntries() == null)
		{
			return false;
		}
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			if (entry.getProduct() != null && StringUtils.startsWith(entry.getProduct().getCode(), "EXP-"))
			{
				return true;
			}
		}
		return false;
	}

	protected List<FlaVoucherData> vouchers(final String orderCode)
	{
		final List<SocioRedemptionModel> found = sessionService.executeInLocalView(new SessionExecutionBody()
		{
			@Override
			public Object execute()
			{
				searchRestrictionService.disableSearchRestrictions();
				final FlexibleSearchQuery q = new FlexibleSearchQuery(
						"SELECT {pk} FROM {SocioRedemption} WHERE {orderCode}=?o AND {cancelledAt} IS NULL ORDER BY {creationtime}, {pk}");
				q.addQueryParameter("o", orderCode);
				return flexibleSearchService.<SocioRedemptionModel> search(q).getResult();
			}
		});
		final List<FlaVoucherData> result = new ArrayList<>();
		for (final SocioRedemptionModel r : found)
		{
			final FlaVoucherData data = new FlaVoucherData();
			data.setVoucher(r.getVoucher());
			data.setOrigin(r.getOrigin());
			if (r.getExperience() != null)
			{
				data.setExperienceCode(r.getExperience().getCode());
				data.setExperienceName(StringUtils.defaultIfBlank(r.getExperience().getName(PT), r.getExperience().getCode()));
			}
			result.add(data);
		}
		return result;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
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
