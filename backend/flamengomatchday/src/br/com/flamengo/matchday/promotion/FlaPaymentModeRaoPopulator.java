package br.com.flamengo.matchday.promotion;

import br.com.flamengo.matchday.enums.SocioPaymentMethod;
import br.com.flamengo.matchday.socio.SocioPayments;
import br.com.flamengo.matchday.socio.SocioPlanService;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.ruleengineservices.rao.AbstractOrderRAO;
import de.hybris.platform.ruleengineservices.rao.PaymentModeRAO;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Forma de pagamento do carrinho para o motor de promoções (05/11): grava no {@code paymentMode} do CartRAO a
 * forma escolhida no checkout (PIX, BOLETO, CARTAO_MENSAL, CARTAO_ANUAL, PIX_AUTOMATICO), com o sufixo
 * {@value #PLAN_SUFFIX} quando o carrinho tem plano de sócio. É o que a condição "Forma de pagamento"
 * ({@link FlaPaymentMethodConditionTranslator}) compara. Carrinho sem forma escolhida (ou com um modo de
 * pagamento do SAP já preenchido): nada muda. Chave {@value #ENABLED_PROPERTY} (false = como antes).
 */
public class FlaPaymentModeRaoPopulator implements Populator<AbstractOrderModel, AbstractOrderRAO>
{
	public static final String ENABLED_PROPERTY = "flamengo.promocao.formaPagamento.ativo";
	public static final String PLAN_SUFFIX = "@PLANO";

	private static final Logger LOG = LoggerFactory.getLogger(FlaPaymentModeRaoPopulator.class);

	private ModelService modelService;
	private ConfigurationService configurationService;
	private SocioPlanService socioPlanService;

	@Override
	public void populate(final AbstractOrderModel source, final AbstractOrderRAO target)
	{
		try
		{
			if (source == null || target == null || target.getPaymentMode() != null
					|| !configurationService.getConfiguration().getBoolean(ENABLED_PROPERTY, true))
			{
				return;
			}
			final SocioPaymentMethod method = SocioPayments.method(modelService, source);
			if (method == null)
			{
				return;
			}
			final PaymentModeRAO mode = new PaymentModeRAO();
			mode.setCode(code(method, hasPlan(source)));
			target.setPaymentMode(mode);
		}
		catch (final RuntimeException e)
		{
			// a promoção nunca impede o cálculo do carrinho
			LOG.warn("Forma de pagamento não enviada ao motor de promoções: {}", e.getMessage());
		}
	}

	/** "PIX" ou "PIX@PLANO" */
	public static String code(final SocioPaymentMethod method, final boolean plan)
	{
		return method.getCode() + (plan ? PLAN_SUFFIX : "");
	}

	protected boolean hasPlan(final AbstractOrderModel order)
	{
		if (order.getEntries() == null || socioPlanService == null)
		{
			return false;
		}
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			if (entry.getProduct() != null && socioPlanService.findForProduct(entry.getProduct()) != null)
			{
				return true;
			}
		}
		return false;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}
}
