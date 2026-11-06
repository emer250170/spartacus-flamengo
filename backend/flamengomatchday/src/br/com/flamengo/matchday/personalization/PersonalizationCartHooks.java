package br.com.flamengo.matchday.personalization;

import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.hook.CommerceAddToCartMethodHook;
import de.hybris.platform.commerceservices.order.hook.CommerceUpdateCartEntryHook;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.CartModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Mantém as linhas de preço da personalização (FLA-PERS-*) com a quantidade do manto: depois
 * de incluir mais unidades de um manto já no carrinho e depois de alterar ou tirar um item
 * (carrinho lateral, página do carrinho, API). Nunca impede a alteração do carrinho.
 */
public class PersonalizationCartHooks implements CommerceAddToCartMethodHook, CommerceUpdateCartEntryHook
{
	private static final Logger LOG = LoggerFactory.getLogger(PersonalizationCartHooks.class);

	private PersonalizationService personalizationService;

	@Override
	public void beforeAddToCart(final CommerceCartParameter parameters)
	{
		// nada
	}

	@Override
	public void afterAddToCart(final CommerceCartParameter parameters, final CommerceCartModification result)
	{
		sync(parameters.getCart());
	}

	@Override
	public void beforeUpdateCartEntry(final CommerceCartParameter parameter)
	{
		// tirou uma linha de preço: a opção sai do manto (senão o ajuste a traria de volta)
		try
		{
			if (parameter.getCart() != null && parameter.getQuantity() == 0 && personalizationService.isActive())
			{
				personalizationService.dropOption(parameter.getCart(), (int) parameter.getEntryNumber());
			}
		}
		catch (final RuntimeException e)
		{
			LOG.error("Falha ao tirar a opção de personalização", e);
		}
	}

	@Override
	public void afterUpdateCartEntry(final CommerceCartParameter parameter, final CommerceCartModification result)
	{
		sync(parameter.getCart());
	}

	protected void sync(final CartModel cart)
	{
		try
		{
			if (cart != null && personalizationService.isActive())
			{
				personalizationService.syncAddOns(cart);
			}
		}
		catch (final RuntimeException e)
		{
			LOG.error("Falha ao ajustar a personalização do carrinho " + (cart == null ? "" : cart.getCode()), e);
		}
	}

	public void setPersonalizationService(final PersonalizationService personalizationService)
	{
		this.personalizationService = personalizationService;
	}
}
