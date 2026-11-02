package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.socio.SocioContractException;
import br.com.flamengo.matchday.socio.SocioContractService;
import br.com.flamengo.matchday.socio.SocioContractStatus;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.user.UserService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
 * Vigência do sócio na API de comércio (OCC v2):
 * <ul>
 * <li>GET /{baseSiteId}/users/current/sociocontract: plano, início e fim,
 * renovação automática, mudança agendada, prazo e planos menores;</li>
 * <li>POST /sociocontract/autorenew {"autoRenew": true|false};</li>
 * <li>POST /sociocontract/nextplan {"nextPlanCode": "NACAO-MARACA-3"} (vazio cancela).</li>
 * </ul>
 * Recusas (fora do prazo, plano inválido): 400 {"errors":[{"type":"SocioContractError",...}]}.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}/sociocontract")
public class SocioContractController
{
	private static final String ERROR_TYPE = "SocioContractError";
	private static final String ORIGIN = "Loja";

	private SocioContractService socioContractService;
	private UserService userService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> status(@PathVariable final String baseSiteId, @PathVariable final String userId)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para ver a vigência.");
		}
		return ResponseEntity.ok(toDto(socioContractService.status(customer), null));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/autorenew", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> autoRenew(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@RequestBody final SocioContractWsDTO body)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para mudar a renovação.");
		}
		if (body == null || body.getAutoRenew() == null)
		{
			return error(HttpStatus.BAD_REQUEST, "Informe autoRenew (true ou false).");
		}
		try
		{
			final String message = socioContractService.setAutoRenew(customer, body.getAutoRenew().booleanValue(), ORIGIN);
			return ResponseEntity.ok(toDto(socioContractService.status(customer), message));
		}
		catch (final SocioContractException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/nextplan", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> nextPlan(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@RequestBody(required = false) final SocioContractWsDTO body)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para mudar de plano.");
		}
		try
		{
			final String message = socioContractService.scheduleChange(customer, body == null ? null : body.getNextPlanCode(),
					ORIGIN);
			return ResponseEntity.ok(toDto(socioContractService.status(customer), message));
		}
		catch (final SocioContractException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	protected CustomerModel currentCustomer()
	{
		final UserModel user = userService.getCurrentUser();
		return user instanceof CustomerModel && !userService.isAnonymousUser(user) ? (CustomerModel) user : null;
	}

	protected static SocioContractWsDTO toDto(final SocioContractStatus s, final String message)
	{
		final SocioContractWsDTO dto = new SocioContractWsDTO();
		dto.setEnabled(Boolean.valueOf(s.enabled));
		dto.setMember(Boolean.valueOf(s.member));
		dto.setPlanCode(s.planCode);
		dto.setPlanName(s.planName);
		dto.setStart(format(s.start));
		dto.setEnd(format(s.end));
		dto.setAutoRenew(Boolean.valueOf(s.autoRenew));
		dto.setNextPlanCode(s.nextPlanCode);
		dto.setNextPlanName(s.nextPlanName);
		dto.setChangeDeadline(format(s.changeDeadline));
		dto.setLocked(Boolean.valueOf(s.locked));
		dto.setRenewed(Boolean.valueOf(s.renewed));
		dto.setRenewals(Integer.valueOf(s.renewals));
		dto.setOrigin(s.origin);
		dto.setEndedAt(format(s.endedAt));
		final List<SocioContractOptionWsDTO> options = new ArrayList<>();
		for (final SocioContractStatus.Option o : s.options)
		{
			final SocioContractOptionWsDTO option = new SocioContractOptionWsDTO();
			option.setCode(o.code);
			option.setName(o.name);
			option.setMonthlyPrice(Double.valueOf(o.monthlyPrice));
			options.add(option);
		}
		dto.setOptions(options);
		dto.setMessage(message);
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

	public void setSocioContractService(final SocioContractService socioContractService)
	{
		this.socioContractService = socioContractService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
