package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.model.SocioGuestModel;
import br.com.flamengo.matchday.socio.SocioGuestException;
import br.com.flamengo.matchday.socio.SocioGuestOverview;
import br.com.flamengo.matchday.socio.SocioGuestService;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.user.UserService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
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
 * Meus convidados na API de comércio (OCC v2), para a área do sócio na loja:
 * <ul>
 * <li>GET /{baseSiteId}/users/current/socioguests: plano, vagas e convidados;</li>
 * <li>POST /{baseSiteId}/users/current/socioguests: cadastra um convidado;</li>
 * <li>DELETE /{baseSiteId}/users/current/socioguests/{id}: tira o convidado.</li>
 * </ul>
 * O filtro de usuário da API já põe o torcedor do token na sessão. Recusas voltam
 * com 400 e {"errors":[{"type":"SocioGuestError","message":"..."}]}, a mensagem em
 * português. Carregado pela commercewebservices a partir de
 * resources/occ/v2/flamengomatchdayocc/web/spring/flamengomatchday-web-spring.xml.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}/socioguests")
public class SocioGuestsController
{
	private static final String ERROR_TYPE = "SocioGuestError";

	private SocioGuestService socioGuestService;
	private UserService userService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> list(@PathVariable final String baseSiteId, @PathVariable final String userId)
	{
		final CustomerModel owner = currentCustomer();
		if (owner == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para ver os convidados.");
		}
		return ResponseEntity.ok(toDto(socioGuestService.overview(owner)));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> add(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@RequestBody final SocioGuestWsDTO guest)
	{
		final CustomerModel owner = currentCustomer();
		if (owner == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para cadastrar convidados.");
		}
		if (guest == null)
		{
			return error(HttpStatus.BAD_REQUEST, "Informe os dados do convidado.");
		}
		try
		{
			final Date birthDate = parseDate(guest.getBirthDate());
			socioGuestService.add(owner, guest.getName(), guest.getCpf(), guest.getEmail(), guest.getPhone(), birthDate);
		}
		catch (final SocioGuestException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(toDto(socioGuestService.overview(owner)));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/{guestId}", method = RequestMethod.DELETE)
	@ResponseBody
	public ResponseEntity<Object> remove(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@PathVariable final String guestId)
	{
		final CustomerModel owner = currentCustomer();
		if (owner == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para tirar convidados.");
		}
		try
		{
			socioGuestService.remove(owner, guestId);
		}
		catch (final SocioGuestException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		return ResponseEntity.ok(toDto(socioGuestService.overview(owner)));
	}

	protected CustomerModel currentCustomer()
	{
		final UserModel user = userService.getCurrentUser();
		return user instanceof CustomerModel && !userService.isAnonymousUser(user) ? (CustomerModel) user : null;
	}

	/** aaaa-mm-dd; vazio ou inválido vira null (o serviço recusa com a mensagem certa) */
	protected static Date parseDate(final String value) throws SocioGuestException
	{
		if (StringUtils.isBlank(value))
		{
			return null;
		}
		final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
		f.setLenient(false);
		f.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
		try
		{
			return f.parse(value.trim());
		}
		catch (final ParseException e)
		{
			throw new SocioGuestException("Data de nascimento inválida.");
		}
	}

	protected static SocioGuestListWsDTO toDto(final SocioGuestOverview o)
	{
		final SocioGuestListWsDTO dto = new SocioGuestListWsDTO();
		dto.setEnabled(Boolean.valueOf(o.enabled));
		dto.setPlanCode(o.planCode);
		dto.setPlanName(o.planName);
		dto.setPlanMaxGuests(Integer.valueOf(o.planMaxGuests));
		dto.setContracted(Integer.valueOf(o.contracted));
		dto.setUsed(Integer.valueOf(o.active.size()));
		dto.setGuestPrice(Double.valueOf(o.guestPrice));
		dto.setSuspended(Boolean.valueOf(o.suspended));
		dto.setCanAdd(Boolean.valueOf(o.canAdd));
		dto.setReason(o.reason);
		o.active.forEach(g -> dto.getGuests().add(toDto(g)));
		o.ended.forEach(g -> dto.getEnded().add(toDto(g)));
		return dto;
	}

	protected static SocioGuestWsDTO toDto(final SocioGuestModel g)
	{
		final SocioGuestWsDTO dto = new SocioGuestWsDTO();
		dto.setId(g.getPk() == null ? null : g.getPk().toString());
		dto.setName(g.getName());
		dto.setCpf(SocioGuestService.formatCpf(g.getCpf()));
		dto.setEmail(g.getEmail());
		dto.setPhone(g.getPhone());
		dto.setBirthDate(format(g.getBirthDate(), "yyyy-MM-dd"));
		dto.setActive(g.getActive());
		dto.setSince(format(g.getSince(), "yyyy-MM-dd'T'HH:mm:ssXXX"));
		dto.setUntil(format(g.getUntil(), "yyyy-MM-dd'T'HH:mm:ssXXX"));
		dto.setEndReason(g.getEndReason());
		return dto;
	}

	protected static String format(final Date date, final String pattern)
	{
		if (date == null)
		{
			return null;
		}
		final SimpleDateFormat f = new SimpleDateFormat(pattern);
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

	public void setSocioGuestService(final SocioGuestService socioGuestService)
	{
		this.socioGuestService = socioGuestService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
