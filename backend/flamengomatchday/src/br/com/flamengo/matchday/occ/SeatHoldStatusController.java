package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.service.SeatHoldService;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.site.BaseSiteService;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * Situação das reservas de poltrona do carrinho (08/11): para o carrinho avisar quando a reserva venceu e
 * travar o "Finalizar compra" até o torcedor retirar a poltrona e reservar de novo.
 * <p>
 * GET /{baseSiteId}/users/current/carts/{cartId}/poltronas-reservadas →
 * {"agora": ms, "bloqueia": true, "poltronas": [{"codigo", "expiraEm": ms|null, "vencida": bool}]}
 * Só o carrinho do torcedor logado ("current" ou o código dele); outro carrinho: 404.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}/carts/{cartId}/poltronas-reservadas")
public class SeatHoldStatusController
{
	private SeatHoldService seatHoldService;
	private CommerceCartService commerceCartService;
	private BaseSiteService baseSiteService;
	private UserService userService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> situacao(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String cartId)
	{
		final UserModel user = userService.getCurrentUser();
		if (user == null || userService.isAnonymousUser(user))
		{
			return MengoEstudioController.error(HttpStatus.UNAUTHORIZED, "Entre na sua conta.");
		}
		final CartModel cart = carrinho(user, cartId);
		if (cart == null)
		{
			return MengoEstudioController.error(HttpStatus.NOT_FOUND, "Carrinho não encontrado.");
		}
		final List<Map<String, Object>> poltronas = new ArrayList<>();
		if (cart.getEntries() != null)
		{
			for (final AbstractOrderEntryModel entry : cart.getEntries())
			{
				final ProductModel product = entry.getProduct();
				if (product == null || !seatHoldService.isSeat(product))
				{
					continue;
				}
				final Date expira = seatHoldService.activeHoldExpiry(product, cart);
				final Map<String, Object> p = new LinkedHashMap<>();
				p.put("codigo", product.getCode());
				p.put("expiraEm", expira == null ? null : Long.valueOf(expira.getTime()));
				p.put("vencida", Boolean.valueOf(expira == null));
				poltronas.add(p);
			}
		}
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("agora", Long.valueOf(System.currentTimeMillis()));
		body.put("bloqueia", Boolean.valueOf(seatHoldService.expiredHoldBlocks()));
		body.put("poltronas", poltronas);
		return ResponseEntity.ok(body);
	}

	protected CartModel carrinho(final UserModel user, final String cartId)
	{
		if (cartId == null || "current".equals(cartId))
		{
			final BaseSiteModel site = baseSiteService.getCurrentBaseSite();
			return site == null ? null : commerceCartService.getCartForGuidAndSiteAndUser(null, site, user);
		}
		return commerceCartService.getCartForCodeAndUser(cartId.trim(), user);
	}

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}

	public void setCommerceCartService(final CommerceCartService commerceCartService)
	{
		this.commerceCartService = commerceCartService;
	}

	public void setBaseSiteService(final BaseSiteService baseSiteService)
	{
		this.baseSiteService = baseSiteService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
