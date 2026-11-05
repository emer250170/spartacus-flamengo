package br.com.flamengo.matchday.socio.impl;

import br.com.flamengo.matchday.model.MatchdayGameModel;
import br.com.flamengo.matchday.model.SocioEligibilityDecisionModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.service.SeatHoldService;
import br.com.flamengo.matchday.socio.SocioCartService;
import br.com.flamengo.matchday.socio.SocioEligibility;
import br.com.flamengo.matchday.socio.SocioEligibilityService;
import br.com.flamengo.matchday.socio.SocioGuestService;
import br.com.flamengo.matchday.socio.SocioPlanService;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.security.PrincipalGroupModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserGroupModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.variants.model.VariantProductModel;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Confere as cinco condições da compra de ingresso e grava cada tentativa. Ver
 * {@link SocioEligibilityService}.
 */
public class DefaultSocioEligibilityService implements SocioEligibilityService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSocioEligibilityService.class);
	private static final Locale PT = new Locale("pt");
	private static final TimeZone TZ = TimeZone.getTimeZone("America/Sao_Paulo");
	private static final int DEFAULT_GENERAL_LIMIT = 4;

	private SocioPlanService socioPlanService;
	private SocioCartService socioCartService;
	private SeatHoldService seatHoldService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private UserService userService;
	private ConfigurationService configurationService;
	private SocioGuestService socioGuestService;

	// ------------------------------------------------------------ conferência

	@Override
	public SocioEligibility evaluate(final CustomerModel customer, final MatchdayGameModel game, final CartModel cart,
			final ProductModel seat, final long quantity, final Date now)
	{
		final SocioEligibility e = new SocioEligibility();
		final Set<PrincipalGroupModel> groups = customer.getGroups() == null ? Collections.emptySet()
				: new HashSet<>(customer.getGroups());

		// 1. plano (o plano no carrinho já vale, como no SocioCartService)
		e.plan = bestPlan(groups);
		e.planInCart = e.plan != null && e.plan.getPendingGroup() != null && groups.contains(e.plan.getPendingGroup());
		if (e.planInCart && cart != null && !hasPlan(cart))
		{
			// grupo do plano "no carrinho" sem o plano neste carrinho (sobra de um carrinho
			// que sumiu sem a sincronização): não é sócio aqui, compra como os demais (04/11)
			final Set<PrincipalGroupModel> clean = new HashSet<>(groups);
			for (final SocioPlanModel p : socioPlanService.getPlans())
			{
				if (p.getPendingGroup() != null && groups.contains(p.getPendingGroup()))
				{
					clean.remove(p.getPendingGroup());
					clean.remove(p.getCustomerGroup());
				}
			}
			final SocioPlanModel paid = socioPlanService.memberPlan(customer); // o plano comprado continua valendo
			if (paid != null && paid.getCustomerGroup() != null)
			{
				clean.add(paid.getCustomerGroup());
			}
			e.plan = bestPlan(clean);
			e.planInCart = false;
		}
		final int planWave = e.plan == null || e.plan.getWave() == null ? 0 : e.plan.getWave().intValue();
		if (e.plan == null)
		{
			e.ok("Não é sócio: compra na venda geral");
		}
		else
		{
			e.ok("Sócio " + planName(e.plan) + (planWave > 0 ? " (" + planWave + "ª onda)" : " (sem prioridade de onda)")
					+ (e.planInCart ? ", plano no carrinho" : ""));
		}

		// 2. situação: suspenso perde a onda de sócio e compra na venda geral
		e.suspended = e.plan != null && groups.stream().anyMatch(g -> SocioPlanService.SUSPENDED_GROUP.equals(g.getUid()));
		if (e.plan != null)
		{
			if (e.suspended)
			{
				e.warn("Benefícios suspensos por mensalidade em atraso: compra só na venda geral");
			}
			else
			{
				e.ok("Mensalidade em dia");
			}
		}

		// 3. janela da onda
		e.memberWave = e.plan == null || planWave <= 0 || e.suspended ? GENERAL_SALE : planWave;
		e.openWave = socioCartService.currentWave(game, now);
		if (e.openWave >= e.memberWave)
		{
			e.ok(e.openWave >= GENERAL_SALE ? "Venda geral aberta" : e.openWave + "ª onda aberta (a sua é a " + e.memberWave + "ª)");
		}
		else
		{
			final String opens = when(waveStart(game, e.memberWave));
			if (e.suspended)
			{
				e.deny("Benefícios suspensos por mensalidade em atraso: a compra só abre na venda geral, " + opens
						+ ". Regularize o pagamento para voltar à sua onda.");
			}
			else if (e.memberWave >= GENERAL_SALE)
			{
				e.deny("A venda geral deste jogo abre " + opens + (e.openWave > 0 ? "; agora só sócios compram." : "."));
			}
			else
			{
				e.deny("A sua onda (" + e.memberWave + "ª) abre " + opens
						+ (e.openWave > 0 ? "; agora está aberta a " + e.openWave + "ª onda." : "; a venda ainda não abriu."));
			}
		}

		// 4. biometria facial (informativa, exigida só com a chave ligada)
		e.biometry = groups.stream().anyMatch(g -> BIOMETRY_GROUP.equals(g.getUid()));
		e.biometryRequired = configurationService.getConfiguration().getBoolean(BIOMETRY_REQUIRED_PROPERTY, false);
		if (e.biometry)
		{
			e.ok("Biometria facial validada");
		}
		else if (e.biometryRequired)
		{
			e.deny("Biometria facial ainda não validada: valide a biometria no app do Flamengo antes de comprar.");
		}
		else
		{
			e.warn("Biometria facial pendente (ainda não exigida)");
		}

		// 5. limite de ingressos por jogo: titular + vagas de convidado contratadas (ou o máximo do
		// plano, com flamengo.socio.convidados.limiteContratado=false); quem não é sócio, o limite geral
		final boolean contracted = socioGuestService != null && configurationService.getConfiguration()
				.getBoolean(SocioGuestService.CONTRACTED_LIMIT_PROPERTY, true);
		e.ticketLimit = e.plan != null && !e.suspended
				? 1 + (contracted ? socioGuestService.ticketGuestSlots(customer, cart)
						: e.plan.getMaxGuests() == null ? 0 : Math.max(0, e.plan.getMaxGuests().intValue()))
				: configurationService.getConfiguration().getInt(GENERAL_LIMIT_PROPERTY, DEFAULT_GENERAL_LIMIT);
		e.ticketsOrdered = countOrdered(customer, game);
		e.ticketsInCart = countInCart(cart, game, seat);
		final long total = e.ticketsOrdered + e.ticketsInCart + Math.max(1, quantity);
		final String whose = e.plan != null && !e.suspended ? "do " + planName(e.plan) + " (titular + convidados contratados)"
				: e.plan != null ? "com os benefícios suspensos" : "para quem não é sócio";
		if (total <= e.ticketLimit)
		{
			e.ok("Ingressos neste jogo: " + total + " de " + e.ticketLimit);
		}
		else
		{
			e.deny("Limite de " + e.ticketLimit + " ingresso(s) por jogo " + whose + ": você já tem "
					+ (e.ticketsOrdered + e.ticketsInCart) + " (" + e.ticketsOrdered + " em pedidos e " + e.ticketsInCart
					+ " no carrinho).");
		}
		return e;
	}

	/** O carrinho tem um plano do Nação */
	protected boolean hasPlan(final CartModel cart)
	{
		return cart.getEntries() != null && cart.getEntries().stream()
				.anyMatch(entry -> socioPlanService.findForProduct(entry.getProduct()) != null);
	}

	@Override
	public void checkAddToCart(final CartModel cart, final ProductModel seat, final long quantity)
			throws CommerceCartModificationException
	{
		final SocioEligibility result;
		final MatchdayGameModel game;
		final CustomerModel customer;
		try
		{
			customer = customer(cart);
			game = customer == null ? null : gameOf(seat);
			if (game == null)
			{
				return;
			}
			result = evaluate(customer, game, cart, seat, quantity, new Date());
		}
		catch (final RuntimeException ex)
		{
			// nunca impede a compra por erro na conferência
			LOG.error("Falha ao conferir a elegibilidade da poltrona " + (seat == null ? "" : seat.getCode()), ex);
			return;
		}
		final boolean blocked = !result.eligible && isBlocking();
		record(customer, game, cart, seat, result, blocked);
		if (!result.eligible)
		{
			LOG.info("Elegibilidade {} {}: negado{} - {}", customer.getUid(), seat.getCode(), blocked ? " e recusado" : " (observação)",
					String.join(" | ", result.reasons));
		}
		if (blocked)
		{
			throw new CommerceCartModificationException(String.join(" ", result.reasons));
		}
	}

	protected void record(final CustomerModel customer, final MatchdayGameModel game, final CartModel cart,
			final ProductModel seat, final SocioEligibility result, final boolean blocked)
	{
		if (!configurationService.getConfiguration().getBoolean(RECORD_PROPERTY, true))
		{
			return;
		}
		try
		{
			final SocioEligibilityDecisionModel decision = modelService.create(SocioEligibilityDecisionModel.class);
			decision.setDate(new Date());
			decision.setCustomer(customer);
			decision.setGame(game);
			decision.setGameCode(game.getCode());
			decision.setProduct(seat);
			decision.setCartCode(cart == null ? null : cart.getCode());
			decision.setEligible(Boolean.valueOf(result.eligible));
			decision.setBlocked(Boolean.valueOf(blocked));
			decision.setReasons(String.join("\n", result.reasons));
			decision.setChecks(String.join("\n", result.checks));
			decision.setPlan(result.plan);
			decision.setMemberWave(Integer.valueOf(result.memberWave));
			decision.setOpenWave(Integer.valueOf(result.openWave));
			decision.setTickets(Integer.valueOf(result.ticketsOrdered + result.ticketsInCart));
			decision.setTicketLimit(Integer.valueOf(result.ticketLimit));
			decision.setOrigin("Carrinho");
			modelService.save(decision);
		}
		catch (final RuntimeException ex)
		{
			LOG.error("Falha ao gravar a decisão de elegibilidade de " + customer.getUid(), ex);
		}
	}

	// ------------------------------------------------------------ apoio

	/** Melhor plano do torcedor (onda menor; sem prioridade por último), contando o plano no carrinho */
	protected SocioPlanModel bestPlan(final Set<PrincipalGroupModel> groups)
	{
		SocioPlanModel best = null;
		for (final SocioPlanModel plan : socioPlanService.getPlans())
		{
			if (plan.getCustomerGroup() != null && groups.contains(plan.getCustomerGroup())
					&& (best == null || rank(plan) < rank(best)))
			{
				best = plan;
			}
		}
		return best;
	}

	protected static int rank(final SocioPlanModel plan)
	{
		final int wave = plan.getWave() == null ? 0 : plan.getWave().intValue();
		return wave <= 0 ? 99 : wave;
	}

	/** Ingressos do jogo nos pedidos do torcedor (sem versões antigas nem pedidos cancelados) */
	protected int countOrdered(final CustomerModel customer, final MatchdayGameModel game)
	{
		int count = 0;
		if (customer.getOrders() == null)
		{
			return 0;
		}
		for (final OrderModel order : customer.getOrders())
		{
			final String status = order.getStatus() == null ? "" : order.getStatus().getCode();
			if (order.getVersionID() != null || "CANCELLED".equals(status) || "CANCELLING".equals(status))
			{
				continue;
			}
			count += countSeats(order, game, null);
		}
		return count;
	}

	/** Ingressos do jogo no carrinho, fora a poltrona que está entrando */
	protected int countInCart(final CartModel cart, final MatchdayGameModel game, final ProductModel seat)
	{
		return cart == null ? 0 : countSeats(cart, game, seat);
	}

	protected int countSeats(final AbstractOrderModel order, final MatchdayGameModel game, final ProductModel skip)
	{
		int count = 0;
		if (order.getEntries() == null)
		{
			return 0;
		}
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final ProductModel product = entry.getProduct();
			if (product == null || modelService.isRemoved(entry) || product.equals(skip) || !seatHoldService.isSeat(product))
			{
				continue;
			}
			final ProductModel base = product instanceof VariantProductModel ? ((VariantProductModel) product).getBaseProduct()
					: null;
			if (base != null && game.getCode().equals(base.getCode()))
			{
				count += entry.getQuantity() == null ? 1 : Math.max(0, entry.getQuantity().intValue());
			}
		}
		return count;
	}

	@Override
	public MatchdayGameModel gameOf(final ProductModel seat)
	{
		if (seat == null || !seatHoldService.isSeat(seat) || !(seat instanceof VariantProductModel))
		{
			return null;
		}
		final ProductModel base = ((VariantProductModel) seat).getBaseProduct();
		if (base == null)
		{
			return null;
		}
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {" + MatchdayGameModel.PK + "} FROM {" + MatchdayGameModel._TYPECODE + "} WHERE {" + MatchdayGameModel.CODE
						+ "} = ?code");
		query.addQueryParameter("code", base.getCode());
		final List<MatchdayGameModel> result = flexibleSearchService.<MatchdayGameModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected static Date waveStart(final MatchdayGameModel game, final int wave)
	{
		switch (wave)
		{
			case 1:
				return game.getWave1Start();
			case 2:
				return game.getWave2Start();
			case 3:
				return game.getWave3Start();
			default:
				return game.getGeneralSaleStart();
		}
	}

	protected static String when(final Date date)
	{
		if (date == null)
		{
			return "em data a definir";
		}
		final SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", PT);
		format.setTimeZone(TZ);
		return "em " + format.format(date);
	}

	protected static String planName(final SocioPlanModel plan)
	{
		return StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode());
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

	// ------------------------------------------------------------ biometria e chaves

	@Override
	public boolean hasBiometry(final CustomerModel customer)
	{
		return customer != null && customer.getGroups() != null
				&& customer.getGroups().stream().anyMatch(g -> BIOMETRY_GROUP.equals(g.getUid()));
	}

	@Override
	public boolean setBiometry(final CustomerModel customer, final boolean validated)
	{
		if (hasBiometry(customer) == validated)
		{
			return false;
		}
		final Set<PrincipalGroupModel> groups = new HashSet<>();
		if (customer.getGroups() != null)
		{
			groups.addAll(customer.getGroups());
		}
		if (validated)
		{
			groups.add(biometryGroup());
		}
		else
		{
			groups.removeIf(g -> BIOMETRY_GROUP.equals(g.getUid()));
		}
		customer.setGroups(groups);
		return true;
	}

	/** Grupo de biometria validada, dentro de customergroup (a API da loja só lista esses) */
	protected UserGroupModel biometryGroup()
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {UserGroup} WHERE {uid}=?u");
		query.addQueryParameter("u", BIOMETRY_GROUP);
		final List<UserGroupModel> found = flexibleSearchService.<UserGroupModel> search(query).getResult();
		if (!found.isEmpty())
		{
			return found.get(0);
		}
		final UserGroupModel group = modelService.create(UserGroupModel.class);
		group.setUid(BIOMETRY_GROUP);
		group.setLocName("Biometria facial validada", PT);
		group.setLocName("Face biometrics validated", Locale.ENGLISH);
		group.setGroups(Collections.singleton(userService.getUserGroupForUID("customergroup")));
		modelService.save(group);
		return group;
	}

	@Override
	public boolean isBlocking()
	{
		return configurationService.getConfiguration().getBoolean(BLOCK_PROPERTY, false);
	}

	// ------------------------------------------------------------ dependências

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}

	public void setSocioCartService(final SocioCartService socioCartService)
	{
		this.socioCartService = socioCartService;
	}

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setSocioGuestService(final SocioGuestService socioGuestService)
	{
		this.socioGuestService = socioGuestService;
	}
}
