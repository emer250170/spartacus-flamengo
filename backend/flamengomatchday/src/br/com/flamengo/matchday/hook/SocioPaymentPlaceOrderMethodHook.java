package br.com.flamengo.matchday.hook;

import br.com.flamengo.matchday.socio.SocioPaymentException;
import br.com.flamengo.matchday.socio.SocioPaymentService;
import de.hybris.platform.commerceservices.order.hook.CommercePlaceOrderMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.order.InvalidCartException;


/**
 * Forma de pagamento do plano (item 7) ao fechar qualquer pedido: Pix ou boleto
 * escolhido não fecha com cartão, e a forma some do carrinho sem plano. Ver
 * {@link SocioPaymentService#checkCart}.
 */
public class SocioPaymentPlaceOrderMethodHook implements CommercePlaceOrderMethodHook
{
	private SocioPaymentService socioPaymentService;

	@Override
	public void beforePlaceOrder(final CommerceCheckoutParameter parameter) throws InvalidCartException
	{
		try
		{
			socioPaymentService.checkCart(parameter.getCart());
		}
		catch (final SocioPaymentException e)
		{
			throw new InvalidCartException(e.getMessage());
		}
	}

	@Override
	public void beforeSubmitOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		// nada
	}

	@Override
	public void afterPlaceOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		// nada
	}

	public void setSocioPaymentService(final SocioPaymentService socioPaymentService)
	{
		this.socioPaymentService = socioPaymentService;
	}
}
