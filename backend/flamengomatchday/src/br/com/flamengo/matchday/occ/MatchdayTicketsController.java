package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.model.MatchdayGameModel;
import br.com.flamengo.matchday.model.MatchdayTicketEventModel;
import br.com.flamengo.matchday.model.MatchdayTicketModel;
import br.com.flamengo.matchday.model.SocioGuestModel;
import br.com.flamengo.matchday.ticket.MatchdayTicketService;
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

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * Meus ingressos (Minha conta, 09/11): GET /{baseSiteId}/users/current/matchdaytickets devolve só os
 * ingressos que o torcedor logado comprou, um por poltrona, com o jogo (adversário, data, competição,
 * estádio, imagem), setor, poltrona, quem vai usar (CPF mascarado), valor, desconto de sócio, situação
 * e o histórico (sem as observações internas do gestor). Só leitura: nada muda no ingresso.
 * Com flamengo.matchday.ingressos.ativo=false a lista vem vazia com "enabled": false.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}/matchdaytickets")
public class MatchdayTicketsController
{
	private static final String ERROR_TYPE = "MatchdayTicketsError";
	/** Observação é anotação interna do gestor de jogos: não aparece para o torcedor */
	private static final String INTERNAL_EVENT = "OBSERVACAO";

	private MatchdayTicketService matchdayTicketService;
	private UserService userService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> list(@PathVariable final String baseSiteId, @PathVariable final String userId)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta para ver os seus ingressos.");
		}
		final boolean enabled = matchdayTicketService.isEnabled();
		final List<Map<String, Object>> tickets = new ArrayList<>();
		if (enabled)
		{
			for (final MatchdayTicketModel t : matchdayTicketService.forCustomer(customer))
			{
				tickets.add(toMap(t));
			}
		}
		final Map<String, Object> out = new LinkedHashMap<>();
		out.put("enabled", Boolean.valueOf(enabled));
		out.put("now", format(new Date()));
		out.put("tickets", tickets);
		return ResponseEntity.ok(out);
	}

	protected Map<String, Object> toMap(final MatchdayTicketModel t)
	{
		final Map<String, Object> m = new LinkedHashMap<>();
		m.put("code", t.getCode());
		m.put("status", t.getStatus() == null ? null : t.getStatus().getCode());
		m.put("orderCode", t.getOrderCode());
		m.put("sector", t.getSector());
		m.put("seatLabel", t.getSeatLabel());
		m.put("seatCode", t.getSeat() == null ? null : t.getSeat().getCode());
		final SocioGuestModel guest = t.getGuest();
		final String cpf = guest != null ? guest.getCpf() : t.getHolderCpf();
		m.put("holder", t.getHolder());
		m.put("holderIsBuyer", Boolean.valueOf(guest == null && StringUtils.isBlank(t.getHolderCpf())));
		m.put("guest", Boolean.valueOf(guest != null));
		m.put("holderCpf", maskCpf(cpf));
		m.put("holderEmail", guest != null ? guest.getEmail() : t.getHolderEmail());
		m.put("price", t.getPrice());
		m.put("memberDiscount", t.getMemberDiscount());
		m.put("soldAt", format(t.getSoldAt()));
		m.put("cancelledAt", format(t.getCancelledAt()));
		m.put("refundAmount", t.getRefundAmount());
		m.put("usedAt", format(t.getUsedAt()));
		m.put("game", game(t.getGame()));
		final List<Map<String, Object>> history = new ArrayList<>();
		for (final MatchdayTicketEventModel e : matchdayTicketService.history(t))
		{
			final String type = e.getType() == null ? null : e.getType().getCode();
			if (type == null || INTERNAL_EVENT.equals(type))
			{
				continue;
			}
			final Map<String, Object> x = new LinkedHashMap<>();
			x.put("type", type);
			x.put("date", format(e.getDate()));
			x.put("from", e.getFromValue());
			x.put("to", e.getToValue());
			x.put("amount", e.getAmount());
			history.add(x);
		}
		m.put("history", history);
		return m;
	}

	protected static Map<String, Object> game(final MatchdayGameModel g)
	{
		if (g == null)
		{
			return null;
		}
		final Map<String, Object> m = new LinkedHashMap<>();
		m.put("code", g.getCode());
		m.put("opponent", g.getOpponent());
		m.put("kickoff", format(g.getKickoff()));
		m.put("competition", g.getCompetition());
		m.put("venue", g.getVenue());
		m.put("status", g.getStatus() == null ? null : g.getStatus().getCode());
		m.put("imageUrl", g.getImage() == null ? null : g.getImage().getURL());
		return m;
	}

	/** Só os 6 dígitos do meio aparecem: ***.456.789-** */
	protected static String maskCpf(final String cpf)
	{
		final String digits = cpf == null ? "" : cpf.replaceAll("\\D", "");
		if (digits.length() != 11)
		{
			return null;
		}
		return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
	}

	protected CustomerModel currentCustomer()
	{
		final UserModel user = userService.getCurrentUser();
		return user instanceof CustomerModel && !userService.isAnonymousUser(user) ? (CustomerModel) user : null;
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

	public void setMatchdayTicketService(final MatchdayTicketService matchdayTicketService)
	{
		this.matchdayTicketService = matchdayTicketService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
