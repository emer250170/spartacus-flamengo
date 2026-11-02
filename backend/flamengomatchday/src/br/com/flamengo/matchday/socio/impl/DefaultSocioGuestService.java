package br.com.flamengo.matchday.socio.impl;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.model.SocioEventModel;
import br.com.flamengo.matchday.model.SocioGuestModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.socio.SocioGuestException;
import br.com.flamengo.matchday.socio.SocioGuestOverview;
import br.com.flamengo.matchday.socio.SocioGuestService;
import br.com.flamengo.matchday.socio.SocioPlanService;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/** Regras do cadastro de convidados ({@link SocioGuestService}). */
public class DefaultSocioGuestService implements SocioGuestService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSocioGuestService.class);
	private static final Locale PT = new Locale("pt");
	private static final String GUEST_PREFIX = "NACAO-CONVIDADO-";
	private static final int MAX_ENDED = 10;
	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private SocioPlanService socioPlanService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private ConfigurationService configurationService;

	@Override
	public boolean isEnabled()
	{
		return configurationService.getConfiguration().getBoolean(ENABLED_PROPERTY, true);
	}

	// ------------------------------------------------------------ leitura

	@Override
	public SocioGuestOverview overview(final CustomerModel owner)
	{
		final SocioGuestOverview o = new SocioGuestOverview();
		o.enabled = isEnabled();
		final SocioPlanModel plan = socioPlanService.memberPlan(owner);
		if (plan != null)
		{
			o.planCode = plan.getCode();
			o.planName = StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode());
			o.planMaxGuests = plan.getMaxGuests() == null ? 0 : plan.getMaxGuests().intValue();
			o.guestPrice = plan.getGuestPrice() == null ? 0 : plan.getGuestPrice().doubleValue();
			o.suspended = socioPlanService.isSuspended(owner);
			o.contracted = contractedSlots(owner);
		}
		for (final SocioGuestModel guest : guests(owner, false))
		{
			if (Boolean.TRUE.equals(guest.getActive()))
			{
				o.active.add(guest);
			}
			else if (o.ended.size() < MAX_ENDED)
			{
				o.ended.add(guest);
			}
		}
		o.reason = refusal(o);
		o.canAdd = o.reason == null;
		return o;
	}

	/** Por que o titular não pode cadastrar mais um convidado (null = pode) */
	protected String refusal(final SocioGuestOverview o)
	{
		if (!o.enabled)
		{
			return "O cadastro de convidados está desligado.";
		}
		if (o.planCode == null)
		{
			return "Só sócios do Nação têm convidados.";
		}
		if (o.suspended)
		{
			return "Seus benefícios estão suspensos por mensalidade em atraso; regularize para incluir convidados.";
		}
		if (o.contracted <= 0)
		{
			return o.planMaxGuests > 0
					? "Seu plano não tem vaga de convidado contratada. Contrate até " + o.planMaxGuests
							+ " convidado(s) mudando o plano em Meu sócio-torcedor."
					: "O plano " + o.planName + " não tem convidados.";
		}
		if (o.active.size() >= o.contracted)
		{
			return "Todas as " + o.contracted + " vaga(s) de convidado contratadas estão ocupadas. Tire um convidado para incluir outro.";
		}
		return null;
	}

	@Override
	public List<SocioGuestModel> guests(final CustomerModel owner, final boolean onlyActive)
	{
		if (owner == null)
		{
			return Collections.emptyList();
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {SocioGuest} WHERE {customer}=?o"
				+ (onlyActive ? " AND {active}=?t" : "") + " ORDER BY {since} DESC, {creationtime} DESC");
		q.addQueryParameter("o", owner);
		if (onlyActive)
		{
			q.addQueryParameter("t", Boolean.TRUE);
		}
		return flexibleSearchService.<SocioGuestModel> search(q).getResult();
	}

	@Override
	public int contractedSlots(final CustomerModel owner)
	{
		if (owner == null || socioPlanService.memberPlan(owner) == null)
		{
			return 0;
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {user}=?c AND {versionID} IS NULL ORDER BY {date} DESC");
		q.addQueryParameter("c", owner);
		for (final OrderModel order : flexibleSearchService.<OrderModel> search(q).getResult())
		{
			final List<AbstractOrderEntryModel> entries = order.getEntries() == null ? Collections.emptyList()
					: order.getEntries();
			final boolean planOrder = entries.stream()
					.anyMatch(e -> socioPlanService.findForProduct(e.getProduct()) != null || isGuestProduct(e.getProduct()));
			if (planOrder)
			{
				// o último pedido do plano manda (mudança de plano refaz a escolha de convidados)
				return entries.stream().filter(e -> isGuestProduct(e.getProduct()))
						.mapToInt(e -> e.getQuantity() == null ? 0 : e.getQuantity().intValue()).sum();
			}
		}
		return 0;
	}

	protected static boolean isGuestProduct(final ProductModel product)
	{
		return product != null && product.getCode() != null && product.getCode().startsWith(GUEST_PREFIX);
	}

	// ------------------------------------------------------------ escrita

	@Override
	public SocioGuestModel add(final CustomerModel owner, final String name, final String cpf, final String email,
			final String phone, final Date birthDate) throws SocioGuestException
	{
		final SocioGuestOverview o = overview(owner);
		if (!o.canAdd)
		{
			throw new SocioGuestException(o.reason);
		}
		final String cleanName = StringUtils.normalizeSpace(name);
		if (StringUtils.length(cleanName) < 3 || !cleanName.contains(" "))
		{
			throw new SocioGuestException("Informe o nome completo do convidado.");
		}
		final String cpfDigits = SocioGuestService.digits(cpf);
		if (!SocioGuestService.isValidCpf(cpfDigits))
		{
			throw new SocioGuestException("CPF inválido: confira os números.");
		}
		final String cleanEmail = StringUtils.trimToEmpty(email).toLowerCase(Locale.ROOT);
		if (!EMAIL.matcher(cleanEmail).matches())
		{
			throw new SocioGuestException("Informe um e-mail válido do convidado.");
		}
		final String phoneDigits = SocioGuestService.digits(phone);
		if (!phoneDigits.isEmpty() && (phoneDigits.length() < 10 || phoneDigits.length() > 11))
		{
			throw new SocioGuestException("Celular inválido: use DDD e número, com 10 ou 11 dígitos.");
		}
		checkBirthDate(birthDate);
		checkDuplicate(owner, cpfDigits);

		final SocioGuestModel guest = modelService.create(SocioGuestModel.class);
		guest.setCustomer(owner);
		guest.setName(cleanName);
		guest.setCpf(cpfDigits);
		guest.setEmail(cleanEmail);
		guest.setPhone(phoneDigits.isEmpty() ? null : phoneDigits);
		guest.setBirthDate(birthDate);
		guest.setPlan(socioPlanService.memberPlan(owner));
		guest.setActive(Boolean.TRUE);
		guest.setSince(new Date());
		modelService.save(guest);
		note(owner, "Convidado incluído: " + cleanName + " (CPF " + SocioGuestService.maskCpf(cpfDigits) + ")",
				(o.active.size() + 1) + " de " + o.contracted + " vaga(s) de convidado ocupada(s)");
		return guest;
	}

	protected void checkBirthDate(final Date birthDate) throws SocioGuestException
	{
		if (birthDate == null)
		{
			throw new SocioGuestException("Informe a data de nascimento do convidado.");
		}
		final Calendar oldest = Calendar.getInstance();
		oldest.add(Calendar.YEAR, -120);
		if (birthDate.after(new Date()) || birthDate.before(oldest.getTime()))
		{
			throw new SocioGuestException("Data de nascimento inválida.");
		}
	}

	protected void checkDuplicate(final CustomerModel owner, final String cpfDigits) throws SocioGuestException
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {SocioGuest} WHERE {cpf}=?cpf AND {active}=?t");
		q.addQueryParameter("cpf", cpfDigits);
		q.addQueryParameter("t", Boolean.TRUE);
		final List<SocioGuestModel> same = flexibleSearchService.<SocioGuestModel> search(q).getResult();
		if (!same.isEmpty())
		{
			throw new SocioGuestException(same.stream().anyMatch(g -> owner.equals(g.getCustomer()))
					? "Este CPF já é seu convidado." : "Este CPF já é convidado de outro sócio.");
		}
	}

	@Override
	public void remove(final CustomerModel owner, final String guestId) throws SocioGuestException
	{
		final SocioGuestModel guest = find(owner, guestId);
		if (guest == null)
		{
			throw new SocioGuestException("Convidado não encontrado.");
		}
		if (!Boolean.TRUE.equals(guest.getActive()))
		{
			throw new SocioGuestException("Este convidado já foi retirado.");
		}
		checkNoFutureTicket(guest);
		end(guest, END_BY_OWNER);
		modelService.save(guest);
		note(owner, "Convidado retirado: " + guest.getName() + " (CPF " + SocioGuestService.maskCpf(guest.getCpf()) + ")",
				"Vaga de convidado liberada");
	}

	/**
	 * Convidado com ingresso de jogo futuro não pode sair (o nome dele está no
	 * ingresso). A compra para o convidado entra no PR 3 do item; até lá não há
	 * ingresso com convidado e nada é recusado aqui.
	 */
	protected void checkNoFutureTicket(final SocioGuestModel guest) throws SocioGuestException
	{
		// PR 3 (compra para o convidado): recusar quando houver ingresso de jogo futuro no nome dele
	}

	protected SocioGuestModel find(final CustomerModel owner, final String guestId)
	{
		if (owner == null || StringUtils.isBlank(guestId) || !StringUtils.isNumeric(guestId))
		{
			return null;
		}
		try
		{
			final Object item = modelService.get(PK.parse(guestId));
			return item instanceof SocioGuestModel && owner.equals(((SocioGuestModel) item).getCustomer())
					? (SocioGuestModel) item : null;
		}
		catch (final Exception e)
		{
			return null;
		}
	}

	@Override
	public List<SocioGuestModel> endAll(final CustomerModel owner, final String reason)
	{
		final List<SocioGuestModel> out = new ArrayList<>();
		for (final SocioGuestModel guest : guests(owner, true))
		{
			end(guest, reason);
			out.add(guest);
		}
		return out;
	}

	protected void end(final SocioGuestModel guest, final String reason)
	{
		guest.setActive(Boolean.FALSE);
		guest.setUntil(new Date());
		guest.setEndReason(reason);
	}

	/** Observação na linha do tempo do sócio; falha aqui não desfaz o cadastro */
	protected void note(final CustomerModel owner, final String note, final String result)
	{
		try
		{
			final SocioEventModel event = socioPlanService.newEvent(owner, SocioEventType.OBSERVACAO, "Loja", note);
			event.setResult(result);
			modelService.save(event);
		}
		catch (final Exception e)
		{
			LOG.warn("Convidados de {}: não gravou o evento ({}): {}", owner.getUid(), note, e.getMessage());
		}
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
