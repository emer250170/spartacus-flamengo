package br.com.flamengo.matchday.hook;

import br.com.flamengo.matchday.socio.SocioTermException;
import br.com.flamengo.matchday.socio.SocioTermService;
import de.hybris.platform.commerceservices.order.hook.CommercePlaceOrderMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.order.InvalidCartException;


/**
 * Adesão com Termo: pedido com plano só fecha com o aceite da versão atual do Termo
 * do Nação para aquele carrinho; depois de fechado, o aceite ganha o número do
 * pedido e vira evento na linha do tempo ({@link SocioTermService}). Chave
 * flamengo.socio.termo.ativo.
 */
public class SocioTermPlaceOrderMethodHook implements CommercePlaceOrderMethodHook
{
	private SocioTermService socioTermService;

	@Override
	public void beforePlaceOrder(final CommerceCheckoutParameter parameter) throws InvalidCartException
	{
		try
		{
			socioTermService.checkCart(parameter.getCart());
		}
		catch (final SocioTermException e)
		{
			throw new InvalidCartException(e.getMessage());
		}
	}

	@Override
	public void beforeSubmitOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		if (parameter.getCart() != null && result != null && result.getOrder() != null)
		{
			socioTermService.linkOrder(result.getOrder(), parameter.getCart().getCode());
		}
	}

	@Override
	public void afterPlaceOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		// nada
	}

	public void setSocioTermService(final SocioTermService socioTermService)
	{
		this.socioTermService = socioTermService;
	}
}
