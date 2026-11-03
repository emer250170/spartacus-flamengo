package br.com.flamengo.matchday.attribute;

import br.com.flamengo.matchday.socio.SocioPaymentService;
import de.hybris.platform.basecommerce.enums.ConsignmentStatus;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.ordersplitting.model.ConsignmentModel;
import de.hybris.platform.servicelayer.model.attribute.DynamicAttributeHandler;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;


/**
 * Item 14: situação do pedido na loja (statusDisplay, usado em Minha conta › Pedidos e no detalhe).
 * Pedido Concluído com remessa de entrega em casa enviada aparece como "shipped" (Enviado), e
 * não "completed". O status do pedido no Backoffice continua Concluído. Os outros casos seguem
 * o mapa padrão do Commerce (delegate). Chave: flamengo.loja.operacao.ativo (false = como antes).
 */
public class ShippedOrderStatusDisplayHandler implements DynamicAttributeHandler<String, OrderModel>
{
	static final String SHIPPED = "shipped";
	private static final Set<ConsignmentStatus> FINISHED = EnumSet.of(ConsignmentStatus.SHIPPED,
			ConsignmentStatus.PICKUP_COMPLETE, ConsignmentStatus.CANCELLED);

	private DynamicAttributeHandler<String, OrderModel> delegate;
	private SocioPaymentService socioPaymentService;

	@Override
	public String get(final OrderModel order)
	{
		if (order != null && order.getStatus() == OrderStatus.COMPLETED && isShipped(order.getConsignments()))
		{
			try
			{
				if (socioPaymentService.isStoreEnabled())
				{
					return SHIPPED;
				}
			}
			catch (final RuntimeException e)
			{
				// sem a chave legível, fica o texto padrão
			}
		}
		return delegate.get(order);
	}

	/** Todas as remessas terminadas e pelo menos uma de entrega em casa enviada. */
	private static boolean isShipped(final Collection<ConsignmentModel> consignments)
	{
		if (consignments == null || consignments.isEmpty())
		{
			return false;
		}
		boolean shipped = false;
		for (final ConsignmentModel c : consignments)
		{
			if (!FINISHED.contains(c.getStatus()))
			{
				return false;
			}
			shipped |= c.getStatus() == ConsignmentStatus.SHIPPED && c.getDeliveryPointOfService() == null;
		}
		return shipped;
	}

	@Override
	public void set(final OrderModel order, final String value)
	{
		delegate.set(order, value);
	}

	public void setDelegate(final DynamicAttributeHandler<String, OrderModel> delegate)
	{
		this.delegate = delegate;
	}

	public void setSocioPaymentService(final SocioPaymentService socioPaymentService)
	{
		this.socioPaymentService = socioPaymentService;
	}
}
