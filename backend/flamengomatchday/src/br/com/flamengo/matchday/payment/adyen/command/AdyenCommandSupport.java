package br.com.flamengo.matchday.payment.adyen.command;

import br.com.flamengo.matchday.payment.adyen.AdyenPaymentService;
import de.hybris.platform.core.Registry;
import de.hybris.platform.payment.commands.result.AbstractResult;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.dto.TransactionStatusDetails;

import java.math.BigDecimal;
import java.util.Currency;


/**
 * Apoio dos comandos de pagamento do provedor "Adyen" (05/11): o SAP chama estes comandos pelo
 * PaymentService (estorno no cancelamento do pedido, devolução, captura) para as transações
 * registradas com o provedor Adyen. O serviço da Adyen é buscado na hora porque a fábrica de
 * comandos é criada junto com o registro de pagamentos, antes dos outros beans.
 */
final class AdyenCommandSupport
{
	private AdyenCommandSupport()
	{
	}

	static AdyenPaymentService adyen()
	{
		return Registry.getApplicationContext().getBean("adyenPaymentService", AdyenPaymentService.class);
	}

	static long cents(final BigDecimal amount)
	{
		return amount == null ? 0 : amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
	}

	static String currency(final Currency currency)
	{
		return currency == null ? "BRL" : currency.getCurrencyCode();
	}

	static void fill(final AbstractResult r, final String merchantCode, final Currency currency, final String requestId,
			final String requestToken, final boolean ok)
	{
		r.setMerchantTransactionCode(merchantCode);
		r.setRequestId(requestId);
		r.setRequestToken(requestToken);
		r.setTransactionStatus(ok ? TransactionStatus.ACCEPTED : TransactionStatus.ERROR);
		r.setTransactionStatusDetails(ok ? TransactionStatusDetails.SUCCESFULL : TransactionStatusDetails.COMMUNICATION_PROBLEM);
	}
}
