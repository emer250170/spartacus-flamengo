package br.com.flamengo.matchday.customer;

import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;

import org.apache.commons.lang3.StringUtils;


/**
 * Criar conta com CPF (07/11): o torcedor novo criado durante o POST /users recebe o CPF que o
 * {@code SignupCpfInterceptor} conferiu (só números). Não mexe em quem já existe nem em quem já tem
 * CPF. Mapeado pelo typeCode Customer, sem dependência de compilação de outras extensões.
 */
public class CustomerCpfPrepareInterceptor implements PrepareInterceptor<ItemModel>
{
	protected static final String CPF = "cpf";

	@Override
	public void onPrepare(final ItemModel model, final InterceptorContext ctx) throws InterceptorException
	{
		final String cpf = SignupCpfHolder.get();
		if (StringUtils.isEmpty(cpf) || !ctx.isNew(model))
		{
			return;
		}
		final Object current = ctx.getModelService().getAttributeValue(model, CPF);
		if (current == null || StringUtils.isBlank(current.toString()))
		{
			ctx.getModelService().setAttributeValue(model, CPF, cpf);
		}
	}
}
