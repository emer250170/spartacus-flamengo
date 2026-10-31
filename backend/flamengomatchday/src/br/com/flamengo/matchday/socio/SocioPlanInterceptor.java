package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.enums.SocioPlanStatus;
import br.com.flamengo.matchday.model.SocioPlanModel;
import de.hybris.platform.servicelayer.interceptor.InitDefaultsInterceptor;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;
import de.hybris.platform.servicelayer.interceptor.RemoveInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Liga a tela "Planos" do Backoffice (perspectiva Sócio-Torcedor) à loja.
 * <ul>
 * <li>novo plano: rascunho, programa Nação Maraca, onda 3, sem descontos nem convidados</li>
 * <li>salvar "À venda": cria ou atualiza o produto de assinatura, as promoções (ingresso e loja) e os grupos</li>
 * <li>"Fora da loja" ou "Rascunho" com produto: tira o plano da loja (quem já é sócio continua)</li>
 * <li>apagar: só plano que nunca foi para a loja</li>
 * </ul>
 * Erros chegam ao usuário como mensagem do Backoffice e o plano não é salvo.
 */
public class SocioPlanInterceptor implements InitDefaultsInterceptor<SocioPlanModel>, PrepareInterceptor<SocioPlanModel>,
		RemoveInterceptor<SocioPlanModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(SocioPlanInterceptor.class);
	private static final Locale PT = new Locale("pt");

	/** Campos que mudam o produto, a promoção ou o que a loja mostra */
	private static final List<String> CONTENT = Arrays.asList(SocioPlanModel.NAME, SocioPlanModel.FAMILY, SocioPlanModel.SUMMARY,
			SocioPlanModel.BENEFITS, SocioPlanModel.MONTHLYPRICE, SocioPlanModel.TICKETDISCOUNT, SocioPlanModel.STOREDISCOUNT, SocioPlanModel.WAVE,
			SocioPlanModel.MAXGUESTS, SocioPlanModel.GUESTPRICE, SocioPlanModel.SLOTS, SocioPlanModel.SORTORDER,
			SocioPlanModel.STATUS);

	private SocioPlanService socioPlanService;

	@Override
	public void onInitDefaults(final SocioPlanModel plan, final InterceptorContext ctx) throws InterceptorException
	{
		if (plan.getStatus() == null)
		{
			plan.setStatus(SocioPlanStatus.DRAFT);
		}
		if (plan.getFamily() == null)
		{
			plan.setFamily("Nação Maraca");
		}
		if (plan.getWave() == null)
		{
			plan.setWave(Integer.valueOf(3));
		}
		if (plan.getTicketDiscount() == null)
		{
			plan.setTicketDiscount(Integer.valueOf(0));
		}
		if (plan.getStoreDiscount() == null)
		{
			plan.setStoreDiscount(Integer.valueOf(0));
		}
		if (plan.getMaxGuests() == null)
		{
			plan.setMaxGuests(Integer.valueOf(0));
		}
		if (plan.getGuestPrice() == null)
		{
			plan.setGuestPrice(Double.valueOf(0));
		}
		if (plan.getSortOrder() == null)
		{
			plan.setSortOrder(Integer.valueOf(100));
		}
		if (plan.getDemoBase() == null)
		{
			plan.setDemoBase(Integer.valueOf(0));
		}
	}

	@Override
	public void onPrepare(final SocioPlanModel plan, final InterceptorContext ctx) throws InterceptorException
	{
		final boolean isNew = ctx.isNew(plan);
		validate(plan);
		if (StringUtils.isBlank(plan.getCode()))
		{
			plan.setCode(socioPlanService.generateCode(plan));
		}
		else if (!isNew && plan.getProduct() != null && ctx.isModified(plan, SocioPlanModel.CODE))
		{
			throw new InterceptorException("O código não pode mudar depois que o plano foi para a loja.");
		}
		final boolean changed = isNew || CONTENT.stream().anyMatch(attribute -> ctx.isModified(plan, attribute));
		final boolean concernsStore = plan.getStatus() == SocioPlanStatus.ON_SALE || plan.getProduct() != null;
		if (!changed || !concernsStore)
		{
			if (isNew && !concernsStore)
			{
				plan.setStatusMessage("Rascunho salvo. O plano entra na loja quando a situação for \"À venda\".");
			}
			return;
		}
		try
		{
			plan.setStatusMessage(timestamp() + socioPlanService.publish(plan));
		}
		catch (final RuntimeException e)
		{
			LOG.error("Falha ao publicar o plano " + plan.getCode(), e);
			throw new InterceptorException(StringUtils.defaultIfBlank(e.getMessage(), "Não foi possível publicar o plano."), e);
		}
	}

	@Override
	public void onRemove(final SocioPlanModel plan, final InterceptorContext ctx) throws InterceptorException
	{
		if (plan.getProduct() != null)
		{
			throw new InterceptorException(
					"Este plano já foi para a loja e pode ter sócios. Para parar de vender, mude a situação para \"Fora da loja\".");
		}
	}

	protected void validate(final SocioPlanModel plan) throws InterceptorException
	{
		if (StringUtils.isBlank(plan.getName(PT)))
		{
			throw new InterceptorException("Informe o nome do plano.");
		}
		if (plan.getMonthlyPrice() == null || plan.getMonthlyPrice().doubleValue() <= 0)
		{
			throw new InterceptorException("Informe a mensalidade do plano (maior que zero).");
		}
		final int discount = plan.getTicketDiscount() == null ? 0 : plan.getTicketDiscount().intValue();
		if (discount < 0 || discount > 100)
		{
			throw new InterceptorException("O desconto no ingresso vai de 0 a 100%.");
		}
		final int storeDiscount = plan.getStoreDiscount() == null ? 0 : plan.getStoreDiscount().intValue();
		if (storeDiscount < 0 || storeDiscount > 100)
		{
			throw new InterceptorException("O desconto na loja vai de 0 a 100%.");
		}
		final int wave = plan.getWave() == null ? 0 : plan.getWave().intValue();
		if (wave < 0 || wave > 3)
		{
			throw new InterceptorException("A onda de compra é 1, 2 ou 3 (ou 0 para sem prioridade, só na venda geral).");
		}
		final int guests = plan.getMaxGuests() == null ? 0 : plan.getMaxGuests().intValue();
		if (guests < 0 || guests > 10)
		{
			throw new InterceptorException("Convidados: de 0 a 10.");
		}
		if (guests > 0 && (plan.getGuestPrice() == null || plan.getGuestPrice().doubleValue() <= 0))
		{
			throw new InterceptorException("Informe a mensalidade do convidado (o plano tem convidados).");
		}
		if (plan.getSlots() != null && plan.getSlots().intValue() < 0)
		{
			throw new InterceptorException("Vagas não pode ser negativo (deixe em branco para sem limite).");
		}
	}

	protected String timestamp()
	{
		final java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", PT);
		format.setTimeZone(java.util.TimeZone.getTimeZone("America/Sao_Paulo"));
		return format.format(new java.util.Date()) + " — ";
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}
}
