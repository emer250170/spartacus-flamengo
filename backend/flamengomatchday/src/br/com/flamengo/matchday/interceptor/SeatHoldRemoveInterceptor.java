package br.com.flamengo.matchday.interceptor;

import br.com.flamengo.matchday.model.SeatHoldModel;
import br.com.flamengo.matchday.service.SeatHoldRemovalContext;
import br.com.flamengo.matchday.service.SeatHoldService;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.RemoveInterceptor;


/**
 * Reserva de poltrona apagada fora do servico de reserva (por exemplo, pelo
 * administrador no Backoffice): a poltrona volta a venda e sai do carrinho do
 * torcedor, que ve o aviso na loja e reserva de novo.
 */
public class SeatHoldRemoveInterceptor implements RemoveInterceptor<SeatHoldModel>
{
	private SeatHoldService seatHoldService;

	@Override
	public void onRemove(final SeatHoldModel hold, final InterceptorContext ctx) throws InterceptorException
	{
		if (!SeatHoldRemovalContext.isInternal())
		{
			seatHoldService.handleExternalRemoval(hold);
		}
	}

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}
}
