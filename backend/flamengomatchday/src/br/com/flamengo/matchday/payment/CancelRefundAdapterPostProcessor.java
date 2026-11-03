package br.com.flamengo.matchday.payment;

import de.hybris.platform.ordercancel.OrderCancelPaymentServiceAdapter;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;


/**
 * Item 14: o cancelamento do SAP usa dois adaptadores de pagamento (orderCancelPaymentServiceAdapter
 * no cancelamento imediato e OmsOrderCancelPaymentServiceAdapter no pedido com remessa). Em vez de
 * trocar a configuração de cada executor, todo adaptador criado passa a ser envolvido pelo
 * CancelRefundPaymentServiceAdapter, que lança o estorno depois do recálculo do SAP.
 */
public class CancelRefundAdapterPostProcessor implements BeanPostProcessor
{
	@Override
	public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException
	{
		if (bean instanceof OrderCancelPaymentServiceAdapter && !(bean instanceof CancelRefundPaymentServiceAdapter))
		{
			return new CancelRefundPaymentServiceAdapter((OrderCancelPaymentServiceAdapter) bean);
		}
		return bean;
	}
}
