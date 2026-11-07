package br.com.flamengo.matchday.customer;

import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;

import org.apache.commons.lang3.StringUtils;


/**
 * Criar conta com CPF e telefone (07/11): o torcedor novo criado durante o POST /users recebe o CPF e o
 * telefone que o {@code SignupCpfInterceptor} conferiu (só números). Não mexe em quem já existe nem em
 * quem já tem o dado. Mapeado pelo typeCode Customer, sem dependência de compilação de outras extensões.
 */
public class CustomerCpfPrepareInterceptor implements PrepareInterceptor<ItemModel>
{
	protected static final String CPF = "cpf";
	protected static final String PHONE = "phone";

	@Override
	public void onPrepare(final ItemModel model, final InterceptorContext ctx) throws InterceptorException
	{
		if (!ctx.isNew(model))
		{
			return;
		}
		fill(model, ctx, CPF, SignupCpfHolder.get());
		fill(model, ctx, PHONE, SignupCpfHolder.getPhone());
	}

	protected void fill(final ItemModel model, final InterceptorContext ctx, final String attribute, final String value)
	{
		if (StringUtils.isEmpty(value))
		{
			return;
		}
		final Object current = ctx.getModelService().getAttributeValue(model, attribute);
		if (current == null || StringUtils.isBlank(current.toString()))
		{
			ctx.getModelService().setAttributeValue(model, attribute, value);
		}
	}
}
