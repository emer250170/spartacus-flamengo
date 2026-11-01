package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.MatchdayGameModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.service.SeatHoldService;
import de.hybris.platform.commerceservices.order.CommerceCartCalculationStrategy;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.Tenant;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.security.PrincipalGroupModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserGroupModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.variants.model.VariantProductModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Plano de sócio-torcedor no carrinho vale como sócio já na compra do ingresso.
 * <p>
 * Ao entrar no carrinho, o plano põe o torcedor no grupo do plano (que a
 * promoção de desconto usa) e no grupo marcador de carrinho do plano. Se o
 * plano sai do carrinho, os dois grupos saem e os ingressos de jogos cuja onda
 * ainda não chegou para ele saem do carrinho. Quando o pedido fecha, o
 * order-process ({@link SocioPlanService#applyOrder}) deixa só o grupo do plano
 * comprado. Planos, grupos e ondas vêm do cadastro de planos (SocioPlan).
 */
public class SocioCartService
{
	private static final Logger LOG = LoggerFactory.getLogger(SocioCartService.class);

	/** Onda 4 = venda geral (quem não é sócio ou plano sem prioridade) */
	private static final int GENERAL_SALE = 4;
	/** Espera a suspensão ou regularização ser gravada antes de recalcular os carrinhos */
	private static final long CART_REFRESH_DELAY_MS = 2000;

	private UserService userService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private SeatHoldService seatHoldService;
	private CommerceCartCalculationStrategy commerceCartCalculationStrategy;
	private SocioPlanService socioPlanService;
	private SessionService sessionService;
	private BaseSiteService baseSiteService;
	private CommonI18NService commonI18NService;

	/** O produto é um plano de sócio-torcedor? */
	public boolean isPlan(final ProductModel product)
	{
		return socioPlanService.findForProduct(product) != null;
	}

	/** O carrinho tem plano ou o torcedor tem grupo marcador? (evita trabalho nos demais carrinhos) */
	public boolean concerns(final CartModel cart)
	{
		final CustomerModel customer = customer(cart);
		if (customer == null)
		{
			return false;
		}
		return !plansInCart(cart).isEmpty() || hasAnyPending(customer);
	}

	/**
	 * Ajusta os grupos do torcedor ao plano que está no carrinho, tira ingressos
	 * que deixaram de estar liberados e recalcula o carrinho se algo mudou.
	 *
	 * @return códigos dos ingressos retirados
	 */
	public List<String> sync(final CartModel cart)
	{
		final CustomerModel customer = customer(cart);
		if (customer == null)
		{
			return Collections.emptyList();
		}
		final Set<SocioPlanModel> wanted = plansInCart(cart);
		final Set<PrincipalGroupModel> groups = new HashSet<>();
		if (customer.getGroups() != null)
		{
			groups.addAll(customer.getGroups());
		}
		boolean changed = false;
		boolean lostPending = false;
		for (final SocioPlanModel plan : socioPlanService.getPlans())
		{
			final UserGroupModel planGroup = plan.getCustomerGroup();
			final UserGroupModel pending = plan.getPendingGroup();
			if (planGroup == null || pending == null)
			{
				continue;
			}
			final boolean isPending = groups.contains(pending);
			if (wanted.contains(plan))
			{
				if (!groups.contains(planGroup))
				{
					groups.add(planGroup);
					groups.add(pending);
					changed = true;
				}
			}
			else if (isPending)
			{
				groups.remove(planGroup);
				groups.remove(pending);
				changed = true;
				lostPending = true;
			}
		}
		if (changed)
		{
			customer.setGroups(groups);
			modelService.save(customer);
			LOG.info("Sócio no carrinho {}: planos {}", cart.getCode(),
					wanted.stream().map(SocioPlanModel::getCode).collect(Collectors.joining(",")));
		}
		final List<String> removed = lostPending ? removeLockedSeats(cart, memberWave(groups)) : Collections.emptyList();
		if (changed || !removed.isEmpty())
		{
			modelService.refresh(cart);
			final CommerceCartParameter parameter = new CommerceCartParameter();
			parameter.setEnableHooks(true);
			parameter.setCart(cart);
			commerceCartCalculationStrategy.recalculateCart(parameter);
		}
		return removed;
	}

	/** Tira do carrinho os ingressos de jogos cuja onda ainda não chegou para o torcedor */
	protected List<String> removeLockedSeats(final CartModel cart, final int wave)
	{
		final List<String> removed = new ArrayList<>();
		final Date now = new Date();
		for (final AbstractOrderEntryModel entry : new ArrayList<>(cart.getEntries()))
		{
			if (modelService.isRemoved(entry))
			{
				continue;
			}
			final ProductModel product = entry.getProduct();
			if (product == null || !seatHoldService.isSeat(product) || !(product instanceof VariantProductModel))
			{
				continue;
			}
			final ProductModel base = ((VariantProductModel) product).getBaseProduct();
			final MatchdayGameModel game = base == null ? null : findGame(base.getCode());
			if (game == null || currentWave(game, now) >= wave)
			{
				continue;
			}
			seatHoldService.release(product, cart);
			modelService.remove(entry);
			removed.add(product.getCode());
		}
		if (!removed.isEmpty())
		{
			LOG.info("Plano saiu do carrinho {}: ingressos retirados {}", cart.getCode(), removed);
		}
		return removed;
	}

	/**
	 * Suspensão ou regularização do sócio: depois que a mudança foi gravada, recalcula
	 * os carrinhos do torcedor (o desconto de sócio sai ou volta) e, na suspensão, tira
	 * os ingressos de jogos cuja venda geral ainda não abriu.
	 */
	public void refreshCartsLater(final CustomerModel customer, final boolean suspended)
	{
		if (customer == null || customer.getPk() == null)
		{
			return;
		}
		final PK customerPk = customer.getPk();
		final String uid = customer.getUid();
		final Tenant tenant = Registry.getCurrentTenant();
		final Thread thread = new Thread(() -> {
			Registry.setCurrentTenant(tenant);
			try
			{
				Thread.sleep(CART_REFRESH_DELAY_MS);
				sessionService.executeInLocalView(new SessionExecutionBody()
				{
					@Override
					public void executeWithoutResult()
					{
						userService.setCurrentUser(userService.getAdminUser());
						refreshCarts(customerPk, suspended);
					}
				});
			}
			catch (final InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			catch (final Exception e)
			{
				LOG.error("Falha ao recalcular os carrinhos de " + uid, e);
			}
			finally
			{
				Registry.unsetCurrentTenant();
			}
		}, "socio-situacao-" + uid);
		thread.setDaemon(true);
		thread.start();
	}

	protected void refreshCarts(final PK customerPk, final boolean suspended)
	{
		final CustomerModel customer = modelService.get(customerPk);
		modelService.refresh(customer);
		if (customer.getCarts() == null || customer.getCarts().isEmpty())
		{
			return;
		}
		final Set<PrincipalGroupModel> groups = new HashSet<>();
		if (customer.getGroups() != null)
		{
			groups.addAll(customer.getGroups());
		}
		for (final CartModel cart : new ArrayList<>(customer.getCarts()))
		{
			if (cart.getEntries() == null || cart.getEntries().isEmpty())
			{
				continue;
			}
			// recálculo com o site, o usuário e a moeda do carrinho (preço e promoções de sócio)
			if (cart.getSite() != null)
			{
				baseSiteService.setCurrentBaseSite(cart.getSite(), true);
			}
			userService.setCurrentUser(customer);
			if (cart.getCurrency() != null)
			{
				commonI18NService.setCurrentCurrency(cart.getCurrency());
			}
			final List<String> removed = suspended ? removeLockedSeats(cart, memberWave(groups)) : Collections.emptyList();
			modelService.refresh(cart);
			final CommerceCartParameter parameter = new CommerceCartParameter();
			parameter.setEnableHooks(true);
			parameter.setCart(cart);
			commerceCartCalculationStrategy.recalculateCart(parameter);
			LOG.info("Sócio {} {}: carrinho {} recalculado{}", customer.getUid(), suspended ? "suspenso" : "regularizado",
					cart.getCode(), removed.isEmpty() ? "" : ", ingressos retirados " + removed);
		}
	}

	/** Onda aberta agora no jogo (4 = venda geral; jogo sem datas está em venda geral) */
	public int currentWave(final MatchdayGameModel game, final Date now)
	{
		final List<Date> starts = new ArrayList<>();
		starts.add(game.getWave1Start());
		starts.add(game.getWave2Start());
		starts.add(game.getWave3Start());
		starts.add(game.getGeneralSaleStart());
		if (starts.contains(null))
		{
			return GENERAL_SALE;
		}
		int current = 0;
		for (int i = 0; i < starts.size(); i++)
		{
			if (!starts.get(i).after(now))
			{
				current = i + 1;
			}
		}
		return current;
	}

	/** Onda do torcedor: a do melhor plano que ele tem (1 a 3) ou a venda geral; suspenso compra na venda geral */
	protected int memberWave(final Set<PrincipalGroupModel> groups)
	{
		int wave = GENERAL_SALE;
		if (groups.stream().anyMatch(group -> SocioPlanService.SUSPENDED_GROUP.equals(group.getUid())))
		{
			return wave;
		}
		for (final SocioPlanModel plan : socioPlanService.getPlans())
		{
			final int planWave = plan.getWave() == null ? 0 : plan.getWave().intValue();
			if (planWave > 0 && plan.getCustomerGroup() != null && groups.contains(plan.getCustomerGroup()))
			{
				wave = Math.min(wave, planWave);
			}
		}
		return wave;
	}

	protected Set<SocioPlanModel> plansInCart(final CartModel cart)
	{
		final Set<SocioPlanModel> plans = new HashSet<>();
		if (cart.getEntries() != null)
		{
			for (final AbstractOrderEntryModel entry : cart.getEntries())
			{
				if (modelService.isRemoved(entry) || entry.getQuantity() == null || entry.getQuantity().longValue() <= 0)
				{
					continue;
				}
				final SocioPlanModel plan = socioPlanService.findForProduct(entry.getProduct());
				if (plan != null)
				{
					plans.add(plan);
				}
			}
		}
		return plans;
	}

	protected boolean hasAnyPending(final CustomerModel customer)
	{
		if (customer.getGroups() == null || customer.getGroups().isEmpty())
		{
			return false;
		}
		for (final SocioPlanModel plan : socioPlanService.getPlans())
		{
			if (plan.getPendingGroup() != null && customer.getGroups().contains(plan.getPendingGroup()))
			{
				return true;
			}
		}
		return false;
	}

	protected CustomerModel customer(final CartModel cart)
	{
		final UserModel user = cart == null ? null : cart.getUser();
		if (!(user instanceof CustomerModel) || userService.isAnonymousUser(user))
		{
			return null;
		}
		return (CustomerModel) user;
	}

	protected UserGroupModel group(final String uid)
	{
		try
		{
			return userService.getUserGroupForUID(uid);
		}
		catch (final UnknownIdentifierException e)
		{
			LOG.warn("Grupo {} não existe (rode o cadastro do sócio-torcedor)", uid);
			return null;
		}
	}

	protected MatchdayGameModel findGame(final String code)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {" + MatchdayGameModel.PK + "} FROM {" + MatchdayGameModel._TYPECODE + "} WHERE {"
						+ MatchdayGameModel.CODE + "} = ?code");
		query.addQueryParameter("code", code);
		final List<MatchdayGameModel> result = flexibleSearchService.<MatchdayGameModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}

	public void setCommerceCartCalculationStrategy(final CommerceCartCalculationStrategy commerceCartCalculationStrategy)
	{
		this.commerceCartCalculationStrategy = commerceCartCalculationStrategy;
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setBaseSiteService(final BaseSiteService baseSiteService)
	{
		this.baseSiteService = baseSiteService;
	}

	public void setCommonI18NService(final CommonI18NService commonI18NService)
	{
		this.commonI18NService = commonI18NService;
	}
}
