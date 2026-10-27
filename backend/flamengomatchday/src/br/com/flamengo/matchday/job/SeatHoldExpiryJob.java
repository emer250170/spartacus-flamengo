package br.com.flamengo.matchday.job;

import br.com.flamengo.matchday.service.SeatHoldService;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.cronjob.AbstractJobPerformable;
import de.hybris.platform.servicelayer.cronjob.PerformResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Libera as poltronas cuja reserva venceu (carrinho abandonado).
 */
public class SeatHoldExpiryJob extends AbstractJobPerformable<CronJobModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(SeatHoldExpiryJob.class);

	private SeatHoldService seatHoldService;

	@Override
	public PerformResult perform(final CronJobModel cronJob)
	{
		final int released = seatHoldService.releaseExpired();
		if (released > 0)
		{
			LOG.info("{} poltrona(s) com reserva vencida liberada(s)", released);
		}
		return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
	}

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}
}
