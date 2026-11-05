package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.enums.SocioPaymentMethod;
import br.com.flamengo.matchday.payment.adyen.AdyenException;
import br.com.flamengo.matchday.payment.adyen.AdyenPaymentService;
import br.com.flamengo.matchday.socio.SocioPayment;
import br.com.flamengo.matchday.socio.SocioPaymentException;
import br.com.flamengo.matchday.socio.SocioPaymentService;
import de.hybris.platform.core.model.order.AbstractOrderModel;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * Forma de pagamento do plano na API de comércio (OCC v2), item 7:
 * <ul>
 * <li>GET /{baseSiteId}/users/current/carts/{cartId}/sociopayment: o que o checkout
 * oferece (cartão mensal, cartão com o ano inteiro em até 12x, Pix e boleto) e a forma escolhida;</li>
 * <li>PUT .../carts/{cartId}/sociopayment {"method","installments"}: escolhe e recalcula o carrinho;</li>
 * <li>POST .../carts/{cartId}/sociopayment/order: fecha o pedido com Pix ou boleto (sem cartão);</li>
 * <li>GET /{baseSiteId}/users/current/sociopayment: o Pix ou boleto mais novo ainda não pago
 * ({"awaiting": false} se não há);</li>
 * <li>GET /{baseSiteId}/users/current/orders/{code}/sociopayment: situação do pagamento do pedido
 * (Pix copia e cola ou linha digitável, vencimento, confirmação).</li>
 * </ul>
 * Recusas: 400 com {"errors":[{"type":"SocioPaymentError","message":"..."}]}.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}")
public class SocioPaymentController
{
	private static final String ERROR_TYPE = "SocioPaymentError";

	private SocioPaymentService socioPaymentService;
	private UserService userService;
	private CartService cartService;
	private FlexibleSearchService flexibleSearchService;
	/** Pix e boleto pela Adyen (05/11); sem o bean, o código fictício de sempre */
	private AdyenPaymentService adyenPaymentService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/carts/{cartId}/sociopayment", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> options(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String cartId)
	{
		final CartModel cart = cart(cartId);
		if (cart == null)
		{
			return error(HttpStatus.NOT_FOUND, "Carrinho não encontrado.");
		}
		return ResponseEntity.ok(withAdyen(toMap(socioPaymentService.options(cart)), null));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/carts/{cartId}/sociopayment", method = RequestMethod.PUT)
	@ResponseBody
	public ResponseEntity<Object> choose(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String cartId, @RequestBody final Map<String, Object> body)
	{
		final CartModel cart = cart(cartId);
		if (cart == null)
		{
			return error(HttpStatus.NOT_FOUND, "Carrinho não encontrado.");
		}
		final Object method = body == null ? null : body.get("method");
		final Object installments = body == null ? null : body.get("installments");
		try
		{
			return ResponseEntity.ok(withAdyen(toMap(socioPaymentService.choose(cart, method == null ? null : method.toString(),
					installments instanceof Number ? Integer.valueOf(((Number) installments).intValue())
							: installments == null || installments.toString().isEmpty() ? null : Integer.valueOf(installments.toString()))),
					null));
		}
		catch (final SocioPaymentException | NumberFormatException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/carts/{cartId}/sociopayment/order", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> placeOrder(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String cartId, @RequestBody(required = false) final Map<String, Object> body)
	{
		final CartModel cart = cart(cartId);
		if (cart == null)
		{
			return error(HttpStatus.NOT_FOUND, "Carrinho não encontrado.");
		}
		try
		{
			final SocioPaymentMethod method = socioPaymentService.validateUpfront(cart);
			if (adyenHandles(method))
			{
				final Object cpf = body == null ? null : body.get("cpf");
				adyenPaymentService.prepareUpfront(cart, method == SocioPaymentMethod.PIX, cpf == null ? null : cpf.toString());
			}
			final OrderModel order = socioPaymentService.placeOrder(cart);
			return ResponseEntity.status(HttpStatus.CREATED).body(withAdyen(toMap(socioPaymentService.status(order)), order));
		}
		catch (final SocioPaymentException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		catch (final AdyenException e)
		{
			return error(e.getStatus() >= 500 ? HttpStatus.BAD_GATEWAY : HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/orders/{code}/sociopayment", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> orderStatus(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String code)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta.");
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {code}=?code AND {user}=?u AND {versionID} IS NULL");
		q.addQueryParameter("code", code);
		q.addQueryParameter("u", customer);
		final List<OrderModel> orders = flexibleSearchService.<OrderModel> search(q).getResult();
		if (orders.isEmpty())
		{
			return error(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
		}
		final SocioPayment status = socioPaymentService.status(orders.get(0));
		final Map<String, Object> body = status == null ? new LinkedHashMap<>() : withAdyen(toMap(status), orders.get(0));
		body.put("orderCode", code);
		return ResponseEntity.ok(body);
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/sociopayment", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> pending(@PathVariable final String baseSiteId, @PathVariable final String userId)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta.");
		}
		final OrderModel order = socioPaymentService.awaitingOrder(customer);
		final SocioPayment status = order == null ? null : socioPaymentService.status(order);
		if (status == null)
		{
			final Map<String, Object> body = new LinkedHashMap<>();
			body.put("enabled", Boolean.valueOf(socioPaymentService.isEnabled()));
			body.put("awaiting", Boolean.FALSE);
			return ResponseEntity.ok(body);
		}
		return ResponseEntity.ok(withAdyen(toMap(status), order));
	}

	// ------------------------------------------------------------ apoio

	protected boolean adyenHandles(final SocioPaymentMethod method)
	{
		if (adyenPaymentService == null || method == null)
		{
			return false;
		}
		final List<String> methods = adyenPaymentService.methods();
		return method == SocioPaymentMethod.PIX ? methods.contains("pix")
				: method == SocioPaymentMethod.BOLETO && methods.contains("boletobancario");
	}

	/**
	 * Adyen (05/11): no checkout, quais formas passam por ela ({@code adyenMethods}, o boleto pede o CPF);
	 * no pedido, o pspReference, a imagem do QR Code do Pix e o PDF do boleto.
	 */
	@SuppressWarnings("unchecked")
	protected Map<String, Object> withAdyen(final Map<String, Object> m, final AbstractOrderModel order)
	{
		if (adyenPaymentService == null)
		{
			return m;
		}
		try
		{
			if (order == null)
			{
				final List<String> methods = new java.util.ArrayList<>();
				if (adyenHandles(SocioPaymentMethod.PIX))
				{
					methods.add("PIX");
				}
				if (adyenHandles(SocioPaymentMethod.BOLETO))
				{
					methods.add("BOLETO");
				}
				m.put("adyenMethods", methods);
				return m;
			}
			final Map<String, Object> p = adyenPaymentService.orderPayment(order);
			if (Boolean.TRUE.equals(p.get("adyen")))
			{
				m.put("provider", "adyen");
				m.put("pspReference", p.get("pspReference"));
				final Object action = p.get("action");
				if (action instanceof Map)
				{
					final Map<String, Object> a = (Map<String, Object>) action;
					m.put("qrImageUrl", a.get("url"));
					m.put("pdfUrl", a.get("downloadUrl"));
				}
			}
		}
		catch (final RuntimeException e)
		{
			// a Adyen nunca impede o resto da resposta
		}
		return m;
	}

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

	protected static Map<String, Object> toMap(final SocioPayment p)
	{
		final Map<String, Object> m = new LinkedHashMap<>();
		m.put("enabled", Boolean.valueOf(p.enabled));
		m.put("planCode", p.planCode);
		m.put("planName", p.planName);
		m.put("upfrontAllowed", Boolean.valueOf(p.upfrontAllowed));
		m.put("monthly", Double.valueOf(p.monthly));
		m.put("months", Integer.valueOf(p.months));
		m.put("annual", Double.valueOf(p.annual));
		m.put("methods", p.methods);
		m.put("maxInstallments", Integer.valueOf(p.maxInstallments));
		m.put("method", p.method);
		m.put("installments", p.installments);
		m.put("total", Double.valueOf(p.total));
		m.put("orderCode", p.orderCode);
		m.put("orderDate", format(p.orderDate));
		m.put("due", format(p.due));
		m.put("code", p.code);
		m.put("confirmedAt", format(p.confirmedAt));
		m.put("awaiting", Boolean.valueOf(p.awaiting));
		m.put("summary", p.summary);
		return m;
	}

	protected static String format(final Date date)
	{
		if (date == null)
		{
			return null;
		}
		final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		f.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
		return f.format(date);
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

	public void setSocioPaymentService(final SocioPaymentService socioPaymentService)
	{
		this.socioPaymentService = socioPaymentService;
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

	public void setAdyenPaymentService(final AdyenPaymentService adyenPaymentService)
	{
		this.adyenPaymentService = adyenPaymentService;
	}
}
