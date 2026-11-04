package br.com.flamengo.matchday.fulfillment;

import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.workflow.jobs.AutomatedWorkflowTemplateJob;
import de.hybris.platform.workflow.model.WorkflowActionModel;
import de.hybris.platform.workflow.model.WorkflowDecisionModel;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Atendimento do pedido (Backoffice > Atendimento do pedido): ação automática do fluxo de
 * consignação ("Pick up" e "Shipping") que espera o processo do pedido ficar pronto antes de
 * avisar a retirada ou o embarque.
 * <p>
 * O padrão do SAP ({@code taskAssignmentPickupConsignmentAction} e
 * {@code taskAssignmentShipConsignmentAction}) dispara o evento na hora; se o atendente concluir o
 * Pick up poucos segundos depois do Packing, o processo da consignação ainda está preparando a
 * retirada (o motor de tarefas leva de 10 a 20 s) e o Backoffice mostra "Internal server error"
 * ("... is not currently waiting for action").
 * <p>
 * Esta ação espera, consultando o banco a cada segundo, até o processo
 * ({@code <consignação>_ordermanagement}) estar parado esperando o evento
 * ({@code ..._ConsignmentActionEvent}: condição da tarefa ainda não cumprida), por até
 * {@code flamengo.oms.tarefa.espera.segundos} (0 = comportamento padrão), e só então chama a ação
 * padrão uma única vez. Não chama a padrão antes (04/11: cada tentativa recusada deixava uma
 * condição nova não gravada na sessão, e o fluxo dava "unique constraint" ao concluir).
 * <p>
 * Ligada pelo {@code jobHandler} dos modelos NPR_Automated_Pickup e NPR_Automated_Shipping
 * (docs/flamengo-matchday/08-atendimento-espera.impex); voltar o jobHandler para o bean padrão
 * desliga.
 */
public class WaitingWorkflowActionJob implements AutomatedWorkflowTemplateJob
{
	private static final Logger LOG = LoggerFactory.getLogger(WaitingWorkflowActionJob.class);
	protected static final String WAIT_SECONDS = "flamengo.oms.tarefa.espera.segundos";
	protected static final int DEFAULT_WAIT_SECONDS = 40;
	protected static final long PAUSE_MS = 1000L;
	protected static final String PROCESS_SUFFIX = "_ordermanagement";
	protected static final String EVENT_SUFFIX = "_ConsignmentActionEvent";
	protected static final String WAITING_QUERY = "SELECT {pk} FROM {TaskCondition} WHERE {uniqueID} = ?id "
			+ "AND ({fulfilled} = ?notFulfilled OR {fulfilled} IS NULL) AND {task} IS NOT NULL";

	private AutomatedWorkflowTemplateJob delegate;
	private ConfigurationService configurationService;
	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;

	@Override
	public WorkflowDecisionModel perform(final WorkflowActionModel action)
	{
		final Optional<String> event = eventId(action);
		final int seconds = waitSeconds();
		if (event.isPresent() && seconds > 0)
		{
			waitUntilWaiting(event.get(), seconds, action);
		}
		return getDelegate().perform(action);
	}

	/** Espera o processo chegar à espera do evento; se não chegar no prazo, segue (como o padrão) */
	protected void waitUntilWaiting(final String eventId, final int seconds, final WorkflowActionModel action)
	{
		final long deadline = System.currentTimeMillis() + seconds * 1000L;
		int checks = 0;
		while (!isWaiting(eventId))
		{
			if (System.currentTimeMillis() >= deadline)
			{
				LOG.warn("Processo de {} não ficou pronto em {} s; segue sem esperar", eventId, Integer.valueOf(seconds));
				return;
			}
			checks++;
			pause();
		}
		if (checks > 0)
		{
			LOG.info("Ação {} da tarefa {}: processo pronto depois de {} s", templateCode(action), action.getCode(),
					Integer.valueOf(checks));
		}
	}

	/** O processo está parado esperando o evento (condição da tarefa ainda não cumprida) */
	protected boolean isWaiting(final String eventId)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(WAITING_QUERY);
		query.addQueryParameter("id", eventId);
		query.addQueryParameter("notFulfilled", Boolean.FALSE);
		query.setCount(1);
		query.setDisableCaching(true);
		final List<ItemModel> result = getFlexibleSearchService().<ItemModel> search(query).getResult();
		return !result.isEmpty();
	}

	/** {@code <código da consignação>_ordermanagement_ConsignmentActionEvent}, pela consignação anexada */
	protected Optional<String> eventId(final WorkflowActionModel action)
	{
		final List<ItemModel> items = action.getAttachmentItems();
		if (items == null)
		{
			return Optional.empty();
		}
		for (final ItemModel item : items)
		{
			if ("Consignment".equals(item.getItemtype()))
			{
				final Object code = getModelService().getAttributeValue(item, "code");
				if (code != null && StringUtils.isNotBlank(code.toString()))
				{
					return Optional.of(code + PROCESS_SUFFIX + EVENT_SUFFIX);
				}
			}
		}
		return Optional.empty();
	}

	protected int waitSeconds()
	{
		final int seconds = getConfigurationService() == null ? DEFAULT_WAIT_SECONDS
				: getConfigurationService().getConfiguration().getInt(WAIT_SECONDS, DEFAULT_WAIT_SECONDS);
		return Math.max(0, seconds);
	}

	protected void pause()
	{
		try
		{
			Thread.sleep(PAUSE_MS);
		}
		catch (final InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Espera do processo interrompida", e);
		}
	}

	protected static String templateCode(final WorkflowActionModel action)
	{
		return action.getTemplate() == null ? "?" : action.getTemplate().getCode();
	}

	protected AutomatedWorkflowTemplateJob getDelegate()
	{
		return delegate;
	}

	public void setDelegate(final AutomatedWorkflowTemplateJob delegate)
	{
		this.delegate = delegate;
	}

	protected ConfigurationService getConfigurationService()
	{
		return configurationService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	protected FlexibleSearchService getFlexibleSearchService()
	{
		return flexibleSearchService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	protected ModelService getModelService()
	{
		return modelService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}
}
