package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.SocioExperienceModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Experiência salva no Backoffice (ou por ImpEx): com preço, vira ou atualiza o produto da
 * loja; sem preço ou inativa, sai da loja (item 9). A quantidade vira o estoque do produto.
 * Erros chegam ao gestor como mensagem do Backoffice e a experiência não é salva.
 */
public class SocioExperienceInterceptor implements PrepareInterceptor<SocioExperienceModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(SocioExperienceInterceptor.class);

	/** Campos que mudam o produto ou o estoque */
	private static final List<String> CONTENT = Arrays.asList(SocioExperienceModel.NAME, SocioExperienceModel.DESCRIPTION,
			SocioExperienceModel.POINTS, SocioExperienceModel.STOCK, SocioExperienceModel.ACTIVE, SocioExperienceModel.PRICE);

	private SocioExperienceService socioExperienceService;

	@Override
	public void onPrepare(final SocioExperienceModel experience, final InterceptorContext ctx) throws InterceptorException
	{
		if (experience.getPrice() != null && experience.getPrice().doubleValue() < 0)
		{
			throw new InterceptorException("O preço não pode ser negativo. Deixe vazio para só resgate com pontos.");
		}
		if (experience.getStock() != null && experience.getStock().intValue() < 0)
		{
			throw new InterceptorException("A quantidade não pode ser negativa. Deixe vazio para sem limite.");
		}
		if (!ctx.isNew(experience) && experience.getProduct() != null && ctx.isModified(experience, SocioExperienceModel.CODE))
		{
			throw new InterceptorException("O código não pode mudar depois que a experiência foi para a loja.");
		}
		final boolean changed = ctx.isNew(experience)
				|| CONTENT.stream().anyMatch(attribute -> ctx.isModified(experience, attribute));
		if (!changed)
		{
			return;
		}
		try
		{
			experience.setStatusMessage(timestamp() + socioExperienceService.publish(experience));
		}
		catch (final RuntimeException e)
		{
			LOG.error("Falha ao publicar a experiência " + experience.getCode(), e);
			throw new InterceptorException(e.getMessage() == null ? "Não foi possível publicar a experiência na loja." : e.getMessage(),
					e);
		}
	}

	protected static String timestamp()
	{
		final SimpleDateFormat format = new SimpleDateFormat("dd/MM HH:mm", new Locale("pt", "BR"));
		format.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
		return format.format(new Date()) + " · ";
	}

	public void setSocioExperienceService(final SocioExperienceService socioExperienceService)
	{
		this.socioExperienceService = socioExperienceService;
	}
}
