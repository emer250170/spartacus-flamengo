package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.model.SocioEventModel;
import br.com.flamengo.matchday.model.SocioExperienceModel;
import br.com.flamengo.matchday.model.SocioRedemptionModel;
import de.hybris.platform.ordersplitting.model.StockLevelModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PersistenceOperation;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Voucher de experiência alterado (no serviço ou direto no Backoffice):
 * <ul>
 * <li>"Cancelado em" preenchido: a vaga volta ao estoque do produto (e, no resgate, os pontos
 * voltam, porque o saldo é calculado) e entra o evento "Resgate cancelado";</li>
 * <li>"Cancelado em" apagado: a vaga é ocupada de novo;</li>
 * <li>"Utilizado em" preenchido: entra o evento "Voucher utilizado".</li>
 * </ul>
 * Quem fez fica em cancelledBy/usedBy (o usuário logado, se não veio preenchido).
 */
public class SocioRedemptionInterceptor implements PrepareInterceptor<SocioRedemptionModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(SocioRedemptionInterceptor.class);
	private static final Locale PT = new Locale("pt");

	private SocioExperienceService socioExperienceService;
	private SocioPlanService socioPlanService;
	private UserService userService;

	@Override
	public void onPrepare(final SocioRedemptionModel r, final InterceptorContext ctx) throws InterceptorException
	{
		if (ctx.isNew(r))
		{
			return;
		}
		if (ctx.isModified(r, SocioRedemptionModel.CANCELLEDAT))
		{
			final boolean cancelled = r.getCancelledAt() != null;
			if (cancelled && r.getUsedAt() != null)
			{
				throw new InterceptorException("Este voucher já foi utilizado e não pode ser cancelado.");
			}
			if (cancelled && StringUtils.isBlank(r.getCancelledBy()))
			{
				r.setCancelledBy(currentUid());
			}
			adjustStock(r.getExperience(), cancelled ? 1 : -1, ctx);
			event(r, cancelled ? SocioEventType.RESGATE_CANCELADO : SocioEventType.OBSERVACAO,
					cancelled ? cancelNote(r) : "Voucher " + r.getVoucher() + " reativado", ctx);
		}
		if (ctx.isModified(r, SocioRedemptionModel.USEDAT) && r.getUsedAt() != null)
		{
			if (r.getCancelledAt() != null)
			{
				throw new InterceptorException("Este voucher está cancelado e não pode ser utilizado.");
			}
			if (StringUtils.isBlank(r.getUsedBy()))
			{
				r.setUsedBy(currentUid());
			}
			event(r, SocioEventType.VOUCHER_UTILIZADO, experienceName(r) + ": voucher " + r.getVoucher() + " utilizado"
					+ (StringUtils.isBlank(r.getUsedBy()) ? "" : " (validado por " + r.getUsedBy() + ")"), ctx);
		}
	}

	protected void adjustStock(final SocioExperienceModel experience, final int delta, final InterceptorContext ctx)
	{
		try
		{
			final StockLevelModel level = socioExperienceService.adjustStock(experience, delta);
			if (level != null)
			{
				ctx.registerElementFor(level, PersistenceOperation.SAVE);
			}
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Não ajustou o estoque da experiência {}: {}", experience == null ? null : experience.getCode(), e.getMessage());
		}
	}

	/** Linha do tempo do torcedor (falha aqui não desfaz a mudança do voucher) */
	protected void event(final SocioRedemptionModel r, final SocioEventType type, final String note, final InterceptorContext ctx)
	{
		try
		{
			final SocioEventModel event = socioPlanService.newEvent(r.getCustomer(), type,
					SocioExperienceService.ORIGIN_PURCHASE.equals(r.getOrigin()) ? "Loja" : StringUtils.defaultIfBlank(r.getOrigin(), "Backoffice"),
					note);
			event.setDate(new Date());
			if (type == SocioEventType.RESGATE_CANCELADO)
			{
				event.setResult(r.getPoints() != null && r.getPoints().intValue() > 0 ? r.getPoints() + " pontos e a vaga voltaram"
						: "A vaga voltou; reembolso pelo atendimento");
			}
			ctx.registerElementFor(event, PersistenceOperation.SAVE);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Voucher {}: não gravou o evento {}: {}", r.getVoucher(), type, e.getMessage());
		}
	}

	protected String cancelNote(final SocioRedemptionModel r)
	{
		final StringBuilder note = new StringBuilder(experienceName(r)).append(": voucher ").append(r.getVoucher()).append(" cancelado");
		if (StringUtils.isNotBlank(r.getOrderCode()))
		{
			note.append(" (compra no pedido ").append(r.getOrderCode()).append(")");
		}
		if (StringUtils.isNotBlank(r.getCancelReason()))
		{
			note.append(". Motivo: ").append(r.getCancelReason().trim());
		}
		return note.toString();
	}

	protected static String experienceName(final SocioRedemptionModel r)
	{
		final SocioExperienceModel e = r.getExperience();
		return e == null ? "Experiência" : StringUtils.defaultIfBlank(e.getName(PT), e.getCode());
	}

	protected String currentUid()
	{
		return userService.getCurrentUser() == null ? null : userService.getCurrentUser().getUid();
	}

	public void setSocioExperienceService(final SocioExperienceService socioExperienceService)
	{
		this.socioExperienceService = socioExperienceService;
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
