package br.com.flamengo.matchday.estudio;

import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.servicelayer.model.attribute.DynamicAttributeHandler;


/**
 * MengoEstampa.situacao (Backoffice): a situação da estampa agora, pela mesma regra da loja
 * (MengoEstudioService), já com o relógio da demo. Só leitura.
 */
public class MengoEstampaSituacaoHandler implements DynamicAttributeHandler<String, ItemModel>
{
	private MengoEstudioService mengoEstudioService;

	@Override
	public String get(final ItemModel estampa)
	{
		if (estampa == null || estampa.getPk() == null)
		{
			return null;
		}
		switch (mengoEstudioService.situacao(estampa, mengoEstudioService.now()))
		{
			case MengoEstudioService.ENCERRADA:
				return "Encerrada (acervo)";
			case MengoEstudioService.ULTIMAS_48H:
				return "Últimas 48 h";
			case MengoEstudioService.ULTIMOS_DIAS:
				return "Últimos dias";
			case MengoEstudioService.NOVA:
				return "Nova";
			default:
				return "À venda";
		}
	}

	@Override
	public void set(final ItemModel estampa, final String value)
	{
		throw new UnsupportedOperationException("situação é calculada");
	}

	public void setMengoEstudioService(final MengoEstudioService mengoEstudioService)
	{
		this.mengoEstudioService = mengoEstudioService;
	}
}
