package br.com.flamengo.matchday.hook;

import br.com.flamengo.matchday.service.SeatHoldService;
import de.hybris.platform.commerceservices.order.hook.CommercePlaceOrderMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.order.InvalidCartException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Ao fechar o pedido: confere que cada poltrona continua reservada para este
 * carrinho e, antes de o processo do pedido comecar, converte a reserva em venda.
 */
public class SeatHoldPlaceOrderMethodHook implements CommercePlaceOrderMethodHook
{
	private static final Logger LOG = LoggerFactory.getLogger(SeatHoldPlaceOrderMethodHook.class);

	private SeatHoldService seatHoldService;

	@Override
	public void beforePlaceOrder(final CommerceCheckoutParameter parameter) throws InvalidCartException
	{
		final CartModel cart = parameter.getCart();
		if (cart == null || cart.getEntries() == null)
		{
			return;
		}
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			final ProductModel product = entry.getProduct();
			if (product != null && seatHoldService.isSeat(product) && !seatHoldService.hold(product, cart, false))
			{
				throw new InvalidCartException("A poltrona " + product.getCode()
						+ " n\u00e3o est\u00e1 mais reservada para voc\u00ea. Escolha outra poltrona.");
			}
		}
	}

	@Override
	public void beforeSubmitOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		if (result == null || result.getOrder() == null)
		{
			return;
		}
		try
		{
			seatHoldService.commitSale(result.getOrder());
		}
		catch (final RuntimeException e)
		{
			// nao derruba o pedido; a rotina de reserva do processo baixa o estoque depois
			LOG.error("Falha ao baixar o estoque do pedido " + result.getOrder().getCode(), e);
		}
	}

	@Override
	public void afterPlaceOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		// nada
	}

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}
}
