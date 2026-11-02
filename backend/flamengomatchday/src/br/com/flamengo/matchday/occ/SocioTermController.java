package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.socio.SocioTermException;
import br.com.flamengo.matchday.socio.SocioTermService;
import br.com.flamengo.matchday.socio.SocioTermStatus;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.user.UserService;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
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
 * Termo do Nação na API de comércio (OCC v2):
 * <ul>
 * <li>GET /{baseSiteId}/users/current/socioterm: versão atual (título e texto na língua
 * da chamada, lang=pt|en) e o aceite do torcedor;</li>
 * <li>POST /{baseSiteId}/users/current/socioterm/acceptance: aceita a versão atual.
 * Com {"cartId"} é o aceite do checkout (carrinho com plano); sem, o da área do sócio.</li>
 * </ul>
 * Recusas: 400 com {"errors":[{"type":"SocioTermError","message":"..."}]}.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}/socioterm")
public class SocioTermController
{
	private static final String ERROR_TYPE = "SocioTermError";

	private SocioTermService socioTermService;
	private UserService userService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> status(@PathVariable final String baseSiteId, @PathVariable final String userId)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para ver o Termo do Nação.");
		}
		return ResponseEntity.ok(toDto(socioTermService.status(customer)));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/acceptance", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> accept(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@RequestBody(required = false) final SocioTermWsDTO body)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para aceitar o Termo do Nação.");
		}
		try
		{
			socioTermService.accept(customer, body == null ? null : body.getCartId());
		}
		catch (final SocioTermException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(toDto(socioTermService.status(customer)));
	}

	protected CustomerModel currentCustomer()
	{
		final UserModel user = userService.getCurrentUser();
		return user instanceof CustomerModel && !userService.isAnonymousUser(user) ? (CustomerModel) user : null;
	}

	protected static SocioTermWsDTO toDto(final SocioTermStatus s)
	{
		final SocioTermWsDTO dto = new SocioTermWsDTO();
		dto.setEnabled(Boolean.valueOf(s.enabled));
		dto.setMember(Boolean.valueOf(s.member));
		dto.setVersion(s.version);
		dto.setTitle(s.title);
		dto.setText(s.text);
		dto.setSummary(s.summary);
		dto.setPublishedAt(format(s.publishedAt));
		dto.setAccepted(Boolean.valueOf(s.accepted));
		dto.setAcceptedVersion(s.acceptedVersion);
		dto.setAcceptedAt(format(s.acceptedAt));
		dto.setAcceptedOrigin(s.acceptedOrigin);
		dto.setAcceptedOrder(s.acceptedOrder);
		return dto;
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

	public void setSocioTermService(final SocioTermService socioTermService)
	{
		this.socioTermService = socioTermService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
