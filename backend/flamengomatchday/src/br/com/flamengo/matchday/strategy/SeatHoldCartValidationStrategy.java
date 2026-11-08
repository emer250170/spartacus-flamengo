package br.com.flamengo.matchday.strategy;

import br.com.flamengo.matchday.service.SeatHoldService;
import de.hybris.platform.b2bacceleratorservices.strategies.impl.DefaultB2BCartValidationStrategy;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationStatus;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;


/**
 * Validacao de carrinho que entende a reserva de poltrona.
 * <p>
 * Para todo mundo a poltrona reservada esta esgotada (reserved = 1); para o
 * carrinho dono da reserva ela continua valendo. Sem isto, a validacao padrao
 * tiraria a poltrona do proprio carrinho que a reservou.
 */
public class SeatHoldCartValidationStrategy extends DefaultB2BCartValidationStrategy
{
	private SeatHoldService seatHoldService;

	@Override
	protected CommerceCartModification validateCartEntry(final CartModel cartModel, final CartEntryModel cartEntryModel)
	{
		final ProductModel product = cartEntryModel.getProduct();
		// 08/11: com a reserva vencida a poltrona fica no carrinho como está (sem reservar de novo em silêncio);
		// o carrinho avisa e o fechamento do pedido recusa até o torcedor retirá-la
		final boolean vencida = product != null && seatHoldService.isSeat(product) && seatHoldService.expiredHoldBlocks()
				&& seatHoldService.activeHoldExpiry(product, cartModel) == null;
		if (vencida || product != null && seatHoldService.isSeat(product) && seatHoldService.hold(product, cartModel, false))
		{
			final long quantity = cartEntryModel.getQuantity() == null ? 0L : cartEntryModel.getQuantity().longValue();
			final CommerceCartModification modification = new CommerceCartModification();
			modification.setStatusCode(CommerceCartModificationStatus.SUCCESS);
			modification.setQuantityAdded(quantity);
			modification.setQuantity(quantity);
			modification.setEntry(cartEntryModel);
			return modification;
		}
		return super.validateCartEntry(cartModel, cartEntryModel);
	}

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}
}
