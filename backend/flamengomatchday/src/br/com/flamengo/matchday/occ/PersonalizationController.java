package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.personalization.Personalization;
import br.com.flamengo.matchday.personalization.PersonalizationException;
import br.com.flamengo.matchday.personalization.PersonalizationOption;
import br.com.flamengo.matchday.personalization.PersonalizationService;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * Personalização do manto (06/11) na API de comércio:
 * <ul>
 * <li>GET /{baseSiteId}/flapersonalization/options?productCode=...: se o produto pode ser
 * personalizado e as opções com preço (nome e número, kit de patrocínio, patches);</li>
 * <li>GET /{baseSiteId}/users/{userId}/carts/{cartId}/flapersonalization: mantos personalizados
 * do carrinho;</li>
 * <li>PUT a mesma URL ({"productCode","name","number","sponsor":true|false,"patchCode"}): grava
 * a personalização do manto e ajusta as linhas de preço;</li>
 * <li>GET /{baseSiteId}/users/{userId}/orders/{code}/flapersonalization: mantos personalizados
 * do pedido.</li>
 * </ul>
 * Recusas voltam com 400 e {"errors":[{"type":"PersonalizationError","message":"..."}]}.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}")
public class PersonalizationController
{
	private static final String ERROR_TYPE = "PersonalizationError";

	private PersonalizationService personalizationService;
	private UserService userService;

	@RequestMapping(value = "/flapersonalization/options", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> options(@PathVariable final String baseSiteId,
			@RequestParam(value = "productCode") final String productCode)
	{
		final Map<String, Object> body = new LinkedHashMap<>();
		final boolean available = personalizationService.isPersonalizable(productCode);
		body.put("available", Boolean.valueOf(available));
		if (available)
		{
			body.put("nameNumber", option(personalizationService.nameNumberOption()));
			body.put("sponsor", option(personalizationService.sponsorOption()));
			final List<Map<String, Object>> patches = new ArrayList<>();
			for (final PersonalizationOption o : personalizationService.patchOptions())
			{
				patches.add(option(o));
			}
			body.put("patches", patches);
		}
		return ResponseEntity.ok(body);
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/users/{userId}/carts/{cartId}/flapersonalization", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> list(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String cartId)
	{
		final CustomerModel owner = currentCustomer();
		if (owner == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para personalizar.");
		}
		try
		{
			return ResponseEntity.ok(toDto(personalizationService.list(owner, cartId)));
		}
		catch (final PersonalizationException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/users/{userId}/carts/{cartId}/flapersonalization", method = RequestMethod.PUT)
	@ResponseBody
	public ResponseEntity<Object> save(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String cartId, @RequestBody final Map<String, Object> body)
	{
		final CustomerModel owner = currentCustomer();
		if (owner == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para personalizar.");
		}
		if (body == null || !(body.get("productCode") instanceof String))
		{
			return error(HttpStatus.BAD_REQUEST, "Informe o produto.");
		}
		try
		{
			return ResponseEntity.ok(toDto(personalizationService.set(owner, cartId, (String) body.get("productCode"),
					text(body.get("name")), text(body.get("number")), Boolean.TRUE.equals(body.get("sponsor")),
					text(body.get("patchCode")))));
		}
		catch (final PersonalizationException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/users/{userId}/orders/{code}/flapersonalization", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> order(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String code)
	{
		final CustomerModel owner = currentCustomer();
		if (owner == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta.");
		}
		try
		{
			return ResponseEntity.ok(toDto(personalizationService.listOrder(owner, code)));
		}
		catch (final PersonalizationException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	protected CustomerModel currentCustomer()
	{
		final UserModel user = userService.getCurrentUser();
		return user instanceof CustomerModel && !userService.isAnonymousUser(user) ? (CustomerModel) user : null;
	}

	protected static String text(final Object value)
	{
		return value == null ? null : String.valueOf(value);
	}

	protected static Map<String, Object> option(final PersonalizationOption o)
	{
		if (o == null)
		{
			return null;
		}
		final Map<String, Object> m = new LinkedHashMap<>();
		m.put("code", o.code);
		m.put("name", o.name);
		m.put("price", o.price);
		return m;
	}

	protected static Map<String, Object> toDto(final List<Personalization> items)
	{
		final List<Map<String, Object>> list = new ArrayList<>();
		for (final Personalization p : items)
		{
			final Map<String, Object> m = new LinkedHashMap<>();
			m.put("entryNumber", Integer.valueOf(p.entryNumber));
			m.put("productCode", p.productCode);
			m.put("name", p.name);
			m.put("number", p.number);
			m.put("sponsor", Boolean.valueOf(p.sponsor));
			m.put("patchCode", p.patchCode);
			m.put("patchName", p.patchName);
			m.put("addOnEntries", p.addOnEntries);
			list.add(m);
		}
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("items", list);
		return body;
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

	public void setPersonalizationService(final PersonalizationService personalizationService)
	{
		this.personalizationService = personalizationService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
