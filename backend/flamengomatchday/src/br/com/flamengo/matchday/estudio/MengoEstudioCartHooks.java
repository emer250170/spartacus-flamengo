package br.com.flamengo.matchday.estudio;

import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.order.hook.CommerceAddToCartMethodHook;
import de.hybris.platform.commerceservices.order.hook.CommercePlaceOrderMethodHook;
import de.hybris.platform.commerceservices.order.hook.CommerceUpdateCartEntryHook;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.payment.CreditCardPaymentInfoModel;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.servicelayer.config.ConfigurationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Mengo Estúdio (08/11, PR 3) nos ganchos do carrinho e do pedido:
 * <ul>
 * <li>adicionar: a camiseta do Estúdio só entra pelo configurador ({@link MengoEstudioCartService#adicionar}, que pede
 * linha nova); pela inclusão comum (sem estampa) é recusada;</li>
 * <li>alterar a quantidade: no máximo flamengo.estudio.quantidade.max por linha;</li>
 * <li>fechar o pedido: recusa se alguma estampa saiu de linha (proteção final, além do aviso no carrinho);</li>
 * <li>pagamento recusado (PR 4, Simulações da demo): com cartão fora da Adyen, a recusa vem aqui, antes de criar o
 * pedido (com a Adyen ligada, vem no /adyen/check, antes de cobrar o cartão);</li>
 * <li>pedido fechado (PR 4): situação Recebido e o primeiro evento da linha do tempo.</li>
 * </ul>
 * Nada muda com flamengo.estudio.ativo=false.
 */
public class MengoEstudioCartHooks
		implements CommerceAddToCartMethodHook, CommerceUpdateCartEntryHook, CommercePlaceOrderMethodHook
{
	private static final Logger LOG = LoggerFactory.getLogger(MengoEstudioCartHooks.class);

	private MengoEstudioService mengoEstudioService;
	private MengoEstudioCartService mengoEstudioCartService;
	private MengoEstudioOrderService mengoEstudioOrderService;
	private ConfigurationService configurationService;

	@Override
	public void beforeAddToCart(final CommerceCartParameter parameters) throws CommerceCartModificationException
	{
		if (mengoEstudioService.isActive() && mengoEstudioCartService.isCamiseta(parameters.getProduct())
				&& !parameters.isCreateNewEntry())
		{
			throw new CommerceCartModificationException("Monte a camiseta na página da estampa do Mengo Estúdio.");
		}
	}

	@Override
	public void afterAddToCart(final CommerceCartParameter parameters, final CommerceCartModification result)
	{
		// nada
	}

	@Override
	public void beforeUpdateCartEntry(final CommerceCartParameter parameter)
	{
		try
		{
			if (!mengoEstudioService.isActive() || parameter.getCart() == null || parameter.getCart().getEntries() == null)
			{
				return;
			}
			final int max = mengoEstudioCartService.quantidadeMax();
			if (parameter.getQuantity() <= max)
			{
				return;
			}
			for (final AbstractOrderEntryModel e : parameter.getCart().getEntries())
			{
				if (e.getEntryNumber() != null && e.getEntryNumber().longValue() == parameter.getEntryNumber()
						&& mengoEstudioCartService.isEstudioEntry(e))
				{
					parameter.setQuantity(max);
					return;
				}
			}
		}
		catch (final RuntimeException e)
		{
			LOG.error("Mengo Estúdio: falha ao conferir a quantidade", e);
		}
	}

	@Override
	public void afterUpdateCartEntry(final CommerceCartParameter parameter, final CommerceCartModification result)
	{
		// nada
	}

	@Override
	public void beforePlaceOrder(final CommerceCheckoutParameter parameter) throws InvalidCartException
	{
		final String bloqueio = mengoEstudioCartService.bloqueio(parameter.getCart());
		if (bloqueio != null)
		{
			LOG.info("Mengo Estúdio: pedido recusado no carrinho {}: {}", parameter.getCart().getCode(), bloqueio);
			throw new InvalidCartException(bloqueio);
		}
		if (mengoEstudioOrderService != null && parameter.getCart().getPaymentInfo() instanceof CreditCardPaymentInfoModel
				&& !configurationService.getConfiguration().getBoolean("flamengo.pagamento.adyen.ativo", false))
		{
			final String recusa = mengoEstudioOrderService.recusaPagamento(parameter.getCart());
			if (recusa != null)
			{
				throw new InvalidCartException(recusa);
			}
		}
	}

	@Override
	public void beforeSubmitOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		// nada
	}

	@Override
	public void afterPlaceOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		try
		{
			if (mengoEstudioOrderService != null && result != null && result.getOrder() != null)
			{
				mengoEstudioOrderService.iniciar(result.getOrder());
			}
		}
		catch (final RuntimeException e)
		{
			// o pedido já está fechado: a situação pode ser criada depois (ao abrir o pedido)
			LOG.error("Mengo Estúdio: situação inicial do pedido não gravada", e);
		}
	}

	public void setMengoEstudioService(final MengoEstudioService mengoEstudioService)
	{
		this.mengoEstudioService = mengoEstudioService;
	}

	public void setMengoEstudioCartService(final MengoEstudioCartService mengoEstudioCartService)
	{
		this.mengoEstudioCartService = mengoEstudioCartService;
	}

	public void setMengoEstudioOrderService(final MengoEstudioOrderService mengoEstudioOrderService)
	{
		this.mengoEstudioOrderService = mengoEstudioOrderService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
