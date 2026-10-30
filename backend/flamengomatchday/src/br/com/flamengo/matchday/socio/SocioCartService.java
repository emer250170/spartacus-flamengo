package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.MatchdayGameModel;
import br.com.flamengo.matchday.service.SeatHoldService;
import de.hybris.platform.commerceservices.order.CommerceCartCalculationStrategy;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.security.PrincipalGroupModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserGroupModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.user.UserService;
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
 * Plano Nação Maraca no carrinho vale como sócio já na compra do ingresso.
 * <p>
 * Ao entrar no carrinho, o plano põe o torcedor no grupo do plano
 * (nacao-maraca-N, que as promoções de desconto usam) e num grupo marcador
 * nacao-pendente-N. Se o plano sai do carrinho, os dois grupos saem e os
 * ingressos de jogos cuja onda ainda não chegou para ele saem do carrinho.
 * Quando o pedido fecha, o order-process tira o marcador e deixa só o grupo do
 * plano comprado.
 */
public class SocioCartService
{
	private static final Logger LOG = LoggerFactory.getLogger(SocioCartService.class);

	public static final String PLAN_PREFIX = "NACAO-MARACA-";
	public static final String GROUP_PREFIX = "nacao-maraca-";
	public static final String PENDING_PREFIX = "nacao-pendente-";
	private static final int LEVELS = 3;
	/** Onda 4 = venda geral (quem não é sócio) */
	private static final int GENERAL_SALE = 4;

	private UserService userService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private SeatHoldService seatHoldService;
	private CommerceCartCalculationStrategy commerceCartCalculationStrategy;

	/** NACAO-MARACA-2 -> 2; outro produto -> 0 */
	public int planLevel(final ProductModel product)
	{
		if (product == null || product.getCode() == null || !product.getCode().startsWith(PLAN_PREFIX))
		{
			return 0;
		}
		try
		{
			final int level = Integer.parseInt(product.getCode().substring(PLAN_PREFIX.length()));
			return level >= 1 && level <= LEVELS ? level : 0;
		}
		catch (final NumberFormatException e)
		{
			return 0;
		}
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
		final Set<Integer> wanted = plansInCart(cart);
		final Set<PrincipalGroupModel> groups = new HashSet<>();
		if (customer.getGroups() != null)
		{
			groups.addAll(customer.getGroups());
		}
		boolean changed = false;
		boolean lostPending = false;
		for (int level = 1; level <= LEVELS; level++)
		{
			final UserGroupModel plan = group(GROUP_PREFIX + level);
			final UserGroupModel pending = group(PENDING_PREFIX + level);
			if (plan == null || pending == null)
			{
				continue;
			}
			final boolean isPending = groups.contains(pending);
			if (wanted.contains(Integer.valueOf(level)))
			{
				if (!groups.contains(plan))
				{
					groups.add(plan);
					groups.add(pending);
					changed = true;
				}
			}
			else if (isPending)
			{
				groups.remove(plan);
				groups.remove(pending);
				changed = true;
				lostPending = true;
			}
		}
		if (changed)
		{
			customer.setGroups(groups);
			modelService.save(customer);
			LOG.info("Sócio no carrinho {}: grupos {}", cart.getCode(), groups.stream().map(PrincipalGroupModel::getUid)
					.filter(uid -> uid != null && uid.startsWith("nacao-")).collect(Collectors.joining(",")));
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

	/** Onda do torcedor: a do melhor plano (1 a 3) ou a venda geral */
	protected int memberWave(final Set<PrincipalGroupModel> groups)
	{
		int wave = GENERAL_SALE;
		for (final PrincipalGroupModel group : groups)
		{
			final String uid = group.getUid();
			if (uid != null && uid.startsWith(GROUP_PREFIX))
			{
				try
				{
					wave = Math.min(wave, Integer.parseInt(uid.substring(GROUP_PREFIX.length())));
				}
				catch (final NumberFormatException e)
				{
					// grupo com outro formato: ignora
				}
			}
		}
		return wave;
	}

	protected Set<Integer> plansInCart(final CartModel cart)
	{
		final Set<Integer> levels = new HashSet<>();
		if (cart.getEntries() != null)
		{
			for (final AbstractOrderEntryModel entry : cart.getEntries())
			{
				if (modelService.isRemoved(entry))
				{
					continue;
				}
				final int level = planLevel(entry.getProduct());
				if (level > 0 && entry.getQuantity() != null && entry.getQuantity().longValue() > 0)
				{
					levels.add(Integer.valueOf(level));
				}
			}
		}
		return levels;
	}

	protected boolean hasAnyPending(final CustomerModel customer)
	{
		return customer.getGroups() != null && customer.getGroups().stream()
				.anyMatch(g -> g.getUid() != null && g.getUid().startsWith(PENDING_PREFIX));
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
}
