package br.com.flamengo.matchday.hook;

import br.com.flamengo.matchday.service.SeatHoldService;
import de.hybris.platform.commerceservices.order.CommerceCartCalculationStrategy;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.order.hook.CommerceAddToCartMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.user.UserService;


/**
 * Ao colocar a poltrona no carrinho, reserva a poltrona para ele.
 * <p>
 * A checagem de estoque padrao ja recusa a poltrona reservada por outra pessoa.
 * Se duas pessoas passarem por ela no mesmo instante, so uma consegue a reserva;
 * a outra tem o item retirado e recebe erro.
 */
public class SeatHoldAddToCartMethodHook implements CommerceAddToCartMethodHook
{
	private SeatHoldService seatHoldService;
	private UserService userService;
	private ModelService modelService;
	private CommerceCartCalculationStrategy commerceCartCalculationStrategy;

	@Override
	public void beforeAddToCart(final CommerceCartParameter parameters) throws CommerceCartModificationException
	{
		final ProductModel product = parameters.getProduct();
		final CartModel cart = parameters.getCart();
		if (product == null || cart == null || !seatHoldService.isSeat(product))
		{
			return;
		}
		if (cart.getUser() == null || userService.isAnonymousUser(cart.getUser()))
		{
			throw new CommerceCartModificationException("Entre na sua conta para reservar a poltrona " + product.getCode());
		}
		// o torcedor esta trazendo a propria poltrona de outro carrinho
		seatHoldService.releaseIfHeldElsewhereBySameUser(product, cart);
	}

	@Override
	public void afterAddToCart(final CommerceCartParameter parameters, final CommerceCartModification result)
			throws CommerceCartModificationException
	{
		final ProductModel product = parameters.getProduct();
		final CartModel cart = parameters.getCart();
		if (product == null || cart == null || result == null || result.getQuantityAdded() <= 0
				|| !seatHoldService.isSeat(product))
		{
			return;
		}
		if (seatHoldService.hold(product, cart, true))
		{
			return;
		}

		// outra pessoa reservou no mesmo instante: tira o item e avisa
		final AbstractOrderEntryModel entry = result.getEntry();
		if (entry != null && !modelService.isRemoved(entry))
		{
			modelService.remove(entry);
			modelService.refresh(cart);
			final CommerceCartParameter recalculate = new CommerceCartParameter();
			recalculate.setEnableHooks(true);
			recalculate.setCart(cart);
			commerceCartCalculationStrategy.calculateCart(recalculate);
		}
		throw new CommerceCartModificationException(
				"A poltrona " + product.getCode() + " acabou de ser reservada por outra pessoa");
	}

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setCommerceCartCalculationStrategy(final CommerceCartCalculationStrategy commerceCartCalculationStrategy)
	{
		this.commerceCartCalculationStrategy = commerceCartCalculationStrategy;
	}
}
