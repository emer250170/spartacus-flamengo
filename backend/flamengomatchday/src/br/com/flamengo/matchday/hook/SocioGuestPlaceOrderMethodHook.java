package br.com.flamengo.matchday.hook;

import br.com.flamengo.matchday.socio.SocioGuestException;
import br.com.flamengo.matchday.socio.SocioGuestService;
import br.com.flamengo.matchday.ticket.TicketHolderService;
import de.hybris.platform.commerceservices.order.hook.CommercePlaceOrderMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.order.InvalidCartException;


/**
 * Ao fechar o pedido do sócio: cada poltrona além da dele no mesmo jogo precisa
 * estar marcada para um convidado ativo, um ingresso por convidado por jogo
 * ({@link SocioGuestService#checkCart}). Chave flamengo.socio.convidados.ingressoNominal.
 * Quem não é sócio: cada ingresso adicional com os dados de quem vai usar
 * ({@link TicketHolderService#checkCart}). Chave flamengo.ingressos.portador.obrigatorio.
 */
public class SocioGuestPlaceOrderMethodHook implements CommercePlaceOrderMethodHook
{
	private SocioGuestService socioGuestService;
	private TicketHolderService ticketHolderService;

	@Override
	public void beforePlaceOrder(final CommerceCheckoutParameter parameter) throws InvalidCartException
	{
		try
		{
			socioGuestService.checkCart(parameter.getCart());
			if (ticketHolderService != null)
			{
				ticketHolderService.checkCart(parameter.getCart());
			}
		}
		catch (final SocioGuestException e)
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

	public void setSocioGuestService(final SocioGuestService socioGuestService)
	{
		this.socioGuestService = socioGuestService;
	}

	public void setTicketHolderService(final TicketHolderService ticketHolderService)
	{
		this.ticketHolderService = ticketHolderService;
	}
}
