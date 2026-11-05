package br.com.flamengo.matchday.payment.adyen.impl;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.enums.SocioPaymentMethod;
import br.com.flamengo.matchday.model.SocioEventModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.payment.adyen.AdyenClient;
import br.com.flamengo.matchday.payment.adyen.AdyenException;
import br.com.flamengo.matchday.payment.adyen.AdyenHmac;
import br.com.flamengo.matchday.payment.adyen.AdyenPaymentService;
import br.com.flamengo.matchday.payment.adyen.AdyenSession;
import br.com.flamengo.matchday.socio.SocioGuestService;
import br.com.flamengo.matchday.socio.SocioPaymentService;
import br.com.flamengo.matchday.socio.SocioPayments;
import br.com.flamengo.matchday.socio.SocioPlanService;
import de.hybris.platform.commerceservices.enums.SalesApplication;
import de.hybris.platform.commerceservices.order.CommerceCartCalculationStrategy;
import de.hybris.platform.commerceservices.order.CommerceCheckoutService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.processengine.BusinessProcessService;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/** Ver {@link AdyenPaymentService}. */
public class DefaultAdyenPaymentService implements AdyenPaymentService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultAdyenPaymentService.class);
	private static final Locale PT = new Locale("pt");

	protected static final String STORE_PROPERTY = "flamengo.pagamento.adyen.loja";
	protected static final String URL_PROPERTY = "flamengo.pagamento.adyen.checkoutUrl";
	protected static final String RETURN_PROPERTY = "flamengo.pagamento.adyen.retorno";
	protected static final String TIMEOUT_PROPERTY = "flamengo.pagamento.adyen.timeoutSegundos";
	protected static final String INSTALLMENTS_PROPERTY = "flamengo.socio.pagamento.parcelas";
	/** Variante do boleto na conta (05/11: a conta de teste só tem boletobancario_santander) */
	protected static final String BOLETO_TYPE_PROPERTY = "flamengo.pagamento.adyen.boleto.tipo";

	public static final String SCHEME = "scheme";
	public static final String PIX = "pix";
	public static final String BOLETO = "boletobancario";
	/** Vencimento do boleto da Adyen (o mesmo prazo do boleto simulado) */
	protected static final int BOLETO_DAYS = 5;

	// atributos novos (lidos por nome: funcionam antes do Update sem quebrar o resto)
	protected static final String REFERENCE = "adyenReference";
	protected static final String SESSION = "adyenSessionId";
	protected static final String PSP = "adyenPspReference";
	protected static final String METHOD = "adyenPaymentMethod";
	protected static final String RESULT = "adyenResultCode";
	protected static final String ACTION = "adyenAction";
	protected static final String NOTIFICATION = "AdyenNotification";

	private ConfigurationService configurationService;
	private BaseStoreService baseStoreService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private CommerceCheckoutService commerceCheckoutService;
	private CommerceCartCalculationStrategy commerceCartCalculationStrategy;
	private CartService cartService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;
	private UserService userService;
	private SocioPaymentService socioPaymentService;
	private SocioPlanService socioPlanService;
	private BusinessProcessService businessProcessService;
	private AdyenClient adyenClient;

	// ------------------------------------------------------------ configuração

	@Override
	public boolean isEnabled()
	{
		if (!configurationService.getConfiguration().getBoolean(ENABLED_PROPERTY, false))
		{
			return false;
		}
		final BaseStoreModel store = store();
		return store != null && StringUtils.isNoneBlank(str(store, "adyenMerchantAccount"), str(store, "adyenApiKey"),
				str(store, "adyenClientKey"));
	}

	@Override
	public Map<String, Object> clientConfig()
	{
		final Map<String, Object> m = new LinkedHashMap<>();
		final boolean enabled = isEnabled();
		m.put("enabled", Boolean.valueOf(enabled));
		m.put("methods", methods());
		if (enabled)
		{
			m.put("clientKey", str(store(), "adyenClientKey"));
			m.put("environment", environment());
			m.put("locale", "pt-BR");
			m.put("countryCode", "BR");
		}
		return m;
	}

	@Override
	public List<String> methods()
	{
		final List<String> out = new ArrayList<>();
		if (!isEnabled())
		{
			return out;
		}
		final String raw = configurationService.getConfiguration().getString(METHODS_PROPERTY, PIX + "," + BOLETO);
		for (final String part : StringUtils.split(StringUtils.defaultString(raw), ", ;"))
		{
			final String m = part.trim().toLowerCase(Locale.ROOT);
			if (List.of(SCHEME, PIX, BOLETO).contains(m) && !out.contains(m))
			{
				out.add(m);
			}
		}
		return out;
	}

	/** Tipo do boleto nas chamadas à Adyen (a conta pode ter só a variante de um banco) */
	protected String boletoType()
	{
		return StringUtils.defaultIfBlank(configurationService.getConfiguration().getString(BOLETO_TYPE_PROPERTY), BOLETO).trim();
	}

	protected BaseStoreModel store()
	{
		BaseStoreModel store = null;
		try
		{
			store = baseStoreService.getCurrentBaseStore();
		}
		catch (final RuntimeException e)
		{
			// sem site na sessão (webhook): a loja da propriedade
		}
		if (store == null)
		{
			store = baseStoreService.getBaseStoreForUid(configurationService.getConfiguration().getString(STORE_PROPERTY, "electronics"));
		}
		return store;
	}

	protected String baseUrl()
	{
		return StringUtils.removeEnd(
				configurationService.getConfiguration().getString(URL_PROPERTY, "https://checkout-test.adyen.com/v71"), "/");
	}

	protected String environment()
	{
		return baseUrl().contains("-test.") ? "test" : "live";
	}

	protected int timeout()
	{
		return Math.max(5, configurationService.getConfiguration().getInt(TIMEOUT_PROPERTY, 20));
	}

	protected void requireEnabled()
	{
		if (!isEnabled())
		{
			throw new AdyenException("O pagamento pela Adyen está desligado (" + ENABLED_PROPERTY
					+ "=false ou conta e chaves não preenchidas na loja base).");
		}
	}

	protected Map<String, Object> post(final String path, final Map<String, Object> body)
	{
		return adyenClient.post(baseUrl(), str(store(), "adyenApiKey"), path, body, timeout());
	}

	// ------------------------------------------------------------ checkout

	@Override
	public Map<String, Object> paymentMethods(final CartModel cart)
	{
		requireEnabled();
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("merchantAccount", str(store(), "adyenMerchantAccount"));
		body.put("countryCode", "BR");
		body.put("shopperLocale", "pt-BR");
		body.put("channel", "Web");
		body.put("amount", amount(cart));
		return post("/paymentMethods", body);
	}

	@Override
	public AdyenSession createSession(final CartModel cart)
	{
		requireEnabled();
		requireDelivery(cart);
		if (!Boolean.TRUE.equals(cart.getCalculated()))
		{
			recalculate(cart);
		}
		final long cents = cents(cart);
		if (cents <= 0)
		{
			throw new AdyenException("Pedido sem valor a pagar: não passa pela Adyen.");
		}
		final SocioPaymentMethod socioMethod = SocioPayments.method(modelService, cart);
		final SocioPlanModel plan = planIn(cart);
		final List<String> allowed = allowedMethods(plan, socioMethod);
		allowed.removeIf(m -> !methods().contains(m.startsWith(BOLETO) ? BOLETO : m));
		if (allowed.isEmpty())
		{
			throw new AdyenException("Esta forma de pagamento ainda não passa pela Adyen (" + METHODS_PROPERTY + "="
					+ String.join(",", methods()) + ").");
		}
		final String reference = cart.getCode() + "-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);

		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("merchantAccount", str(store(), "adyenMerchantAccount"));
		body.put("amount", amount(cart));
		body.put("reference", reference);
		body.put("returnUrl", configurationService.getConfiguration().getString(RETURN_PROPERTY));
		body.put("countryCode", "BR");
		body.put("shopperLocale", "pt-BR");
		body.put("channel", "Web");
		body.put("allowedPaymentMethods", allowed);
		final CustomerModel customer = cart.getUser() instanceof CustomerModel ? (CustomerModel) cart.getUser() : null;
		if (customer != null)
		{
			body.put("shopperReference", shopperReference(customer));
			body.put("shopperEmail", StringUtils.defaultIfBlank(customer.getContactEmail(), customer.getUid()));
			body.put("storePaymentMethodMode", "askForConsent");
			body.put("recurringProcessingModel", "CardOnFile");
		}
		final Map<String, Object> shopper = shopper(cart, customer);
		if (shopper.get("shopperName") != null)
		{
			body.put("shopperName", shopper.get("shopperName"));
		}
		if (shopper.get("billingAddress") != null)
		{
			body.put("billingAddress", shopper.get("billingAddress"));
		}
		final List<Integer> installments = new ArrayList<>();
		Integer preselected = null;
		if (socioMethod == SocioPaymentMethod.CARTAO_ANUAL)
		{
			final int max = Math.max(1, Math.min(12, configurationService.getConfiguration().getInt(INSTALLMENTS_PROPERTY, 12)));
			for (int i = 1; i <= max; i++)
			{
				installments.add(Integer.valueOf(i));
			}
			preselected = SocioPayments.integer(modelService, cart, SocioPayments.INSTALLMENTS);
			final Map<String, Object> card = new LinkedHashMap<>();
			card.put("values", installments);
			if (preselected != null)
			{
				card.put("preselectedValue", preselected);
			}
			body.put("installmentOptions", Collections.singletonMap("card", card));
		}
		final Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("cartCode", cart.getCode());
		if (plan != null)
		{
			metadata.put("plano", plan.getCode());
		}
		body.put("metadata", metadata);

		final Map<String, Object> response = post("/sessions", body);
		final AdyenSession session = new AdyenSession();
		session.id = (String) response.get("id");
		session.sessionData = (String) response.get("sessionData");
		session.reference = reference;
		session.amount = cents;
		session.currency = currency(cart);
		session.allowedPaymentMethods = allowed;
		session.installments = installments;
		session.preselectedInstallments = preselected;
		session.clientKey = str(store(), "adyenClientKey");
		session.environment = environment();
		session.shopper = shopper;
		if (StringUtils.isAnyBlank(session.id, session.sessionData))
		{
			throw new AdyenException("A Adyen não devolveu a sessão de pagamento.", 502);
		}
		set(cart, SESSION, session.id);
		set(cart, REFERENCE, reference);
		modelService.save(cart);
		LOG.info("Adyen: sessão {} para o carrinho {} ({} centavos, {})", session.id, cart.getCode(), Long.valueOf(cents), allowed);
		return session;
	}

	/** Plano com forma escolhida: só ela; sem plano: cartão, Pix e boleto (Pix/boleto com a operação da loja ligada) */
	protected List<String> allowedMethods(final SocioPlanModel plan, final SocioPaymentMethod method)
	{
		final List<String> allowed = new ArrayList<>();
		if (method == SocioPaymentMethod.PIX)
		{
			allowed.add(PIX);
		}
		else if (method == SocioPaymentMethod.BOLETO)
		{
			allowed.add(boletoType());
		}
		else if (plan != null || method != null)
		{
			allowed.add(SCHEME);
		}
		else
		{
			allowed.add(SCHEME);
			if (socioPaymentService.isStoreEnabled())
			{
				allowed.add(PIX);
				allowed.add(boletoType());
			}
		}
		return allowed;
	}

	@Override
	public OrderModel placeOrder(final CartModel cart, final String sessionId, final String sessionResult, final String resultCode,
			final Map<String, Object> action)
	{
		requireEnabled();
		requireDelivery(cart);
		final String expected = str(cart, SESSION);
		if (StringUtils.isBlank(sessionId) || !sessionId.equals(expected))
		{
			throw new AdyenException("A sessão de pagamento não é a deste carrinho. Volte ao pagamento e tente de novo.");
		}
		final String actionType = action == null ? null : (String) action.get("type");
		final boolean pending = "qrCode".equals(actionType) || "voucher".equals(actionType);
		final String status = sessionStatus(sessionId, sessionResult);
		if (pending)
		{
			if (!List.of("paymentPending", "active", "completed").contains(status))
			{
				throw new AdyenException("A Adyen não confirmou o " + ("qrCode".equals(actionType) ? "Pix" : "boleto")
						+ " desta sessão (situação " + status + ").");
			}
		}
		else if (!"completed".equals(status) || !"Authorised".equals(resultCode))
		{
			throw new AdyenException("O pagamento não foi aprovado pela Adyen (" + StringUtils.defaultIfBlank(resultCode, status)
					+ "). Confira os dados ou escolha outra forma de pagamento.");
		}

		final String method = pending ? (isPix(action) ? PIX : BOLETO) : SCHEME;
		final Date now = new Date();
		final PaymentInfoModel info = modelService.create(PaymentInfoModel.class);
		info.setCode("adyen-" + str(cart, REFERENCE));
		info.setUser(cart.getUser());
		info.setSaved(false);
		modelService.save(info);
		cart.setPaymentInfo(info);
		set(cart, METHOD, method);
		set(cart, RESULT, resultCode);
		if (pending)
		{
			// Pix/boleto: o pedido espera o pagamento como no item 7 (o webhook confirma)
			modelService.setAttributeValue(cart, SocioPayments.METHOD,
					PIX.equals(method) ? SocioPaymentMethod.PIX : SocioPaymentMethod.BOLETO);
			modelService.setAttributeValue(cart, SocioPayments.DUE, expiry(action, method, now));
			modelService.setAttributeValue(cart, SocioPayments.CODE,
					PIX.equals(method) ? (String) action.get("qrCodeData") : (String) action.get("reference"));
			modelService.setAttributeValue(cart, SocioPayments.CONFIRMED, null);
			set(cart, ACTION, adyenClient.write(action));
		}
		modelService.save(cart);
		if (!Boolean.TRUE.equals(cart.getCalculated()))
		{
			recalculate(cart);
		}

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
			throw new AdyenException(StringUtils.defaultIfBlank(e.getMessage(), "O pedido não pôde ser fechado."));
		}
		final OrderModel order = result.getOrder();
		removeCart(cart);
		LOG.info("Adyen: pedido {} ({}, {}) referência {}", order.getCode(), method, pending ? "aguardando pagamento" : "pago",
				str(order, REFERENCE));
		// aviso que chegou antes do pedido existir (o cartão costuma avisar na hora)
		unrestricted(() -> {
			pendingNotifications(str(order, REFERENCE)).forEach(this::process);
			return null;
		});
		return order;
	}

	protected String sessionStatus(final String sessionId, final String sessionResult)
	{
		if (StringUtils.isBlank(sessionResult))
		{
			throw new AdyenException("Resultado da sessão de pagamento ausente.");
		}
		final Map<String, Object> r = adyenClient.get(baseUrl(), str(store(), "adyenApiKey"),
				"/sessions/" + urlEncode(sessionId) + "?sessionResult=" + urlEncode(sessionResult), timeout());
		return String.valueOf(r.get("status"));
	}

	protected static boolean isPix(final Map<String, Object> action)
	{
		return "qrCode".equals(action.get("type")) || PIX.equals(action.get("paymentMethodType"));
	}

	protected Date expiry(final Map<String, Object> action, final String method, final Date now)
	{
		final Object at = action.get("expiresAt");
		if (at instanceof String && StringUtils.isNotBlank((String) at))
		{
			try
			{
				return Date.from(OffsetDateTime.parse((String) at).toInstant());
			}
			catch (final RuntimeException e)
			{
				// formato inesperado: usa o prazo padrão
			}
		}
		final long minutes = PIX.equals(method) ? 60 : 5 * 24 * 60;
		return new Date(now.getTime() + minutes * 60000L);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> prepareUpfront(final CartModel cart, final boolean pix, final String cpf)
	{
		requireEnabled();
		final String type = pix ? PIX : BOLETO;
		final String name = pix ? "Pix" : "boleto";
		if (!methods().contains(type))
		{
			throw new AdyenException("O " + name + " não passa pela Adyen agora (" + METHODS_PROPERTY + ").");
		}
		requireDelivery(cart);
		if (!Boolean.TRUE.equals(cart.getCalculated()))
		{
			recalculate(cart);
		}
		final long cents = cents(cart);
		if (cents <= 0)
		{
			throw new AdyenException("Pedido sem valor a pagar: não passa pela Adyen.");
		}
		final String digits = StringUtils.defaultString(cpf).replaceAll("\\D", "");
		if (!digits.isEmpty() && !SocioGuestService.isValidCpf(digits))
		{
			throw new AdyenException("CPF inválido. Confira os 11 números.");
		}
		final CustomerModel customer = cart.getUser() instanceof CustomerModel ? (CustomerModel) cart.getUser() : null;
		final Map<String, Object> shopper = shopper(cart, customer);
		if (!pix)
		{
			if (digits.isEmpty())
			{
				throw new AdyenException("Informe o CPF de quem vai pagar o boleto.");
			}
			if (shopper.get("shopperName") == null || shopper.get("billingAddress") == null)
			{
				throw new AdyenException(
						"O boleto precisa do nome e do endereço completo da entrega (rua, número, CEP, cidade e estado).");
			}
		}
		final Date now = new Date();
		final String reference = cart.getCode() + "-" + Long.toString(now.getTime(), 36).toUpperCase(Locale.ROOT);

		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("merchantAccount", str(store(), "adyenMerchantAccount"));
		body.put("amount", amount(cart));
		body.put("reference", reference);
		body.put("paymentMethod", Collections.singletonMap("type", pix ? PIX : boletoType()));
		body.put("countryCode", "BR");
		body.put("shopperLocale", "pt-BR");
		body.put("channel", "Web");
		body.put("returnUrl", configurationService.getConfiguration().getString(RETURN_PROPERTY));
		if (customer != null)
		{
			body.put("shopperReference", shopperReference(customer));
			body.put("shopperEmail", StringUtils.defaultIfBlank(customer.getContactEmail(), customer.getUid()));
		}
		if (shopper.get("shopperName") != null)
		{
			body.put("shopperName", shopper.get("shopperName"));
		}
		if (!digits.isEmpty())
		{
			body.put("socialSecurityNumber", digits);
		}
		if (!pix)
		{
			body.put("billingAddress", shopper.get("billingAddress"));
			body.put("deliveryDate", OffsetDateTime.now().plusDays(BOLETO_DAYS).withNano(0).toString());
		}
		final Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("cartCode", cart.getCode());
		final SocioPlanModel plan = planIn(cart);
		if (plan != null)
		{
			metadata.put("plano", plan.getCode());
		}
		body.put("metadata", metadata);

		final Map<String, Object> response = post("/payments", body);
		final String resultCode = (String) response.get("resultCode");
		final Object rawAction = response.get("action");
		if (!(rawAction instanceof Map) || !List.of("Pending", "PresentToShopper", "Received").contains(resultCode))
		{
			throw new AdyenException("A Adyen não gerou o " + name + " (" + StringUtils.defaultIfBlank(
					(String) response.get("refusalReason"), StringUtils.defaultString(resultCode, "sem resposta")) + ").", 502);
		}
		final Map<String, Object> action = (Map<String, Object>) rawAction;
		final String code = pix ? (String) action.get("qrCodeData") : (String) action.get("reference");
		if (StringUtils.isBlank(code))
		{
			throw new AdyenException("A Adyen não devolveu o " + (pix ? "código do Pix" : "código do boleto") + ".", 502);
		}
		set(cart, SESSION, null);
		set(cart, REFERENCE, reference);
		set(cart, PSP, response.get("pspReference"));
		set(cart, METHOD, type);
		set(cart, RESULT, resultCode);
		set(cart, ACTION, adyenClient.write(action));
		modelService.setAttributeValue(cart, SocioPayments.DUE, expiry(action, type, now));
		modelService.setAttributeValue(cart, SocioPayments.CODE, code);
		modelService.setAttributeValue(cart, SocioPayments.CONFIRMED, null);
		modelService.save(cart);
		LOG.info("Adyen: {} {} para o carrinho {} ({} centavos), referência {}", name, response.get("pspReference"),
				cart.getCode(), Long.valueOf(cents), reference);
		return action;
	}

	@Override
	public Map<String, Object> orderPayment(final AbstractOrderModel order)
	{
		final Map<String, Object> m = new LinkedHashMap<>();
		final String method = str(order, METHOD);
		m.put("adyen", Boolean.valueOf(method != null));
		if (method == null)
		{
			return m;
		}
		m.put("method", method);
		m.put("pspReference", str(order, PSP));
		m.put("resultCode", str(order, RESULT));
		m.put("awaiting", Boolean.valueOf(SocioPayments.isAwaiting(modelService, order)));
		m.put("confirmedAt", SocioPayments.date(modelService, order, SocioPayments.CONFIRMED));
		m.put("due", SocioPayments.date(modelService, order, SocioPayments.DUE));
		final String action = str(order, ACTION);
		m.put("action", action == null ? null : adyenClient.read(action));
		return m;
	}

	// ------------------------------------------------------------ webhook

	@Override
	@SuppressWarnings("unchecked")
	public int handleNotifications(final Map<String, Object> body, final String authorization)
	{
		final BaseStoreModel store = store();
		final String user = str(store, "adyenWebhookUser");
		final String password = str(store, "adyenWebhookPassword");
		if (StringUtils.isAnyBlank(user, password) || !basicMatches(authorization, user, password))
		{
			throw new AdyenException("Webhook sem autorização.", 401);
		}
		final String hmacKey = str(store, "adyenHmacKey");
		final Object items = body == null ? null : body.get("notificationItems");
		if (!(items instanceof List))
		{
			return 0;
		}
		int accepted = 0;
		for (final Object wrapper : (List<Object>) items)
		{
			final Object raw = wrapper instanceof Map ? ((Map<String, Object>) wrapper).get("NotificationRequestItem") : null;
			if (!(raw instanceof Map))
			{
				continue;
			}
			final Map<String, Object> item = (Map<String, Object>) raw;
			if (!AdyenHmac.isValid(item, hmacKey))
			{
				LOG.warn("Adyen: aviso {} {} com assinatura HMAC inválida, ignorado", item.get("eventCode"), item.get("pspReference"));
				continue;
			}
			accepted++;
			unrestricted(() -> {
				final ItemModel n = record(item);
				if (n != null)
				{
					process(n);
				}
				return null;
			});
		}
		return accepted;
	}

	protected static boolean basicMatches(final String authorization, final String user, final String password)
	{
		if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6))
		{
			return false;
		}
		final String expected = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				authorization.substring(6).trim().getBytes(StandardCharsets.UTF_8));
	}

	/** Grava o aviso (o mesmo aviso repetido pela Adyen não é gravado de novo) */
	@SuppressWarnings("unchecked")
	protected ItemModel record(final Map<String, Object> item)
	{
		final String psp = (String) item.get("pspReference");
		final String event = (String) item.get("eventCode");
		final Boolean success = Boolean.valueOf("true".equalsIgnoreCase(String.valueOf(item.get("success"))));
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {" + NOTIFICATION
				+ "} WHERE {pspReference}=?p AND {eventCode}=?e AND {success}=?s");
		q.addQueryParameter("p", StringUtils.defaultString(psp));
		q.addQueryParameter("e", StringUtils.defaultString(event));
		q.addQueryParameter("s", success);
		if (!flexibleSearchService.search(q).getResult().isEmpty())
		{
			LOG.info("Adyen: aviso {} {} repetido", event, psp);
			return null;
		}
		final ItemModel n = modelService.create(NOTIFICATION);
		modelService.setAttributeValue(n, "pspReference", psp);
		modelService.setAttributeValue(n, "originalReference", item.get("originalReference"));
		modelService.setAttributeValue(n, "merchantReference", item.get("merchantReference"));
		modelService.setAttributeValue(n, "eventCode", event);
		modelService.setAttributeValue(n, "success", success);
		final Object amount = item.get("amount");
		if (amount instanceof Map)
		{
			final Object value = ((Map<String, Object>) amount).get("value");
			if (value instanceof Number)
			{
				modelService.setAttributeValue(n, "amount", Double.valueOf(((Number) value).doubleValue() / 100.0));
			}
			modelService.setAttributeValue(n, "currency", ((Map<String, Object>) amount).get("currency"));
		}
		modelService.setAttributeValue(n, "paymentMethod", item.get("paymentMethod"));
		modelService.setAttributeValue(n, "reason", item.get("reason"));
		modelService.setAttributeValue(n, "receivedAt", new Date());
		modelService.setAttributeValue(n, "processed", Boolean.FALSE);
		modelService.save(n);
		return n;
	}

	/** Aplica o aviso ao pedido da mesma referência (sem pedido ainda: fica para quando ele fechar) */
	protected void process(final ItemModel n)
	{
		final String event = (String) modelService.getAttributeValue(n, "eventCode");
		final boolean success = Boolean.TRUE.equals(modelService.getAttributeValue(n, "success"));
		final String reference = (String) modelService.getAttributeValue(n, "merchantReference");
		final String psp = (String) modelService.getAttributeValue(n, "pspReference");
		final OrderModel order = orderFor(reference);
		String result;
		if (order == null)
		{
			result = "AUTHORISATION".equals(event) && success
					? "Pedido ainda não fechado: o aviso é aplicado quando o pedido for criado."
					: "Sem pedido com esta referência.";
			modelService.setAttributeValue(n, "result", result);
			modelService.save(n);
			return;
		}
		modelService.setAttributeValue(n, "orderCode", order.getCode());
		try
		{
			if ("AUTHORISATION".equals(event))
			{
				if (success)
				{
					set(order, PSP, psp);
					modelService.save(order);
					if (SocioPayments.isAwaiting(modelService, order))
					{
						confirmEvent(order, psp);
						socioPaymentService.confirmOrder(order);
						triggerPaymentEvent(order);
						result = "Pagamento confirmado pela Adyen: pedido " + order.getCode() + " liberado.";
					}
					else
					{
						result = "Pagamento autorizado: pedido " + order.getCode() + ".";
					}
				}
				else
				{
					result = "Pagamento recusado pela Adyen: " + StringUtils.defaultIfBlank(
							(String) modelService.getAttributeValue(n, "reason"), "sem motivo informado") + ".";
				}
			}
			else if ("OFFER_CLOSED".equals(event))
			{
				result = SocioPayments.isAwaiting(modelService, order)
						? "Prazo do Pix/boleto encerrado sem pagamento (pedido " + order.getCode() + " continua aguardando)."
						: "Oferta encerrada; o pedido " + order.getCode() + " já estava pago.";
			}
			else
			{
				result = "Registrado (" + event + (success ? "" : ", sem sucesso") + ").";
			}
			modelService.setAttributeValue(n, "processed", Boolean.TRUE);
		}
		catch (final RuntimeException e)
		{
			LOG.error("Adyen: aviso " + event + " do pedido " + order.getCode() + " não aplicado", e);
			result = "Erro ao aplicar: " + e.getMessage();
		}
		modelService.setAttributeValue(n, "result", result);
		modelService.save(n);
		LOG.info("Adyen: {} {} -> {}", event, reference, result);
	}

	protected OrderModel orderFor(final String reference)
	{
		if (StringUtils.isBlank(reference))
		{
			return null;
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {" + REFERENCE + "}=?r AND {versionID} IS NULL ORDER BY {creationtime} DESC");
		q.addQueryParameter("r", reference);
		final List<OrderModel> found = flexibleSearchService.<OrderModel> search(q).getResult();
		return found.isEmpty() ? null : found.get(0);
	}

	protected List<ItemModel> pendingNotifications(final String reference)
	{
		if (StringUtils.isBlank(reference))
		{
			return Collections.emptyList();
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {" + NOTIFICATION
				+ "} WHERE {merchantReference}=?r AND ({processed}=?f OR {processed} IS NULL) ORDER BY {creationtime}");
		q.addQueryParameter("r", reference);
		q.addQueryParameter("f", Boolean.FALSE);
		return flexibleSearchService.<ItemModel> search(q).getResult();
	}

	/** Pedido com plano: "Pagamento confirmado" na linha do tempo do sócio, com a Adyen como autora */
	protected void confirmEvent(final OrderModel order, final String psp)
	{
		if (!(order.getUser() instanceof CustomerModel) || planIn(order) == null)
		{
			return;
		}
		try
		{
			final SocioEventModel event = socioPlanService.newEvent((CustomerModel) order.getUser(),
					SocioEventType.PAGAMENTO_CONFIRMADO, "Adyen", "Pagamento do pedido " + order.getCode()
							+ " confirmado pela Adyen (" + SocioPayments.describe(modelService, order) + ", psp " + psp + ")");
			event.setResult("Pagamento confirmado pela Adyen, pedido " + order.getCode());
			modelService.save(event);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Adyen: evento do sócio para o pedido {} não gravado: {}", order.getCode(), e.getMessage());
		}
	}

	/** Acorda o processo do pedido que espera o pagamento (sem isso ele confere de novo em até 1 minuto) */
	protected void triggerPaymentEvent(final OrderModel order)
	{
		if (businessProcessService == null || order.getOrderProcess() == null)
		{
			return;
		}
		for (final OrderProcessModel process : order.getOrderProcess())
		{
			try
			{
				businessProcessService.triggerEvent(process.getCode() + "_PagamentoConfirmado");
			}
			catch (final RuntimeException e)
			{
				LOG.debug("Adyen: evento do processo {} não disparado: {}", process.getCode(), e.getMessage());
			}
		}
	}

	// ------------------------------------------------------------ apoio

	protected void requireDelivery(final CartModel cart)
	{
		final boolean allPickup = cart.getEntries() != null && !cart.getEntries().isEmpty()
				&& cart.getEntries().stream().allMatch(e -> e.getDeliveryPointOfService() != null);
		if ((cart.getDeliveryAddress() == null || cart.getDeliveryMode() == null) && !allPickup)
		{
			throw new AdyenException("Preencha o endereço e a forma de entrega antes do pagamento.");
		}
	}

	protected SocioPlanModel planIn(final AbstractOrderModel order)
	{
		if (order == null || order.getEntries() == null)
		{
			return null;
		}
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final SocioPlanModel plan = socioPlanService.findForProduct(entry.getProduct());
			if (plan != null)
			{
				return plan;
			}
		}
		return null;
	}

	protected Map<String, Object> amount(final AbstractOrderModel order)
	{
		final Map<String, Object> m = new LinkedHashMap<>();
		m.put("currency", currency(order));
		m.put("value", Long.valueOf(cents(order)));
		return m;
	}

	protected static long cents(final AbstractOrderModel order)
	{
		return order.getTotalPrice() == null ? 0 : Math.round(order.getTotalPrice().doubleValue() * 100);
	}

	protected static String currency(final AbstractOrderModel order)
	{
		return order.getCurrency() == null ? "BRL" : order.getCurrency().getIsocode();
	}

	/** Identificador do torcedor na Adyen (cartão guardado), sem o e-mail */
	protected static String shopperReference(final CustomerModel customer)
	{
		return "fla-" + (customer.getPk() == null ? customer.getUid().hashCode() : customer.getPk().getLongValue());
	}

	/** Nome e endereço de cobrança já conhecidos (endereço de entrega do carrinho, quando completo) */
	protected Map<String, Object> shopper(final CartModel cart, final CustomerModel customer)
	{
		final Map<String, Object> m = new LinkedHashMap<>();
		final AddressModel a = cart.getDeliveryAddress();
		final String first = a != null && StringUtils.isNotBlank(a.getFirstname()) ? a.getFirstname()
				: customer == null ? null : StringUtils.substringBefore(StringUtils.trimToEmpty(customer.getName()), " ");
		final String last = a != null && StringUtils.isNotBlank(a.getLastname()) ? a.getLastname()
				: customer == null ? null : StringUtils.substringAfter(StringUtils.trimToEmpty(customer.getName()), " ");
		if (StringUtils.isNoneBlank(first, last))
		{
			final Map<String, Object> name = new LinkedHashMap<>();
			name.put("firstName", first);
			name.put("lastName", last);
			m.put("shopperName", name);
		}
		if (a != null && StringUtils.isNoneBlank(a.getLine1(), a.getTown(), a.getPostalcode())
				&& a.getRegion() != null && a.getCountry() != null)
		{
			final Map<String, Object> address = new LinkedHashMap<>();
			address.put("street", a.getLine1());
			address.put("houseNumberOrName", StringUtils.defaultIfBlank(a.getLine2(), "s/n"));
			address.put("city", a.getTown());
			address.put("postalCode", a.getPostalcode().replaceAll("\\D", ""));
			address.put("stateOrProvince", StringUtils.substringAfterLast(a.getRegion().getIsocode(), "-"));
			address.put("country", a.getCountry().getIsocode());
			m.put("billingAddress", address);
		}
		return m;
	}

	protected void recalculate(final CartModel cart)
	{
		final CommerceCartParameter parameter = new CommerceCartParameter();
		parameter.setEnableHooks(true);
		parameter.setCart(cart);
		commerceCartCalculationStrategy.recalculateCart(parameter);
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

	protected String str(final Object item, final String qualifier)
	{
		if (item == null)
		{
			return null;
		}
		try
		{
			final Object value = modelService.getAttributeValue(item, qualifier);
			return value instanceof String && StringUtils.isNotBlank((String) value) ? ((String) value).trim() : null;
		}
		catch (final RuntimeException e)
		{
			return null; // antes do Update o atributo ainda não existe
		}
	}

	protected void set(final Object item, final String qualifier, final Object value)
	{
		modelService.setAttributeValue(item, qualifier, value);
	}

	protected static String urlEncode(final String value)
	{
		return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
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
		}, userService.getAdminUser());
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setBaseStoreService(final BaseStoreService baseStoreService)
	{
		this.baseStoreService = baseStoreService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setCommerceCheckoutService(final CommerceCheckoutService commerceCheckoutService)
	{
		this.commerceCheckoutService = commerceCheckoutService;
	}

	public void setCommerceCartCalculationStrategy(final CommerceCartCalculationStrategy commerceCartCalculationStrategy)
	{
		this.commerceCartCalculationStrategy = commerceCartCalculationStrategy;
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

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setSocioPaymentService(final SocioPaymentService socioPaymentService)
	{
		this.socioPaymentService = socioPaymentService;
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}

	public void setBusinessProcessService(final BusinessProcessService businessProcessService)
	{
		this.businessProcessService = businessProcessService;
	}

	public void setAdyenClient(final AdyenClient adyenClient)
	{
		this.adyenClient = adyenClient;
	}
}
