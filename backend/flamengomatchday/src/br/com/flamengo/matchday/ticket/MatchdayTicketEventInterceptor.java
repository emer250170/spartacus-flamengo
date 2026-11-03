package br.com.flamengo.matchday.ticket;

import br.com.flamengo.matchday.model.MatchdayTicketEventModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;


/**
 * Evento de ingresso criado por fora do serviço (Integration API usada pelo Claude do gestor de
 * jogos, ou o Backoffice): vira a ação pedida (trocar poltrona, trocar quem vai usar, cancelar,
 * marcar como utilizado, observação), com as regras do {@link MatchdayTicketService}. Recusa com
 * mensagem quando a regra não deixa. Os eventos que o próprio serviço grava passam direto.
 */
public class MatchdayTicketEventInterceptor implements PrepareInterceptor<MatchdayTicketEventModel>
{
	private MatchdayTicketService matchdayTicketService;

	@Override
	public void onPrepare(final MatchdayTicketEventModel event, final InterceptorContext ctx) throws InterceptorException
	{
		if (!ctx.isNew(event) || matchdayTicketService.isInternal())
		{
			return;
		}
		try
		{
			matchdayTicketService.apply(event, ctx);
		}
		catch (final MatchdayTicketException e)
		{
			throw new InterceptorException(e.getMessage(), this);
		}
	}

	public void setMatchdayTicketService(final MatchdayTicketService matchdayTicketService)
	{
		this.matchdayTicketService = matchdayTicketService;
	}
}
