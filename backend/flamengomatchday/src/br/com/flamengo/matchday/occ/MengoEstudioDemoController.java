package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.estudio.impl.MengoEstudioDemoService;
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
 * Painel da demonstração do Mengo Estúdio (08/11, PR 5), só para usuários de teste:
 * GET /{baseSiteId}/users/{userId}/mengo-estudio/demo (estado) e POST a mesma URL ({"acao", ...}; ver
 * {@link MengoEstudioDemoService#executar}). Devolve {"mensagem", "estado"}. Usuário fora da lista: 403.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}/mengo-estudio/demo")
public class MengoEstudioDemoController
{
	private MengoEstudioDemoService mengoEstudioDemoService;
	private UserService userService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> estado(@PathVariable final String baseSiteId, @PathVariable final String userId)
	{
		final CustomerModel customer = currentCustomer();
		if (!mengoEstudioDemoService.permitido(customer))
		{
			return MengoEstudioController.error(HttpStatus.FORBIDDEN, "O painel da demonstração é só para os usuários de teste.");
		}
		return ResponseEntity.ok(mengoEstudioDemoService.estado(customer));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> executar(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@RequestBody(required = false) final Map<String, Object> req)
	{
		final CustomerModel customer = currentCustomer();
		if (!mengoEstudioDemoService.permitido(customer))
		{
			return MengoEstudioController.error(HttpStatus.FORBIDDEN, "O painel da demonstração é só para os usuários de teste.");
		}
		try
		{
			final Map<String, Object> r = req == null ? Collections.emptyMap() : req;
			final String msg = mengoEstudioDemoService.executar(customer, r.get("acao") == null ? null : String.valueOf(r.get("acao")), r);
			final Map<String, Object> body = new LinkedHashMap<>();
			body.put("mensagem", msg);
			body.put("estado", mengoEstudioDemoService.estado(customer));
			return ResponseEntity.ok(body);
		}
		catch (final IllegalArgumentException e)
		{
			return MengoEstudioController.error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	protected CustomerModel currentCustomer()
	{
		final UserModel user = userService.getCurrentUser();
		return user instanceof CustomerModel && !userService.isAnonymousUser(user) ? (CustomerModel) user : null;
	}

	public void setMengoEstudioDemoService(final MengoEstudioDemoService mengoEstudioDemoService)
	{
		this.mengoEstudioDemoService = mengoEstudioDemoService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
