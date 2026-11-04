package br.com.flamengo.matchday.fulfillment;

import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.workflow.jobs.AutomatedWorkflowTemplateJob;
import de.hybris.platform.workflow.model.WorkflowActionModel;
import de.hybris.platform.workflow.model.WorkflowDecisionModel;

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
 * ("... is not currently waiting for action"). Esta ação chama a padrão e, nesse caso, tenta de
 * novo a cada segundo até {@code flamengo.oms.tarefa.espera.segundos} (0 = comportamento padrão,
 * sem espera). Outros erros sobem como antes.
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

	private AutomatedWorkflowTemplateJob delegate;
	private ConfigurationService configurationService;

	@Override
	public WorkflowDecisionModel perform(final WorkflowActionModel action)
	{
		final long deadline = System.currentTimeMillis() + waitSeconds() * 1000L;
		int attempt = 0;
		while (true)
		{
			try
			{
				final WorkflowDecisionModel decision = getDelegate().perform(action);
				if (attempt > 0)
				{
					LOG.info("Ação {} da tarefa {} concluída depois de {} tentativa(s)", templateCode(action),
							action.getCode(), Integer.valueOf(attempt + 1));
				}
				return decision;
			}
			catch (final RuntimeException e)
			{
				if (!processNotReady(e) || System.currentTimeMillis() >= deadline)
				{
					throw e;
				}
				attempt++;
				LOG.debug("Processo ainda não está pronto para {} ({}); nova tentativa", templateCode(action), e.getMessage());
				pause();
			}
		}
	}

	/** O processo da consignação ainda não chegou à espera do evento (corrida com o motor de tarefas) */
	protected boolean processNotReady(final Throwable error)
	{
		for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause())
		{
			if ("BusinessProcessException".equals(t.getClass().getSimpleName())
					&& StringUtils.contains(t.getMessage(), "not currently waiting"))
			{
				return true;
			}
		}
		return false;
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
}
