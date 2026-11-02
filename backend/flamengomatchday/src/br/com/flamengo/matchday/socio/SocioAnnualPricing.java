package br.com.flamengo.matchday.socio;

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.order.impl.DefaultCalculationService;
import de.hybris.platform.order.strategies.calculation.FindPriceStrategy;
import de.hybris.platform.util.PriceValue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;


/**
 * Ano inteiro no checkout (item 7): com CARTAO_ANUAL, PIX ou BOLETO gravado no
 * carrinho, o preço base da linha do plano e dos convidados é 12 mensalidades.
 * <p>
 * Embrulha a estratégia de preço do calculationService (a mesma usada pelo carrinho
 * e pelo pedido): todas as chamadas vão para a estratégia original; só o
 * findBasePrice da linha do plano/convidado é multiplicado
 * ({@link SocioPaymentService#priceMultiplier}). Qualquer falha devolve o preço
 * original. Sem forma gravada, nada muda.
 */
public class SocioAnnualPricing implements InitializingBean
{
	private static final Logger LOG = LoggerFactory.getLogger(SocioAnnualPricing.class);

	private Object calculationService;
	private SocioPaymentService socioPaymentService;

	@Override
	public void afterPropertiesSet()
	{
		if (!(calculationService instanceof DefaultCalculationService))
		{
			LOG.warn("Ano inteiro no checkout: calculationService não é o padrão ({}); preço não embrulhado",
					calculationService == null ? null : calculationService.getClass().getName());
			return;
		}
		try
		{
			final Field field = DefaultCalculationService.class.getDeclaredField("findPriceStrategy");
			field.setAccessible(true);
			final FindPriceStrategy original = (FindPriceStrategy) field.get(calculationService);
			if (original == null || (Proxy.isProxyClass(original.getClass())
					&& Proxy.getInvocationHandler(original) instanceof Handler))
			{
				return;
			}
			final FindPriceStrategy wrapped = (FindPriceStrategy) Proxy.newProxyInstance(FindPriceStrategy.class.getClassLoader(),
					new Class<?>[] { FindPriceStrategy.class }, new Handler(original, socioPaymentService));
			((DefaultCalculationService) calculationService).setFindPriceStrategy(wrapped);
			LOG.info("Ano inteiro no checkout: preço do plano e dos convidados embrulhado ({})", original.getClass().getSimpleName());
		}
		catch (final ReflectiveOperationException | RuntimeException e)
		{
			LOG.warn("Ano inteiro no checkout: preço não embrulhado ({})", e.toString());
		}
	}

	protected static class Handler implements InvocationHandler
	{
		private final FindPriceStrategy original;
		private final SocioPaymentService socioPaymentService;

		protected Handler(final FindPriceStrategy original, final SocioPaymentService socioPaymentService)
		{
			this.original = original;
			this.socioPaymentService = socioPaymentService;
		}

		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable
		{
			final Object result;
			try
			{
				result = method.invoke(original, args);
			}
			catch (final InvocationTargetException e)
			{
				throw e.getCause();
			}
			if ("findBasePrice".equals(method.getName()) && args != null && args.length == 1
					&& args[0] instanceof AbstractOrderEntryModel && result instanceof PriceValue)
			{
				final int months = socioPaymentService == null ? 1 : socioPaymentService.priceMultiplier((AbstractOrderEntryModel) args[0]);
				if (months > 1)
				{
					final PriceValue base = (PriceValue) result;
					return new PriceValue(base.getCurrencyIso(), Math.round(base.getValue() * months * 100) / 100.0, base.isNet());
				}
			}
			return result;
		}
	}

	public void setCalculationService(final Object calculationService)
	{
		this.calculationService = calculationService;
	}

	public void setSocioPaymentService(final SocioPaymentService socioPaymentService)
	{
		this.socioPaymentService = socioPaymentService;
	}
}
