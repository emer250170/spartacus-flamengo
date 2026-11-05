package br.com.flamengo.matchday.payment.adyen.command;

import de.hybris.platform.payment.commands.CaptureCommand;
import de.hybris.platform.payment.commands.request.CaptureRequest;
import de.hybris.platform.payment.commands.result.CaptureResult;


/**
 * Captura: a conta da Adyen captura na hora (captura imediata), então o pagamento autorizado já
 * está capturado; o comando só confirma para o SAP.
 */
public class AdyenCaptureCommand implements CaptureCommand
{
	@Override
	public CaptureResult perform(final CaptureRequest request)
	{
		final CaptureResult r = new CaptureResult();
		r.setTotalAmount(request.getTotalAmount());
		r.setCurrency(request.getCurrency());
		r.setRequestTime(new java.util.Date());
		AdyenCommandSupport.fill(r, request.getMerchantTransactionCode(), request.getCurrency(), request.getRequestId(),
				request.getRequestToken(), true);
		return r;
	}
}
