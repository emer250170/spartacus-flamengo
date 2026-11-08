package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.estudio.MengoEstudioOrderService;
import br.com.flamengo.matchday.estudio.MengoEstudioService;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.user.UserService;

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
 * Mengo Estúdio nos pedidos (08/11, PR 4), torcedor logado:
 * <ul>
 * <li>GET /{baseSiteId}/users/{userId}/mengo-estudio/pedidos: os pedidos do torcedor com a origem (loja, estudio) e a
 * situação do Estúdio, para o selo e o filtro de Meus pedidos;</li>
 * <li>GET /{baseSiteId}/users/{userId}/orders/{code}/mengo-estudio: situação, linha do tempo, rastreio, camisetas com
 * número de série e se ainda dá para cancelar;</li>
 * <li>POST /{baseSiteId}/users/{userId}/orders/{code}/mengo-estudio/cancelamento: pede o cancelamento à estamparia;
 * devolve "resultado" (ACEITO, RECUSADO ou FALHA), "mensagem" e o pedido atualizado;</li>
 * <li>POST …/mengo-estudio/devolucao ({motivo, solucao, comentario}) e …/devolucao/envio (PR 5): pede a devolução e
 * informa que a peça foi postada; devolvem o pedido atualizado.</li>
 * </ul>
 * Recusas voltam com 400 e {"errors":[{"type":"MengoEstudioError","message":"..."}]}.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}")
public class MengoEstudioOrderController
{
	private MengoEstudioService mengoEstudioService;
	private MengoEstudioOrderService mengoEstudioOrderService;
	private UserService userService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/mengo-estudio/pedidos", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> pedidos(@PathVariable final String baseSiteId, @PathVariable final String userId)
	{
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("ativo", Boolean.valueOf(mengoEstudioService.isActive()));
		body.put("pedidos", mengoEstudioOrderService.pedidos(currentCustomer()));
		return ResponseEntity.ok(body);
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/orders/{code}/mengo-estudio", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> pedido(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String code)
	{
		final OrderModel order = mengoEstudioOrderService.pedido(currentCustomer(), code);
		if (order == null)
		{
			return MengoEstudioController.error(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
		}
		return ResponseEntity.ok(mengoEstudioOrderService.detalhe(order));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/orders/{code}/mengo-estudio/cancelamento", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> cancelar(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String code)
	{
		final CustomerModel customer = currentCustomer();
		final OrderModel order = mengoEstudioOrderService.pedido(customer, code);
		if (order == null)
		{
			return MengoEstudioController.error(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
		}
		try
		{
			final Map<String, Object> body = new LinkedHashMap<>(mengoEstudioOrderService.cancelar(order, customer.getUid()));
			body.put("pedido", mengoEstudioOrderService.detalhe(order));
			return ResponseEntity.ok(body);
		}
		catch (final IllegalArgumentException e)
		{
			return MengoEstudioController.error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	/** Pede a devolução: {"motivo": DEFEITO | ERRO | ARREPENDIMENTO, "solucao": REEMBOLSO | REIMPRESSAO, "comentario"} */
	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/orders/{code}/mengo-estudio/devolucao", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> devolucao(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String code, @RequestBody(required = false) final Map<String, Object> req)
	{
		final CustomerModel customer = currentCustomer();
		final OrderModel order = mengoEstudioOrderService.pedido(customer, code);
		if (order == null)
		{
			return MengoEstudioController.error(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
		}
		try
		{
			final Map<String, Object> r = req == null ? new LinkedHashMap<>() : req;
			mengoEstudioOrderService.pedirDevolucao(order, text(r.get("motivo")), text(r.get("solucao")),
					text(r.get("comentario")), customer.getUid());
			return ResponseEntity.ok(mengoEstudioOrderService.detalhe(order));
		}
		catch (final IllegalArgumentException e)
		{
			return MengoEstudioController.error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	/** O torcedor informa que postou a peça */
	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/orders/{code}/mengo-estudio/devolucao/envio", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> envio(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String code)
	{
		final CustomerModel customer = currentCustomer();
		final OrderModel order = mengoEstudioOrderService.pedido(customer, code);
		if (order == null)
		{
			return MengoEstudioController.error(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
		}
		try
		{
			mengoEstudioOrderService.enviarDevolucao(order, customer.getUid());
			return ResponseEntity.ok(mengoEstudioOrderService.detalhe(order));
		}
		catch (final IllegalArgumentException e)
		{
			return MengoEstudioController.error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	protected static String text(final Object v)
	{
		return v == null ? null : String.valueOf(v);
	}

	protected CustomerModel currentCustomer()
	{
		final UserModel user = userService.getCurrentUser();
		return user instanceof CustomerModel && !userService.isAnonymousUser(user) ? (CustomerModel) user : null;
	}

	public void setMengoEstudioService(final MengoEstudioService mengoEstudioService)
	{
		this.mengoEstudioService = mengoEstudioService;
	}

	public void setMengoEstudioOrderService(final MengoEstudioOrderService mengoEstudioOrderService)
	{
		this.mengoEstudioOrderService = mengoEstudioOrderService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
