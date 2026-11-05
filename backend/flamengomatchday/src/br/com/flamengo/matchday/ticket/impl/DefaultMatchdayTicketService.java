package br.com.flamengo.matchday.ticket.impl;

import br.com.flamengo.matchday.enums.MatchdayTicketEventType;
import br.com.flamengo.matchday.enums.MatchdayTicketStatus;
import br.com.flamengo.matchday.model.MatchdayGameModel;
import br.com.flamengo.matchday.model.MatchdayTicketEventModel;
import br.com.flamengo.matchday.model.MatchdayTicketModel;
import br.com.flamengo.matchday.model.SeatHoldModel;
import br.com.flamengo.matchday.model.SocioGuestModel;
import br.com.flamengo.matchday.service.SeatHoldService;
import br.com.flamengo.matchday.socio.SocioGuestService;
import br.com.flamengo.matchday.socio.SocioPayments;
import br.com.flamengo.matchday.ticket.MatchdayTicketException;
import br.com.flamengo.matchday.ticket.MatchdayTicketService;
import br.com.flamengo.matchday.ticket.TicketHolderService;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.ordersplitting.model.StockLevelModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.PersistenceOperation;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.variants.model.VariantProductModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DefaultMatchdayTicketService implements MatchdayTicketService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultMatchdayTicketService.class);
	private static final Locale PT = new Locale("pt");
	private static final String SEP = " · ";
	/** Comando em execução nesta thread (ação pedida por um evento salvo por fora) */
	private static final ThreadLocal<Command> COMMAND = new ThreadLocal<>();
	/** Evento gravado pelo próprio serviço (o interceptor não trata como comando) */
	private static final ThreadLocal<Boolean> INTERNAL = new ThreadLocal<>();

	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private SeatHoldService seatHoldService;
	private SocioGuestService socioGuestService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;
	private ConfigurationService configurationService;
	private UserService userService;

	@Override
	public boolean isEnabled()
	{
		return configurationService.getConfiguration().getBoolean(ENABLED_PROPERTY, false);
	}

	// ------------------------------------------------------------ criação

	@Override
	public List<MatchdayTicketModel> createForOrder(final AbstractOrderModel order)
	{
		return unrestricted(() -> create(order, "Loja"));
	}

	protected List<MatchdayTicketModel> create(final AbstractOrderModel order, final String author)
	{
		final List<MatchdayTicketModel> created = new ArrayList<>();
		if (order == null || order.getEntries() == null || order.getCode() == null)
		{
			return created;
		}
		final FlexibleSearchQuery existing = new FlexibleSearchQuery("SELECT {pk} FROM {MatchdayTicket} WHERE {orderCode}=?o");
		existing.addQueryParameter("o", order.getCode());
		if (!flexibleSearchService.search(existing).getResult().isEmpty())
		{
			return created;
		}
		// pedido com plano em Pix/boleto ainda não pago: os ingressos esperam a confirmação (04/11)
		final boolean awaiting = configurationService.getConfiguration().getBoolean(AWAIT_PAYMENT_PROPERTY, true)
				&& SocioPayments.isAwaiting(modelService, order);
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final ProductModel seat = entry.getProduct();
			if (seat == null || !seatHoldService.isSeat(seat))
			{
				continue;
			}
			final MatchdayTicketModel t = modelService.create(MatchdayTicketModel.class);
			t.setCode(order.getCode() + "-" + entry.getEntryNumber());
			t.setGame(gameOf(seat));
			t.setSeat(seat);
			t.setSector(sectorOf(seat, t.getGame()));
			t.setSeatLabel(labelOf(seat));
			t.setOrderCode(order.getCode());
			t.setEntryNumber(entry.getEntryNumber());
			t.setCustomer(order.getUser() instanceof CustomerModel ? (CustomerModel) order.getUser() : null);
			final Object guest = attr(entry, SocioGuestService.ENTRY_GUEST);
			t.setGuest(guest instanceof SocioGuestModel ? (SocioGuestModel) guest : null);
			final Object holder = attr(entry, SocioGuestService.ENTRY_HOLDER);
			t.setHolder(holder instanceof String && StringUtils.isNotBlank((String) holder) ? (String) holder : buyerName(t));
			holderData(t, entry);
			final double paid = entry.getTotalPrice() == null ? 0 : entry.getTotalPrice().doubleValue();
			final double list = (entry.getBasePrice() == null ? 0 : entry.getBasePrice().doubleValue())
					* (entry.getQuantity() == null ? 1 : entry.getQuantity().longValue());
			t.setPrice(Double.valueOf(paid));
			t.setMemberDiscount(list - paid > 0.004 ? Double.valueOf(Math.round((list - paid) * 100) / 100.0) : null);
			t.setStatus(awaiting ? MatchdayTicketStatus.AGUARDANDO_PAGAMENTO : MatchdayTicketStatus.VALIDO);
			t.setSoldAt(order.getDate() != null ? order.getDate() : new Date());
			save(t);
			event(t, MatchdayTicketEventType.VENDA, author, null, t.getSeatLabel(), t.getPrice(),
					"Pedido " + order.getCode() + (t.getGuest() != null ? " · convidado " + t.getHolder() : "")
							+ (awaiting ? " · aguardando o pagamento do Pix/boleto" : ""));
			created.add(t);
		}
		if (!created.isEmpty())
		{
			LOG.info("Pedido {}: {} ingresso(s) registrado(s)", order.getCode(), created.size());
		}
		return created;
	}

	@Override
	public int releaseForOrder(final AbstractOrderModel order, final String author)
	{
		if (order == null || order.getCode() == null)
		{
			return 0;
		}
		return unrestricted(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery(
					"SELECT {pk} FROM {MatchdayTicket} WHERE {orderCode}=?o AND {status}=?s");
			q.addQueryParameter("o", order.getCode());
			q.addQueryParameter("s", MatchdayTicketStatus.AGUARDANDO_PAGAMENTO);
			final List<MatchdayTicketModel> waiting = flexibleSearchService.<MatchdayTicketModel> search(q).getResult();
			for (final MatchdayTicketModel t : waiting)
			{
				t.setStatus(MatchdayTicketStatus.VALIDO);
				save(t);
				event(t, MatchdayTicketEventType.OBSERVACAO, author, null, null, null,
						"Pagamento confirmado: ingresso liberado");
			}
			if (!waiting.isEmpty())
			{
				LOG.info("Pedido {}: {} ingresso(s) liberado(s) com o pagamento", order.getCode(), waiting.size());
			}
			return waiting.size();
		});
	}

	@Override
	public int backfill()
	{
		return unrestricted(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery(
					"SELECT DISTINCT {o.pk}, {o.date} FROM {Order AS o JOIN OrderEntry AS e ON {e.order}={o.pk} JOIN Product AS p ON {e.product}={p.pk}}"
							+ " WHERE {p.code} LIKE 'JOGO-%' AND {o.versionID} IS NULL ORDER BY {o.date}");
			int count = 0;
			for (final OrderModel order : flexibleSearchService.<OrderModel> search(q).getResult())
			{
				if (order.getStatus() != OrderStatus.CANCELLED)
				{
					count += create(order, "Sistema (pedido anterior ao item 10)").size();
				}
			}
			return Integer.valueOf(count);
		}).intValue();
	}

	// ------------------------------------------------------------ consultas

	@Override
	public List<MatchdayTicketModel> search(final String gameCode, final String sector, final String status, final String text)
	{
		return unrestricted(() -> {
			final List<MatchdayTicketModel> all = flexibleSearchService.<MatchdayTicketModel> search(
					new FlexibleSearchQuery("SELECT {pk} FROM {MatchdayTicket} ORDER BY {soldAt} DESC, {code}")).getResult();
			final String needle = StringUtils.trimToEmpty(text).toLowerCase(Locale.ROOT);
			final List<MatchdayTicketModel> result = new ArrayList<>();
			for (final MatchdayTicketModel t : all)
			{
				if (StringUtils.isNotBlank(gameCode) && (t.getGame() == null || !gameCode.equals(t.getGame().getCode()))
						|| StringUtils.isNotBlank(sector) && !sector.equals(t.getSector())
						|| StringUtils.isNotBlank(status) && (t.getStatus() == null || !status.equals(t.getStatus().getCode())))
				{
					continue;
				}
				if (!needle.isEmpty())
				{
					final String hay = (t.getCode() + " " + StringUtils.defaultString(t.getOrderCode()) + " "
							+ StringUtils.defaultString(t.getHolder()) + " " + StringUtils.defaultString(t.getSeatLabel()) + " "
							+ (t.getCustomer() == null ? ""
									: t.getCustomer().getUid() + " " + StringUtils.defaultString(t.getCustomer().getName())))
							.toLowerCase(Locale.ROOT);
					if (!hay.contains(needle))
					{
						continue;
					}
				}
				result.add(t);
			}
			return result;
		});
	}

	@Override
	public MatchdayTicketModel find(final String code)
	{
		if (StringUtils.isBlank(code))
		{
			return null;
		}
		return unrestricted(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {MatchdayTicket} WHERE {code}=?c");
			q.addQueryParameter("c", code.trim());
			final List<MatchdayTicketModel> r = flexibleSearchService.<MatchdayTicketModel> search(q).getResult();
			return r.isEmpty() ? null : r.get(0);
		});
	}

	@Override
	public List<MatchdayTicketEventModel> history(final MatchdayTicketModel ticket)
	{
		return unrestricted(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery(
					"SELECT {pk} FROM {MatchdayTicketEvent} WHERE {ticket}=?t ORDER BY {date} DESC, {creationtime} DESC");
			q.addQueryParameter("t", ticket);
			return flexibleSearchService.<MatchdayTicketEventModel> search(q).getResult();
		});
	}

	@Override
	public List<ProductModel> freeSeats(final MatchdayTicketModel ticket)
	{
		if (ticket == null || ticket.getGame() == null || StringUtils.isBlank(ticket.getSector()))
		{
			return List.of();
		}
		return unrestricted(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {s.pk} FROM {StockLevel AS s} WHERE {s.productCode} LIKE ?p"
					+ " AND {s.available} - {s.reserved} > 0 ORDER BY {s.productCode}");
			q.addQueryParameter("p", ticket.getGame().getCode() + "-" + ticket.getSector() + "-%");
			final List<ProductModel> seats = new ArrayList<>();
			for (final StockLevelModel level : flexibleSearchService.<StockLevelModel> search(q).getResult())
			{
				final ProductModel seat = product(level.getProductCode(), ticket.getSeat());
				if (seat != null && !held(seat))
				{
					seats.add(seat);
				}
			}
			return seats;
		});
	}

	@Override
	public List<SocioGuestModel> guests(final MatchdayTicketModel ticket)
	{
		return ticket == null || ticket.getCustomer() == null || socioGuestService == null ? List.of()
				: unrestricted(() -> socioGuestService.guests(ticket.getCustomer(), true));
	}

	@Override
	public Double refundQuote(final MatchdayTicketModel ticket, final Date now)
	{
		if (cancelBlock(ticket, now) != null)
		{
			return null;
		}
		final double paid = ticket.getPrice() == null ? 0 : ticket.getPrice().doubleValue();
		final Date kickoff = ticket.getGame() == null ? null : ticket.getGame().getKickoff();
		final int hours = configurationService.getConfiguration().getInt(FULL_REFUND_HOURS_PROPERTY, 48);
		if (kickoff == null || kickoff.getTime() - now.getTime() >= hours * 3600_000L)
		{
			return Double.valueOf(paid);
		}
		final int percent = configurationService.getConfiguration().getInt(LATE_REFUND_PERCENT_PROPERTY, 80);
		return Double.valueOf(Math.round(paid * percent) / 100.0);
	}

	@Override
	public String cancelBlock(final MatchdayTicketModel ticket, final Date now)
	{
		if (ticket == null)
		{
			return "Ingresso não encontrado.";
		}
		if (!isEnabled())
		{
			return "A gestão de ingressos está desligada (" + ENABLED_PROPERTY + ").";
		}
		if (ticket.getStatus() == MatchdayTicketStatus.CANCELADO)
		{
			return "O ingresso já foi cancelado em " + date(ticket.getCancelledAt()) + ".";
		}
		if (ticket.getStatus() == MatchdayTicketStatus.UTILIZADO)
		{
			return "O ingresso já foi utilizado em " + date(ticket.getUsedAt()) + ".";
		}
		final Date kickoff = ticket.getGame() == null ? null : ticket.getGame().getKickoff();
		if (kickoff != null && !now.before(kickoff))
		{
			return "O jogo já começou: o ingresso não pode mais ser cancelado.";
		}
		return null;
	}

	@Override
	public boolean isCancelled(final String orderCode, final Integer entryNumber)
	{
		if (orderCode == null || entryNumber == null)
		{
			return false;
		}
		return unrestricted(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery(
					"SELECT {pk} FROM {MatchdayTicket} WHERE {orderCode}=?o AND {entryNumber}=?n AND {status}=?s");
			q.addQueryParameter("o", orderCode);
			q.addQueryParameter("n", entryNumber);
			q.addQueryParameter("s", MatchdayTicketStatus.CANCELADO);
			return Boolean.valueOf(!flexibleSearchService.search(q).getResult().isEmpty());
		}).booleanValue();
	}

	// ------------------------------------------------------------ ações

	@Override
	public MatchdayTicketModel changeSeat(final String code, final String newSeatCode, final String author)
	{
		return unrestricted(() -> {
			final MatchdayTicketModel t = editable(code);
			if (t.getGame() != null && t.getGame().getKickoff() != null && !new Date().before(t.getGame().getKickoff()))
			{
				throw new MatchdayTicketException("O jogo já começou: a poltrona não pode mais ser trocada.");
			}
			final ProductModel target = freeSeats(t).stream().filter(s -> s.getCode().equals(newSeatCode)).findFirst().orElse(null);
			if (target == null)
			{
				throw new MatchdayTicketException("A poltrona " + newSeatCode + " não está livre neste jogo e setor.");
			}
			final ProductModel old = t.getSeat();
			setAvailable(target, 0);
			if (old != null)
			{
				setAvailable(old, 1);
			}
			final String from = t.getSeatLabel();
			t.setSeat(target);
			t.setSeatLabel(labelOf(target));
			save(t);
			event(t, MatchdayTicketEventType.TROCA_POLTRONA, author, from, t.getSeatLabel(), null,
					"A poltrona anterior voltou para a venda");
			return t;
		});
	}

	@Override
	public MatchdayTicketModel changeHolder(final String code, final String guestPk, final String holder, final String author)
	{
		return unrestricted(() -> {
			final MatchdayTicketModel t = editable(code);
			final String from = StringUtils.defaultString(t.getHolder());
			SocioGuestModel guest = null;
			if (StringUtils.isNotBlank(guestPk))
			{
				guest = guests(t).stream().filter(g -> g.getPk().toString().equals(guestPk)).findFirst().orElse(null);
				if (guest == null)
				{
					throw new MatchdayTicketException("Convidado não encontrado entre os convidados ativos do comprador.");
				}
			}
			t.setGuest(guest);
			t.setHolder(guest != null ? guest.getName() : StringUtils.isNotBlank(holder) ? holder.trim() : buyerName(t));
			// nome digitado pelo gestor ou volta ao comprador: sem CPF/nascimento/e-mail de outra pessoa
			t.setHolderCpf(guest != null ? guest.getCpf() : null);
			t.setHolderBirthDate(guest != null ? guest.getBirthDate() : null);
			t.setHolderEmail(guest != null ? guest.getEmail() : null);
			save(t);
			event(t, MatchdayTicketEventType.TROCA_TITULAR, author, from, t.getHolder(), null,
					guest != null ? "Convidado do sócio" : StringUtils.isNotBlank(holder) ? "Nome informado pelo gestor" : "Volta para o titular");
			return t;
		});
	}

	@Override
	public MatchdayTicketModel cancel(final String code, final String reason, final String author)
	{
		return unrestricted(() -> {
			final MatchdayTicketModel t = find(code);
			final Date now = new Date();
			final String block = cancelBlock(t, now);
			if (block != null)
			{
				throw new MatchdayTicketException(block);
			}
			final Double refund = refundQuote(t, now);
			t.setStatus(MatchdayTicketStatus.CANCELADO);
			t.setCancelledAt(now);
			t.setRefundAmount(refund);
			save(t);
			if (t.getSeat() != null)
			{
				setAvailable(t.getSeat(), 1);
			}
			final double paid = t.getPrice() == null ? 0 : t.getPrice().doubleValue();
			// pedido pago pela Adyen (05/11): o reembolso vira estorno de verdade na Adyen
			final String adyen = refund == null ? null : adyenRefund(t.getOrderCode(), refund.doubleValue(), "ingresso " + t.getCode());
			event(t, MatchdayTicketEventType.CANCELAMENTO, author, null, null, refund,
					(refund != null && refund.doubleValue() < paid ? "Reembolso parcial (perto do jogo)" : "Reembolso integral")
							+ (adyen == null ? "" : " (" + adyen + ")")
							+ ". A poltrona voltou para a venda." + (StringUtils.isBlank(reason) ? "" : " Motivo: " + reason.trim()));
			return t;
		});
	}

	/** Estorno pela Adyen quando o pedido foi pago por ela; sem Adyen (ou se falhar), só o registro de sempre */
	protected String adyenRefund(final String orderCode, final double amount, final String reason)
	{
		try
		{
			return de.hybris.platform.core.Registry.getApplicationContext()
					.getBean("adyenPaymentService", br.com.flamengo.matchday.payment.adyen.AdyenPaymentService.class)
					.refund(orderCode, amount, reason);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Pedido {}: estorno pela Adyen não lançado ({}): {}", orderCode, reason, e.getMessage());
			return null;
		}
	}

	@Override
	public MatchdayTicketModel markUsed(final String code, final String author)
	{
		return unrestricted(() -> {
			final MatchdayTicketModel t = editable(code);
			if (t.getStatus() == MatchdayTicketStatus.AGUARDANDO_PAGAMENTO)
			{
				throw new MatchdayTicketException("O ingresso " + code + " ainda aguarda o pagamento do pedido (Pix/boleto).");
			}
			t.setStatus(MatchdayTicketStatus.UTILIZADO);
			t.setUsedAt(new Date());
			save(t);
			event(t, MatchdayTicketEventType.UTILIZACAO, author, null, null, null, "Entrada no estádio");
			return t;
		});
	}

	@Override
	public MatchdayTicketModel addNote(final String code, final String note, final String author)
	{
		if (StringUtils.isBlank(note))
		{
			throw new MatchdayTicketException("Escreva a observação.");
		}
		return unrestricted(() -> {
			final MatchdayTicketModel t = find(code);
			if (t == null)
			{
				throw new MatchdayTicketException("Ingresso " + code + " não encontrado.");
			}
			event(t, MatchdayTicketEventType.OBSERVACAO, author, null, null, null, note.trim());
			return t;
		});
	}

	@Override
	public boolean isInternal()
	{
		return Boolean.TRUE.equals(INTERNAL.get());
	}

	@Override
	public void apply(final MatchdayTicketEventModel command, final InterceptorContext ctx)
	{
		final MatchdayTicketModel ticket = command.getTicket();
		if (ticket == null || ticket.getCode() == null)
		{
			throw new MatchdayTicketException("Informe o ingresso.");
		}
		if (command.getType() == null)
		{
			throw new MatchdayTicketException("Informe o tipo: TROCA_POLTRONA, TROCA_TITULAR, CANCELAMENTO, UTILIZACAO ou OBSERVACAO.");
		}
		final String code = ticket.getCode();
		final String author = StringUtils.defaultIfBlank(command.getAuthor(), currentUid());
		final String to = StringUtils.trimToNull(command.getToValue());
		final String note = StringUtils.trimToNull(command.getNote());
		COMMAND.set(new Command(command, ctx));
		try
		{
			switch (command.getType())
			{
				case TROCA_POLTRONA:
					if (to == null)
					{
						throw new MatchdayTicketException("Informe em toValue o código da nova poltrona (mesmo jogo e setor).");
					}
					changeSeat(code, to, author);
					break;
				case TROCA_TITULAR:
					final SocioGuestModel guest = to == null ? null
							: guests(ticket).stream().filter(g -> to.equalsIgnoreCase(StringUtils.trimToEmpty(g.getName()))).findFirst()
									.orElse(null);
					changeHolder(code, guest == null ? null : guest.getPk().toString(), guest == null ? to : null, author);
					break;
				case CANCELAMENTO:
					if (note == null)
					{
						throw new MatchdayTicketException("Informe em note o motivo do cancelamento.");
					}
					cancel(code, note, author);
					break;
				case UTILIZACAO:
					markUsed(code, author);
					break;
				case OBSERVACAO:
					addNote(code, note, author);
					break;
				default:
					throw new MatchdayTicketException("A venda é registrada pela loja; use TROCA_POLTRONA, TROCA_TITULAR, CANCELAMENTO, UTILIZACAO ou OBSERVACAO.");
			}
		}
		finally
		{
			COMMAND.remove();
		}
	}

	/** Ação pedida por fora (OData, Claude do gestor de jogos): o evento salvo é o próprio comando */
	protected static final class Command
	{
		private MatchdayTicketEventModel event;
		private final InterceptorContext ctx;

		protected Command(final MatchdayTicketEventModel event, final InterceptorContext ctx)
		{
			this.event = event;
			this.ctx = ctx;
		}
	}

	/** Ingresso que ainda aceita troca e marcação de uso */
	protected MatchdayTicketModel editable(final String code)
	{
		if (!isEnabled())
		{
			throw new MatchdayTicketException("A gestão de ingressos está desligada (" + ENABLED_PROPERTY + ").");
		}
		final MatchdayTicketModel t = find(code);
		if (t == null)
		{
			throw new MatchdayTicketException("Ingresso " + code + " não encontrado.");
		}
		if (t.getStatus() == MatchdayTicketStatus.CANCELADO)
		{
			throw new MatchdayTicketException("O ingresso foi cancelado em " + date(t.getCancelledAt()) + ".");
		}
		if (t.getStatus() == MatchdayTicketStatus.UTILIZADO)
		{
			throw new MatchdayTicketException("O ingresso já foi utilizado em " + date(t.getUsedAt()) + ".");
		}
		return t;
	}

	// ------------------------------------------------------------ apoio

	protected void event(final MatchdayTicketModel t, final MatchdayTicketEventType type, final String author, final String from,
			final String to, final Double amount, final String note)
	{
		final Command command = COMMAND.get();
		final boolean fromCommand = command != null && command.event != null;
		final MatchdayTicketEventModel e = fromCommand ? command.event : modelService.create(MatchdayTicketEventModel.class);
		if (fromCommand)
		{
			command.event = null; // só o primeiro evento da ação vai no comando
		}
		e.setTicket(t);
		e.setType(type);
		if (!fromCommand || e.getDate() == null)
		{
			e.setDate(new Date()); // o comando chega com a data (parte da chave na Integration API)
		}
		e.setAuthor(StringUtils.defaultIfBlank(author, StringUtils.defaultIfBlank(e.getAuthor(), currentUid())));
		e.setFromValue(from);
		e.setToValue(to);
		e.setAmount(amount);
		e.setNote(note);
		if (fromCommand)
		{
			return; // o evento do comando está sendo salvo agora
		}
		if (command != null)
		{
			command.ctx.registerElementFor(e, PersistenceOperation.SAVE);
			return;
		}
		INTERNAL.set(Boolean.TRUE);
		try
		{
			modelService.save(e);
		}
		finally
		{
			INTERNAL.remove();
		}
	}

	/** Grava já ou, dentro de um comando (interceptor), registra para gravar junto com o evento */
	protected void save(final Object item)
	{
		final Command command = COMMAND.get();
		if (command != null)
		{
			command.ctx.registerElementFor(item, PersistenceOperation.SAVE);
		}
		else
		{
			modelService.save(item);
		}
	}

	protected void setAvailable(final ProductModel seat, final int available)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {StockLevel} WHERE {productCode}=?p");
		q.addQueryParameter("p", seat.getCode());
		for (final StockLevelModel level : flexibleSearchService.<StockLevelModel> search(q).getResult())
		{
			level.setAvailable(available);
			save(level);
		}
	}

	protected boolean held(final ProductModel seat)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {SeatHold} WHERE {product}=?p");
		q.addQueryParameter("p", seat);
		return !flexibleSearchService.<SeatHoldModel> search(q).getResult().isEmpty();
	}

	/** Produto da poltrona no mesmo catálogo da poltrona de referência */
	protected ProductModel product(final String code, final ProductModel reference)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(reference == null ? "SELECT {pk} FROM {Product} WHERE {code}=?c"
				: "SELECT {pk} FROM {Product} WHERE {code}=?c AND {catalogVersion}=?cv");
		q.addQueryParameter("c", code);
		if (reference != null)
		{
			q.addQueryParameter("cv", reference.getCatalogVersion());
		}
		final List<ProductModel> r = flexibleSearchService.<ProductModel> search(q).getResult();
		return r.isEmpty() ? null : r.get(0);
	}

	protected MatchdayGameModel gameOf(final ProductModel seat)
	{
		final String code = seat instanceof VariantProductModel && ((VariantProductModel) seat).getBaseProduct() != null
				? ((VariantProductModel) seat).getBaseProduct().getCode()
				: null;
		if (code == null)
		{
			return null;
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {MatchdayGame} WHERE {code}=?c");
		q.addQueryParameter("c", code);
		final List<MatchdayGameModel> r = flexibleSearchService.<MatchdayGameModel> search(q).getResult();
		return r.isEmpty() ? null : r.get(0);
	}

	protected static String sectorOf(final ProductModel seat, final MatchdayGameModel game)
	{
		final String code = seat.getCode();
		final String prefix = game != null ? game.getCode() + "-" : null;
		if (prefix != null && code.startsWith(prefix))
		{
			return code.substring(prefix.length()).split("-")[0];
		}
		final String[] parts = code.split("-");
		return parts.length > 3 ? parts[3] : null;
	}

	/** "Leste Inferior · Poltrona 12" (o nome da poltrona sem o título do jogo) */
	protected static String labelOf(final ProductModel seat)
	{
		final String name = StringUtils.defaultIfBlank(seat.getName(PT), seat.getCode());
		final int i = name.indexOf(SEP);
		return i < 0 ? name : name.substring(i + SEP.length());
	}

	protected static String buyerName(final MatchdayTicketModel t)
	{
		return t.getCustomer() == null ? null : StringUtils.defaultIfBlank(t.getCustomer().getName(), t.getCustomer().getUid());
	}

	/**
	 * CPF, nascimento e e-mail de quem vai usar: do convidado do sócio ou, no ingresso
	 * adicional de quem não é sócio, os informados na compra (vazio = o comprador)
	 */
	protected void holderData(final MatchdayTicketModel t, final AbstractOrderEntryModel entry)
	{
		final SocioGuestModel guest = t.getGuest();
		if (guest != null)
		{
			t.setHolderCpf(guest.getCpf());
			t.setHolderBirthDate(guest.getBirthDate());
			t.setHolderEmail(guest.getEmail());
			return;
		}
		final Object cpf = attr(entry, TicketHolderService.ENTRY_CPF);
		final Object birth = attr(entry, TicketHolderService.ENTRY_BIRTH_DATE);
		final Object email = attr(entry, TicketHolderService.ENTRY_EMAIL);
		t.setHolderCpf(cpf instanceof String && StringUtils.isNotBlank((String) cpf) ? (String) cpf : null);
		t.setHolderBirthDate(birth instanceof Date ? (Date) birth : null);
		t.setHolderEmail(email instanceof String && StringUtils.isNotBlank((String) email) ? (String) email : null);
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

	protected String currentUid()
	{
		final UserModel user = userService.getCurrentUser();
		return user == null ? null : user.getUid();
	}

	protected static String date(final Date date)
	{
		if (date == null)
		{
			return "-";
		}
		final SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));
		f.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
		return f.format(date);
	}

	protected <T> T unrestricted(final Supplier<T> body)
	{
		return sessionService.executeInLocalView(new SessionExecutionBody()
		{
			@Override
			public Object execute()
			{
				searchRestrictionService.disableSearchRestrictions();
				return body.get();
			}
		});
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}

	public void setSocioGuestService(final SocioGuestService socioGuestService)
	{
		this.socioGuestService = socioGuestService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setSearchRestrictionService(final SearchRestrictionService searchRestrictionService)
	{
		this.searchRestrictionService = searchRestrictionService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
