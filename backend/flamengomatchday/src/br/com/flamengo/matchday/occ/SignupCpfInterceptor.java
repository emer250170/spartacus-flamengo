package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.customer.SignupCpfHolder;
import br.com.flamengo.matchday.customer.Telefone;
import br.com.flamengo.matchday.socio.SocioGuestService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;


/**
 * Criar conta com CPF (07/11): no POST /{baseSiteId}/users (cadastro), a loja manda o CPF no parâmetro
 * {@code cpf}. Aqui o CPF é conferido (11 números, dígitos verificadores) e não pode ser de outra conta;
 * se estiver errado, a conta não é criada e a resposta é 400 no formato de erro da API
 * (ValidationError, subject cpf, reason invalid ou duplicate). Certo, fica guardado até o fim da chamada
 * para o {@code CustomerCpfPrepareInterceptor} gravar no torcedor novo. Sem o parâmetro, o cadastro
 * segue como antes (outros clientes da API). Chave {@code flamengo.cadastro.cpf.ativo}.
 * <p>
 * Telefone (07/11, para o ClearSale): no mesmo POST, parâmetro {@code telefone} (DDD + número). Conferido
 * (celular com 11 números e o 9, fixo com 10) e gravado em {@code Customer.phone}; errado, 400 com subject
 * {@code telefone}. Chave {@code flamengo.cadastro.telefone.ativo}.
 */
public class SignupCpfInterceptor implements HandlerInterceptor
{
	private static final Logger LOG = LoggerFactory.getLogger(SignupCpfInterceptor.class);
	private static final Pattern REGISTER_PATH = Pattern.compile(".*/[^/]+/users/?$");

	private FlexibleSearchService flexibleSearchService;
	private ConfigurationService configurationService;

	@Override
	public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler)
			throws IOException
	{
		SignupCpfHolder.clear();
		if (!"POST".equalsIgnoreCase(request.getMethod())
				|| !REGISTER_PATH.matcher(StringUtils.defaultString(request.getRequestURI())).matches())
		{
			return true;
		}
		final String cpf = request.getParameter("cpf");
		if (cpf != null && configurationService.getConfiguration().getBoolean(SignupCpfHolder.ENABLED_PROPERTY, true))
		{
			final String digits = SocioGuestService.digits(cpf);
			if (!SocioGuestService.isValidCpf(digits))
			{
				return reject(response, "cpf", "invalid", "CPF inválido: confira os 11 números.");
			}
			if (inUse(digits))
			{
				return reject(response, "cpf", "duplicate", "Já existe uma conta com este CPF.");
			}
			SignupCpfHolder.set(digits);
		}
		final String telefone = request.getParameter("telefone");
		if (telefone != null && configurationService.getConfiguration().getBoolean(SignupCpfHolder.PHONE_ENABLED_PROPERTY, true))
		{
			if (!Telefone.valido(telefone))
			{
				SignupCpfHolder.clear();
				return reject(response, "telefone", "invalid", "Telefone inválido: informe o DDD e o número.");
			}
			SignupCpfHolder.setPhone(Telefone.digitos(telefone));
		}
		return true;
	}

	@Override
	public void afterCompletion(final HttpServletRequest request, final HttpServletResponse response, final Object handler,
			final Exception ex)
	{
		SignupCpfHolder.clear();
	}

	protected boolean inUse(final String digits)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {Customer} WHERE {cpf}=?cpf");
		query.addQueryParameter("cpf", digits);
		query.setCount(1);
		final List<Object> result = flexibleSearchService.<Object> search(query).getResult();
		return !result.isEmpty();
	}

	protected boolean reject(final HttpServletResponse response, final String subject, final String reason,
			final String message) throws IOException
	{
		LOG.info("Cadastro recusado: {} {}", subject, reason);
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		response.setContentType("application/json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"errors\":[{\"type\":\"ValidationError\",\"subject\":\"" + subject + "\",\"subjectType\":\"parameter\","
				+ "\"reason\":\"" + reason + "\",\"message\":\"" + message + "\"}]}");
		return false;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
