package br.com.flamengo.matchday.hook;

import br.com.flamengo.matchday.socio.SocioEligibilityService;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.order.hook.CommerceAddToCartMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;


/**
 * Antes de a poltrona entrar no carrinho: confere a elegibilidade do torcedor e
 * grava a decisão ({@link SocioEligibilityService#checkAddToCart}). Só recusa a
 * poltrona com o bloqueio ligado (flamengo.socio.elegibilidade.bloqueio=true).
 */
public class SocioEligibilityAddToCartHook implements CommerceAddToCartMethodHook
{
	private SocioEligibilityService socioEligibilityService;

	@Override
	public void beforeAddToCart(final CommerceCartParameter parameters) throws CommerceCartModificationException
	{
		if (parameters.getProduct() == null || parameters.getCart() == null)
		{
			return;
		}
		socioEligibilityService.checkAddToCart(parameters.getCart(), parameters.getProduct(), parameters.getQuantity());
	}

	@Override
	public void afterAddToCart(final CommerceCartParameter parameters, final CommerceCartModification result)
	{
		// nada
	}

	public void setSocioEligibilityService(final SocioEligibilityService socioEligibilityService)
	{
		this.socioEligibilityService = socioEligibilityService;
	}
}
