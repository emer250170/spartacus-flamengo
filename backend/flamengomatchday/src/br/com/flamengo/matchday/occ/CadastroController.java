package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.cadastro.CadastroException;
import br.com.flamengo.matchday.cadastro.CadastroService;
import de.hybris.platform.core.model.user.CustomerModel;

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
 * Criação de conta com CPF na API de comércio (OCC v2), usada pela loja no lugar do POST /users (07/11):
 * POST /{baseSiteId}/flamengo/cadastro com {"firstName","lastName","uid","password","cpf"}.
 * <p>
 * Como o cadastro padrão, roda com o token do cliente da loja (client credentials). Devolve 201 com
 * {"uid","name"}; recusa com 400 e {"errors":[{"type":"CadastroError","message":"..."}]}, em português.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/flamengo/cadastro")
public class CadastroController
{
	private static final String ERROR_TYPE = "CadastroError";

	private CadastroService cadastroService;

	@Secured({ "ROLE_CLIENT", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Object> cadastrar(@PathVariable final String baseSiteId, @RequestBody final CadastroWsDTO dados)
	{
		if (dados == null)
		{
			return error("Preencha os dados do cadastro.");
		}
		try
		{
			final CustomerModel cliente = cadastroService.cadastrar(dados.getFirstName(), dados.getLastName(), dados.getUid(),
					dados.getPassword(), dados.getCpf());
			final Map<String, String> body = new LinkedHashMap<>();
			body.put("uid", cliente.getUid());
			body.put("name", cliente.getName());
			return ResponseEntity.status(HttpStatus.CREATED).body(body);
		}
		catch (final CadastroException e)
		{
			return error(e.getMessage());
		}
	}

	protected static ResponseEntity<Object> error(final String message)
	{
		final Map<String, String> error = new LinkedHashMap<>();
		error.put("type", ERROR_TYPE);
		error.put("message", message);
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("errors", Collections.singletonList(error));
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}

	public void setCadastroService(final CadastroService cadastroService)
	{
		this.cadastroService = cadastroService;
	}
}
