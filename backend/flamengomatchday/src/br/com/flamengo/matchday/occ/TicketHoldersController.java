package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.socio.SocioGuestException;
import br.com.flamengo.matchday.socio.SocioGuestService;
import br.com.flamengo.matchday.ticket.TicketHolder;
import br.com.flamengo.matchday.ticket.TicketHolderService;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
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
 * Quem vai usar cada ingresso adicional de quem não é sócio (04/11), na API de comércio:
 * <ul>
 * <li>GET /{baseSiteId}/users/current/ticketholders?cartId=...: poltronas de jogo do
 * carrinho com nome, CPF, nascimento e e-mail de quem vai usar;</li>
 * <li>PUT /{baseSiteId}/users/current/ticketholders: grava ({"cartId","seatCode","name",
 * "cpf","birthDate":"aaaa-mm-dd","email"}; name vazio = volta para o comprador).</li>
 * </ul>
 * Recusas voltam com 400 e {"errors":[{"type":"TicketHolderError","message":"..."}]}.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}/ticketholders")
public class TicketHoldersController
{
	private static final String ERROR_TYPE = "TicketHolderError";

	private TicketHolderService ticketHolderService;
	private UserService userService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> list(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@RequestParam(value = "cartId", required = false) final String cartId)
	{
		final CustomerModel owner = currentCustomer();
		if (owner == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta.");
		}
		try
		{
			return ResponseEntity.ok(toDto(ticketHolderService.holders(owner, cartId)));
		}
		catch (final SocioGuestException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.PUT)
	@ResponseBody
	public ResponseEntity<Object> save(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@RequestBody final Map<String, String> body)
	{
		final CustomerModel owner = currentCustomer();
		if (owner == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta.");
		}
		if (body == null || StringUtils.isBlank(body.get("seatCode")))
		{
			return error(HttpStatus.BAD_REQUEST, "Informe a poltrona.");
		}
		try
		{
			final Date birthDate = SocioGuestsController.parseDate(body.get("birthDate"));
			ticketHolderService.setHolder(owner, body.get("cartId"), body.get("seatCode"), body.get("name"), body.get("cpf"),
					birthDate, body.get("email"));
			return ResponseEntity.ok(toDto(ticketHolderService.holders(owner, body.get("cartId"))));
		}
		catch (final SocioGuestException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	protected CustomerModel currentCustomer()
	{
		final UserModel user = userService.getCurrentUser();
		return user instanceof CustomerModel && !userService.isAnonymousUser(user) ? (CustomerModel) user : null;
	}

	protected static Map<String, Object> toDto(final List<TicketHolder> holders)
	{
		final List<Map<String, Object>> list = new ArrayList<>();
		for (final TicketHolder h : holders)
		{
			final Map<String, Object> m = new LinkedHashMap<>();
			m.put("seatCode", h.seatCode);
			m.put("gameCode", h.gameCode);
			m.put("required", Boolean.valueOf(h.required));
			m.put("name", h.name);
			m.put("cpf", h.cpf == null ? null : SocioGuestService.formatCpf(h.cpf));
			m.put("birthDate", SocioGuestsController.format(h.birthDate, "yyyy-MM-dd"));
			m.put("email", h.email);
			list.add(m);
		}
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("holders", list);
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

	public void setTicketHolderService(final TicketHolderService ticketHolderService)
	{
		this.ticketHolderService = ticketHolderService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
