package br.com.flamengo.matchday.cadastro.impl;

import br.com.flamengo.matchday.cadastro.CadastroException;
import br.com.flamengo.matchday.cadastro.CadastroService;
import br.com.flamengo.matchday.socio.SocioGuestService;
import de.hybris.platform.commerceservices.customer.CustomerAccountService;
import de.hybris.platform.commerceservices.customer.DuplicateUidException;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.util.Collections;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DefaultCadastroService implements CadastroService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultCadastroService.class);
	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final int SENHA_MINIMA = 6;

	private CustomerAccountService customerAccountService;
	private CommonI18NService commonI18NService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private ConfigurationService configurationService;

	@Override
	public CustomerModel cadastrar(final String nome, final String sobrenome, final String email, final String senha,
			final String cpf) throws CadastroException
	{
		final String primeiro = StringUtils.trimToEmpty(nome);
		final String ultimo = StringUtils.trimToEmpty(sobrenome);
		final String login = StringUtils.trimToEmpty(email);
		final String digitos = CadastroService.digitos(cpf);
		if (primeiro.isEmpty() || ultimo.isEmpty())
		{
			throw new CadastroException("Informe o nome e o sobrenome.");
		}
		if (!EMAIL.matcher(login).matches())
		{
			throw new CadastroException("Informe um e-mail válido.");
		}
		if (StringUtils.length(senha) < SENHA_MINIMA)
		{
			throw new CadastroException("A senha precisa ter pelo menos " + SENHA_MINIMA + " caracteres.");
		}
		if (digitos.isEmpty())
		{
			if (configurationService.getConfiguration().getBoolean(PROPRIEDADE_CPF_OBRIGATORIO, true))
			{
				throw new CadastroException("Informe o CPF.");
			}
		}
		else if (!SocioGuestService.isValidCpf(digitos))
		{
			throw new CadastroException("CPF inválido. Confira os 11 números.");
		}
		else if (cpfEmUso(digitos))
		{
			throw new CadastroException("Este CPF já está em outra conta. Entre com ela ou fale com a gente.");
		}

		// como o DefaultCustomerFacade.register
		final CustomerModel cliente = modelService.create(CustomerModel.class);
		cliente.setName(primeiro + " " + ultimo);
		cliente.setUid(login.toLowerCase());
		cliente.setOriginalUid(login);
		cliente.setSessionLanguage(commonI18NService.getCurrentLanguage());
		cliente.setSessionCurrency(commonI18NService.getCurrentCurrency());
		if (!digitos.isEmpty())
		{
			modelService.setAttributeValue(cliente, CadastroService.ATRIBUTO_CPF, digitos);
		}
		try
		{
			customerAccountService.register(cliente, senha);
		}
		catch (final DuplicateUidException e)
		{
			throw new CadastroException("Já existe uma conta com este e-mail. Entre com ela ou use \"Esqueci minha senha\".");
		}
		LOG.info("Cadastro: conta {} criada {}", cliente.getUid(), digitos.isEmpty() ? "sem CPF" : "com CPF");
		return cliente;
	}

	protected boolean cpfEmUso(final String digitos)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {Customer} WHERE {cpf} = ?cpf");
		q.addQueryParameter("cpf", digitos);
		q.setCount(1);
		q.setResultClassList(Collections.singletonList(de.hybris.platform.core.PK.class));
		return !flexibleSearchService.search(q).getResult().isEmpty();
	}

	public void setCustomerAccountService(final CustomerAccountService customerAccountService)
	{
		this.customerAccountService = customerAccountService;
	}

	public void setCommonI18NService(final CommonI18NService commonI18NService)
	{
		this.commonI18NService = commonI18NService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
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
