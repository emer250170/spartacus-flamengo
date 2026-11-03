package br.com.flamengo.matchday.hook;

import br.com.flamengo.matchday.ticket.MatchdayTicketService;
import de.hybris.platform.commerceservices.order.hook.CommercePlaceOrderMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Ingressos vendidos (item 10): ao fechar o pedido, cada poltrona vira um ingresso
 * (MatchdayTicket) com o evento "Venda" no histórico, para o gestor de jogos acompanhar e agir
 * no cockpit Matchday. Roda depois do SeatHoldPlaceOrderMethodHook (estoque já baixado) e do
 * hook de convidados (quem vai usar já gravado na linha). Os ingressos são registrados mesmo
 * com a chave desligada (a chave só recusa as ações). Falha aqui não impede o pedido: o
 * backfill cria depois.
 */
public class MatchdayTicketPlaceOrderMethodHook implements CommercePlaceOrderMethodHook
{
	private static final Logger LOG = LoggerFactory.getLogger(MatchdayTicketPlaceOrderMethodHook.class);

	private MatchdayTicketService matchdayTicketService;

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
			matchdayTicketService.createForOrder(result.getOrder());
		}
		catch (final RuntimeException e)
		{
			LOG.error("Pedido " + result.getOrder().getCode() + ": não registrou os ingressos (rodar o backfill)", e);
		}
	}

	@Override
	public void afterPlaceOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		// nada
	}

	public void setMatchdayTicketService(final MatchdayTicketService matchdayTicketService)
	{
		this.matchdayTicketService = matchdayTicketService;
	}
}
