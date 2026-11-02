package br.com.flamengo.matchday.socio.impl;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.model.MatchdayGameModel;
import br.com.flamengo.matchday.model.SocioEventModel;
import br.com.flamengo.matchday.model.SocioGuestModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.socio.SocioGuestException;
import br.com.flamengo.matchday.socio.SocioGuestOverview;
import br.com.flamengo.matchday.service.SeatHoldService;
import br.com.flamengo.matchday.socio.SocioGuestService;
import br.com.flamengo.matchday.socio.SocioGuestTicket;
import br.com.flamengo.matchday.socio.SocioPlanService;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.variants.model.VariantProductModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/** Regras do cadastro de convidados ({@link SocioGuestService}). */
public class DefaultSocioGuestService implements SocioGuestService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSocioGuestService.class);
	private static final Locale PT = new Locale("pt");
	private static final String GUEST_PREFIX = "NACAO-CONVIDADO-";
	private static final int MAX_ENDED = 10;
	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private SocioPlanService socioPlanService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private ConfigurationService configurationService;
	private SeatHoldService seatHoldService;

	@Override
	public boolean isEnabled()
	{
		return configurationService.getConfiguration().getBoolean(ENABLED_PROPERTY, true);
	}

	// ------------------------------------------------------------ leitura

	@Override
	public SocioGuestOverview overview(final CustomerModel owner)
	{
		final SocioGuestOverview o = new SocioGuestOverview();
		o.enabled = isEnabled();
		final SocioPlanModel plan = socioPlanService.memberPlan(owner);
		if (plan != null)
		{
			o.planCode = plan.getCode();
			o.planName = StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode());
			o.planMaxGuests = plan.getMaxGuests() == null ? 0 : plan.getMaxGuests().intValue();
			o.guestPrice = plan.getGuestPrice() == null ? 0 : plan.getGuestPrice().doubleValue();
			o.suspended = socioPlanService.isSuspended(owner);
			o.contracted = contractedSlots(owner);
		}
		for (final SocioGuestModel guest : guests(owner, false))
		{
			if (Boolean.TRUE.equals(guest.getActive()))
			{
				o.active.add(guest);
			}
			else if (o.ended.size() < MAX_ENDED)
			{
				o.ended.add(guest);
			}
		}
		o.reason = refusal(o);
		o.canAdd = o.reason == null;
		o.tickets.addAll(tickets(owner));
		return o;
	}

	/** Por que o titular não pode cadastrar mais um convidado (null = pode) */
	protected String refusal(final SocioGuestOverview o)
	{
		if (!o.enabled)
		{
			return "O cadastro de convidados está desligado.";
		}
		if (o.planCode == null)
		{
			return "Só sócios do Nação têm convidados.";
		}
		if (o.suspended)
		{
			return "Seus benefícios estão suspensos por mensalidade em atraso; regularize para incluir convidados.";
		}
		if (o.contracted <= 0)
		{
			return o.planMaxGuests > 0
					? "Seu plano permite até " + o.planMaxGuests
							+ " convidado(s), mas nenhuma vaga foi contratada na adesão. A vaga de convidado é escolhida na compra do plano."
					: "O plano " + o.planName + " não tem convidados.";
		}
		if (o.active.size() >= o.contracted)
		{
			return "Todas as " + o.contracted + " vaga(s) de convidado contratadas estão ocupadas. Tire um convidado para incluir outro.";
		}
		return null;
	}

	@Override
	public List<SocioGuestModel> guests(final CustomerModel owner, final boolean onlyActive)
	{
		if (owner == null)
		{
			return Collections.emptyList();
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {SocioGuest} WHERE {customer}=?o"
				+ (onlyActive ? " AND {active}=?t" : "") + " ORDER BY {since} DESC, {creationtime} DESC");
		q.addQueryParameter("o", owner);
		if (onlyActive)
		{
			q.addQueryParameter("t", Boolean.TRUE);
		}
		return flexibleSearchService.<SocioGuestModel> search(q).getResult();
	}

	@Override
	public int contractedSlots(final CustomerModel owner)
	{
		if (owner == null || socioPlanService.memberPlan(owner) == null)
		{
			return 0;
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {user}=?c AND {versionID} IS NULL ORDER BY {date} DESC");
		q.addQueryParameter("c", owner);
		for (final OrderModel order : flexibleSearchService.<OrderModel> search(q).getResult())
		{
			final List<AbstractOrderEntryModel> entries = order.getEntries() == null ? Collections.emptyList()
					: order.getEntries();
			final boolean planOrder = entries.stream()
					.anyMatch(e -> socioPlanService.findForProduct(e.getProduct()) != null || isGuestProduct(e.getProduct()));
			if (planOrder)
			{
				// o último pedido do plano manda (mudança de plano refaz a escolha de convidados)
				return entries.stream().filter(e -> isGuestProduct(e.getProduct()))
						.mapToInt(e -> e.getQuantity() == null ? 0 : e.getQuantity().intValue()).sum();
			}
		}
		return 0;
	}

	protected static boolean isGuestProduct(final ProductModel product)
	{
		return product != null && product.getCode() != null && product.getCode().startsWith(GUEST_PREFIX);
	}

	// ------------------------------------------------------------ escrita

	@Override
	public SocioGuestModel add(final CustomerModel owner, final String name, final String cpf, final String email,
			final String phone, final Date birthDate) throws SocioGuestException
	{
		final SocioGuestOverview o = overview(owner);
		if (!o.canAdd)
		{
			throw new SocioGuestException(o.reason);
		}
		final String cleanName = StringUtils.normalizeSpace(name);
		if (StringUtils.length(cleanName) < 3 || !cleanName.contains(" "))
		{
			throw new SocioGuestException("Informe o nome completo do convidado.");
		}
		final String cpfDigits = SocioGuestService.digits(cpf);
		if (!SocioGuestService.isValidCpf(cpfDigits))
		{
			throw new SocioGuestException("CPF inválido: confira os números.");
		}
		final String cleanEmail = StringUtils.trimToEmpty(email).toLowerCase(Locale.ROOT);
		if (!EMAIL.matcher(cleanEmail).matches())
		{
			throw new SocioGuestException("Informe um e-mail válido do convidado.");
		}
		final String phoneDigits = SocioGuestService.digits(phone);
		if (!phoneDigits.isEmpty() && (phoneDigits.length() < 10 || phoneDigits.length() > 11))
		{
			throw new SocioGuestException("Celular inválido: use DDD e número, com 10 ou 11 dígitos.");
		}
		checkBirthDate(birthDate);
		checkDuplicate(owner, cpfDigits);

		final SocioGuestModel guest = modelService.create(SocioGuestModel.class);
		guest.setCustomer(owner);
		guest.setName(cleanName);
		guest.setCpf(cpfDigits);
		guest.setEmail(cleanEmail);
		guest.setPhone(phoneDigits.isEmpty() ? null : phoneDigits);
		guest.setBirthDate(birthDate);
		guest.setPlan(socioPlanService.memberPlan(owner));
		guest.setActive(Boolean.TRUE);
		guest.setSince(new Date());
		modelService.save(guest);
		note(owner, "Convidado incluído: " + cleanName + " (CPF " + SocioGuestService.maskCpf(cpfDigits) + ")",
				(o.active.size() + 1) + " de " + o.contracted + " vaga(s) de convidado ocupada(s)");
		return guest;
	}

	protected void checkBirthDate(final Date birthDate) throws SocioGuestException
	{
		if (birthDate == null)
		{
			throw new SocioGuestException("Informe a data de nascimento do convidado.");
		}
		final Calendar oldest = Calendar.getInstance();
		oldest.add(Calendar.YEAR, -120);
		if (birthDate.after(new Date()) || birthDate.before(oldest.getTime()))
		{
			throw new SocioGuestException("Data de nascimento inválida.");
		}
	}

	protected void checkDuplicate(final CustomerModel owner, final String cpfDigits) throws SocioGuestException
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {SocioGuest} WHERE {cpf}=?cpf AND {active}=?t");
		q.addQueryParameter("cpf", cpfDigits);
		q.addQueryParameter("t", Boolean.TRUE);
		final List<SocioGuestModel> same = flexibleSearchService.<SocioGuestModel> search(q).getResult();
		if (!same.isEmpty())
		{
			throw new SocioGuestException(same.stream().anyMatch(g -> owner.equals(g.getCustomer()))
					? "Este CPF já é seu convidado." : "Este CPF já é convidado de outro sócio.");
		}
	}

	@Override
	public void remove(final CustomerModel owner, final String guestId) throws SocioGuestException
	{
		final SocioGuestModel guest = find(owner, guestId);
		if (guest == null)
		{
			throw new SocioGuestException("Convidado não encontrado.");
		}
		if (!Boolean.TRUE.equals(guest.getActive()))
		{
			throw new SocioGuestException("Este convidado já foi retirado.");
		}
		checkNoFutureTicket(guest);
		end(guest, END_BY_OWNER);
		modelService.save(guest);
		note(owner, "Convidado retirado: " + guest.getName() + " (CPF " + SocioGuestService.maskCpf(guest.getCpf()) + ")",
				"Vaga de convidado liberada");
	}

	/**
	 * Convidado com ingresso de jogo futuro num pedido não pode sair (o nome dele está
	 * no ingresso). Se a poltrona dele ainda está só no carrinho, ela volta a ser do titular.
	 */
	protected void checkNoFutureTicket(final SocioGuestModel guest) throws SocioGuestException
	{
		final String id = guest.getPk().toString();
		for (final SocioGuestTicket t : tickets(guest.getCustomer()))
		{
			if (id.equals(t.guestId) && t.orderCode != null)
			{
				throw new SocioGuestException(guest.getName() + " tem ingresso para " + t.gameName + " em "
						+ day(t.kickoff) + " (pedido " + t.orderCode + "); não dá para tirar antes do jogo.");
			}
		}
		for (final AbstractOrderEntryModel entry : guestEntries(guest.getCustomer()))
		{
			if (entry.getOrder() instanceof CartModel && guest.equals(modelService.getAttributeValue(entry, ENTRY_GUEST)))
			{
				setGuest(entry, null);
			}
		}
	}

	// ------------------------------------------------------------ ingressos dos convidados

	@Override
	public int ticketGuestSlots(final CustomerModel owner, final AbstractOrderModel cart)
	{
		if (socioPlanService.memberPlan(owner) != null)
		{
			return contractedSlots(owner);
		}
		// plano ainda no carrinho: as vagas de convidado que estão junto
		return cart == null || cart.getEntries() == null ? 0
				: cart.getEntries().stream().filter(e -> isGuestProduct(e.getProduct()))
						.mapToInt(e -> e.getQuantity() == null ? 0 : e.getQuantity().intValue()).sum();
	}

	@Override
	public List<SocioGuestTicket> tickets(final CustomerModel owner)
	{
		final List<SocioGuestTicket> out = new ArrayList<>();
		final Date now = new Date();
		for (final AbstractOrderEntryModel entry : guestEntries(owner))
		{
			final AbstractOrderModel order = entry.getOrder();
			if (order instanceof OrderModel && !liveOrder((OrderModel) order))
			{
				continue;
			}
			final MatchdayGameModel game = gameOf(entry.getProduct());
			if (game == null || (game.getKickoff() != null && game.getKickoff().before(now)))
			{
				continue;
			}
			final SocioGuestModel guest = (SocioGuestModel) modelService.getAttributeValue(entry, ENTRY_GUEST);
			final SocioGuestTicket t = new SocioGuestTicket();
			t.guestId = guest.getPk().toString();
			t.holder = StringUtils.defaultIfBlank((String) modelService.getAttributeValue(entry, ENTRY_HOLDER), guest.getName());
			t.seatCode = entry.getProduct().getCode();
			t.seatName = StringUtils.defaultIfBlank(entry.getProduct().getName(PT), t.seatCode);
			t.gameCode = game.getCode();
			t.gameName = gameName(game);
			t.kickoff = game.getKickoff();
			if (order instanceof OrderModel)
			{
				t.orderCode = order.getCode();
			}
			else
			{
				t.cartCode = order.getCode();
			}
			out.add(t);
		}
		out.sort((a, b) -> a.kickoff == null || b.kickoff == null ? 0 : a.kickoff.compareTo(b.kickoff));
		return out;
	}

	/** Itens (carrinhos e pedidos) do titular marcados para algum convidado */
	protected List<AbstractOrderEntryModel> guestEntries(final CustomerModel owner)
	{
		if (owner == null)
		{
			return Collections.emptyList();
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {e.pk} FROM {AbstractOrderEntry AS e JOIN AbstractOrder AS o"
				+ " ON {e.order}={o.pk}} WHERE {o.user}=?u AND {e." + ENTRY_GUEST + "} IS NOT NULL");
		q.addQueryParameter("u", owner);
		return flexibleSearchService.<AbstractOrderEntryModel> search(q).getResult();
	}

	protected static boolean liveOrder(final OrderModel order)
	{
		return order.getVersionID() == null && order.getStatus() != OrderStatus.CANCELLED
				&& order.getStatus() != OrderStatus.CANCELLING;
	}

	@Override
	public void assign(final CustomerModel owner, final String cartCode, final String seatCode, final String guestId)
			throws SocioGuestException
	{
		final AbstractOrderEntryModel entry = seatEntry(owner, cartCode, seatCode);
		if (StringUtils.isBlank(guestId))
		{
			setGuest(entry, null);
			return;
		}
		if (!isEnabled())
		{
			throw new SocioGuestException("O ingresso para convidado está desligado.");
		}
		final SocioGuestModel guest = find(owner, guestId);
		if (guest == null || !Boolean.TRUE.equals(guest.getActive()))
		{
			throw new SocioGuestException("Convidado não encontrado entre os seus convidados ativos.");
		}
		final MatchdayGameModel game = gameOf(entry.getProduct());
		for (final SocioGuestTicket t : tickets(owner))
		{
			if (t.guestId.equals(guestId) && game != null && game.getCode().equals(t.gameCode)
					&& !t.seatCode.equals(seatCode))
			{
				throw new SocioGuestException(guest.getName() + " já tem ingresso para este jogo ("
						+ (t.orderCode != null ? "pedido " + t.orderCode : "no carrinho") + ").");
			}
		}
		setGuest(entry, guest);
	}

	/** Poltrona no carrinho do titular */
	protected AbstractOrderEntryModel seatEntry(final CustomerModel owner, final String cartCode, final String seatCode)
			throws SocioGuestException
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {Cart} WHERE {code}=?c AND {user}=?u");
		q.addQueryParameter("c", StringUtils.trimToEmpty(cartCode));
		q.addQueryParameter("u", owner);
		final List<CartModel> carts = flexibleSearchService.<CartModel> search(q).getResult();
		if (carts.isEmpty())
		{
			throw new SocioGuestException("Carrinho não encontrado.");
		}
		for (final AbstractOrderEntryModel entry : carts.get(0).getEntries())
		{
			if (entry.getProduct() != null && entry.getProduct().getCode().equals(seatCode)
					&& seatHoldService.isSeat(entry.getProduct()))
			{
				return entry;
			}
		}
		throw new SocioGuestException("A poltrona " + seatCode + " não está no seu carrinho.");
	}

	protected void setGuest(final AbstractOrderEntryModel entry, final SocioGuestModel guest)
	{
		modelService.setAttributeValue(entry, ENTRY_GUEST, guest);
		modelService.setAttributeValue(entry, ENTRY_HOLDER, guest == null ? null : guest.getName());
		modelService.save(entry);
	}

	@Override
	public void checkCart(final CartModel cart) throws SocioGuestException
	{
		if (cart == null || cart.getEntries() == null || !(cart.getUser() instanceof CustomerModel)
				|| !configurationService.getConfiguration().getBoolean(NOMINAL_PROPERTY, true))
		{
			return;
		}
		final CustomerModel owner = (CustomerModel) cart.getUser();
		if (socioPlanService.memberPlan(owner) == null)
		{
			return; // quem não é sócio (ou tem o plano só no carrinho) segue como antes
		}
		final Map<String, List<AbstractOrderEntryModel>> byGame = new LinkedHashMap<>();
		final Map<String, MatchdayGameModel> games = new HashMap<>();
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			final MatchdayGameModel game = gameOf(entry.getProduct());
			if (game != null)
			{
				byGame.computeIfAbsent(game.getCode(), k -> new ArrayList<>()).add(entry);
				games.put(game.getCode(), game);
			}
		}
		for (final Map.Entry<String, List<AbstractOrderEntryModel>> g : byGame.entrySet())
		{
			final MatchdayGameModel game = games.get(g.getKey());
			final boolean ownTicket = ownTicketOrdered(owner, game);
			int withoutGuest = 0;
			final List<String> seen = new ArrayList<>();
			for (final AbstractOrderEntryModel entry : g.getValue())
			{
				final Object guest = modelService.getAttributeValue(entry, ENTRY_GUEST);
				if (!(guest instanceof SocioGuestModel))
				{
					withoutGuest += entry.getQuantity() == null ? 1 : entry.getQuantity().intValue();
					continue;
				}
				final SocioGuestModel sg = (SocioGuestModel) guest;
				if (!Boolean.TRUE.equals(sg.getActive()) || !owner.equals(sg.getCustomer()))
				{
					throw new SocioGuestException(sg.getName() + " não é mais seu convidado: escolha outra pessoa para a poltrona "
							+ entry.getProduct().getCode() + ".");
				}
				if (seen.contains(sg.getPk().toString()) || orderedForGuest(owner, sg, game))
				{
					throw new SocioGuestException(sg.getName() + " já tem ingresso para " + gameName(game) + ".");
				}
				seen.add(sg.getPk().toString());
				if (!sg.getName().equals(modelService.getAttributeValue(entry, ENTRY_HOLDER)))
				{
					modelService.setAttributeValue(entry, ENTRY_HOLDER, sg.getName());
					modelService.save(entry);
				}
			}
			final int allowed = ownTicket ? 0 : 1;
			if (withoutGuest > allowed)
			{
				throw new SocioGuestException("Cada ingresso além do seu para " + gameName(game)
						+ " precisa ter o nome de um convidado cadastrado em Meu sócio-torcedor › Meus convidados.");
			}
		}
	}

	/** O titular já comprou o próprio ingresso (poltrona sem convidado) para o jogo */
	protected boolean ownTicketOrdered(final CustomerModel owner, final MatchdayGameModel game)
	{
		return orderedEntries(owner, game).stream().anyMatch(e -> modelService.getAttributeValue(e, ENTRY_GUEST) == null);
	}

	protected boolean orderedForGuest(final CustomerModel owner, final SocioGuestModel guest, final MatchdayGameModel game)
	{
		return orderedEntries(owner, game).stream().anyMatch(e -> guest.equals(modelService.getAttributeValue(e, ENTRY_GUEST)));
	}

	/** Poltronas do jogo em pedidos válidos do titular */
	protected List<AbstractOrderEntryModel> orderedEntries(final CustomerModel owner, final MatchdayGameModel game)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {o.pk} FROM {Order AS o} WHERE {o.user}=?u AND {o.versionID} IS NULL");
		q.addQueryParameter("u", owner);
		final List<AbstractOrderEntryModel> out = new ArrayList<>();
		for (final OrderModel order : flexibleSearchService.<OrderModel> search(q).getResult())
		{
			if (liveOrder(order) && order.getEntries() != null)
			{
				order.getEntries().stream().filter(e -> {
					final MatchdayGameModel g = gameOf(e.getProduct());
					return g != null && g.getCode().equals(game.getCode());
				}).forEach(out::add);
			}
		}
		return out;
	}

	/** Jogo da poltrona (a poltrona é variante do produto do jogo); null se não é poltrona */
	protected MatchdayGameModel gameOf(final ProductModel product)
	{
		if (product == null || !seatHoldService.isSeat(product) || !(product instanceof VariantProductModel)
				|| ((VariantProductModel) product).getBaseProduct() == null)
		{
			return null;
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {MatchdayGame} WHERE {code}=?code");
		q.addQueryParameter("code", ((VariantProductModel) product).getBaseProduct().getCode());
		final List<MatchdayGameModel> games = flexibleSearchService.<MatchdayGameModel> search(q).getResult();
		return games.isEmpty() ? null : games.get(0);
	}

	protected static String gameName(final MatchdayGameModel game)
	{
		return game == null ? "" : "Flamengo x " + StringUtils.defaultIfBlank(game.getOpponent(), game.getCode());
	}

	protected static String day(final Date date)
	{
		if (date == null)
		{
			return "";
		}
		final java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd/MM/yyyy");
		f.setTimeZone(java.util.TimeZone.getTimeZone("America/Sao_Paulo"));
		return f.format(date);
	}

	protected SocioGuestModel find(final CustomerModel owner, final String guestId)
	{
		if (owner == null || StringUtils.isBlank(guestId) || !StringUtils.isNumeric(guestId))
		{
			return null;
		}
		try
		{
			final Object item = modelService.get(PK.parse(guestId));
			return item instanceof SocioGuestModel && owner.equals(((SocioGuestModel) item).getCustomer())
					? (SocioGuestModel) item : null;
		}
		catch (final Exception e)
		{
			return null;
		}
	}

	@Override
	public List<SocioGuestModel> endAll(final CustomerModel owner, final String reason)
	{
		final List<SocioGuestModel> out = new ArrayList<>();
		for (final SocioGuestModel guest : guests(owner, true))
		{
			end(guest, reason);
			out.add(guest);
		}
		return out;
	}

	protected void end(final SocioGuestModel guest, final String reason)
	{
		guest.setActive(Boolean.FALSE);
		guest.setUntil(new Date());
		guest.setEndReason(reason);
	}

	/** Observação na linha do tempo do sócio; falha aqui não desfaz o cadastro */
	protected void note(final CustomerModel owner, final String note, final String result)
	{
		try
		{
			final SocioEventModel event = socioPlanService.newEvent(owner, SocioEventType.OBSERVACAO, "Loja", note);
			event.setResult(result);
			modelService.save(event);
		}
		catch (final Exception e)
		{
			LOG.warn("Convidados de {}: não gravou o evento ({}): {}", owner.getUid(), note, e.getMessage());
		}
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
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

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}
}
