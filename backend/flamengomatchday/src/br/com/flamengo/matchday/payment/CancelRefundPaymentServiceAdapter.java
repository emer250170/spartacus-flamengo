package br.com.flamengo.matchday.payment;

import br.com.flamengo.matchday.socio.SocioPaymentService;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.ordercancel.OrderCancelPaymentServiceAdapter;
import de.hybris.platform.ordercancel.model.OrderCancelRecordEntryModel;
import de.hybris.platform.payment.PaymentService;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.model.ModelService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Item 14: estorno no cancelamento. O pedido da loja é cobrado no início (takePayment), e o
 * cancelamento padrão do SAP (linha ou pedido, antes do envio) só recalcula o pedido, sem
 * lançamento no pagamento. Este adaptador deixa o SAP recalcular como antes e, em seguida, lança
 * um Estorno (REFUND_FOLLOW_ON) na transação com captura, no valor de (capturado - estornado) -
 * novo total do pedido. Pedido sem nada a entregar (cancelado ou todas as linhas zeradas) é
 * estornado por inteiro, frete incluído. Chave: flamengo.loja.operacao.ativo (false = como antes).
 * <p>
 * Os serviços são buscados na hora (não injetados) porque este objeto é criado por um
 * BeanPostProcessor, antes dos outros beans.
 */
public class CancelRefundPaymentServiceAdapter implements OrderCancelPaymentServiceAdapter
{
	private static final Logger LOG = LoggerFactory.getLogger(CancelRefundPaymentServiceAdapter.class);
	private static final Set<PaymentTransactionType> CAPTURES = EnumSet.of(PaymentTransactionType.CAPTURE,
			PaymentTransactionType.PARTIAL_CAPTURE);
	private static final Set<PaymentTransactionType> REFUNDS = EnumSet.of(PaymentTransactionType.REFUND_FOLLOW_ON,
			PaymentTransactionType.REFUND_STANDALONE);
	private static final BigDecimal CENT = new BigDecimal("0.01");

	private final OrderCancelPaymentServiceAdapter delegate;

	public CancelRefundPaymentServiceAdapter(final OrderCancelPaymentServiceAdapter delegate)
	{
		this.delegate = delegate;
	}

	@Override
	public void recalculateOrderAndModifyPayments(final OrderModel order)
	{
		delegate.recalculateOrderAndModifyPayments(order);
		refund(order);
	}

	@Override
	public void recalculateOrderAndModifyPayments(final OrderModel order, final OrderCancelRecordEntryModel entry)
	{
		delegate.recalculateOrderAndModifyPayments(order, entry);
		refund(order);
	}

	public OrderCancelPaymentServiceAdapter getDelegate()
	{
		return delegate;
	}

	protected void refund(final OrderModel order)
	{
		try
		{
			if (order == null || !bean("socioPaymentService", SocioPaymentService.class).isStoreEnabled())
			{
				return;
			}
			final PaymentTransactionModel tx = capturedTransaction(order);
			if (tx == null)
			{
				return;
			}
			final BigDecimal net = sum(tx, CAPTURES).subtract(sum(tx, REFUNDS));
			final BigDecimal due = nothingLeft(order) ? BigDecimal.ZERO : value(order.getTotalPrice());
			final BigDecimal amount = net.subtract(due).setScale(2, RoundingMode.HALF_UP);
			if (amount.compareTo(CENT) < 0)
			{
				return;
			}
			final PaymentTransactionEntryModel e = bean("paymentService", PaymentService.class).refundFollowOn(tx, amount);
			bean("modelService", ModelService.class).refresh(order);
			LOG.info("Pedido {}: estorno de {} no cancelamento ({})", order.getCode(), amount,
					e == null ? "sem lançamento" : e.getTransactionStatus());
		}
		catch (final RuntimeException ex)
		{
			// o cancelamento já aconteceu; sem o estorno, o pedido fica como no padrão do SAP
			LOG.error("Pedido {}: estorno do cancelamento não lançado", order == null ? "?" : order.getCode(), ex);
		}
	}

	/** A transação com captura aceita (a cobrança do pedido). */
	private static PaymentTransactionModel capturedTransaction(final OrderModel order)
	{
		if (order.getPaymentTransactions() == null)
		{
			return null;
		}
		for (final PaymentTransactionModel tx : order.getPaymentTransactions())
		{
			if (sum(tx, CAPTURES).signum() > 0)
			{
				return tx;
			}
		}
		return null;
	}

	private static BigDecimal sum(final PaymentTransactionModel tx, final Set<PaymentTransactionType> types)
	{
		BigDecimal total = BigDecimal.ZERO;
		if (tx.getEntries() != null)
		{
			for (final PaymentTransactionEntryModel e : tx.getEntries())
			{
				if (types.contains(e.getType()) && TransactionStatus.ACCEPTED.name().equals(e.getTransactionStatus())
						&& e.getAmount() != null)
				{
					total = total.add(e.getAmount());
				}
			}
		}
		return total;
	}

	private static boolean nothingLeft(final OrderModel order)
	{
		if (order.getStatus() == OrderStatus.CANCELLED)
		{
			return true;
		}
		if (order.getEntries() == null)
		{
			return true;
		}
		for (final AbstractOrderEntryModel e : order.getEntries())
		{
			if (e.getQuantity() != null && e.getQuantity().longValue() > 0)
			{
				return false;
			}
		}
		return true;
	}

	private static BigDecimal value(final Double d)
	{
		return d == null ? BigDecimal.ZERO : BigDecimal.valueOf(d.doubleValue());
	}

	private static <T> T bean(final String name, final Class<T> type)
	{
		return Registry.getApplicationContext().getBean(name, type);
	}
}
