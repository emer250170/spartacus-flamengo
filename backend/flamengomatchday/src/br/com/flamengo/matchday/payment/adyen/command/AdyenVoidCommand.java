package br.com.flamengo.matchday.payment.adyen.command;

import br.com.flamengo.matchday.payment.adyen.AdyenException;
import de.hybris.platform.payment.commands.VoidCommand;
import de.hybris.platform.payment.commands.request.VoidRequest;
import de.hybris.platform.payment.commands.result.VoidResult;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/** Cancelamento do pagamento na Adyen (POST /payments/{pspReference}/cancels), confirmado pelo aviso CANCELLATION */
public class AdyenVoidCommand implements VoidCommand
{
	private static final Logger LOG = LoggerFactory.getLogger(AdyenVoidCommand.class);

	@Override
	public VoidResult perform(final VoidRequest request)
	{
		final VoidResult r = new VoidResult();
		r.setAmount(request.getTotalAmount());
		r.setCurrency(request.getCurrency());
		r.setRequestTime(new java.util.Date());
		try
		{
			final Map<String, Object> res = AdyenCommandSupport.adyen().cancelPayment(request.getRequestId(),
					request.getMerchantTransactionCode());
			AdyenCommandSupport.fill(r, request.getMerchantTransactionCode(), request.getCurrency(), (String) res.get("pspReference"),
					request.getRequestToken(), true);
		}
		catch (final AdyenException e)
		{
			LOG.warn("Adyen: cancelamento de {} recusado: {}", request.getRequestId(), e.getMessage());
			AdyenCommandSupport.fill(r, request.getMerchantTransactionCode(), request.getCurrency(), request.getRequestId(),
					request.getRequestToken(), false);
		}
		return r;
	}
}
