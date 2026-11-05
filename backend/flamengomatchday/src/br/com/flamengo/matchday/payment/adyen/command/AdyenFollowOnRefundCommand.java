package br.com.flamengo.matchday.payment.adyen.command;

import br.com.flamengo.matchday.payment.adyen.AdyenException;
import de.hybris.platform.payment.commands.FollowOnRefundCommand;
import de.hybris.platform.payment.commands.request.FollowOnRefundRequest;
import de.hybris.platform.payment.commands.result.RefundResult;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Estorno na Adyen (POST /payments/{pspReference}/refunds). A Adyen aceita na hora ("received") e
 * confirma pelo aviso REFUND do webhook; com recusa da Adyen, o lançamento fica com erro.
 */
public class AdyenFollowOnRefundCommand implements FollowOnRefundCommand<FollowOnRefundRequest>
{
	private static final Logger LOG = LoggerFactory.getLogger(AdyenFollowOnRefundCommand.class);

	@Override
	public RefundResult perform(final FollowOnRefundRequest request)
	{
		final RefundResult r = new RefundResult();
		r.setPaymentProvider(request.getPaymentProvider());
		r.setTotalAmount(request.getTotalAmount());
		r.setCurrency(request.getCurrency());
		r.setRequestTime(new java.util.Date());
		try
		{
			final Map<String, Object> res = AdyenCommandSupport.adyen().refundPayment(request.getRequestId(),
					AdyenCommandSupport.cents(request.getTotalAmount()), AdyenCommandSupport.currency(request.getCurrency()),
					request.getMerchantTransactionCode());
			AdyenCommandSupport.fill(r, request.getMerchantTransactionCode(), request.getCurrency(), (String) res.get("pspReference"),
					request.getRequestToken(), true);
		}
		catch (final AdyenException e)
		{
			LOG.warn("Adyen: estorno de {} em {} recusado: {}", request.getTotalAmount(), request.getRequestId(), e.getMessage());
			AdyenCommandSupport.fill(r, request.getMerchantTransactionCode(), request.getCurrency(), request.getRequestId(),
					request.getRequestToken(), false);
		}
		return r;
	}
}
