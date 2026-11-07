package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.estudio.MengoEstudioService;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * Mengo Estúdio (07/11) na API de comércio, leitura pública:
 * <ul>
 * <li>GET /{baseSiteId}/mengo-estudio/catalogo: drops, linhas, estampas (com situação e prazo), camisetas (preço,
 * tamanhos e medidas) e as regras (limites das faixas, parcelamento, quantidade máxima);</li>
 * <li>GET /{baseSiteId}/mengo-estudio/estampas/{code}: uma estampa, inclusive do acervo;</li>
 * <li>POST /{baseSiteId}/mengo-estudio/estampas/{code}/aviso ({"email","consentimento":true}): "Avise-me se
 * voltar" (sem login também; logado, a inscrição fica ligada ao torcedor).</li>
 * </ul>
 * "agora" vem do servidor (com o relógio da demo) para o contador da loja bater com o bloqueio do carrinho.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/mengo-estudio")
public class MengoEstudioController
{
	private MengoEstudioService mengoEstudioService;
	private UserService userService;

	@RequestMapping(value = "/catalogo", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> catalogo(@PathVariable final String baseSiteId)
	{
		if (!mengoEstudioService.isActive())
		{
			return ResponseEntity.ok(Collections.singletonMap("ativo", Boolean.FALSE));
		}
		return ResponseEntity.ok(mengoEstudioService.catalogo());
	}

	@RequestMapping(value = "/estampas/{code}", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> estampa(@PathVariable final String baseSiteId, @PathVariable final String code)
	{
		final ItemModel estampa = mengoEstudioService.isActive() ? mengoEstudioService.estampa(code) : null;
		if (estampa == null || !mengoEstudioService.isVisible(estampa))
		{
			return error(HttpStatus.NOT_FOUND, "Estampa não encontrada.");
		}
		final long now = mengoEstudioService.now();
		final Map<String, Object> body = new LinkedHashMap<>(mengoEstudioService.estampaDto(estampa, now));
		body.put("agora", Long.valueOf(now));
		return ResponseEntity.ok(body);
	}

	@RequestMapping(value = "/estampas/{code}/aviso", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> aviso(@PathVariable final String baseSiteId, @PathVariable final String code,
			@RequestBody final Map<String, Object> body)
	{
		if (!mengoEstudioService.isActive())
		{
			return error(HttpStatus.NOT_FOUND, "Estampa não encontrada.");
		}
		try
		{
			final UserModel user = userService.getCurrentUser();
			final CustomerModel customer = user instanceof CustomerModel && !userService.isAnonymousUser(user)
					? (CustomerModel) user
					: null;
			mengoEstudioService.avisar(code, body == null ? null : String.valueOf(body.get("email")),
					body != null && Boolean.TRUE.equals(body.get("consentimento")), customer);
			return ResponseEntity.ok(Collections.singletonMap("ok", Boolean.TRUE));
		}
		catch (final IllegalArgumentException e)
		{
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	protected static ResponseEntity<Object> error(final HttpStatus status, final String message)
	{
		final Map<String, String> error = new LinkedHashMap<>();
		error.put("type", "MengoEstudioError");
		error.put("message", message);
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("errors", Collections.singletonList(error));
		return ResponseEntity.status(status).body(body);
	}

	public void setMengoEstudioService(final MengoEstudioService mengoEstudioService)
	{
		this.mengoEstudioService = mengoEstudioService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
