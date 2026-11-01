package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.model.SocioEventModel;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PersistenceOperation;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;
import de.hybris.platform.servicelayer.model.ModelService;

import org.apache.commons.lang3.StringUtils;


/**
 * Sócio suspenso por inadimplência cadastra um cartão novo (Minha conta ›
 * Meu sócio-torcedor › trocar cartão, ou no pagamento de um pedido): a
 * mensalidade em atraso é cobrada no cartão novo (simulado) e os benefícios voltam
 * na hora, com um evento de Regularização na linha do tempo.
 * <p>
 * Mapeado no tipo CreditCardPaymentInfo pelo nome; só cartões novos salvos na
 * conta (não as cópias que o pedido faz).
 */
public class SocioCardRegularizationInterceptor implements PrepareInterceptor<ItemModel>
{
	private SocioPlanService socioPlanService;
	private SocioEventInterceptor socioEventInterceptor;

	@Override
	public void onPrepare(final ItemModel card, final InterceptorContext ctx) throws InterceptorException
	{
		if (!ctx.isNew(card) || !socioPlanService.isSuspensionEnabled())
		{
			return;
		}
		final ModelService modelService = ctx.getModelService();
		if (!Boolean.TRUE.equals(modelService.getAttributeValue(card, "saved"))
				|| Boolean.TRUE.equals(modelService.getAttributeValue(card, "duplicate")))
		{
			return;
		}
		final Object user = modelService.getAttributeValue(card, "user");
		if (!(user instanceof CustomerModel) || !socioPlanService.isSuspended((CustomerModel) user))
		{
			return;
		}
		final CustomerModel customer = (CustomerModel) user;
		final String number = StringUtils.defaultString((String) modelService.getAttributeValue(card, "number"));
		final String last4 = number.length() >= 4 ? number.substring(number.length() - 4) : number;
		final SocioEventModel event = socioPlanService.newEvent(customer, SocioEventType.REGULARIZACAO, "Loja",
				"Mensalidade em atraso cobrada no cartão novo" + (last4.isEmpty() ? "" : " (final " + last4 + ")"));
		socioEventInterceptor.apply(event, ctx);
		ctx.registerElementFor(event, PersistenceOperation.SAVE);
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}

	public void setSocioEventInterceptor(final SocioEventInterceptor socioEventInterceptor)
	{
		this.socioEventInterceptor = socioEventInterceptor;
	}
}
