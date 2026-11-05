package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.payment.adyen.AdyenException;
import br.com.flamengo.matchday.payment.adyen.AdyenPaymentService;
import br.com.flamengo.matchday.payment.adyen.AdyenSession;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.user.UserService;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * Pagamento pela Adyen na API de comércio (OCC v2), 05/11:
 * <ul>
 * <li>GET /{baseSiteId}/users/current/adyen/config: ligado, client key e ambiente;</li>
 * <li>GET .../users/current/carts/{cartId}/adyen/paymentmethods: meios que a conta oferece (diagnóstico);</li>
 * <li>POST .../users/current/carts/{cartId}/adyen/sessions: sessão de checkout para o Drop-in;</li>
 * <li>POST .../users/current/carts/{cartId}/adyen/check: confere o carrinho antes de cobrar o cartão;</li>
 * <li>POST .../users/current/carts/{cartId}/adyen/order {"sessionId","sessionResult","resultCode","action"}:
 * fecha o pedido (cartão pago; Pix/boleto aguardando);</li>
 * <li>GET .../users/current/orders/{code}/adyen: meio, pspReference e o Pix/boleto da Adyen;</li>
 * <li>POST /{baseSiteId}/adyen/notifications: webhook da Adyen (sem login do torcedor; usuário/senha
 * Basic + HMAC), responde "[accepted]".</li>
 * </ul>
 * Recusas: {"errors":[{"type":"AdyenError","message":"..."}]}.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}")
public class AdyenController
{
	private static final String ERROR_TYPE = "AdyenError";

	private AdyenPaymentService adyenPaymentService;
	private UserService userService;
	private CartService cartService;
	private FlexibleSearchService flexibleSearchService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/users/{userId}/adyen/config", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> config(@PathVariable final String baseSiteId, @PathVariable final String userId)
	{
		return ResponseEntity.ok(adyenPaymentService.clientConfig());
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/users/{userId}/carts/{cartId}/adyen/paymentmethods", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> paymentMethods(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String cartId)
	{
		final CartModel cart = cart(cartId);
		if (cart == null)
		{
			return error(HttpStatus.NOT_FOUND, "Carrinho não encontrado.");
		}
		try
		{
			return ResponseEntity.ok(adyenPaymentService.paymentMethods(cart));
		}
		catch (final AdyenException e)
		{
			return error(HttpStatus.valueOf(e.getStatus()), e.getMessage());
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/users/{userId}/carts/{cartId}/adyen/sessions", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> session(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String cartId)
	{
		final CartModel cart = cart(cartId);
		if (cart == null)
		{
			return error(HttpStatus.NOT_FOUND, "Carrinho não encontrado.");
		}
		try
		{
			final AdyenSession s = adyenPaymentService.createSession(cart);
			final Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", s.id);
			m.put("sessionData", s.sessionData);
			m.put("reference", s.reference);
			m.put("amount", Long.valueOf(s.amount));
			m.put("currency", s.currency);
			m.put("allowedPaymentMethods", s.allowedPaymentMethods);
			m.put("installments", s.installments);
			m.put("preselectedInstallments", s.preselectedInstallments);
			m.put("clientKey", s.clientKey);
			m.put("environment", s.environment);
			m.put("shopper", s.shopper);
			return ResponseEntity.status(HttpStatus.CREATED).body(m);
		}
		catch (final AdyenException e)
		{
			return error(HttpStatus.valueOf(e.getStatus()), e.getMessage());
		}
	}

	/** Confere o carrinho antes de cobrar o cartão (as regras do "Finalizar pedido"); 204 = pode cobrar */
	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/users/{userId}/carts/{cartId}/adyen/check", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> check(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String cartId)
	{
		final CartModel cart = cart(cartId);
		if (cart == null)
		{
			return error(HttpStatus.NOT_FOUND, "Carrinho não encontrado.");
		}
		try
		{
			adyenPaymentService.check(cart);
			return ResponseEntity.noContent().build();
		}
		catch (final AdyenException e)
		{
			return error(HttpStatus.valueOf(e.getStatus()), e.getMessage());
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/users/{userId}/carts/{cartId}/adyen/order", method = RequestMethod.POST)
	@ResponseBody
	@SuppressWarnings("unchecked")
	public ResponseEntity<Object> placeOrder(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String cartId, @RequestBody final Map<String, Object> body)
	{
		final CartModel cart = cart(cartId);
		if (cart == null)
		{
			return error(HttpStatus.NOT_FOUND, "Carrinho não encontrado.");
		}
		try
		{
			final Object action = body == null ? null : body.get("action");
			final OrderModel order = adyenPaymentService.placeOrder(cart, string(body, "sessionId"), string(body, "sessionResult"),
					string(body, "resultCode"), action instanceof Map ? (Map<String, Object>) action : null);
			final Map<String, Object> m = new LinkedHashMap<>();
			m.put("orderCode", order.getCode());
			m.putAll(dates(adyenPaymentService.orderPayment(order)));
			return ResponseEntity.status(HttpStatus.CREATED).body(m);
		}
		catch (final AdyenException e)
		{
			return error(HttpStatus.valueOf(e.getStatus()), e.getMessage());
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/users/{userId}/orders/{code}/adyen", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> orderPayment(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String code)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta.");
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {code}=?c AND {user}=?u AND {versionID} IS NULL");
		q.addQueryParameter("c", code);
		q.addQueryParameter("u", customer);
		final List<OrderModel> orders = flexibleSearchService.<OrderModel> search(q).getResult();
		if (orders.isEmpty())
		{
			return error(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
		}
		return ResponseEntity.ok(dates(adyenPaymentService.orderPayment(orders.get(0))));
	}

	/** Webhook da Adyen: sempre "[accepted]" quando autorizado (a Adyen reenvia o que não for aceito) */
	@RequestMapping(value = "/adyen/notifications", method = RequestMethod.POST, produces = MediaType.TEXT_PLAIN_VALUE)
	@ResponseBody
	public ResponseEntity<String> notifications(@PathVariable final String baseSiteId,
			@RequestHeader(value = "Authorization", required = false) final String authorization,
			@RequestBody final Map<String, Object> body)
	{
		try
		{
			adyenPaymentService.handleNotifications(body, authorization);
			return ResponseEntity.ok("[accepted]");
		}
		catch (final AdyenException e)
		{
			return ResponseEntity.status(e.getStatus()).body(e.getMessage());
		}
	}

	// ------------------------------------------------------------ apoio

	protected CartModel cart(final String cartId)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return null;
		}
		if ("current".equals(cartId))
		{
			return cartService.hasSessionCart() ? cartService.getSessionCart() : null;
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {Cart} WHERE {code}=?code AND {user}=?u");
		q.addQueryParameter("code", cartId);
		q.addQueryParameter("u", customer);
		final List<CartModel> carts = flexibleSearchService.<CartModel> search(q).getResult();
		return carts.isEmpty() ? null : carts.get(0);
	}

	protected CustomerModel currentCustomer()
	{
		final UserModel user = userService.getCurrentUser();
		return user instanceof CustomerModel && !userService.isAnonymousUser(user) ? (CustomerModel) user : null;
	}

	protected static String string(final Map<String, Object> body, final String key)
	{
		final Object value = body == null ? null : body.get(key);
		return value == null ? null : String.valueOf(value);
	}

	/** Datas no formato da loja (ISO com fuso de Brasília) */
	protected static Map<String, Object> dates(final Map<String, Object> m)
	{
		final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		f.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
		final Map<String, Object> out = new LinkedHashMap<>();
		m.forEach((k, v) -> out.put(k, v instanceof Date ? f.format((Date) v) : v));
		return out;
	}

	protected static ResponseEntity<Object> error(final HttpStatus status, final String message)
	{
		final Map<String, String> error = new LinkedHashMap<>();
		error.put("type", ERROR_TYPE);
		error.put("message", message);
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("errors", Collections.singletonList(error));
		return ResponseEntity.status(status).body(body);
	}

	public void setAdyenPaymentService(final AdyenPaymentService adyenPaymentService)
	{
		this.adyenPaymentService = adyenPaymentService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setCartService(final CartService cartService)
	{
		this.cartService = cartService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}
}
