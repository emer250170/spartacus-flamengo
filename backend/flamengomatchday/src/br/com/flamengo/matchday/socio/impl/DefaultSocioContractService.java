package br.com.flamengo.matchday.socio.impl;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.enums.SocioPlanStatus;
import br.com.flamengo.matchday.model.SocioContractModel;
import br.com.flamengo.matchday.model.SocioEventModel;
import br.com.flamengo.matchday.model.SocioGuestModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.socio.SocioPayments;
import br.com.flamengo.matchday.socio.SocioCartService;
import br.com.flamengo.matchday.socio.SocioContractException;
import br.com.flamengo.matchday.socio.SocioContractService;
import br.com.flamengo.matchday.socio.SocioContractStatus;
import br.com.flamengo.matchday.socio.SocioGuestService;
import br.com.flamengo.matchday.socio.SocioPlanService;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.security.PrincipalGroupModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Vigência do sócio: contrato gravado (SocioContract) ou calculado do pedido mais
 * novo do plano. Escolhas do sócio até o prazo; virada no Backoffice ou pelo Claude.
 */
public class DefaultSocioContractService implements SocioContractService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSocioContractService.class);
	private static final Locale PT = new Locale("pt");
	private static final String RENEWAL = "Renovação";
	private static final Pattern ORDER_IN_NOTE = Pattern.compile("pedido (\\w+)");

	private SocioPlanService socioPlanService;
	private SocioGuestService socioGuestService;
	private SocioCartService socioCartService;
	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private ConfigurationService configurationService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;

	@Override
	public boolean isEnabled()
	{
		return configurationService.getConfiguration().getBoolean(ENABLED_PROPERTY, true);
	}

	protected int deadlineDays()
	{
		return Math.max(0, configurationService.getConfiguration().getInt(DEADLINE_PROPERTY, DEFAULT_DEADLINE));
	}

	// ------------------------------------------------------------ leitura

	@Override
	public SocioContractStatus status(final CustomerModel customer)
	{
		final SocioContractStatus s = new SocioContractStatus();
		s.enabled = isEnabled();
		if (customer == null)
		{
			return s;
		}
		return unrestricted(() -> {
			final SocioContractModel record = find(customer);
			final SocioPlanModel plan = socioPlanService.memberPlan(customer);
			s.member = plan != null;
			if (!s.member)
			{
				if (record != null)
				{
					s.persisted = true;
					s.endedAt = record.getEndedAt();
					fillPlan(s, record.getPlan());
					s.start = record.getStart();
					s.end = record.getEnd();
				}
				return s;
			}
			fillPlan(s, plan);
			if (record != null && record.getEndedAt() == null && record.getStart() != null && record.getPlan() != null
					&& StringUtils.equals(record.getPlan().getCode(), plan.getCode()))
			{
				s.persisted = true;
				s.start = record.getStart();
				s.end = record.getEnd() != null ? record.getEnd() : addMonths(record.getStart(), TERM_MONTHS);
				s.autoRenew = !Boolean.FALSE.equals(record.getAutoRenew());
				s.origin = record.getOrigin();
				s.renewals = record.getRenewals() == null ? 0 : record.getRenewals().intValue();
				s.renewed = RENEWAL.equals(record.getOrigin());
				if (record.getNextPlan() != null)
				{
					s.nextPlanCode = record.getNextPlan().getCode();
					s.nextPlanName = name(record.getNextPlan());
				}
			}
			else
			{
				// sem contrato gravado (ou desatualizado): como antes, do pedido mais novo do plano
				final OrderModel order = contractOrder(customer, plan);
				if (order != null && order.getDate() != null)
				{
					s.start = order.getDate();
					s.end = addMonths(order.getDate(), TERM_MONTHS);
					s.origin = "Pedido " + order.getCode();
				}
				else
				{
					// sócio sem pedido do plano (cadastro direto no grupo): a última Adesão da linha do tempo
					final Date joined = lastJoin(customer);
					if (joined != null)
					{
						s.start = joined;
						s.end = addMonths(joined, TERM_MONTHS);
						s.origin = "Adesão";
					}
				}
				s.renewals = record == null || record.getRenewals() == null ? 0 : record.getRenewals().intValue();
			}
			if (s.end != null)
			{
				s.changeDeadline = addDays(s.end, -deadlineDays());
				s.locked = new Date().after(s.changeDeadline);
			}
			for (final SocioPlanModel option : smallerPlans(plan))
			{
				final SocioContractStatus.Option o = new SocioContractStatus.Option();
				o.code = option.getCode();
				o.name = name(option);
				o.monthlyPrice = option.getMonthlyPrice() == null ? 0 : option.getMonthlyPrice().doubleValue();
				s.options.add(o);
			}
			return s;
		});
	}

	/** Planos da mesma família, à venda, com mensalidade menor que a do plano atual */
	protected List<SocioPlanModel> smallerPlans(final SocioPlanModel current)
	{
		final double price = current.getMonthlyPrice() == null ? 0 : current.getMonthlyPrice().doubleValue();
		final List<SocioPlanModel> out = new ArrayList<>();
		for (final SocioPlanModel plan : socioPlanService.getPlans())
		{
			if (!plan.getCode().equals(current.getCode()) && plan.getCustomerGroup() != null
					&& plan.getStatus() == SocioPlanStatus.ON_SALE && StringUtils.equals(plan.getFamily(), current.getFamily())
					&& plan.getMonthlyPrice() != null && plan.getMonthlyPrice().doubleValue() < price)
			{
				out.add(plan);
			}
		}
		out.sort(Comparator.comparing((SocioPlanModel p) -> -p.getMonthlyPrice().doubleValue()));
		return out;
	}

	// ------------------------------------------------------------ escolhas do sócio

	@Override
	public String setAutoRenew(final CustomerModel customer, final boolean autoRenew, final String origin)
	{
		final SocioContractStatus s = checkChange(customer);
		if (s.autoRenew == autoRenew)
		{
			return autoRenew ? "A renovação automática já está ligada." : "A renovação automática já está desligada.";
		}
		final SocioContractModel record = ensure(customer, s);
		record.setAutoRenew(Boolean.valueOf(autoRenew));
		final String end = day(s.end);
		final String text = autoRenew ? "Renovação automática ligada: renova em " + end
				: "Renovação automática desligada: o plano termina em " + end + " sem multa";
		final SocioEventModel event = socioPlanService.newEvent(customer, SocioEventType.OBSERVACAO, origin, text);
		event.setResult(text);
		modelService.saveAll(record, event);
		return text + ".";
	}

	@Override
	public String scheduleChange(final CustomerModel customer, final String planCode, final String origin)
	{
		final SocioContractStatus s = checkChange(customer);
		final SocioContractModel record = ensure(customer, s);
		final String text;
		if (StringUtils.isBlank(planCode))
		{
			if (record.getNextPlan() == null)
			{
				return "Não há mudança de plano agendada.";
			}
			text = "Mudança para o " + name(record.getNextPlan()) + " cancelada: renova no " + s.planName;
			record.setNextPlan(null);
		}
		else
		{
			final SocioPlanModel target = socioPlanService.findByCode(planCode);
			final SocioPlanModel current = socioPlanService.memberPlan(customer);
			if (target == null || current == null
					|| smallerPlans(current).stream().noneMatch(p -> p.getCode().equals(target.getCode())))
			{
				throw new SocioContractException("Escolha um plano menor da mesma família, à venda na loja.");
			}
			text = "Mudança para o " + name(target) + " agendada para " + day(s.end) + " (na renovação)";
			record.setNextPlan(target);
		}
		final SocioEventModel event = socioPlanService.newEvent(customer, SocioEventType.OBSERVACAO, origin, text);
		event.setResult(text);
		modelService.saveAll(record, event);
		return text + ".";
	}

	protected SocioContractStatus checkChange(final CustomerModel customer)
	{
		if (!isEnabled())
		{
			throw new SocioContractException("A vigência está desligada (" + ENABLED_PROPERTY + "=false).");
		}
		final SocioContractStatus s = status(customer);
		if (!s.member || s.end == null)
		{
			throw new SocioContractException("Só o sócio com plano ativo escolhe a renovação.");
		}
		if (s.locked)
		{
			throw new SocioContractException("O prazo para mudar a renovação terminou em " + day(s.changeDeadline)
					+ " (" + deadlineDays() + " dias antes do fim da vigência, " + day(s.end) + ").");
		}
		return s;
	}

	// ------------------------------------------------------------ virada

	@Override
	public List<ItemModel> turn(final CustomerModel customer, final SocioEventModel event)
	{
		if (!isEnabled())
		{
			throw new SocioContractException("A vigência está desligada (" + ENABLED_PROPERTY + "=false).");
		}
		final SocioContractStatus s = status(customer);
		if (!s.member)
		{
			throw new SocioContractException(customer.getUid() + " não é sócio: não há vigência para virar.");
		}
		final SocioContractModel record = ensure(customer, s);
		final SocioPlanModel current = socioPlanService.memberPlan(customer);
		final List<ItemModel> changed = new ArrayList<>();
		changed.add(record);
		final Date now = new Date();
		if (!s.autoRenew)
		{
			// sem renovação: sai do plano sem multa, como no cancelamento
			event.setType(SocioEventType.FIM_VIGENCIA);
			event.setPlan(current);
			final List<String> plans = socioPlanService.removeMembership(customer);
			changed.add(customer);
			final List<SocioGuestModel> guests = socioGuestService == null ? List.of()
					: socioGuestService.endAll(customer, END_BY_EXPIRY);
			changed.addAll(guests);
			record.setEndedAt(now);
			record.setNextPlan(null);
			event.setResult("Contrato encerrado sem renovação, sem multa: deixou de ser sócio (" + String.join(", ", plans) + ")"
					+ (guests.isEmpty() ? "" : "; " + guests.size() + " convidado(s) encerrado(s)"));
			if (StringUtils.isBlank(event.getNote()))
			{
				event.setNote("Fim da vigência com a renovação automática desligada");
			}
			LOG.info("Vigência: {} encerrada sem renovação ({})", customer.getUid(), plans);
			refreshCarts(customer);
			return changed;
		}
		final SocioPlanModel target = record.getNextPlan() != null ? record.getNextPlan() : current;
		final boolean change = !target.getCode().equals(current.getCode());
		if (change)
		{
			moveGroup(customer, current, target);
			changed.add(customer);
			final List<SocioGuestModel> active = socioGuestService == null ? List.of() : socioGuestService.guests(customer, true);
			final int max = target.getMaxGuests() == null ? 0 : target.getMaxGuests().intValue();
			if (active.size() > max)
			{
				changed.addAll(socioGuestService.endAll(customer, END_BY_DOWNGRADE));
			}
		}
		record.setPlan(target);
		record.setStart(now);
		record.setEnd(addMonths(now, TERM_MONTHS));
		record.setNextPlan(null);
		record.setOrigin(RENEWAL);
		record.setRenewals(Integer.valueOf((record.getRenewals() == null ? 0 : record.getRenewals().intValue()) + 1));
		record.setEndedAt(null);
		event.setType(SocioEventType.RENOVACAO);
		event.setPlan(target);
		if (StringUtils.isBlank(event.getNote()))
		{
			event.setNote("Virada da vigência (renovação automática)");
		}
		event.setResult((change ? "Renovado com mudança de plano: " + name(current) + " para " + name(target)
				: "Renovado no " + name(target)) + ", vigência " + day(now) + " a " + day(record.getEnd()));
		LOG.info("Vigência: {} renovado no {} até {}", customer.getUid(), target.getCode(), day(record.getEnd()));
		if (change)
		{
			refreshCarts(customer);
		}
		return changed;
	}

	@Override
	public String turnNow(final CustomerModel customer, final String origin)
	{
		final SocioEventModel event = socioPlanService.newEvent(customer, SocioEventType.RENOVACAO, origin, null);
		final List<ItemModel> changed = turn(customer, event);
		final List<Object> all = new ArrayList<>(changed);
		all.add(event);
		modelService.saveAll(all);
		return event.getResult() + ".";
	}

	/** Troca o grupo do plano atual pelo do novo (mantém suspensão e outros grupos) */
	protected void moveGroup(final CustomerModel customer, final SocioPlanModel from, final SocioPlanModel to)
	{
		final Set<PrincipalGroupModel> groups = new HashSet<>(customer.getGroups());
		groups.remove(from.getCustomerGroup());
		if (from.getPendingGroup() != null)
		{
			groups.remove(from.getPendingGroup());
		}
		groups.add(to.getCustomerGroup());
		customer.setGroups(groups);
	}

	protected void refreshCarts(final CustomerModel customer)
	{
		if (socioCartService != null)
		{
			try
			{
				socioCartService.refreshCartsLater(customer, socioPlanService.isSuspended(customer));
			}
			catch (final RuntimeException e)
			{
				LOG.warn("Vigência: não recalculou os carrinhos de {}: {}", customer.getUid(), e.getMessage());
			}
		}
	}

	// ------------------------------------------------------------ adesão e upgrade

	@Override
	public SocioContractModel onJoin(final SocioEventModel joinEvent)
	{
		if (joinEvent == null || joinEvent.getCustomer() == null || joinEvent.getPlan() == null)
		{
			return null;
		}
		final SocioContractModel record = unrestricted(() -> find(joinEvent.getCustomer()));
		if (record == null)
		{
			return null; // sem contrato gravado, a vigência já sai do pedido
		}
		final Date start = joinEvent.getDate() != null ? joinEvent.getDate() : new Date();
		record.setPlan(joinEvent.getPlan());
		record.setStart(start);
		record.setEnd(addMonths(start, TERM_MONTHS));
		record.setAutoRenew(Boolean.TRUE);
		record.setNextPlan(null);
		record.setEndedAt(null);
		final Matcher m = ORDER_IN_NOTE.matcher(StringUtils.defaultString(joinEvent.getNote()));
		record.setOrigin(m.find() ? "Pedido " + m.group(1) : "Pedido");
		return record;
	}

	// ------------------------------------------------------------ apoio

	protected SocioContractModel find(final CustomerModel customer)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {SocioContract} WHERE {customer}=?c");
		q.addQueryParameter("c", customer);
		final List<SocioContractModel> found = flexibleSearchService.<SocioContractModel> search(q).getResult();
		return found.isEmpty() ? null : found.get(0);
	}

	/** Contrato gravado com os valores da vigência atual (cria se não existe; não salva) */
	protected SocioContractModel ensure(final CustomerModel customer, final SocioContractStatus s)
	{
		SocioContractModel record = unrestricted(() -> find(customer));
		if (record == null)
		{
			record = modelService.create(SocioContractModel.class);
			record.setCustomer(customer);
			record.setRenewals(Integer.valueOf(0));
		}
		if (!s.persisted)
		{
			record.setPlan(socioPlanService.memberPlan(customer));
			record.setStart(s.start);
			record.setEnd(s.end);
			record.setAutoRenew(Boolean.TRUE);
			record.setNextPlan(null);
			record.setOrigin(s.origin);
			record.setEndedAt(null);
		}
		return record;
	}

	/** Pedido mais novo do torcedor com o plano (início do contrato quando não há contrato gravado) */
	protected OrderModel contractOrder(final CustomerModel customer, final SocioPlanModel plan)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {user}=?c AND {versionID} IS NULL ORDER BY {date} DESC");
		q.addQueryParameter("c", customer);
		for (final OrderModel order : flexibleSearchService.<OrderModel> search(q).getResult())
		{
			if (SocioPayments.isAwaiting(modelService, order))
			{
				continue; // Pix ou boleto ainda não pago (item 7)
			}
			if (order.getEntries() == null)
			{
				continue;
			}
			for (final AbstractOrderEntryModel entry : order.getEntries())
			{
				final SocioPlanModel bought = entry.getProduct() == null ? null : socioPlanService.findForProduct(entry.getProduct());
				if (bought != null && bought.getCode().equals(plan.getCode()))
				{
					return order;
				}
			}
		}
		return null;
	}

	/** Data da última Adesão na linha do tempo do torcedor (ou null) */
	protected Date lastJoin(final CustomerModel customer)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {SocioEvent} WHERE {customer}=?c AND {type}=?t ORDER BY {date} DESC");
		q.addQueryParameter("c", customer);
		q.addQueryParameter("t", SocioEventType.ADESAO);
		q.setCount(1);
		final List<SocioEventModel> found = flexibleSearchService.<SocioEventModel> search(q).getResult();
		return found.isEmpty() ? null : found.get(0).getDate();
	}

	protected static void fillPlan(final SocioContractStatus s, final SocioPlanModel plan)
	{
		if (plan != null)
		{
			s.planCode = plan.getCode();
			s.planName = name(plan);
		}
	}

	protected static String name(final SocioPlanModel plan)
	{
		return plan == null ? null : StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode());
	}

	protected static Date addMonths(final Date date, final int months)
	{
		final Calendar c = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"));
		c.setTime(date);
		c.add(Calendar.MONTH, months);
		return c.getTime();
	}

	protected static Date addDays(final Date date, final int days)
	{
		final Calendar c = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"));
		c.setTime(date);
		c.add(Calendar.DAY_OF_MONTH, days);
		return c.getTime();
	}

	protected static String day(final Date date)
	{
		if (date == null)
		{
			return "-";
		}
		final SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy");
		f.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
		return f.format(date);
	}

	/** Lê contratos e pedidos de qualquer torcedor (Backoffice) e do próprio (loja) */
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

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}

	public void setSocioGuestService(final SocioGuestService socioGuestService)
	{
		this.socioGuestService = socioGuestService;
	}

	public void setSocioCartService(final SocioCartService socioCartService)
	{
		this.socioCartService = socioCartService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
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
