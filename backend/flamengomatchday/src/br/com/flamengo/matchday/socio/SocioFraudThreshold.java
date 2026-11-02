package br.com.flamengo.matchday.socio;

import de.hybris.platform.fraud.FraudService;
import de.hybris.platform.fraud.impl.DefaultFraudService;
import de.hybris.platform.fraud.impl.DefaultHybrisFraudServiceProvider;
import de.hybris.platform.fraud.impl.FraudServiceProvider;
import de.hybris.platform.fraud.symptom.FraudSymptom;
import de.hybris.platform.fraud.symptom.impl.OrderThresholdSymptom;
import de.hybris.platform.servicelayer.config.ConfigurationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;


/**
 * Limite de valor da checagem de fraude padrão (item 7): o sintoma "Price threshold"
 * do Commerce manda para revisão manual pedidos acima de R$ 1.000 (mais R$ 50 de
 * pontuação a cada R$ 100), e o ano inteiro dos planos maiores (Pix, boleto ou
 * cartão) passa disso. Com flamengo.fraude.limitePedido preenchida, o limite do
 * sintoma vira esse valor na subida do servidor; vazia, nada muda.
 */
public class SocioFraudThreshold implements InitializingBean
{
	public static final String PROPERTY = "flamengo.fraude.limitePedido";
	private static final Logger LOG = LoggerFactory.getLogger(SocioFraudThreshold.class);

	private FraudService fraudService;
	private ConfigurationService configurationService;

	@Override
	public void afterPropertiesSet()
	{
		final double limit = configurationService.getConfiguration().getDouble(PROPERTY, 0);
		if (limit <= 0 || !(fraudService instanceof DefaultFraudService))
		{
			return;
		}
		try
		{
			for (final FraudServiceProvider provider : ((DefaultFraudService) fraudService).getProviders())
			{
				if (!(provider instanceof DefaultHybrisFraudServiceProvider))
				{
					continue;
				}
				for (final FraudSymptom symptom : ((DefaultHybrisFraudServiceProvider) provider).getSymptomList())
				{
					if (symptom instanceof OrderThresholdSymptom)
					{
						((OrderThresholdSymptom) symptom).setThresholdLimit(limit);
						LOG.info("Checagem de fraude: limite de valor do pedido em {}", Double.valueOf(limit));
					}
				}
			}
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Checagem de fraude: limite não aplicado ({})", e.toString());
		}
	}

	public void setFraudService(final FraudService fraudService)
	{
		this.fraudService = fraudService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
