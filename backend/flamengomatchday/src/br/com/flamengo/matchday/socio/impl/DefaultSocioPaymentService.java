package br.com.flamengo.matchday.socio.impl;

import br.com.flamengo.matchday.enums.SocioPaymentMethod;
import br.com.flamengo.matchday.model.SocioEventModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.socio.SocioPayment;
import br.com.flamengo.matchday.socio.SocioPaymentException;
import br.com.flamengo.matchday.socio.SocioPaymentService;
import br.com.flamengo.matchday.socio.SocioPayments;
import br.com.flamengo.matchday.socio.SocioPlanService;
import de.hybris.platform.commerceservices.enums.SalesApplication;
import de.hybris.platform.commerceservices.order.CommerceCartCalculationStrategy;
import de.hybris.platform.commerceservices.order.CommerceCheckoutService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.CreditCardPaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Pix e boleto à vista e plano aguardando pagamento (item 7). Ver {@link SocioPaymentService}.
 */
public class DefaultSocioPaymentService implements SocioPaymentService
{
	private br.com.flamengo.matchday.ticket.MatchdayTicketService matchdayTicketService;
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSocioPaymentService.class);
	private static final Locale PT = new Locale("pt");
	private static final String GUEST_PREFIX = "NACAO-CONVIDADO-";
	private static final Pattern ORDER_CODE = Pattern.compile("\\b(\\d{8})\\b");
	private static final TimeZone SAO_PAULO = TimeZone.getTimeZone("America/Sao_Paulo");

	private ConfigurationService configurationService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private SocioPlanService socioPlanService;
	private CommerceCartCalculationStrategy commerceCartCalculationStrategy;
	private CommerceCheckoutService commerceCheckoutService;
	private CartService cartService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;

	@Override
	public boolean isEnabled()
	{
		return configurationService.getConfiguration().getBoolean(ENABLED_PROPERTY, false);
	}

	@Override
	public boolean isStoreEnabled()
	{
		return isEnabled() && configurationService.getConfiguration().getBoolean(STORE_PROPERTY, false);
	}

	/**
	 * Pix Automático na mensalidade do plano (05/11): Adyen ligada, Pix na lista de meios da Adyen e o Pix indo
	 * como Pix Automático (flamengo.pagamento.adyen.pix.automatico). Lido da configuração, sem depender da Adyen.
	 */
	protected boolean pixAutomaticOffered()
	{
		final var c = configurationService.getConfiguration();
		return c.getBoolean("flamengo.pagamento.adyen.ativo", false) && c.getBoolean("flamengo.pagamento.adyen.pix.automatico", false)
				&& java.util.Arrays.stream(c.getString("flamengo.pagamento.adyen.meios", "").split(","))
						.anyMatch(m -> "pix".equalsIgnoreCase(m.trim()));
	}

	protected int maxInstallments()
	{
		return Math.max(1, Math.min(SocioPayments.MONTHS, configurationService.getConfiguration().getInt(INSTALLMENTS_PROPERTY, 12)));
	}

	// ------------------------------------------------------------ checkout

	@Override
	public SocioPayment options(final CartModel cart)
	{
		final SocioPayment p = new SocioPayment();
		p.enabled = isEnabled();
		p.months = SocioPayments.MONTHS;
		p.maxInstallments = maxInstallments();
		if (cart == null)
		{
			return p;
		}
		p.total = value(cart.getTotalPrice());
		final SocioPlanModel plan = planIn(cart);
		final SocioPaymentMethod method = SocioPayments.method(modelService, cart);
		if (plan != null)
		{
			p.planCode = plan.getCode();
			p.planName = StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode());
			p.upfrontAllowed = !Boolean.FALSE.equals(plan.getUpfrontPayment());
			p.monthly = round(value(plan.getMonthlyPrice()) + guestsIn(cart) * value(plan.getGuestPrice()));
			p.annual = round(p.monthly * SocioPayments.MONTHS);
			if (p.enabled)
			{
				p.methods.add(SocioPaymentMethod.CARTAO_MENSAL.getCode());
				if (pixAutomaticOffered())
				{
					// 05/11: mensalidade pelo Pix Automático da Adyen (1ª agora pelo QR Code, as próximas no débito)
					p.methods.add(SocioPaymentMethod.PIX_AUTOMATICO.getCode());
				}
				p.methods.add(SocioPaymentMethod.CARTAO_ANUAL.getCode());
				if (p.upfrontAllowed)
				{
					p.methods.add(SocioPaymentMethod.PIX.getCode());
					p.methods.add(SocioPaymentMethod.BOLETO.getCode());
				}
			}
		}
		else if (isStoreEnabled())
		{
			// Item 14: carrinho sem plano paga o total no cartão, no Pix ou no boleto
			p.methods.add(SocioPaymentMethod.CARTAO_MENSAL.getCode());
			p.methods.add(SocioPaymentMethod.PIX.getCode());
			p.methods.add(SocioPaymentMethod.BOLETO.getCode());
		}
		p.method = method == null || (plan == null && !isStoreEnabled()) ? null : method.getCode();
		p.installments = method == SocioPaymentMethod.CARTAO_ANUAL ? SocioPayments.integer(modelService, cart, SocioPayments.INSTALLMENTS)
				: null;
		if (plan == null && p.methods.contains(SocioPaymentMethod.PIX.getCode()))
		{
			pixPreview(cart, p, method == SocioPaymentMethod.PIX);
		}
		return p;
	}

	private static final Pattern PIX_CONDITION = Pattern
			.compile("\"fla_forma_pagamento\".*?\"value\"\\s*:\\s*\"PIX\"", Pattern.DOTALL);
	private static final Pattern ORDER_PERCENT = Pattern.compile(
			"\"y_order_percentage_discount\".*?\"value\"\\s*:\\s*\\{[^{}]*?\"value\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.DOTALL);

	/**
	 * Preço no Pix e nas outras formas (06/11), para o card do Pix mostrar o valor com desconto e o selo antes da
	 * escolha: o percentual vem da promoção publicada com a condição "Forma de pagamento = Pix" e a ação "desconto
	 * percentual no pedido" (o motor aplica sobre o subtotal dos produtos, sem o frete). Com o Pix já escolhido o
	 * total do carrinho já tem o desconto. Sem promoção (ou com a chave flamengo.promocao.formaPagamento.ativo
	 * desligada), nada muda.
	 */
	protected void pixPreview(final CartModel cart, final SocioPayment p, final boolean pixChosen)
	{
		final double percent = pixPercent();
		if (percent <= 0)
		{
			return;
		}
		final double off = round(value(cart.getSubtotal()) * percent / 100.0);
		p.pixPercent = percent;
		p.pixTotal = pixChosen ? p.total : round(p.total - off);
		p.baseTotal = pixChosen ? round(p.total + off) : p.total;
	}

	/** Maior percentual entre as promoções publicadas "Forma de pagamento = Pix" (0 = nenhuma) */
	protected double pixPercent()
	{
		if (!configurationService.getConfiguration().getBoolean("flamengo.promocao.formaPagamento.ativo", true))
		{
			return 0;
		}
		try
		{
			final List<ItemModel> rules = flexibleSearchService.<ItemModel> search(new FlexibleSearchQuery(
					"SELECT {r.pk} FROM {PromotionSourceRule AS r JOIN RuleStatus AS s ON {r.status} = {s.pk}} "
							+ "WHERE {s.code} = 'PUBLISHED' AND {r.conditions} LIKE '%fla_forma_pagamento%'"))
					.getResult();
			final Date now = new Date();
			double best = 0;
			for (final ItemModel rule : rules)
			{
				final Object start = modelService.getAttributeValue(rule, "startDate");
				final Object end = modelService.getAttributeValue(rule, "endDate");
				if ((start instanceof Date && now.before((Date) start)) || (end instanceof Date && now.after((Date) end)))
				{
					continue;
				}
				final String conditions = String.valueOf(modelService.<Object> getAttributeValue(rule, "conditions"));
				final String actions = String.valueOf(modelService.<Object> getAttributeValue(rule, "actions"));
				final Matcher action = ORDER_PERCENT.matcher(actions);
				if (PIX_CONDITION.matcher(conditions).find() && action.find())
				{
					best = Math.max(best, Double.parseDouble(action.group(1)));
				}
			}
			return best;
		}
		catch (final RuntimeException e)
		{
			// a prévia nunca impede a escolha da forma de pagamento
			return 0;
		}
	}

	@Override
	public SocioPayment choose(final CartModel cart, final String method, final Integer installments)
	{
		if (!isEnabled())
		{
			throw new SocioPaymentException("Pix, boleto e o ano inteiro no cartão estão desligados (" + ENABLED_PROPERTY + "=false).");
		}
		final SocioPlanModel plan = planIn(cart);
		if (plan == null && !isStoreEnabled())
		{
			throw new SocioPaymentException("A forma de pagamento do plano só vale com um plano de sócio no carrinho.");
		}
		final SocioPaymentMethod chosen = parse(method);
		if (plan == null && chosen == SocioPaymentMethod.CARTAO_ANUAL)
		{
			throw new SocioPaymentException("O ano inteiro no cartão só vale com um plano de sócio no carrinho.");
		}
		if (chosen == SocioPaymentMethod.PIX_AUTOMATICO && (plan == null || !pixAutomaticOffered()))
		{
			throw new SocioPaymentException("O Pix Automático vale só para a mensalidade de um plano de sócio, com o Pix pela Adyen ligado.");
		}
		if (plan != null && SocioPayments.annual(chosen) && SocioPayments.upfront(chosen)
				&& Boolean.FALSE.equals(plan.getUpfrontPayment()))
		{
			throw new SocioPaymentException("O " + StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode())
					+ " não aceita Pix nem boleto à vista. Escolha o cartão.");
		}
		Integer parcels = null;
		if (chosen == SocioPaymentMethod.CARTAO_ANUAL)
		{
			final int n = installments == null ? 1 : installments.intValue();
			if (n < 1 || n > maxInstallments())
			{
				throw new SocioPaymentException("Parcelas no cartão: de 1 a " + maxInstallments() + ".");
			}
			parcels = Integer.valueOf(n);
		}
		modelService.setAttributeValue(cart, SocioPayments.METHOD, chosen);
		modelService.setAttributeValue(cart, SocioPayments.INSTALLMENTS, parcels);
		modelService.setAttributeValue(cart, SocioPayments.DUE, null);
		modelService.setAttributeValue(cart, SocioPayments.CODE, null);
		clearAdyen(cart);
		modelService.save(cart);
		recalculate(cart);
		LOG.info("Carrinho {}: forma de pagamento do plano {}{}", cart.getCode(), chosen.getCode(),
				parcels == null ? "" : " em " + parcels + "x");
		return options(cart);
	}

	@Override
	public SocioPaymentMethod validateUpfront(final CartModel cart)
	{
		if (!isEnabled())
		{
			throw new SocioPaymentException("Pix e boleto estão desligados (" + ENABLED_PROPERTY + "=false).");
		}
		final SocioPaymentMethod method = SocioPayments.method(modelService, cart);
		if (!SocioPayments.upfront(method))
		{
			throw new SocioPaymentException("Escolha Pix ou boleto antes de finalizar o pedido por aqui.");
		}
		final SocioPlanModel plan = planIn(cart);
		if (plan == null && !isStoreEnabled())
		{
			throw new SocioPaymentException("Pix e boleto à vista só valem com um plano de sócio no carrinho.");
		}
		if (plan != null && SocioPayments.annual(method) && Boolean.FALSE.equals(plan.getUpfrontPayment()))
		{
			throw new SocioPaymentException("O " + StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode())
					+ " não aceita Pix nem boleto à vista. Escolha o cartão.");
		}
		if (method == SocioPaymentMethod.PIX_AUTOMATICO && plan == null)
		{
			throw new SocioPaymentException("O Pix Automático vale só para a mensalidade de um plano de sócio.");
		}
		final boolean allPickup = cart.getEntries() != null && !cart.getEntries().isEmpty()
				&& cart.getEntries().stream().allMatch(e -> e.getDeliveryPointOfService() != null);
		if ((cart.getDeliveryAddress() == null && !allPickup) || (cart.getDeliveryMode() == null && !allPickup))
		{
			throw new SocioPaymentException("Preencha o endereço e a forma de entrega antes de finalizar o pedido.");
		}
		return method;
	}

	@Override
	public OrderModel placeOrder(final CartModel cart)
	{
		final SocioPaymentMethod method = validateUpfront(cart);
		if (!Boolean.TRUE.equals(cart.getCalculated()))
		{
			recalculate(cart);
		}

		final Date now = new Date();
		// Pix/boleto gerado pela Adyen (05/11): código, vencimento e referência já estão no carrinho. Conferido pelo
		// meio gravado (o Pix da Adyen pode voltar sem pspReference; o webhook grava o pspReference depois)
		final String adyenReference = adyenString(cart, "adyenPaymentMethod") == null ? null : adyenString(cart, "adyenReference");
		final Date due = adyenReference != null && SocioPayments.date(modelService, cart, SocioPayments.DUE) != null
				? SocioPayments.date(modelService, cart, SocioPayments.DUE)
				: due(method, now);
		final PaymentInfoModel info = modelService.create(PaymentInfoModel.class);
		info.setCode(adyenReference != null ? "adyen-" + adyenReference
				: method.getCode().toLowerCase(Locale.ROOT) + "-" + cart.getCode() + "-" + now.getTime());
		info.setUser(cart.getUser());
		info.setSaved(false);
		modelService.save(info);
		cart.setPaymentInfo(info);
		modelService.setAttributeValue(cart, SocioPayments.DUE, due);
		if (adyenReference == null || SocioPayments.string(modelService, cart, SocioPayments.CODE) == null)
		{
			modelService.setAttributeValue(cart, SocioPayments.CODE,
					SocioPayments.pix(method) ? pixCode(cart, now) : boletoLine(cart, due));
		}
		modelService.setAttributeValue(cart, SocioPayments.CONFIRMED, null);
		modelService.save(cart);

		final CommerceCheckoutParameter parameter = new CommerceCheckoutParameter();
		parameter.setEnableHooks(true);
		parameter.setCart(cart);
		parameter.setSalesApplication(SalesApplication.WEB);
		final CommerceOrderResult result;
		try
		{
			result = commerceCheckoutService.placeOrder(parameter);
		}
		catch (final InvalidCartException e)
		{
			throw new SocioPaymentException(StringUtils.defaultIfBlank(e.getMessage(), "O pedido não pôde ser fechado."));
		}
		final OrderModel order = result.getOrder();
		removeCart(cart);
		LOG.info("Pedido {} com {}: aguardando pagamento até {}", order.getCode(), method.getCode(), due);
		return order;
	}

	@Override
	public void checkCart(final CartModel cart)
	{
		final SocioPaymentMethod method = SocioPayments.method(modelService, cart);
		if (cart == null || method == null)
		{
			return;
		}
		if (planIn(cart) == null && (!isStoreEnabled() || method == SocioPaymentMethod.CARTAO_ANUAL))
		{
			modelService.setAttributeValue(cart, SocioPayments.METHOD, null);
			modelService.setAttributeValue(cart, SocioPayments.INSTALLMENTS, null);
			modelService.save(cart);
			return;
		}
		if (SocioPayments.upfront(method) && cart.getPaymentInfo() instanceof CreditCardPaymentInfoModel)
		{
			final String name = SocioPayments.name(method);
			throw new SocioPaymentException("O plano está com " + name + " como forma de pagamento: finalize pelo " + name
					+ " ou volte ao pagamento e escolha o cartão.");
		}
	}

	/** Pix/boleto da Adyen preparado para outra forma: some quando a forma muda (atributos lidos por nome) */
	protected void clearAdyen(final CartModel cart)
	{
		for (final String q : new String[]
		{ "adyenReference", "adyenSessionId", "adyenPspReference", "adyenPaymentMethod", "adyenResultCode", "adyenAction" })
		{
			try
			{
				if (modelService.getAttributeValue(cart, q) != null)
				{
					modelService.setAttributeValue(cart, q, null);
				}
			}
			catch (final RuntimeException e)
			{
				return; // antes do Update os atributos da Adyen ainda não existem
			}
		}
	}

	protected String adyenString(final AbstractOrderModel order, final String qualifier)
	{
		try
		{
			final Object v = modelService.getAttributeValue(order, qualifier);
			return v instanceof String && StringUtils.isNotBlank((String) v) ? (String) v : null;
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	protected void removeCart(final CartModel cart)
	{
		try
		{
			if (cartService != null && cartService.hasSessionCart() && cart.equals(cartService.getSessionCart()))
			{
				cartService.removeSessionCart();
			}
			else
			{
				modelService.remove(cart);
			}
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Carrinho {} não foi removido depois do pedido: {}", cart.getCode(), e.getMessage());
		}
	}

	// ------------------------------------------------------------ pedido

	@Override
	public SocioPayment status(final AbstractOrderModel order)
	{
		final SocioPaymentMethod method = SocioPayments.method(modelService, order);
		if (order == null || method == null)
		{
			return null;
		}
		final SocioPayment p = new SocioPayment();
		p.enabled = isEnabled();
		p.months = SocioPayments.MONTHS;
		p.maxInstallments = maxInstallments();
		p.method = method.getCode();
		p.installments = SocioPayments.integer(modelService, order, SocioPayments.INSTALLMENTS);
		p.total = value(order.getTotalPrice());
		p.orderCode = order.getCode();
		p.orderDate = order.getDate();
		p.due = SocioPayments.date(modelService, order, SocioPayments.DUE);
		p.code = SocioPayments.string(modelService, order, SocioPayments.CODE);
		p.confirmedAt = SocioPayments.date(modelService, order, SocioPayments.CONFIRMED);
		p.awaiting = SocioPayments.isAwaiting(modelService, order);
		p.summary = SocioPayments.describe(modelService, order);
		final SocioPlanModel plan = planIn(order);
		if (plan != null)
		{
			p.planCode = plan.getCode();
			p.planName = StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode());
			p.upfrontAllowed = !Boolean.FALSE.equals(plan.getUpfrontPayment());
			p.monthly = round(value(plan.getMonthlyPrice()) + guestsIn(order) * value(plan.getGuestPrice()));
			p.annual = round(p.monthly * SocioPayments.MONTHS);
		}
		return p;
	}

	@Override
	public OrderModel awaitingOrder(final CustomerModel customer)
	{
		if (customer == null)
		{
			return null;
		}
		// só pedidos com plano: o Pix ou boleto de um pedido só de produtos (item 14) não muda a ficha do sócio
		return unrestricted(() -> awaitingOrders(customer).stream().filter(o -> planIn(o) != null).findFirst().orElse(null));
	}

	protected List<OrderModel> awaitingOrders(final CustomerModel customer)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {Order} WHERE {user}=?c AND {versionID} IS NULL"
				+ " AND {" + SocioPayments.METHOD + "} IN (?m) AND {" + SocioPayments.CONFIRMED + "} IS NULL"
				+ " AND ({status} IS NULL OR {status}<>?x) ORDER BY {date} DESC");
		q.addQueryParameter("c", customer);
		q.addQueryParameter("m", List.of(SocioPaymentMethod.PIX, SocioPaymentMethod.BOLETO, SocioPaymentMethod.PIX_AUTOMATICO));
		q.addQueryParameter("x", OrderStatus.CANCELLED);
		return flexibleSearchService.<OrderModel> search(q).getResult();
	}

	@Override
	public void confirm(final CustomerModel customer, final SocioEventModel event)
	{
		final String note = event == null ? null : event.getNote();
		final Matcher m = note == null ? null : ORDER_CODE.matcher(note);
		final String cited = m != null && m.find() ? m.group(1) : null;
		final OrderModel order = unrestricted(() -> {
			final List<OrderModel> waiting = awaitingOrders(customer);
			if (cited != null)
			{
				return waiting.stream().filter(o -> cited.equals(o.getCode())).findFirst().orElse(null);
			}
			// sem pedido citado: o Pix ou boleto mais novo de um pedido com plano
			return waiting.stream().filter(o -> planIn(o) != null).findFirst().orElseThrow(() -> new SocioPaymentException(
					"Nenhum Pix ou boleto aguardando pagamento para " + StringUtils.defaultIfBlank(customer.getName(), customer.getUid()) + "."));
		});
		if (order == null)
		{
			// Item 14: o pagamento pode já ter sido confirmado pela mudança de status do pedido;
			// o evento só registra na linha do tempo
			final OrderModel done = cited == null ? null : unrestricted(() -> confirmedOrder(customer, cited));
			if (done == null)
			{
				throw new SocioPaymentException("O pedido " + cited + " de " + customer.getUid() + " não está aguardando pagamento.");
			}
			final SocioPlanModel plan = planIn(done);
			if (event != null)
			{
				if (plan != null)
				{
					event.setPlan(plan);
				}
				event.setResult("Pagamento confirmado: " + SocioPayments.describe(modelService, done) + ", pedido " + done.getCode()
						+ (plan == null ? "" : "; sócio " + StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode())));
			}
			return;
		}
		confirmOrder(order);
		final String paid = SocioPayments.describe(modelService, order);
		final SocioPlanModel plan = planIn(order);
		if (event != null)
		{
			if (plan != null)
			{
				event.setPlan(plan);
			}
			if (StringUtils.isBlank(event.getNote()))
			{
				event.setNote("Pagamento do pedido " + order.getCode() + " confirmado");
			}
			event.setResult("Pagamento confirmado: " + paid + ", pedido " + order.getCode()
					+ (plan == null ? "" : "; sócio " + StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode())));
		}
		LOG.info("Pagamento do pedido {} confirmado ({})", order.getCode(), customer.getUid());
	}

	@Override
	public boolean confirmOrder(final OrderModel order)
	{
		if (!SocioPayments.isAwaiting(modelService, order))
		{
			return false;
		}
		modelService.setAttributeValue(order, SocioPayments.CONFIRMED, new Date());
		if (order.getStatus() == OrderStatus.PAYMENT_NOT_CAPTURED)
		{
			// o processo do pedido (que espera o pagamento) segue sozinho
			order.setStatus(OrderStatus.PAYMENT_CAPTURED);
		}
		modelService.save(order);
		socioPlanService.applyOrder(order);
		if (matchdayTicketService != null)
		{
			matchdayTicketService.releaseForOrder(order, "Pagamento confirmado"); // ingressos do pedido (04/11)
		}
		LOG.info("Pagamento do pedido {} confirmado", order.getCode());
		return true;
	}

	/** Pedido do torcedor com Pix ou boleto já confirmado */
	protected OrderModel confirmedOrder(final CustomerModel customer, final String code)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {Order} WHERE {user}=?c AND {code}=?o"
				+ " AND {versionID} IS NULL AND {" + SocioPayments.CONFIRMED + "} IS NOT NULL");
		q.addQueryParameter("c", customer);
		q.addQueryParameter("o", code);
		final List<OrderModel> found = flexibleSearchService.<OrderModel> search(q).getResult();
		return found.isEmpty() ? null : found.get(0);
	}

	// ------------------------------------------------------------ preço

	@Override
	public int priceMultiplier(final AbstractOrderEntryModel entry)
	{
		try
		{
			final AbstractOrderModel order = entry == null ? null : entry.getOrder();
			if (order == null || !SocioPayments.annual(SocioPayments.method(modelService, order)))
			{
				return 1;
			}
			if (order instanceof CartModel && !isEnabled())
			{
				return 1; // chave desligada: carrinho volta à mensalidade (pedidos guardam o que foi cobrado)
			}
			final ProductModel product = entry.getProduct();
			return isGuest(product) || socioPlanService.findForProduct(product) != null ? SocioPayments.MONTHS : 1;
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Preço do ano inteiro não aplicado: {}", e.getMessage());
			return 1;
		}
	}

	// ------------------------------------------------------------ códigos fictícios

	protected Date due(final SocioPaymentMethod method, final Date now)
	{
		final Calendar c = Calendar.getInstance(SAO_PAULO);
		c.setTime(now);
		if (SocioPayments.pix(method))
		{
			c.add(Calendar.MINUTE, Math.max(1, configurationService.getConfiguration().getInt(PIX_MINUTES_PROPERTY, 30)));
		}
		else
		{
			c.add(Calendar.DAY_OF_MONTH, Math.max(1, configurationService.getConfiguration().getInt(BOLETO_DAYS_PROPERTY, 3)));
			c.set(Calendar.HOUR_OF_DAY, 23);
			c.set(Calendar.MINUTE, 59);
			c.set(Calendar.SECOND, 0);
		}
		return c.getTime();
	}

	/** Pix copia e cola no formato do BR Code (fictício: a chave é da demo) */
	protected String pixCode(final CartModel cart, final Date now)
	{
		final String amount = String.format(Locale.ROOT, "%.2f", value(cart.getTotalPrice()));
		final String txid = ("FLA" + cart.getCode() + Long.toString(now.getTime(), 36)).toUpperCase(Locale.ROOT);
		final String gui = "0014BR.GOV.BCB.PIX";
		final String key = "pix.demo.flamengo.com.br";
		final String account = gui + tlv("01", key);
		final String txField = tlv("05", txid.length() > 25 ? txid.substring(0, 25) : txid);
		final String payload = tlv("00", "01") + tlv("26", account) + tlv("52", "0000") + tlv("53", "986") + tlv("54", amount)
				+ tlv("58", "BR") + tlv("59", "FLAMENGO DEMO") + tlv("60", "RIO DE JANEIRO") + tlv("62", txField) + "6304";
		return payload + crc16(payload);
	}

	/** Linha digitável de boleto (fictícia: banco 001, carteira da demo) */
	protected String boletoLine(final CartModel cart, final Date due)
	{
		final String digits = StringUtils.leftPad(cart.getCode().replaceAll("\\D", ""), 10, '0');
		final String free = StringUtils.rightPad("6000" + digits, 25, '0').substring(0, 25);
		final String f1 = "0019" + free.substring(0, 5);
		final String f2 = free.substring(5, 15);
		final String f3 = free.substring(15, 25);
		final Calendar base = Calendar.getInstance(SAO_PAULO);
		base.clear();
		base.set(1997, Calendar.OCTOBER, 7);
		final long days = (due.getTime() - base.getTimeInMillis()) / 86400000L;
		final String factor = String.valueOf(1000 + days % 9000);
		final String value = StringUtils.leftPad(String.valueOf(Math.round(value(cart.getTotalPrice()) * 100)), 10, '0');
		return f1.substring(0, 5) + "." + f1.substring(5) + mod10(f1) + " " + f2.substring(0, 5) + "." + f2.substring(5) + mod10(f2)
				+ " " + f3.substring(0, 5) + "." + f3.substring(5) + mod10(f3) + " " + mod10(factor + value + free) + " " + factor
				+ value;
	}

	protected static String tlv(final String id, final String value)
	{
		return id + StringUtils.leftPad(String.valueOf(value.length()), 2, '0') + value;
	}

	protected static String crc16(final String text)
	{
		int crc = 0xFFFF;
		for (final byte b : text.getBytes(java.nio.charset.StandardCharsets.UTF_8))
		{
			crc ^= (b & 0xFF) << 8;
			for (int i = 0; i < 8; i++)
			{
				crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
				crc &= 0xFFFF;
			}
		}
		return StringUtils.leftPad(Integer.toHexString(crc).toUpperCase(Locale.ROOT), 4, '0');
	}

	protected static int mod10(final String digits)
	{
		int sum = 0;
		int weight = 2;
		for (int i = digits.length() - 1; i >= 0; i--)
		{
			int v = (digits.charAt(i) - '0') * weight;
			sum += v > 9 ? v / 10 + v % 10 : v;
			weight = weight == 2 ? 1 : 2;
		}
		return (10 - sum % 10) % 10;
	}

	// ------------------------------------------------------------ apoio

	protected SocioPaymentMethod parse(final String method)
	{
		for (final SocioPaymentMethod m : SocioPaymentMethod.values())
		{
			if (m.getCode().equalsIgnoreCase(StringUtils.trimToEmpty(method)))
			{
				return m;
			}
		}
		throw new SocioPaymentException("Forma de pagamento desconhecida: " + method + " (CARTAO_MENSAL, CARTAO_ANUAL, PIX, BOLETO ou PIX_AUTOMATICO).");
	}

	/** Plano no carrinho/pedido (o de maior onda, como no applyOrder) */
	protected SocioPlanModel planIn(final AbstractOrderModel order)
	{
		SocioPlanModel best = null;
		if (order != null && order.getEntries() != null)
		{
			for (final AbstractOrderEntryModel entry : order.getEntries())
			{
				final SocioPlanModel plan = entry.getProduct() == null ? null : socioPlanService.findForProduct(entry.getProduct());
				if (plan != null && (best == null || value(plan.getMonthlyPrice()) > value(best.getMonthlyPrice())))
				{
					best = plan;
				}
			}
		}
		return best;
	}

	protected int guestsIn(final AbstractOrderModel order)
	{
		int guests = 0;
		if (order.getEntries() != null)
		{
			for (final AbstractOrderEntryModel entry : order.getEntries())
			{
				if (isGuest(entry.getProduct()) && entry.getQuantity() != null)
				{
					guests += entry.getQuantity().intValue();
				}
			}
		}
		return guests;
	}

	protected static boolean isGuest(final ProductModel product)
	{
		return product != null && product.getCode() != null && product.getCode().startsWith(GUEST_PREFIX);
	}

	protected void recalculate(final CartModel cart)
	{
		final CommerceCartParameter parameter = new CommerceCartParameter();
		parameter.setEnableHooks(true);
		parameter.setCart(cart);
		commerceCartCalculationStrategy.recalculateCart(parameter);
	}

	protected static double value(final Number number)
	{
		return number == null ? 0 : number.doubleValue();
	}

	protected static double round(final double value)
	{
		return Math.round(value * 100) / 100.0;
	}

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

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}

	public void setCommerceCartCalculationStrategy(final CommerceCartCalculationStrategy commerceCartCalculationStrategy)
	{
		this.commerceCartCalculationStrategy = commerceCartCalculationStrategy;
	}

	public void setCommerceCheckoutService(final CommerceCheckoutService commerceCheckoutService)
	{
		this.commerceCheckoutService = commerceCheckoutService;
	}

	public void setCartService(final CartService cartService)
	{
		this.cartService = cartService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setSearchRestrictionService(final SearchRestrictionService searchRestrictionService)
	{
		this.searchRestrictionService = searchRestrictionService;
	}

	public void setMatchdayTicketService(final br.com.flamengo.matchday.ticket.MatchdayTicketService matchdayTicketService)
	{
		this.matchdayTicketService = matchdayTicketService;
	}
}
