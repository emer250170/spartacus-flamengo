package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.estudio.MengoEstudioCartService;
import br.com.flamengo.matchday.estudio.MengoEstudioService;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * Mengo Estúdio no carrinho (08/11, PR 3), torcedor logado:
 * <ul>
 * <li>GET /{baseSiteId}/users/{userId}/carts/{cartId}/mengo-estudio: linhas do Estúdio (estampa, camiseta, cor,
 * tamanho, se a estampa saiu de linha) e o bloqueio do fechamento (mensagem ou null);</li>
 * <li>POST a mesma URL ({"estampa","camiseta","cor","tamanho","quantidade","substituir"}): inclui a camiseta com a
 * estampa (ou soma, se a combinação já está no carrinho); "substituir" = linha que está sendo editada. cartId
 * "current" usa o carrinho mais recente ou cria um.</li>
 * </ul>
 * Recusas voltam com 400 e {"errors":[{"type":"MengoEstudioError","message":"..."}]}.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}/carts/{cartId}/mengo-estudio")
public class MengoEstudioCartController
{
	private MengoEstudioService mengoEstudioService;
	private MengoEstudioCartService mengoEstudioCartService;
	private UserService userService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> itens(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String cartId)
	{
		try
		{
			final CartModel cart = mengoEstudioCartService.carrinho(currentCustomer(), cartId);
			return ResponseEntity.ok(body(cart));
		}
		catch (final IllegalArgumentException e)
		{
			return MengoEstudioController.error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> adicionar(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String cartId, @RequestBody final Map<String, Object> req)
	{
		if (!mengoEstudioService.isActive())
		{
			return MengoEstudioController.error(HttpStatus.NOT_FOUND, "O Mengo Estúdio não está disponível agora.");
		}
		try
		{
			final Map<String, Object> r = req == null ? Collections.emptyMap() : req;
			final CartModel cart = mengoEstudioCartService.carrinho(currentCustomer(), cartId);
			final Map<String, Object> out = new LinkedHashMap<>(mengoEstudioCartService.adicionar(cart, text(r.get("estampa")),
					text(r.get("camiseta")), text(r.get("cor")), text(r.get("tamanho")), number(r.get("quantidade"), 1),
					r.get("substituir") == null ? null : Integer.valueOf(number(r.get("substituir"), -1))));
			out.putAll(body(cart));
			return ResponseEntity.ok(out);
		}
		catch (final IllegalArgumentException e)
		{
			return MengoEstudioController.error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	protected Map<String, Object> body(final CartModel cart)
	{
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("cartCode", cart.getCode());
		body.put("itens", mengoEstudioCartService.itens(cart));
		body.put("bloqueio", mengoEstudioCartService.bloqueio(cart));
		body.put("agora", Long.valueOf(mengoEstudioService.now()));
		return body;
	}

	protected CustomerModel currentCustomer()
	{
		final UserModel user = userService.getCurrentUser();
		return user instanceof CustomerModel && !userService.isAnonymousUser(user) ? (CustomerModel) user : null;
	}

	protected static String text(final Object v)
	{
		return v == null ? null : String.valueOf(v);
	}

	protected static int number(final Object v, final int def)
	{
		if (v instanceof Number)
		{
			return ((Number) v).intValue();
		}
		try
		{
			return v == null ? def : Integer.parseInt(String.valueOf(v).trim());
		}
		catch (final NumberFormatException e)
		{
			return def;
		}
	}

	public void setMengoEstudioService(final MengoEstudioService mengoEstudioService)
	{
		this.mengoEstudioService = mengoEstudioService;
	}

	public void setMengoEstudioCartService(final MengoEstudioCartService mengoEstudioCartService)
	{
		this.mengoEstudioCartService = mengoEstudioCartService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
