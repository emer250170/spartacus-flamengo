package br.com.flamengo.matchday.socio.impl;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.model.SocioEventModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.model.SocioTermAcceptanceModel;
import br.com.flamengo.matchday.model.SocioTermVersionModel;
import br.com.flamengo.matchday.socio.SocioPlanService;
import br.com.flamengo.matchday.socio.SocioTermException;
import br.com.flamengo.matchday.socio.SocioTermService;
import br.com.flamengo.matchday.socio.SocioTermStatus;
import de.hybris.platform.core.model.c2l.LanguageModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.security.PrincipalModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/** Termo do Nação: versão atual, aceites e conferência no fechamento do pedido. */
public class DefaultSocioTermService implements SocioTermService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSocioTermService.class);
	private static final Locale PT = new Locale("pt");

	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private ConfigurationService configurationService;
	private CommonI18NService commonI18NService;
	private SocioPlanService socioPlanService;

	@Override
	public boolean isEnabled()
	{
		return configurationService.getConfiguration().getBoolean(ENABLED_PROPERTY, true);
	}

	@Override
	public SocioTermVersionModel current()
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {SocioTermVersion} WHERE {publishedAt} IS NOT NULL "
				+ "AND {publishedAt} <= ?now ORDER BY {publishedAt} DESC, {creationtime} DESC");
		query.addQueryParameter("now", new Date());
		query.setCount(1);
		final List<SocioTermVersionModel> result = flexibleSearchService.<SocioTermVersionModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	@Override
	public SocioTermAcceptanceModel lastAcceptance(final CustomerModel customer)
	{
		if (customer == null)
		{
			return null;
		}
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {SocioTermAcceptance} WHERE {customer}=?c ORDER BY {date} DESC, {creationtime} DESC");
		query.addQueryParameter("c", customer);
		query.setCount(1);
		final List<SocioTermAcceptanceModel> result = flexibleSearchService.<SocioTermAcceptanceModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	@Override
	public boolean hasAccepted(final CustomerModel customer, final SocioTermVersionModel version)
	{
		return find(customer, version, null) != null;
	}

	@Override
	public boolean hasPlan(final AbstractOrderModel cart)
	{
		if (cart == null || cart.getEntries() == null)
		{
			return false;
		}
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			if (socioPlanService.findForProduct(entry.getProduct()) != null)
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public SocioTermAcceptanceModel accept(final CustomerModel customer, final String cartCode) throws SocioTermException
	{
		if (customer == null)
		{
			throw new SocioTermException("Entre na sua conta para aceitar o Termo do Nação.");
		}
		final SocioTermVersionModel version = current();
		if (version == null)
		{
			throw new SocioTermException("Não há Termo do Nação publicado no momento.");
		}
		if (StringUtils.isNotBlank(cartCode))
		{
			final CartModel cart = findCart(customer, cartCode.trim());
			if (cart == null)
			{
				throw new SocioTermException("Carrinho não encontrado.");
			}
			final SocioTermAcceptanceModel existing = find(customer, version, cart.getCode());
			if (existing != null)
			{
				return existing;
			}
			final SocioTermAcceptanceModel acceptance = newAcceptance(customer, version, ORIGIN_CHECKOUT);
			acceptance.setCartCode(cart.getCode());
			acceptance.setPlan(planIn(cart));
			modelService.save(acceptance);
			LOG.info("{} aceitou o Termo {} no checkout (carrinho {})", customer.getUid(), version.getVersion(), cart.getCode());
			return acceptance;
		}
		final SocioTermAcceptanceModel existing = find(customer, version, null);
		if (existing != null)
		{
			return existing;
		}
		final SocioTermAcceptanceModel acceptance = newAcceptance(customer, version, ORIGIN_MEMBER_AREA);
		acceptance.setPlan(socioPlanService.memberPlan(customer));
		modelService.save(acceptance);
		recordEvent(customer, "Aceite do Termo do Nação " + version.getVersion() + " (área do sócio)", version);
		LOG.info("{} aceitou o Termo {} na área do sócio", customer.getUid(), version.getVersion());
		return acceptance;
	}

	@Override
	public void checkCart(final CartModel cart) throws SocioTermException
	{
		if (!isEnabled() || cart == null || !(cart.getUser() instanceof CustomerModel) || !hasPlan(cart))
		{
			return;
		}
		final SocioTermVersionModel version = current();
		if (version == null)
		{
			return;
		}
		if (find((CustomerModel) cart.getUser(), version, cart.getCode()) == null)
		{
			throw new SocioTermException("Para concluir a adesão, leia e aceite o Termo do Nação " + version.getVersion() + ".");
		}
	}

	@Override
	public void linkOrder(final AbstractOrderModel order, final String cartCode)
	{
		if (order == null || StringUtils.isBlank(cartCode) || !(order.getUser() instanceof CustomerModel))
		{
			return;
		}
		try
		{
			final CustomerModel customer = (CustomerModel) order.getUser();
			final FlexibleSearchQuery query = new FlexibleSearchQuery(
					"SELECT {pk} FROM {SocioTermAcceptance} WHERE {customer}=?c AND {cartCode}=?cart AND {orderCode} IS NULL");
			query.addQueryParameter("c", customer);
			query.addQueryParameter("cart", cartCode);
			for (final SocioTermAcceptanceModel acceptance : flexibleSearchService.<SocioTermAcceptanceModel> search(query).getResult())
			{
				acceptance.setOrderCode(order.getCode());
				final SocioPlanModel plan = planIn(order);
				if (plan != null)
				{
					acceptance.setPlan(plan);
				}
				modelService.save(acceptance);
				recordEvent(customer, "Aceite do Termo do Nação " + acceptance.getTermVersion().getVersion() + " na adesão (pedido "
						+ order.getCode() + ")", acceptance.getTermVersion());
			}
		}
		catch (final Exception e)
		{
			// não derruba o pedido
			LOG.warn("Pedido {}: não ligou o aceite do Termo: {}", order.getCode(), e.getMessage());
		}
	}

	@Override
	public SocioTermStatus status(final CustomerModel customer)
	{
		final SocioTermStatus s = new SocioTermStatus();
		s.enabled = isEnabled();
		s.member = customer != null && socioPlanService.memberPlan(customer) != null;
		final SocioTermVersionModel version = current();
		if (version != null)
		{
			final Locale locale = sessionLocale();
			s.version = version.getVersion();
			s.title = localized(version.getTitle(locale), version.getTitle(PT));
			s.text = localized(version.getText(locale), version.getText(PT));
			s.summary = localized(version.getSummary(locale), version.getSummary(PT));
			s.publishedAt = version.getPublishedAt();
			s.accepted = customer != null && hasAccepted(customer, version);
		}
		final SocioTermAcceptanceModel last = lastAcceptance(customer);
		if (last != null)
		{
			s.acceptedVersion = last.getTermVersion() == null ? null : last.getTermVersion().getVersion();
			s.acceptedAt = last.getDate();
			s.acceptedOrigin = last.getOrigin();
			s.acceptedOrder = last.getOrderCode();
		}
		return s;
	}

	@Override
	public List<CustomerModel> pending()
	{
		final List<CustomerModel> pending = new ArrayList<>();
		final SocioTermVersionModel version = current();
		if (version == null)
		{
			return pending;
		}
		final Map<String, CustomerModel> members = new LinkedHashMap<>();
		for (final SocioPlanModel plan : socioPlanService.getPlans())
		{
			if (plan.getCustomerGroup() == null || plan.getCustomerGroup().getMembers() == null)
			{
				continue;
			}
			for (final PrincipalModel member : plan.getCustomerGroup().getMembers())
			{
				if (member instanceof CustomerModel && !members.containsKey(member.getUid())
						&& socioPlanService.memberPlan((CustomerModel) member) != null)
				{
					members.put(member.getUid(), (CustomerModel) member);
				}
			}
		}
		for (final CustomerModel customer : members.values())
		{
			if (!hasAccepted(customer, version))
			{
				pending.add(customer);
			}
		}
		return pending;
	}

	// ------------------------------------------------------------ apoio

	/** Aceite da versão (num carrinho, se informado) */
	protected SocioTermAcceptanceModel find(final CustomerModel customer, final SocioTermVersionModel version, final String cartCode)
	{
		if (customer == null || version == null)
		{
			return null;
		}
		final StringBuilder q = new StringBuilder("SELECT {pk} FROM {SocioTermAcceptance} WHERE {customer}=?c AND {termVersion}=?v");
		if (cartCode != null)
		{
			q.append(" AND {cartCode}=?cart");
		}
		final FlexibleSearchQuery query = new FlexibleSearchQuery(q.toString());
		query.addQueryParameter("c", customer);
		query.addQueryParameter("v", version);
		if (cartCode != null)
		{
			query.addQueryParameter("cart", cartCode);
		}
		query.setCount(1);
		final List<SocioTermAcceptanceModel> result = flexibleSearchService.<SocioTermAcceptanceModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected CartModel findCart(final CustomerModel customer, final String code)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {Cart} WHERE {code}=?code AND {user}=?u");
		query.addQueryParameter("code", code);
		query.addQueryParameter("u", customer);
		final List<CartModel> result = flexibleSearchService.<CartModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected SocioPlanModel planIn(final AbstractOrderModel order)
	{
		if (order.getEntries() != null)
		{
			for (final AbstractOrderEntryModel entry : order.getEntries())
			{
				final SocioPlanModel plan = socioPlanService.findForProduct(entry.getProduct());
				if (plan != null)
				{
					return plan;
				}
			}
		}
		return null;
	}

	protected SocioTermAcceptanceModel newAcceptance(final CustomerModel customer, final SocioTermVersionModel version,
			final String origin)
	{
		final SocioTermAcceptanceModel acceptance = modelService.create(SocioTermAcceptanceModel.class);
		acceptance.setCustomer(customer);
		acceptance.setTermVersion(version);
		acceptance.setDate(new Date());
		acceptance.setOrigin(origin);
		return acceptance;
	}

	/** Linha do tempo (falha aqui não desfaz o aceite) */
	protected void recordEvent(final CustomerModel customer, final String note, final SocioTermVersionModel version)
	{
		try
		{
			final SocioEventModel event = socioPlanService.newEvent(customer, SocioEventType.ACEITE_TERMO, "Loja", note);
			event.setResult("Termo " + version.getVersion() + " aceito");
			modelService.save(event);
		}
		catch (final Exception e)
		{
			LOG.warn("Não gravou o evento de aceite do Termo de {}: {}", customer.getUid(), e.getMessage());
		}
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

	protected static String localized(final String value, final String fallback)
	{
		return StringUtils.isNotBlank(value) ? value : fallback;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setCommonI18NService(final CommonI18NService commonI18NService)
	{
		this.commonI18NService = commonI18NService;
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}
}
