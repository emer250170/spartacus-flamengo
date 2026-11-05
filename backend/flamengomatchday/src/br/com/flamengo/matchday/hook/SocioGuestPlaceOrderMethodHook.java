package br.com.flamengo.matchday.hook;

import de.hybris.platform.core.model.user.CustomerModel;
import br.com.flamengo.matchday.socio.SocioGuestException;
import br.com.flamengo.matchday.socio.SocioGuestService;
import de.hybris.platform.commerceservices.order.hook.CommercePlaceOrderMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.order.InvalidCartException;


/**
 * Ao fechar o pedido do sócio: cada poltrona além da dele no mesmo jogo precisa
 * estar marcada para um convidado ativo, um ingresso por convidado por jogo
 * ({@link SocioGuestService#checkCart}). Chave flamengo.socio.convidados.ingressoNominal.
 */
public class SocioGuestPlaceOrderMethodHook implements CommercePlaceOrderMethodHook
{
	private SocioGuestService socioGuestService;

	@Override
	public void beforePlaceOrder(final CommerceCheckoutParameter parameter) throws InvalidCartException
	{
		try
		{
			socioGuestService.checkCart(parameter.getCart());
		}
		catch (final SocioGuestException e)
		{
			throw new InvalidCartException(e.getMessage());
		}
	}

	@Override
	public void beforeSubmitOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		// convidados cadastrados com o plano no carrinho passam a ser do plano (04/11)
		if (result != null && result.getOrder() != null && result.getOrder().getUser() instanceof CustomerModel)
		{
			socioGuestService.confirmCartGuests((CustomerModel) result.getOrder().getUser(), result.getOrder());
		}
	}

	@Override
	public void afterPlaceOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		// nada
	}

	public void setSocioGuestService(final SocioGuestService socioGuestService)
	{
		this.socioGuestService = socioGuestService;
	}
}
