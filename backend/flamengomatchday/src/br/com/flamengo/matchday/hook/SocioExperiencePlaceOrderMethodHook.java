package br.com.flamengo.matchday.hook;

import br.com.flamengo.matchday.socio.SocioExperienceService;
import de.hybris.platform.commerceservices.order.hook.CommercePlaceOrderMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Experiências compradas na loja (item 9): ao fechar o pedido, cada unidade vira um voucher
 * NACAO-… com origem "Compra" (Meus resgates, ficha do sócio e linha do tempo) e o estoque
 * do produto volta a ser as vagas que sobram ({@link SocioExperienceService}). Roda depois
 * do SeatHoldPlaceOrderMethodHook, que já baixou o estoque do pedido. Falha aqui não
 * impede o pedido: fica no log para gerar o voucher à mão.
 */
public class SocioExperiencePlaceOrderMethodHook implements CommercePlaceOrderMethodHook
{
	private static final Logger LOG = LoggerFactory.getLogger(SocioExperiencePlaceOrderMethodHook.class);

	private SocioExperienceService socioExperienceService;

	@Override
	public void beforePlaceOrder(final CommerceCheckoutParameter parameter)
	{
		// nada
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
			socioExperienceService.recordPurchase(result.getOrder());
		}
		catch (final RuntimeException e)
		{
			LOG.error("Pedido " + result.getOrder().getCode() + ": não gerou os vouchers das experiências", e);
		}
	}

	@Override
	public void afterPlaceOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		// nada
	}

	public void setSocioExperienceService(final SocioExperienceService socioExperienceService)
	{
		this.socioExperienceService = socioExperienceService;
	}
}
