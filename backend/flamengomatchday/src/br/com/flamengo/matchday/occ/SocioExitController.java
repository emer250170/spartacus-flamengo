package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.socio.SocioExitQuote;
import br.com.flamengo.matchday.socio.SocioExitService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * Regras de saída do sócio na API de comércio (OCC v2):
 * GET /{baseSiteId}/users/current/socioexit devolve a simulação do cancelamento
 * (reembolso no prazo de arrependimento ou multa sobre as mensalidades
 * restantes). Com pedido de cancelamento aberto, a data-base é a abertura do
 * chamado. Para o ex-sócio, settlement traz o valor do cancelamento confirmado.
 * Com a chave flamengo.socio.saida.ativo desligada: {"enabled": false}.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}/socioexit")
public class SocioExitController
{
	private static final String ERROR_TYPE = "SocioExitError";

	private SocioExitService socioExitService;
	private UserService userService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> quote(@PathVariable final String baseSiteId, @PathVariable final String userId)
	{
		final UserModel user = userService.getCurrentUser();
		if (!(user instanceof CustomerModel) || userService.isAnonymousUser(user))
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para simular o cancelamento.");
		}
		final CustomerModel customer = (CustomerModel) user;
		final SocioExitWsDTO dto = new SocioExitWsDTO();
		dto.setEnabled(Boolean.valueOf(socioExitService.isEnabled()));
		if (!socioExitService.isEnabled())
		{
			dto.setMember(Boolean.FALSE);
			return ResponseEntity.ok(dto);
		}
		final SocioExitQuote q = socioExitService.currentQuote(customer);
		dto.setMember(Boolean.valueOf(q != null));
		if (q != null)
		{
			fill(dto, q);
		}
		final SocioExitQuote last = socioExitService.lastSettlement(customer);
		if (last != null)
		{
			final SocioExitWsDTO settlement = new SocioExitWsDTO();
			fill(settlement, last);
			dto.setSettlement(settlement);
		}
		return ResponseEntity.ok(dto);
	}

	protected void fill(final SocioExitWsDTO dto, final SocioExitQuote q)
	{
		dto.setPlanCode(q.planCode);
		dto.setPlanName(q.planName);
		dto.setOrderCode(q.orderCode);
		dto.setStart(format(q.start));
		dto.setBasis(format(q.basis));
		dto.setWithdrawalDays(Integer.valueOf(q.withdrawalDays));
		dto.setWithdrawalUntil(format(q.withdrawalUntil));
		dto.setWithinWithdrawal(Boolean.valueOf(q.withinWithdrawal));
		dto.setInstallments(Integer.valueOf(q.installments));
		dto.setPaidInstallments(Integer.valueOf(q.paidInstallments));
		dto.setRemainingInstallments(Integer.valueOf(q.remainingInstallments));
		dto.setMonthly(Double.valueOf(q.monthly));
		dto.setGuests(Integer.valueOf(q.guests));
		dto.setPaidAtJoin(Double.valueOf(q.paidAtJoin));
		dto.setFinePercent(Double.valueOf(q.finePercent));
		dto.setRefund(Double.valueOf(q.refund));
		dto.setFine(Double.valueOf(q.fine));
		dto.setSummary(socioExitService.describe(q));
		dto.setConfirmed(Boolean.valueOf(q.confirmed));
		dto.setOpenTicket(q.openTicket);
		dto.setEndOfTerm(Boolean.valueOf(q.endOfTerm));
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

	public void setSocioExitService(final SocioExitService socioExitService)
	{
		this.socioExitService = socioExitService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
