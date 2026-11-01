package br.com.flamengo.matchday.socio.impl;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.model.SocioEventModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.socio.SocioCancelTicketInterceptor;
import br.com.flamengo.matchday.socio.SocioMember;
import br.com.flamengo.matchday.socio.SocioMemberService;
import br.com.flamengo.matchday.socio.SocioPlanService;
import de.hybris.platform.core.HybrisEnumValue;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.CreditCardPaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;


/**
 * Busca, ficha e ações da tela Sócio-Torcedor › Sócios. Leitura sem restrições de
 * busca (o gestor vê todos os sócios); as ações gravam com o usuário da sessão,
 * que vira o autor do evento na linha do tempo.
 */
public class DefaultSocioMemberService implements SocioMemberService
{
	private static final Locale PT = new Locale("pt");
	private static final String GUEST_PREFIX = "NACAO-CONVIDADO-";
	private static final int MAX_RESULTS = 200;

	private SocioPlanService socioPlanService;
	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private UserService userService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;
	private EnumerationService enumerationService;

	// ------------------------------------------------------------ leitura

	@Override
	public List<SocioMember> search(final String text, final String planCode, final SocioMember.Status status)
	{
		return unrestricted(() -> {
			final String wanted = normalize(text);
			final List<SocioMember> out = new ArrayList<>();
			for (final CustomerModel customer : candidates())
			{
				if (!wanted.isEmpty() && !normalize(customer.getName()).contains(wanted)
						&& !normalize(customer.getUid()).contains(wanted))
				{
					continue;
				}
				final SocioMember member = build(customer, false);
				if (member == null || (StringUtils.isNotBlank(planCode) && !planCode.equals(member.planCode))
						|| (status != null && status != member.status))
				{
					continue;
				}
				out.add(member);
			}
			out.sort(Comparator.comparing((SocioMember m) -> normalize(m.name)).thenComparing(m -> m.uid));
			return out.size() > MAX_RESULTS ? new ArrayList<>(out.subList(0, MAX_RESULTS)) : out;
		});
	}

	@Override
	public SocioMember detail(final String uid)
	{
		return unrestricted(() -> {
			final CustomerModel customer = customer(uid);
			return customer == null ? null : build(customer, true);
		});
	}

	/** Torcedores no grupo de algum plano, com evento na linha do tempo ou pedido de cancelamento */
	protected Set<CustomerModel> candidates()
	{
		final Set<CustomerModel> out = new LinkedHashSet<>();
		for (final SocioPlanModel plan : socioPlanService.getPlans())
		{
			if (plan.getCustomerGroup() != null && plan.getCustomerGroup().getMembers() != null)
			{
				plan.getCustomerGroup().getMembers().stream().filter(CustomerModel.class::isInstance).map(CustomerModel.class::cast)
						.forEach(out::add);
			}
		}
		out.addAll(customersOf("SELECT DISTINCT {customer} FROM {SocioEvent}"));
		out.addAll(customersOf("SELECT DISTINCT {customer} FROM {CsTicket} WHERE {headline} LIKE '"
				+ SocioCancelTicketInterceptor.SUBJECT + "%'"));
		return out;
	}

	protected List<CustomerModel> customersOf(final String query)
	{
		final FlexibleSearchQuery fsq = new FlexibleSearchQuery(query);
		fsq.setResultClassList(Collections.singletonList(UserModel.class));
		final List<CustomerModel> out = new ArrayList<>();
		for (final Object user : flexibleSearchService.search(fsq).getResult())
		{
			if (user instanceof CustomerModel)
			{
				out.add((CustomerModel) user);
			}
		}
		return out;
	}

	/** Sócio, sócio suspenso, cancelando ou ex-sócio; null se nunca foi sócio */
	protected SocioMember build(final CustomerModel customer, final boolean full)
	{
		final List<ItemModel> tickets = cancelTickets(customer);
		final ItemModel openTicket = tickets.stream().filter(t -> !isClosed(t)).findFirst().orElse(null);
		final List<SocioEventModel> events = events(customer);
		final List<OrderModel> planOrders = planOrders(customer);

		SocioPlanModel plan = socioPlanService.memberPlan(customer);
		final SocioMember.Status status;
		final boolean suspended = plan != null && socioPlanService.isSuspended(customer);
		if (plan != null)
		{
			status = openTicket != null ? SocioMember.Status.CANCELLING
					: suspended ? SocioMember.Status.SUSPENDED : SocioMember.Status.ACTIVE;
		}
		else
		{
			final SocioEventModel cancel = events.stream().filter(e -> e.getType() == SocioEventType.CANCELAMENTO).findFirst()
					.orElse(null);
			final boolean closedTicket = tickets.stream().anyMatch(this::isClosed);
			if (cancel == null && !closedTicket)
			{
				return null; // nunca foi sócio (plano só no carrinho, por exemplo)
			}
			status = SocioMember.Status.INACTIVE;
			plan = cancel != null && cancel.getPlan() != null ? cancel.getPlan() : planOf(planOrders);
		}

		final SocioMember m = new SocioMember();
		m.uid = customer.getUid();
		m.name = StringUtils.defaultIfBlank(customer.getName(), customer.getUid());
		m.status = status;
		m.suspended = suspended;
		if (plan != null)
		{
			m.planCode = plan.getCode();
			m.planName = StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode());
		}
		m.since = planOrders.isEmpty() ? null : planOrders.get(0).getDate();
		if (m.since == null)
		{
			m.since = events.stream().filter(e -> e.getType() == SocioEventType.ADESAO).map(SocioEventModel::getDate)
					.min(Date::compareTo).orElse(null);
		}
		if (!full)
		{
			return m;
		}

		if (plan != null)
		{
			m.family = plan.getFamily();
			m.monthlyPrice = plan.getMonthlyPrice() == null ? 0 : plan.getMonthlyPrice().doubleValue();
			m.guestPrice = plan.getGuestPrice() == null ? 0 : plan.getGuestPrice().doubleValue();
			m.wave = plan.getWave() == null ? 0 : plan.getWave().intValue();
			m.ticketDiscount = plan.getTicketDiscount() == null ? 0 : plan.getTicketDiscount().intValue();
			m.storeDiscount = plan.getStoreDiscount() == null ? 0 : plan.getStoreDiscount().intValue();
		}
		if (!planOrders.isEmpty())
		{
			final OrderModel last = planOrders.get(planOrders.size() - 1);
			m.guests = last.getEntries() == null ? 0
					: last.getEntries().stream().filter(e -> isGuest(e.getProduct()))
							.mapToInt(e -> e.getQuantity() == null ? 0 : e.getQuantity().intValue()).sum();
		}
		if (m.since != null)
		{
			m.until = addMonths(m.since, 12);
			m.nextCharge = status == SocioMember.Status.INACTIVE ? null : nextCharge(m.since, new Date());
		}
		m.card = card(customer);
		m.openCancelTicket = openTicket == null ? null : (String) modelService.getAttributeValue(openTicket, "ticketID");

		for (final OrderModel order : planOrders)
		{
			final SocioMember.Order o = new SocioMember.Order();
			o.code = order.getCode();
			o.date = order.getDate();
			o.total = order.getTotalPrice() == null ? 0 : order.getTotalPrice().doubleValue();
			final List<String> items = new ArrayList<>();
			for (final AbstractOrderEntryModel entry : order.getEntries())
			{
				if (entry.getProduct() != null)
				{
					items.add((entry.getQuantity() == null ? 1 : entry.getQuantity()) + "x "
							+ StringUtils.defaultIfBlank(entry.getProduct().getName(PT), entry.getProduct().getCode()));
				}
			}
			o.items = String.join(", ", items);
			m.orders.add(o);
		}
		Collections.reverse(m.orders); // mais novo primeiro
		for (final ItemModel ticket : allTickets(customer))
		{
			final SocioMember.Ticket t = new SocioMember.Ticket();
			t.id = (String) modelService.getAttributeValue(ticket, "ticketID");
			t.headline = (String) modelService.getAttributeValue(ticket, "headline");
			final Object state = modelService.getAttributeValue(ticket, "state");
			t.state = state instanceof HybrisEnumValue ? ((HybrisEnumValue) state).getCode() : null;
			t.date = ticket.getCreationtime();
			m.tickets.add(t);
		}
		for (final SocioEventModel event : events)
		{
			final SocioMember.Event e = new SocioMember.Event();
			e.date = event.getDate() != null ? event.getDate() : event.getCreationtime();
			e.type = event.getType() == null ? null : event.getType().getCode();
			e.origin = event.getOrigin();
			e.author = event.getAuthor();
			e.note = event.getNote();
			e.result = event.getResult();
			m.events.add(e);
		}
		return m;
	}

	protected List<SocioEventModel> events(final CustomerModel customer)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {SocioEvent} WHERE {customer}=?c ORDER BY {date} DESC, {creationtime} DESC");
		q.addQueryParameter("c", customer);
		return flexibleSearchService.<SocioEventModel> search(q).getResult();
	}

	protected List<ItemModel> allTickets(final CustomerModel customer)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {CsTicket} WHERE {customer}=?c ORDER BY {creationtime} DESC");
		q.addQueryParameter("c", customer);
		return flexibleSearchService.<ItemModel> search(q).getResult();
	}

	/** Pedidos de cancelamento do torcedor, o mais novo primeiro */
	protected List<ItemModel> cancelTickets(final CustomerModel customer)
	{
		final List<ItemModel> out = new ArrayList<>();
		for (final ItemModel ticket : allTickets(customer))
		{
			final Object headline = modelService.getAttributeValue(ticket, "headline");
			if (headline instanceof String && ((String) headline).startsWith(SocioCancelTicketInterceptor.SUBJECT))
			{
				out.add(ticket);
			}
		}
		return out;
	}

	protected boolean isClosed(final ItemModel ticket)
	{
		final Object state = modelService.getAttributeValue(ticket, "state");
		return state instanceof HybrisEnumValue && "Closed".equalsIgnoreCase(((HybrisEnumValue) state).getCode());
	}

	/** Pedidos com plano ou convidado, do mais antigo para o mais novo */
	protected List<OrderModel> planOrders(final CustomerModel customer)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {user}=?c AND {versionID} IS NULL ORDER BY {date} ASC");
		q.addQueryParameter("c", customer);
		final List<OrderModel> out = new ArrayList<>();
		for (final OrderModel order : flexibleSearchService.<OrderModel> search(q).getResult())
		{
			if (order.getEntries() != null && order.getEntries().stream()
					.anyMatch(e -> socioPlanService.findForProduct(e.getProduct()) != null || isGuest(e.getProduct())))
			{
				out.add(order);
			}
		}
		return out;
	}

	protected SocioPlanModel planOf(final List<OrderModel> orders)
	{
		for (int i = orders.size() - 1; i >= 0; i--)
		{
			for (final AbstractOrderEntryModel entry : orders.get(i).getEntries())
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

	protected static boolean isGuest(final ProductModel product)
	{
		return product != null && product.getCode() != null && product.getCode().startsWith(GUEST_PREFIX);
	}

	protected String card(final CustomerModel customer)
	{
		final PaymentInfoModel info = customer.getDefaultPaymentInfo();
		if (!(info instanceof CreditCardPaymentInfoModel))
		{
			return null;
		}
		final CreditCardPaymentInfoModel card = (CreditCardPaymentInfoModel) info;
		final String number = StringUtils.defaultString(card.getNumber()).replaceAll("\\D", "");
		final String last = number.length() >= 4 ? number.substring(number.length() - 4) : number;
		final String brand = card.getType() == null ? "Cartão" : card.getType().getCode().toUpperCase(Locale.ROOT);
		return brand + " •••• " + last;
	}

	protected static Date addMonths(final Date date, final int months)
	{
		final Calendar c = Calendar.getInstance();
		c.setTime(date);
		c.add(Calendar.MONTH, months);
		return c.getTime();
	}

	/** Próxima data no mesmo dia do mês da adesão (último dia do mês quando ele não existe) */
	protected static Date nextCharge(final Date since, final Date now)
	{
		final Calendar start = Calendar.getInstance();
		start.setTime(since);
		final int day = start.get(Calendar.DAY_OF_MONTH);
		final Calendar c = Calendar.getInstance();
		c.setTime(now);
		for (int i = 0; i < 2; i++)
		{
			final Calendar candidate = (Calendar) c.clone();
			candidate.add(Calendar.MONTH, i);
			candidate.set(Calendar.DAY_OF_MONTH, Math.min(day, candidate.getActualMaximum(Calendar.DAY_OF_MONTH)));
			candidate.set(Calendar.HOUR_OF_DAY, 0);
			candidate.set(Calendar.MINUTE, 0);
			candidate.set(Calendar.SECOND, 0);
			candidate.set(Calendar.MILLISECOND, 0);
			if (candidate.getTime().after(now))
			{
				return candidate.getTime();
			}
		}
		return addMonths(now, 1);
	}

	// ------------------------------------------------------------ ações

	@Override
	public String suspend(final String uid, final String note)
	{
		final CustomerModel customer = memberOrFail(uid);
		if (socioPlanService.isSuspended(customer))
		{
			throw new IllegalStateException(customer.getName() + " já está com os benefícios suspensos.");
		}
		return record(customer, SocioEventType.SUSPENSAO,
				StringUtils.defaultIfBlank(note, "Mensalidade de " + month(new Date()) + " em atraso (simulação)"));
	}

	@Override
	public String regularize(final String uid, final String note)
	{
		final CustomerModel customer = memberOrFail(uid);
		if (!socioPlanService.isSuspended(customer))
		{
			throw new IllegalStateException(customer.getName() + " não está suspenso.");
		}
		return record(customer, SocioEventType.REGULARIZACAO, StringUtils.defaultIfBlank(note, "Pagamento confirmado"));
	}

	@Override
	public String addNote(final String uid, final String note)
	{
		final CustomerModel customer = customer(uid);
		if (customer == null)
		{
			throw new IllegalStateException("Torcedor não encontrado.");
		}
		if (StringUtils.isBlank(note))
		{
			throw new IllegalStateException("Escreva a observação.");
		}
		record(customer, SocioEventType.OBSERVACAO, note.trim());
		return "Observação registrada na linha do tempo.";
	}

	@Override
	public String confirmCancellation(final String uid)
	{
		final CustomerModel customer = memberOrFail(uid);
		final ItemModel ticket = unrestricted(
				() -> cancelTickets(customer).stream().filter(t -> !isClosed(t)).findFirst().orElse(null));
		if (ticket == null)
		{
			throw new IllegalStateException("Não há pedido de cancelamento aberto para " + customer.getName() + ".");
		}
		final SocioPlanModel plan = socioPlanService.memberPlan(customer);
		modelService.setAttributeValue(ticket, "state", (Object) enumerationService.getEnumerationValue("CsTicketState", "Closed"));
		modelService.save(ticket);
		return "Cancelamento confirmado: " + customer.getName() + " deixou de ser sócio"
				+ (plan == null ? "" : " do " + StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode())) + ".";
	}

	protected String record(final CustomerModel customer, final SocioEventType type, final String note)
	{
		final SocioEventModel event = socioPlanService.newEvent(customer, type, "Backoffice", note);
		modelService.save(event);
		return event.getResult();
	}

	protected CustomerModel memberOrFail(final String uid)
	{
		final CustomerModel customer = customer(uid);
		if (customer == null)
		{
			throw new IllegalStateException("Torcedor não encontrado.");
		}
		if (socioPlanService.memberPlan(customer) == null)
		{
			throw new IllegalStateException(customer.getName() + " não é sócio.");
		}
		return customer;
	}

	protected CustomerModel customer(final String uid)
	{
		try
		{
			final UserModel user = userService.getUserForUID(uid);
			return user instanceof CustomerModel ? (CustomerModel) user : null;
		}
		catch (final UnknownIdentifierException e)
		{
			return null;
		}
	}

	protected static String month(final Date date)
	{
		return new SimpleDateFormat("MMMM", PT).format(date);
	}

	protected static String normalize(final String text)
	{
		return Normalizer.normalize(StringUtils.defaultString(text), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT).trim();
	}

	/** Executa sem as restrições de busca (o gestor vê todos os sócios, pedidos e chamados) */
	protected <T> T unrestricted(final java.util.function.Supplier<T> body)
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

	// ------------------------------------------------------------ dependências

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setSearchRestrictionService(final SearchRestrictionService searchRestrictionService)
	{
		this.searchRestrictionService = searchRestrictionService;
	}

	public void setEnumerationService(final EnumerationService enumerationService)
	{
		this.enumerationService = enumerationService;
	}
}
