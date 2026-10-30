package br.com.flamengo.matchday.socio;

import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.hook.CommerceAddToCartMethodHook;
import de.hybris.platform.commerceservices.order.hook.CommerceUpdateCartEntryHook;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.CartModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Liga o {@link SocioCartService} ao carrinho: depois de incluir um plano e
 * depois de qualquer alteração/remoção de item.
 */
public class SocioCartHooks implements CommerceAddToCartMethodHook, CommerceUpdateCartEntryHook
{
	private static final Logger LOG = LoggerFactory.getLogger(SocioCartHooks.class);

	private SocioCartService socioCartService;

	@Override
	public void beforeAddToCart(final CommerceCartParameter parameters)
	{
		// nada
	}

	@Override
	public void afterAddToCart(final CommerceCartParameter parameters, final CommerceCartModification result)
	{
		if (socioCartService.planLevel(parameters.getProduct()) > 0)
		{
			sync(parameters.getCart());
		}
	}

	@Override
	public void beforeUpdateCartEntry(final CommerceCartParameter parameter)
	{
		// nada
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
			if (cart != null && socioCartService.concerns(cart))
			{
				socioCartService.sync(cart);
			}
		}
		catch (final RuntimeException e)
		{
			// nunca impede a alteração do carrinho
			LOG.error("Falha ao ajustar o sócio do carrinho " + (cart == null ? "" : cart.getCode()), e);
		}
	}

	public void setSocioCartService(final SocioCartService socioCartService)
	{
		this.socioCartService = socioCartService;
	}
}
