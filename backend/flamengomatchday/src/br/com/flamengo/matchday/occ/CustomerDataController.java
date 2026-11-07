package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.customer.Telefone;
import br.com.flamengo.matchday.socio.SocioGuestService;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.Collections;
import java.util.LinkedHashMap;
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
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * CPF e telefone de quem já tinha conta (07/11, para o ClearSale): o cadastro novo já pede os dois; as contas
 * antigas informam uma vez no checkout.
 * <ul>
 * <li>GET /{baseSiteId}/users/{userId}/flamengo/dados → {"cpf": "***.123.456-**" ou null, "telefone": "(21) 9****-4321"
 * ou null, "faltando": ["cpf","telefone"]}</li>
 * <li>PUT /{baseSiteId}/users/{userId}/flamengo/dados com {"cpf","telefone"}: grava só o que ainda falta. CPF
 * conferido (dígitos e não pode ser de outra conta), telefone com DDD. Errado: 400 com a mensagem.</li>
 * </ul>
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}/flamengo/dados")
public class CustomerDataController
{
	private static final String ERROR_TYPE = "CustomerDataError";

	private UserService userService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> get(@PathVariable final String baseSiteId, @PathVariable final String userId)
	{
		final CustomerModel c = current();
		if (c == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta.");
		}
		return ResponseEntity.ok(view(c));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@RequestMapping(method = RequestMethod.PUT)
	@ResponseBody
	public ResponseEntity<Object> put(@PathVariable final String baseSiteId, @PathVariable final String userId,
			@RequestBody(required = false) final Map<String, String> body)
	{
		final CustomerModel c = current();
		if (c == null)
		{
			return error(HttpStatus.UNAUTHORIZED, "Entre na sua conta.");
		}
		final String cpf = body == null ? null : body.get("cpf");
		final String tel = body == null ? null : body.get("telefone");
		boolean mudou = false;
		if (StringUtils.isBlank(attr(c, "cpf")) && StringUtils.isNotBlank(cpf))
		{
			final String d = SocioGuestService.digits(cpf);
			if (!SocioGuestService.isValidCpf(d))
			{
				return error(HttpStatus.BAD_REQUEST, "CPF inválido: confira os 11 números.");
			}
			if (cpfEmUso(d))
			{
				return error(HttpStatus.BAD_REQUEST, "Este CPF já está em outra conta. Fale com a gente.");
			}
			modelService.setAttributeValue(c, "cpf", d);
			mudou = true;
		}
		if (StringUtils.isBlank(attr(c, "phone")) && StringUtils.isNotBlank(tel))
		{
			if (!Telefone.valido(tel))
			{
				return error(HttpStatus.BAD_REQUEST, "Telefone inválido: informe o DDD e o número.");
			}
			modelService.setAttributeValue(c, "phone", Telefone.digitos(tel));
			mudou = true;
		}
		if (mudou)
		{
			modelService.save(c);
		}
		return ResponseEntity.ok(view(c));
	}

	protected Map<String, Object> view(final CustomerModel c)
	{
		final String cpf = attr(c, "cpf");
		final String tel = attr(c, "phone");
		final Map<String, Object> m = new LinkedHashMap<>();
		m.put("cpf", StringUtils.isBlank(cpf) ? null : mascaraCpf(cpf));
		m.put("telefone", StringUtils.isBlank(tel) ? null : mascaraTelefone(tel));
		final java.util.List<String> faltando = new java.util.ArrayList<>();
		if (StringUtils.isBlank(cpf))
		{
			faltando.add("cpf");
		}
		if (StringUtils.isBlank(tel))
		{
			faltando.add("telefone");
		}
		m.put("faltando", faltando);
		return m;
	}

	/** ***.123.456-** */
	protected static String mascaraCpf(final String cpf)
	{
		final String d = SocioGuestService.digits(cpf);
		return d.length() == 11 ? "***." + d.substring(3, 6) + "." + d.substring(6, 9) + "-**" : "***";
	}

	/** (21) 9****-4321 */
	protected static String mascaraTelefone(final String tel)
	{
		final String d = Telefone.digitos(tel);
		if (d.length() < 10)
		{
			return "***";
		}
		final String n = d.substring(2);
		return "(" + d.substring(0, 2) + ") " + n.charAt(0) + StringUtils.repeat('*', n.length() - 5) + "-" + n.substring(n.length() - 4);
	}

	protected boolean cpfEmUso(final String digits)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {Customer} WHERE {cpf}=?cpf");
		q.addQueryParameter("cpf", digits);
		q.setCount(1);
		return !flexibleSearchService.search(q).getResult().isEmpty();
	}

	protected String attr(final CustomerModel c, final String q)
	{
		try
		{
			final Object v = modelService.getAttributeValue(c, q);
			return v == null ? null : v.toString();
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	protected CustomerModel current()
	{
		final UserModel user = userService.getCurrentUser();
		return user instanceof CustomerModel && !userService.isAnonymousUser(user) ? (CustomerModel) user : null;
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

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}
}
