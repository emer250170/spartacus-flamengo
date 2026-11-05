package br.com.flamengo.matchday.ticket.impl;

import br.com.flamengo.matchday.service.SeatHoldService;
import br.com.flamengo.matchday.socio.SocioGuestException;
import br.com.flamengo.matchday.socio.SocioGuestService;
import br.com.flamengo.matchday.socio.SocioPlanService;
import br.com.flamengo.matchday.ticket.TicketHolder;
import br.com.flamengo.matchday.ticket.TicketHolderService;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.variants.model.VariantProductModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;


/** Ver {@link TicketHolderService}. */
public class DefaultTicketHolderService implements TicketHolderService
{
	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private ConfigurationService configurationService;
	private SeatHoldService seatHoldService;
	private SocioPlanService socioPlanService;

	@Override
	public List<TicketHolder> holders(final CustomerModel owner, final String cartCode) throws SocioGuestException
	{
		final CartModel cart = cart(owner, cartCode);
		final List<TicketHolder> out = new ArrayList<>();
		final boolean member = isMember(owner, cart);
		for (final List<AbstractOrderEntryModel> seats : seatsByGame(cart).values())
		{
			boolean buyerSeat = false; // a primeira sem dados de outra pessoa fica com o comprador
			for (final AbstractOrderEntryModel entry : seats)
			{
				final TicketHolder h = new TicketHolder();
				h.seatCode = entry.getProduct().getCode();
				h.gameCode = gameCode(entry.getProduct());
				h.name = str(entry, SocioGuestService.ENTRY_HOLDER);
				h.cpf = cpfOf(entry);
				h.birthDate = birthDateOf(entry);
				h.email = emailOf(entry);
				final boolean named = hasData(entry) || attr(entry, SocioGuestService.ENTRY_GUEST) != null;
				h.required = !member && !named && buyerSeat;
				if (!named)
				{
					buyerSeat = true;
				}
				out.add(h);
			}
		}
		return out;
	}

	@Override
	public void setHolder(final CustomerModel owner, final String cartCode, final String seatCode, final String name,
			final String cpf, final Date birthDate, final String email) throws SocioGuestException
	{
		final CartModel cart = cart(owner, cartCode);
		AbstractOrderEntryModel entry = null;
		for (final List<AbstractOrderEntryModel> seats : seatsByGame(cart).values())
		{
			for (final AbstractOrderEntryModel e : seats)
			{
				if (e.getProduct().getCode().equals(seatCode))
				{
					entry = e;
				}
			}
		}
		if (entry == null)
		{
			throw new SocioGuestException("A poltrona " + StringUtils.defaultString(seatCode) + " não está no seu carrinho.");
		}
		if (attr(entry, SocioGuestService.ENTRY_GUEST) != null)
		{
			throw new SocioGuestException("Esta poltrona está marcada para um convidado do sócio.");
		}
		if (StringUtils.isBlank(name))
		{
			// volta para o comprador
			write(entry, null, null, null, null);
			return;
		}
		final String cleanName = StringUtils.normalizeSpace(name);
		if (cleanName.length() < 3 || !cleanName.contains(" "))
		{
			throw new SocioGuestException("Informe o nome completo de quem vai usar o ingresso.");
		}
		final String cpfDigits = SocioGuestService.digits(cpf);
		if (!SocioGuestService.isValidCpf(cpfDigits))
		{
			throw new SocioGuestException("CPF inválido: confira os números.");
		}
		final Calendar oldest = Calendar.getInstance();
		oldest.add(Calendar.YEAR, -120);
		if (birthDate == null || birthDate.after(new Date()) || birthDate.before(oldest.getTime()))
		{
			throw new SocioGuestException("Informe uma data de nascimento válida.");
		}
		final String cleanEmail = StringUtils.trimToEmpty(email).toLowerCase();
		if (!EMAIL.matcher(cleanEmail).matches())
		{
			throw new SocioGuestException("Informe um e-mail válido de quem vai usar o ingresso.");
		}
		final String game = gameCode(entry.getProduct());
		for (final List<AbstractOrderEntryModel> seats : seatsByGame(cart).values())
		{
			for (final AbstractOrderEntryModel other : seats)
			{
				if (other != entry && game.equals(gameCode(other.getProduct())) && cpfDigits.equals(cpfOf(other)))
				{
					throw new SocioGuestException("Este CPF já está em outro ingresso deste jogo.");
				}
			}
		}
		write(entry, cleanName, cpfDigits, birthDate, cleanEmail);
	}

	@Override
	public void checkCart(final CartModel cart) throws SocioGuestException
	{
		if (cart == null || !(cart.getUser() instanceof CustomerModel)
				|| !configurationService.getConfiguration().getBoolean(REQUIRED_PROPERTY, true))
		{
			return;
		}
		if (isMember((CustomerModel) cart.getUser(), cart))
		{
			return; // as poltronas a mais do sócio são dos convidados (SocioGuestService.checkCart)
		}
		for (final List<AbstractOrderEntryModel> seats : seatsByGame(cart).values())
		{
			final long missing = seats.stream()
					.filter(e -> !hasData(e) && attr(e, SocioGuestService.ENTRY_GUEST) == null).count();
			if (missing > 1)
			{
				throw new SocioGuestException("Informe nome, CPF, data de nascimento e e-mail de quem vai usar cada ingresso"
						+ " adicional (falta(m) " + (missing - 1) + "). O primeiro ingresso de cada jogo fica no seu nome.");
			}
		}
	}

	@Override
	public String cpfOf(final AbstractOrderEntryModel entry)
	{
		return StringUtils.trimToNull(str(entry, ENTRY_CPF));
	}

	@Override
	public Date birthDateOf(final AbstractOrderEntryModel entry)
	{
		final Object v = attr(entry, ENTRY_BIRTH_DATE);
		return v instanceof Date ? (Date) v : null;
	}

	@Override
	public String emailOf(final AbstractOrderEntryModel entry)
	{
		return StringUtils.trimToNull(str(entry, ENTRY_EMAIL));
	}

	// ------------------------------------------------------------------------------

	/** Sócio (ou com plano no carrinho): as poltronas a mais seguem a regra dos convidados */
	protected boolean isMember(final CustomerModel owner, final AbstractOrderModel cart)
	{
		if (socioPlanService.memberPlan(owner) != null)
		{
			return true;
		}
		if (cart != null && cart.getEntries() != null)
		{
			for (final AbstractOrderEntryModel e : cart.getEntries())
			{
				if (socioPlanService.findForProduct(e.getProduct()) != null)
				{
					return true;
				}
			}
		}
		return false;
	}

	protected boolean hasData(final AbstractOrderEntryModel entry)
	{
		return cpfOf(entry) != null && StringUtils.isNotBlank(str(entry, SocioGuestService.ENTRY_HOLDER));
	}

	protected void write(final AbstractOrderEntryModel entry, final String name, final String cpf, final Date birthDate,
			final String email)
	{
		modelService.setAttributeValue(entry, SocioGuestService.ENTRY_HOLDER, name);
		modelService.setAttributeValue(entry, ENTRY_CPF, cpf);
		modelService.setAttributeValue(entry, ENTRY_BIRTH_DATE, birthDate);
		modelService.setAttributeValue(entry, ENTRY_EMAIL, email);
		modelService.save(entry);
	}

	/** Poltronas do carrinho agrupadas por jogo, na ordem das linhas */
	protected Map<String, List<AbstractOrderEntryModel>> seatsByGame(final CartModel cart)
	{
		final Map<String, List<AbstractOrderEntryModel>> byGame = new LinkedHashMap<>();
		if (cart == null || cart.getEntries() == null)
		{
			return byGame;
		}
		final List<AbstractOrderEntryModel> entries = new ArrayList<>(cart.getEntries());
		entries.sort(Comparator.comparing(e -> e.getEntryNumber() == null ? Integer.MAX_VALUE : e.getEntryNumber().intValue()));
		for (final AbstractOrderEntryModel e : entries)
		{
			final String game = gameCode(e.getProduct());
			if (game != null)
			{
				byGame.computeIfAbsent(game, k -> new ArrayList<>()).add(e);
			}
		}
		return byGame;
	}

	/** Código do jogo da poltrona (o produto base); null = não é poltrona */
	protected String gameCode(final ProductModel product)
	{
		if (product == null || !seatHoldService.isSeat(product) || !(product instanceof VariantProductModel)
				|| ((VariantProductModel) product).getBaseProduct() == null)
		{
			return null;
		}
		return ((VariantProductModel) product).getBaseProduct().getCode();
	}

	protected CartModel cart(final CustomerModel owner, final String cartCode) throws SocioGuestException
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {Cart} WHERE {code}=?c AND {user}=?u");
		q.addQueryParameter("c", StringUtils.trimToEmpty(cartCode));
		q.addQueryParameter("u", owner);
		final List<CartModel> carts = flexibleSearchService.<CartModel> search(q).getResult();
		if (carts.isEmpty())
		{
			throw new SocioGuestException("Carrinho não encontrado.");
		}
		return carts.get(0);
	}

	protected String str(final Object item, final String qualifier)
	{
		final Object v = attr(item, qualifier);
		return v instanceof String ? (String) v : null;
	}

	protected Object attr(final Object item, final String qualifier)
	{
		try
		{
			return modelService.getAttributeValue(item, qualifier);
		}
		catch (final RuntimeException e)
		{
			return null;
		}
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

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}
}
