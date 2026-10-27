package br.com.flamengo.matchday.hook;

import br.com.flamengo.matchday.service.SeatHoldService;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.hook.CommerceUpdateCartEntryHook;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;


/**
 * Ao tirar a poltrona do carrinho, libera a poltrona na hora.
 */
public class SeatHoldUpdateCartEntryHook implements CommerceUpdateCartEntryHook
{
	private SeatHoldService seatHoldService;

	@Override
	public void beforeUpdateCartEntry(final CommerceCartParameter parameter)
	{
		final CartModel cart = parameter.getCart();
		if (cart == null || parameter.getQuantity() != 0 || cart.getEntries() == null)
		{
			return;
		}
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			if (entry.getEntryNumber() != null && entry.getEntryNumber().longValue() == parameter.getEntryNumber()
					&& entry.getProduct() != null && seatHoldService.isSeat(entry.getProduct()))
			{
				seatHoldService.release(entry.getProduct(), cart);
			}
		}
	}

	@Override
	public void afterUpdateCartEntry(final CommerceCartParameter parameter, final CommerceCartModification result)
	{
		// nada: a liberacao acontece antes da remocao
	}

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}
}
