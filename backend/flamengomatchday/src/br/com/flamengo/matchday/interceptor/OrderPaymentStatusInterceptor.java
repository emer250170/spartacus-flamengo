package br.com.flamengo.matchday.interceptor;

import br.com.flamengo.matchday.socio.SocioPaymentService;
import br.com.flamengo.matchday.socio.SocioPayments;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Item 14: a confirmação do Pix ou boleto é a mudança do status do pedido de "Aguardando
 * pagamento" (PAYMENT_NOT_CAPTURED) para "Pagamento confirmado" (PAYMENT_CAPTURED), no
 * Backoffice ou pelo Claude. Aqui só a data da confirmação é gravada no próprio pedido; o
 * processo do pedido, que espera o pagamento, vê a data e segue (expedição, plano do sócio,
 * fechamento). Chave: flamengo.loja.operacao.ativo (false = não faz nada).
 */
public class OrderPaymentStatusInterceptor implements PrepareInterceptor<OrderModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(OrderPaymentStatusInterceptor.class);

	private ModelService modelService;
	private SocioPaymentService socioPaymentService;

	@Override
	public void onPrepare(final OrderModel order, final InterceptorContext ctx)
	{
		if (ctx.isNew(order) || !ctx.isModified(order, OrderModel.STATUS) || order.getStatus() != OrderStatus.PAYMENT_CAPTURED)
		{
			return;
		}
		try
		{
			if (!socioPaymentService.isStoreEnabled() || !SocioPayments.isAwaiting(modelService, order))
			{
				return;
			}
			modelService.setAttributeValue(order, SocioPayments.CONFIRMED, new Date());
			LOG.info("Pedido {}: pagamento confirmado pela mudança de status", order.getCode());
		}
		catch (final RuntimeException e)
		{
			// antes do Update do sistema o atributo não existe: o status muda como antes
			LOG.warn("Pedido {}: confirmação pelo status não gravada: {}", order.getCode(), e.getMessage());
		}
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setSocioPaymentService(final SocioPaymentService socioPaymentService)
	{
		this.socioPaymentService = socioPaymentService;
	}
}
