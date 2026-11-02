package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.model.SocioRedemptionModel;
import br.com.flamengo.matchday.socio.SocioPoints;
import br.com.flamengo.matchday.socio.SocioPointsException;
import br.com.flamengo.matchday.socio.SocioPointsService;
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
 * Pontos do sócio na API de comércio (OCC v2), item 8:
 * <ul>
 * <li>GET /{baseSiteId}/users/current/sociopoints: saldo, pedidos que deram pontos,
 * resgates e a vitrine de experiências (nome e descrição na língua da chamada);</li>
 * <li>POST /{baseSiteId}/users/current/sociopoints/redemptions {"experience","origin"}:
 * resgata a experiência e devolve o voucher e o saldo novo (origin: Loja ou Claude).</li>
 * </ul>
 * Recusas: 400 com {"errors":[{"type":"SocioPointsError","message":"..."}]}.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}/sociopoints")
public class SocioPointsController
{
	private static final String ERROR_TYPE = "SocioPointsError";
	private static final int MAX_EARNINGS = 20;

	private SocioPointsService socioPointsService;
	private UserService userService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> status(@PathVariable final String baseSiteId, @PathVariable final String userId)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para ver os seus pontos.");
		}
		return ResponseEntity.ok(toMap(socioPointsService.status(customer)));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(value = "/redemptions", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> redeem(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@RequestBody final Map<String, Object> body)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para resgatar.");
		}
		final Object code = body == null ? null : body.get("experience");
		if (code == null || String.valueOf(code).isBlank())
		{
			return error(HttpStatus.BAD_REQUEST, "Informe a experiência.");
		}
		final Object origin = body.get("origin");
		final SocioRedemptionModel r;
		try
		{
			r = socioPointsService.redeem(customer, String.valueOf(code).trim(),
					"Claude".equals(origin) ? "Claude" : "Loja");
		}
		catch (final SocioPointsException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		final Map<String, Object> out = new LinkedHashMap<>();
		out.put("voucher", r.getVoucher());
		out.put("experience", r.getExperience().getCode());
		out.put("points", r.getPoints());
		out.put("date", format(r.getDate()));
		out.put("status", toMap(socioPointsService.status(customer)));
		return ResponseEntity.status(HttpStatus.CREATED).body(out);
	}

	protected CustomerModel currentCustomer()
	{
		final UserModel user = userService.getCurrentUser();
		return user instanceof CustomerModel && !userService.isAnonymousUser(user) ? (CustomerModel) user : null;
	}

	protected static Map<String, Object> toMap(final SocioPoints p)
	{
		final Map<String, Object> m = new LinkedHashMap<>();
		m.put("enabled", Boolean.valueOf(p.enabled));
		m.put("member", Boolean.valueOf(p.member));
		m.put("canRedeem", Boolean.valueOf(p.canRedeem));
		m.put("reason", p.reason);
		m.put("planCode", p.planCode);
		m.put("planName", p.planName);
		m.put("since", format(p.since));
		m.put("perTicket", Integer.valueOf(p.perTicket));
		m.put("perReal", Integer.valueOf(p.perReal));
		m.put("bonus", Integer.valueOf(p.bonus));
		m.put("earned", Integer.valueOf(p.earned));
		m.put("redeemed", Integer.valueOf(p.redeemed));
		m.put("balance", Integer.valueOf(p.balance));
		m.put("tickets", Integer.valueOf(p.tickets));
		m.put("productsAmount", Double.valueOf(p.productsAmount));
		final List<Map<String, Object>> earnings = new ArrayList<>();
		for (final SocioPoints.Earning e : p.earnings)
		{
			if (earnings.size() >= MAX_EARNINGS && e.orderCode != null)
			{
				continue; // os 20 pedidos mais novos e o bônus de adesão
			}
			final Map<String, Object> x = new LinkedHashMap<>();
			x.put("orderCode", e.orderCode);
			x.put("bonus", Boolean.valueOf(e.orderCode == null));
			x.put("date", format(e.date));
			x.put("tickets", Integer.valueOf(e.tickets));
			x.put("productsAmount", Double.valueOf(e.productsAmount));
			x.put("points", Integer.valueOf(e.points));
			earnings.add(x);
		}
		m.put("earnings", earnings);
		final List<Map<String, Object>> redemptions = new ArrayList<>();
		for (final SocioPoints.Redemption r : p.redemptions)
		{
			final Map<String, Object> x = new LinkedHashMap<>();
			x.put("voucher", r.voucher);
			x.put("experienceCode", r.experienceCode);
			x.put("experienceName", r.experienceName);
			x.put("points", Integer.valueOf(r.points));
			x.put("date", format(r.date));
			x.put("origin", r.origin);
			x.put("cancelledAt", format(r.cancelledAt));
			redemptions.add(x);
		}
		m.put("redemptions", redemptions);
		final List<Map<String, Object>> experiences = new ArrayList<>();
		for (final SocioPoints.Experience e : p.experiences)
		{
			final Map<String, Object> x = new LinkedHashMap<>();
			x.put("code", e.code);
			x.put("name", e.name);
			x.put("description", e.description);
			x.put("points", Integer.valueOf(e.points));
			x.put("available", e.available);
			x.put("minPlanCode", e.minPlanCode);
			x.put("minPlanName", e.minPlanName);
			x.put("redeemable", Boolean.valueOf(e.redeemable));
			x.put("reason", e.reason);
			experiences.add(x);
		}
		m.put("experiences", experiences);
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

	public void setSocioPointsService(final SocioPointsService socioPointsService)
	{
		this.socioPointsService = socioPointsService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
