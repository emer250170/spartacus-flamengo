package br.com.flamengo.matchday.socio.impl;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.model.SocioEventModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.socio.SocioCancelTicketInterceptor;
import br.com.flamengo.matchday.socio.SocioExitQuote;
import br.com.flamengo.matchday.socio.SocioExitService;
import br.com.flamengo.matchday.socio.SocioPlanService;
import de.hybris.platform.core.HybrisEnumValue;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;


/**
 * Regras de saída do sócio: reembolso no prazo de arrependimento, multa fora dele.
 * Contrato = pedido mais novo com o plano atual (o mesmo da área do sócio na
 * loja); mensalidades pagas = meses completos desde a adesão + 1 (a 1ª é paga na
 * compra), como a loja mostra no histórico.
 */
public class DefaultSocioExitService implements SocioExitService
{
	private static final Locale PT = new Locale("pt");
	private static final Locale PT_BR = new Locale("pt", "BR");
	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
	private static final String GUEST_PREFIX = "NACAO-CONVIDADO-";
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private SocioPlanService socioPlanService;
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

	protected int withdrawalDays()
	{
		return Math.max(0, configurationService.getConfiguration().getInt(DAYS_PROPERTY, DEFAULT_DAYS));
	}

	protected double finePercent()
	{
		return Math.max(0, configurationService.getConfiguration().getDouble(FINE_PROPERTY, DEFAULT_FINE));
	}

	// ------------------------------------------------------------ simulação

	@Override
	public SocioExitQuote quote(final CustomerModel customer, final Date basis)
	{
		if (customer == null)
		{
			return null;
		}
		return unrestricted(() -> {
			final SocioPlanModel plan = socioPlanService.memberPlan(customer);
			if (plan == null)
			{
				return null;
			}
			final OrderModel order = contractOrder(customer, plan);
			if (order == null || order.getDate() == null)
			{
				return null;
			}
			return calculate(plan, order, basis == null ? new Date() : basis);
		});
	}

	@Override
	public SocioExitQuote currentQuote(final CustomerModel customer)
	{
		if (customer == null)
		{
			return null;
		}
		final ItemModel ticket = unrestricted(() -> openCancelTicket(customer));
		final SocioExitQuote q = quote(customer, ticket == null ? new Date() : ticket.getCreationtime());
		if (q != null && ticket != null)
		{
			q.openTicket = (String) modelService.getAttributeValue(ticket, "ticketID");
		}
		return q;
	}

	protected SocioExitQuote calculate(final SocioPlanModel plan, final OrderModel order, final Date basis)
	{
		final SocioExitQuote q = new SocioExitQuote();
		q.planCode = plan.getCode();
		q.planName = StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode());
		q.orderCode = order.getCode();
		q.start = order.getDate();
		q.basis = basis;
		q.installments = INSTALLMENTS;

		double paid = 0;
		int guests = 0;
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final ProductModel product = entry.getProduct();
			final boolean planEntry = product != null && socioPlanService.findForProduct(product) != null;
			final boolean guestEntry = isGuest(product);
			if (planEntry || guestEntry)
			{
				paid += entry.getTotalPrice() == null ? 0 : entry.getTotalPrice().doubleValue();
			}
			if (guestEntry)
			{
				guests += entry.getQuantity() == null ? 0 : entry.getQuantity().intValue();
			}
		}
		q.paidAtJoin = round(paid);
		q.guests = guests;
		q.monthly = round(value(plan.getMonthlyPrice()) + guests * value(plan.getGuestPrice()));

		final LocalDate startDay = day(q.start);
		final LocalDate basisDay = day(basis);
		q.withdrawalDays = withdrawalDays();
		final LocalDate lastDay = startDay.plusDays(q.withdrawalDays);
		q.withdrawalUntil = Date.from(lastDay.atStartOfDay(ZONE).toInstant());
		q.withinWithdrawal = q.withdrawalDays > 0 && !basisDay.isAfter(lastDay);

		q.paidInstallments = (int) Math.min(INSTALLMENTS, Math.max(0, ChronoUnit.MONTHS.between(startDay, basisDay)) + 1);
		q.remainingInstallments = INSTALLMENTS - q.paidInstallments;
		q.finePercent = finePercent();
		if (q.withinWithdrawal)
		{
			q.refund = q.paidAtJoin;
		}
		else
		{
			q.fine = round(q.remainingInstallments * q.monthly * q.finePercent / 100.0);
		}
		return q;
	}

	// ------------------------------------------------------------ cancelamento confirmado

	@Override
	public SocioExitQuote lastSettlement(final CustomerModel customer)
	{
		if (customer == null)
		{
			return null;
		}
		return unrestricted(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {SocioEvent} WHERE {customer}=?c AND {type}=?t"
					+ " AND ({refundAmount} IS NOT NULL OR {fineAmount} IS NOT NULL) ORDER BY {date} DESC, {creationtime} DESC");
			q.addQueryParameter("c", customer);
			q.addQueryParameter("t", SocioEventType.CANCELAMENTO);
			q.setCount(1);
			final List<SocioEventModel> events = flexibleSearchService.<SocioEventModel> search(q).getResult();
			return events.isEmpty() ? null : fromEvent(events.get(0));
		});
	}

	/** Valor gravado num evento CANCELAMENTO */
	public static SocioExitQuote fromEvent(final SocioEventModel event)
	{
		final SocioExitQuote q = new SocioExitQuote();
		q.confirmed = true;
		q.basis = event.getDate() != null ? event.getDate() : event.getCreationtime();
		if (event.getPlan() != null)
		{
			q.planCode = event.getPlan().getCode();
			q.planName = StringUtils.defaultIfBlank(event.getPlan().getName(PT), event.getPlan().getCode());
		}
		q.withinWithdrawal = event.getRefundAmount() != null;
		q.refund = event.getRefundAmount() == null ? 0 : event.getRefundAmount().doubleValue();
		q.fine = event.getFineAmount() == null ? 0 : event.getFineAmount().doubleValue();
		return q;
	}

	@Override
	public String apply(final SocioEventModel event, final SocioExitQuote quote)
	{
		if (event == null || quote == null)
		{
			return null;
		}
		event.setRefundAmount(quote.withinWithdrawal ? Double.valueOf(quote.refund) : null);
		event.setFineAmount(quote.withinWithdrawal ? null : Double.valueOf(quote.fine));
		final String text = describe(quote);
		return text == null ? null : StringUtils.uncapitalize(text);
	}

	@Override
	public String describe(final SocioExitQuote q)
	{
		if (q == null)
		{
			return null;
		}
		if (q.withinWithdrawal)
		{
			return "Reembolso de " + money(q.refund)
					+ (q.withdrawalUntil == null ? " (arrependimento)" : " (arrependimento até " + DAY.format(day(q.withdrawalUntil)) + ")");
		}
		if (q.confirmed)
		{
			return q.fine > 0 ? "Multa de " + money(q.fine) : "Sem multa";
		}
		if (q.remainingInstallments <= 0 || q.fine <= 0)
		{
			return "Sem multa (sem mensalidades restantes no contrato)";
		}
		return "Multa de " + money(q.fine) + " (" + percent(q.finePercent) + " de " + q.remainingInstallments
				+ (q.remainingInstallments == 1 ? " mensalidade restante" : " mensalidades restantes") + " de " + money(q.monthly) + ")";
	}

	// ------------------------------------------------------------ apoio

	/** Pedido mais novo do torcedor com o plano atual (início do contrato) */
	protected OrderModel contractOrder(final CustomerModel customer, final SocioPlanModel plan)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {user}=?c AND {versionID} IS NULL ORDER BY {date} DESC");
		q.addQueryParameter("c", customer);
		for (final OrderModel order : flexibleSearchService.<OrderModel> search(q).getResult())
		{
			if (order.getEntries() == null)
			{
				continue;
			}
			for (final AbstractOrderEntryModel entry : order.getEntries())
			{
				final SocioPlanModel bought = entry.getProduct() == null ? null : socioPlanService.findForProduct(entry.getProduct());
				if (bought != null && StringUtils.equals(bought.getCode(), plan.getCode()))
				{
					return order;
				}
			}
		}
		return null;
	}

	/** Chamado "Cancelamento do plano ..." ainda não fechado (o mais novo) */
	protected ItemModel openCancelTicket(final CustomerModel customer)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {CsTicket} WHERE {customer}=?c AND {headline} LIKE ?s ORDER BY {creationtime} DESC");
		q.addQueryParameter("c", customer);
		q.addQueryParameter("s", SocioCancelTicketInterceptor.SUBJECT + "%");
		for (final ItemModel ticket : flexibleSearchService.<ItemModel> search(q).getResult())
		{
			final Object state = modelService.getAttributeValue(ticket, "state");
			if (!(state instanceof HybrisEnumValue) || !"Closed".equalsIgnoreCase(((HybrisEnumValue) state).getCode()))
			{
				return ticket;
			}
		}
		return null;
	}

	protected static boolean isGuest(final ProductModel product)
	{
		return product != null && product.getCode() != null && product.getCode().startsWith(GUEST_PREFIX);
	}

	protected static LocalDate day(final Date date)
	{
		return date.toInstant().atZone(ZONE).toLocalDate();
	}

	protected static double value(final Number number)
	{
		return number == null ? 0 : number.doubleValue();
	}

	protected static double round(final double value)
	{
		return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
	}

	public static String money(final double value)
	{
		return NumberFormat.getCurrencyInstance(PT_BR).format(value).replace(' ', ' ');
	}

	protected static String percent(final double value)
	{
		return (value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value).replace('.', ',')) + "%";
	}

	/** Lê pedidos e chamados de qualquer torcedor (Backoffice) e do próprio torcedor (loja) */
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
