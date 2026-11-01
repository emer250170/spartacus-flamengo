package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.model.SocioEventModel;
import de.hybris.platform.core.HybrisEnumValue;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PersistenceOperation;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Cancelamento do plano de sócio confirmado pelo atendimento.
 * <p>
 * O torcedor pede o cancelamento em Minha conta › Meu sócio-torcedor, o que abre
 * um chamado "Cancelamento do plano X" (CsTicket). Quando o atendimento fecha o
 * chamado no Backoffice, o torcedor sai do grupo do plano: perde na hora o selo,
 * a onda de compra e os descontos no ingresso e na loja, e a vaga volta ao plano.
 * <p>
 * Mapeado no tipo CsTicket pelo nome (sem depender das classes do sistema de
 * tickets): lê assunto, situação e cliente pelo ModelService.
 */
public class SocioCancelTicketInterceptor implements PrepareInterceptor<ItemModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(SocioCancelTicketInterceptor.class);
	/** Assunto dos chamados abertos pela loja (SocioAccountService no Spartacus) */
	public static final String SUBJECT = "Cancelamento do plano";
	private static final String CLOSED = "Closed";

	private SocioPlanService socioPlanService;

	@Override
	public void onPrepare(final ItemModel ticket, final InterceptorContext ctx) throws InterceptorException
	{
		if (ctx.isNew(ticket) || !ctx.isModified(ticket, "state"))
		{
			return;
		}
		final ModelService modelService = ctx.getModelService();
		final Object state = modelService.getAttributeValue(ticket, "state");
		if (!(state instanceof HybrisEnumValue) || !CLOSED.equalsIgnoreCase(((HybrisEnumValue) state).getCode()))
		{
			return;
		}
		final Object headline = modelService.getAttributeValue(ticket, "headline");
		if (!(headline instanceof String) || !((String) headline).startsWith(SUBJECT))
		{
			return;
		}
		final Object customer = modelService.getAttributeValue(ticket, "customer");
		if (!(customer instanceof CustomerModel))
		{
			return;
		}
		// evento criado antes de sair dos grupos, para guardar o plano que ele tinha
		final SocioEventModel event = socioPlanService.newEvent((CustomerModel) customer, SocioEventType.CANCELAMENTO,
				"Atendimento", "Chamado fechado: " + headline);
		final List<String> plans = socioPlanService.removeMembership((CustomerModel) customer);
		if (plans.isEmpty())
		{
			LOG.info("Chamado de cancelamento fechado: {} já não era sócio", ((CustomerModel) customer).getUid());
			return;
		}
		ctx.registerElementFor(customer, PersistenceOperation.SAVE);
		event.setResult("Deixou de ser sócio (" + String.join(", ", plans) + ")");
		ctx.registerElementFor(event, PersistenceOperation.SAVE);
		LOG.info("Chamado de cancelamento fechado: {} deixa de ser sócio ({})", ((CustomerModel) customer).getUid(), plans);
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}
}
