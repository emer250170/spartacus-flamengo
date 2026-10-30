package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.SocioPlanModel;
import de.hybris.platform.servicelayer.model.attribute.DynamicAttributeHandler;


/**
 * Titulares ativos do plano, contados na hora pelo grupo do plano (sem quem só
 * tem o plano no carrinho).
 */
public class SocioPlanMembersAttributeHandler implements DynamicAttributeHandler<Integer, SocioPlanModel>
{
	private SocioPlanService socioPlanService;

	@Override
	public Integer get(final SocioPlanModel plan)
	{
		return Integer.valueOf(plan == null ? 0 : socioPlanService.countMembers(plan));
	}

	@Override
	public void set(final SocioPlanModel plan, final Integer value)
	{
		throw new UnsupportedOperationException("Calculado pelo grupo do plano");
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}
}
