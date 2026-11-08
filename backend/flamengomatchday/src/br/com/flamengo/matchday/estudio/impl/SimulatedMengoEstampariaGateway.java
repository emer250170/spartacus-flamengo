package br.com.flamengo.matchday.estudio.impl;

import br.com.flamengo.matchday.estudio.MengoEstampariaGateway;
import br.com.flamengo.matchday.estudio.MengoEstudioService;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.model.ModelService;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Estamparia simulada (08/11, PR 4): a resposta ao cancelamento vem de Simulações da demo › Resposta ao cancelamento.
 * Vazio ou ACEITO = aceita. RECUSADO e FALHA valem para um pedido de cancelamento e voltam a vazio depois de usados,
 * para a próxima tentativa (ou a próxima demonstração) não ser pega de surpresa.
 */
public class SimulatedMengoEstampariaGateway implements MengoEstampariaGateway
{
	private static final Logger LOG = LoggerFactory.getLogger(SimulatedMengoEstampariaGateway.class);

	private MengoEstudioService mengoEstudioService;
	private ModelService modelService;

	@Override
	public String cancelar(final OrderModel order)
	{
		final ItemModel demo = mengoEstudioService.demoConfig();
		final Object v = demo == null ? null : modelService.getAttributeValue(demo, "cancelResponse");
		final String resposta = v instanceof String ? StringUtils.upperCase(StringUtils.trimToEmpty((String) v)) : "";
		if (RECUSADO.equals(resposta) || FALHA.equals(resposta))
		{
			modelService.setAttributeValue(demo, "cancelResponse", null);
			modelService.save(demo);
			LOG.info("Estamparia simulada: cancelamento do pedido {} -> {}", order.getCode(), resposta);
			return resposta;
		}
		LOG.info("Estamparia simulada: cancelamento do pedido {} -> ACEITO", order.getCode());
		return ACEITO;
	}

	public void setMengoEstudioService(final MengoEstudioService mengoEstudioService)
	{
		this.mengoEstudioService = mengoEstudioService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}
}
