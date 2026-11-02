package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.model.SocioContractModel;
import br.com.flamengo.matchday.model.SocioEventModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PersistenceOperation;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Evento novo na linha do tempo do sócio-torcedor.
 * <p>
 * Completa data, autor, origem e plano. Suspensão e Regularização mudam o
 * torcedor: entram ou saem do grupo {@link SocioPlanService#SUSPENDED_GROUP}, que
 * tira a onda de sócio e os descontos (as promoções excluem esse grupo) sem tirar
 * o vínculo nem a vaga. Os carrinhos do torcedor são recalculados logo depois.
 * <p>
 * O gestor cria o evento no Backoffice (Sócio-Torcedor › Eventos do sócio) ou
 * pelo Claude; a loja cria a Regularização quando o torcedor cadastra um cartão
 * novo ({@link SocioCardRegularizationInterceptor}).
 * <p>
 * Vigência (item 6): a Adesão do pedido recomeça o contrato gravado
 * ({@link SocioContractService#onJoin}); um evento Renovação criado sem resultado
 * (Backoffice ou Claude) vira a vigência ({@link SocioContractService#turn}).
 */
public class SocioEventInterceptor implements PrepareInterceptor<SocioEventModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(SocioEventInterceptor.class);

	private SocioPlanService socioPlanService;
	private SocioCartService socioCartService;
	private UserService userService;
	private SocioContractService socioContractService;

	@Override
	public void onPrepare(final SocioEventModel event, final InterceptorContext ctx) throws InterceptorException
	{
		if (!ctx.isNew(event))
		{
			return;
		}
		final CustomerModel customer = event.getCustomer();
		if (event.getDate() == null)
		{
			event.setDate(new Date());
		}
		if (StringUtils.isBlank(event.getAuthor()))
		{
			final UserModel current = userService.getCurrentUser();
			event.setAuthor(current == null ? null : current.getUid());
		}
		if (StringUtils.isBlank(event.getOrigin()))
		{
			event.setOrigin("Backoffice");
		}
		if (event.getPlan() == null && customer != null)
		{
			event.setPlan(socioPlanService.memberPlan(customer));
		}
		if (event.getType() == SocioEventType.ADESAO && customer != null && socioContractService != null
				&& socioContractService.isEnabled())
		{
			// adesão ou upgrade: o contrato gravado recomeça no plano comprado
			final SocioContractModel contract = socioContractService.onJoin(event);
			if (contract != null)
			{
				ctx.registerElementFor(contract, PersistenceOperation.SAVE);
			}
		}
		if (StringUtils.isNotBlank(event.getResult()) || customer == null)
		{
			return; // já aplicado por quem criou o evento
		}
		if (event.getType() == SocioEventType.SUSPENSAO || event.getType() == SocioEventType.REGULARIZACAO)
		{
			apply(event, ctx);
		}
		else if ((event.getType() == SocioEventType.RENOVACAO || event.getType() == SocioEventType.FIM_VIGENCIA)
				&& socioContractService != null)
		{
			try
			{
				for (final ItemModel item : socioContractService.turn(customer, event))
				{
					ctx.registerElementFor(item, PersistenceOperation.SAVE);
				}
			}
			catch (final SocioContractException e)
			{
				throw new InterceptorException(e.getMessage());
			}
		}
		else
		{
			event.setResult("Registro na linha do tempo");
		}
	}

	/**
	 * Aplica Suspensão ou Regularização ao torcedor do evento e preenche o
	 * resultado. O cliente é salvo junto com o evento.
	 */
	public void apply(final SocioEventModel event, final InterceptorContext ctx) throws InterceptorException
	{
		final CustomerModel customer = event.getCustomer();
		final boolean suspend = event.getType() == SocioEventType.SUSPENSAO;
		if (suspend)
		{
			if (!socioPlanService.isSuspensionEnabled())
			{
				throw new InterceptorException("A suspensão por inadimplência está desligada ("
						+ SocioPlanService.SUSPENSION_PROPERTY + "=false).");
			}
			final SocioPlanModel plan = socioPlanService.memberPlan(customer);
			if (plan == null)
			{
				throw new InterceptorException(customer.getUid() + " não é sócio: não há benefícios para suspender.");
			}
		}
		if (!socioPlanService.setSuspended(customer, suspend))
		{
			event.setResult(suspend ? "Já estava com os benefícios suspensos" : "Não estava suspenso");
			return;
		}
		ctx.registerElementFor(customer, PersistenceOperation.SAVE);
		event.setResult(suspend ? "Benefícios suspensos: sem onda de sócio e sem descontos até a regularização"
				: "Benefícios de volta: onda de sócio e descontos");
		LOG.info("Sócio {}: {} ({}, {})", customer.getUid(), suspend ? "benefícios suspensos" : "benefícios de volta",
				event.getOrigin(), event.getAuthor());
		socioCartService.refreshCartsLater(customer, suspend);
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}

	public void setSocioCartService(final SocioCartService socioCartService)
	{
		this.socioCartService = socioCartService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setSocioContractService(final SocioContractService socioContractService)
	{
		this.socioContractService = socioContractService;
	}
}
