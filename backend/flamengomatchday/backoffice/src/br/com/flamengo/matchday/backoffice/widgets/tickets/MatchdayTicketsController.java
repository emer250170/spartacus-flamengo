package br.com.flamengo.matchday.backoffice.widgets.tickets;

import br.com.flamengo.matchday.backoffice.widgets.experiences.ExperienceWidgetSupport;
import br.com.flamengo.matchday.enums.MatchdayTicketStatus;
import br.com.flamengo.matchday.model.MatchdayGameModel;
import br.com.flamengo.matchday.model.MatchdayTicketEventModel;
import br.com.flamengo.matchday.model.MatchdayTicketModel;
import br.com.flamengo.matchday.model.SocioGuestModel;
import br.com.flamengo.matchday.report.MatchdayReportService;
import br.com.flamengo.matchday.ticket.MatchdayTicketService;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zul.Button;
import org.zkoss.zul.Div;
import org.zkoss.zul.Html;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Textbox;

import com.hybris.cockpitng.annotations.SocketEvent;
import com.hybris.cockpitng.annotations.ViewEvent;


/**
 * Matchday Flamengo > Ingressos (item 10): lista dos ingressos vendidos (filtros por jogo, setor,
 * situação e texto) e ficha do ingresso com histórico. Ações do gestor de jogos: trocar a poltrona
 * (mesmo jogo e setor), trocar quem vai usar (convidado do comprador ou outro nome), cancelar com
 * reembolso (integral até 48 h antes do jogo, depois 80 %; nada depois do início), marcar como
 * utilizado e anotar observação. As regras ficam no {@link MatchdayTicketService}.
 */
public class MatchdayTicketsController extends ExperienceWidgetSupport
{
	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(MatchdayTicketsController.class);

	@Wire
	private Textbox ticketBox;
	@Wire
	private Label switchLabel;
	@Wire
	private Html ticketDetail;
	@Wire
	private Div ticketActions;
	@Wire
	private Listbox seatList;
	@Wire
	private Button changeSeatButton;
	@Wire
	private Listbox holderList;
	@Wire
	private Textbox holderBox;
	@Wire
	private Textbox reasonBox;
	@Wire
	private Button cancelTicketButton;
	@Wire
	private Label refundLabel;
	@Wire
	private Div noteActions;
	@Wire
	private Textbox noteBox;
	@Wire
	private Html historyHtml;
	@Wire
	private Textbox searchBox;
	@Wire
	private Listbox gameFilter;
	@Wire
	private Listbox sectorFilter;
	@Wire
	private Listbox statusFilter;
	@Wire
	private Label countLabel;
	@Wire
	private Listbox resultList;

	@WireVariable
	private transient MatchdayTicketService matchdayTicketService;
	@WireVariable
	private transient MatchdayReportService matchdayReportService;
	@WireVariable
	private transient UserService userService;

	/** Ingresso aberto na ficha */
	private String current;

	@Override
	public void initialize(final Component comp)
	{
		super.initialize(comp);
		filters();
		search();
		show(null);
	}

	@SocketEvent(socketId = "refresh")
	public void refresh(final Object data)
	{
		filters();
		search();
		show(current);
	}

	@ViewEvent(componentID = "findButton", eventName = Events.ON_CLICK)
	public void onFind()
	{
		find();
	}

	@ViewEvent(componentID = "ticketBox", eventName = Events.ON_OK)
	public void onTicketEnter()
	{
		find();
	}

	@ViewEvent(componentID = "searchButton", eventName = Events.ON_CLICK)
	public void onSearch()
	{
		search();
	}

	@ViewEvent(componentID = "searchBox", eventName = Events.ON_OK)
	public void onSearchEnter()
	{
		search();
	}

	@ViewEvent(componentID = "resultList", eventName = Events.ON_SELECT)
	public void onSelect()
	{
		final Listitem item = resultList.getSelectedItem();
		if (item != null)
		{
			ticketBox.setValue((String) item.getValue());
			show((String) item.getValue());
		}
	}

	@ViewEvent(componentID = "changeSeatButton", eventName = Events.ON_CLICK)
	public void onChangeSeat()
	{
		final String seat = selected(seatList);
		if (StringUtils.isBlank(seat))
		{
			return;
		}
		final String seatName = seatList.getSelectedItem().getLabel();
		final String code = current;
		confirm(label("seat.confirm", code, seatName), () -> {
			final MatchdayTicketModel t = matchdayTicketService.changeSeat(code, seat, author());
			return label("seat.done", code, t.getSeatLabel());
		});
	}

	@ViewEvent(componentID = "changeHolderButton", eventName = Events.ON_CLICK)
	public void onChangeHolder()
	{
		final String typed = StringUtils.trimToNull(holderBox.getValue());
		final String choice = selected(holderList);
		final Listitem item = holderList.getSelectedItem();
		if (typed == null && item == null)
		{
			warn(label("holder.required"));
			return;
		}
		// nome digitado vale mais que a lista; na lista, "" = o titular
		final String guestPk = typed != null ? null : StringUtils.trimToNull(choice);
		final String who = typed != null ? typed : item.getLabel();
		final String code = current;
		confirm(label("holder.confirm", code, who), () -> {
			final MatchdayTicketModel t = matchdayTicketService.changeHolder(code, guestPk, typed, author());
			return label("holder.done", code, t.getHolder());
		});
	}

	@ViewEvent(componentID = "cancelTicketButton", eventName = Events.ON_CLICK)
	public void onCancel()
	{
		final String reason = StringUtils.trimToNull(reasonBox.getValue());
		if (reason == null)
		{
			warn(label("cancel.reasonRequired"));
			return;
		}
		final MatchdayTicketModel t = matchdayTicketService.find(current);
		final Double quote = t == null ? null : matchdayTicketService.refundQuote(t, new Date());
		final String code = current;
		confirm(label("cancel.confirm", code, money(quote)), () -> {
			final MatchdayTicketModel done = matchdayTicketService.cancel(code, reason, author());
			return label("cancel.done", code, money(done.getRefundAmount()));
		});
	}

	@ViewEvent(componentID = "useButton", eventName = Events.ON_CLICK)
	public void onUse()
	{
		final String code = current;
		confirm(label("use.confirm", code), () -> {
			matchdayTicketService.markUsed(code, author());
			return label("use.done", code);
		});
	}

	@ViewEvent(componentID = "noteButton", eventName = Events.ON_CLICK)
	public void onNote()
	{
		final String note = StringUtils.trimToNull(noteBox.getValue());
		if (note == null || current == null)
		{
			warn(label("note.required"));
			return;
		}
		final String code = current;
		try
		{
			matchdayTicketService.addNote(code, note, author());
			info(label("note.done", code));
		}
		catch (final RuntimeException e)
		{
			error(e);
		}
		show(code);
	}

	protected void find()
	{
		final String code = StringUtils.trimToEmpty(ticketBox.getValue());
		if (code.isEmpty())
		{
			return;
		}
		if (matchdayTicketService.find(code) == null)
		{
			warn(label("ticket.notFound", code));
			show(null);
			return;
		}
		show(code);
	}

	protected void show(final String code)
	{
		final MatchdayTicketModel t = code == null ? null : matchdayTicketService.find(code);
		current = t == null ? null : t.getCode();
		reasonBox.setValue("");
		holderBox.setValue("");
		noteBox.setValue("");
		final boolean enabled = matchdayTicketService.isEnabled();
		switchLabel.setValue(enabled ? "" : label("switch.off"));
		if (t == null)
		{
			ticketDetail.setContent("<div style=\"color:#556b82\">" + esc(label("ticket.choose")) + "</div>");
			historyHtml.setContent("");
			ticketActions.setVisible(false);
			noteActions.setVisible(false);
			return;
		}
		ticketDetail.setContent(detail(t));
		historyHtml.setContent(history(t));
		noteActions.setVisible(true);
		final boolean open = enabled && t.getStatus() == MatchdayTicketStatus.VALIDO;
		ticketActions.setVisible(open);
		if (!open)
		{
			return;
		}
		seats(t);
		holders(t);
		final Date now = new Date();
		final String block = matchdayTicketService.cancelBlock(t, now);
		cancelTicketButton.setDisabled(block != null);
		refundLabel.setValue(block != null ? label("refund.blocked", block)
				: label("refund.quote", money(matchdayTicketService.refundQuote(t, now)), money(t.getPrice())));
	}

	protected String detail(final MatchdayTicketModel t)
	{
		final StringBuilder h = new StringBuilder();
		h.append("<div style=\"display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;align-items:baseline\">")
				.append("<div style=\"font-family:ui-monospace,Menlo,Consolas,monospace;font-size:20px;font-weight:700;color:#1d2d3e\">")
				.append(esc(t.getCode())).append("</div><div style=\"font-weight:700;color:").append(color(t)).append("\">")
				.append(esc(state(t))).append("</div></div>");
		h.append("<table style=\"border-collapse:collapse;margin-top:10px;font-size:13px;color:#1d2d3e\">");
		row(h, label("detail.game"), gameName(t.getGame()));
		row(h, label("detail.kickoff"), date(t.getGame() == null ? null : t.getGame().getKickoff(), true));
		row(h, label("detail.seat"), seatText(t));
		row(h, label("detail.holder"), t.getGuest() != null ? label("detail.guest", t.getHolder()) : StringUtils.defaultString(t.getHolder(), "-"));
		final CustomerModel c = t.getCustomer();
		row(h, label("detail.customer"), c == null ? "-" : StringUtils.defaultIfBlank(c.getName(), c.getUid()) + " (" + c.getUid() + ")");
		row(h, label("detail.order"), StringUtils.defaultString(t.getOrderCode(), "-"));
		row(h, label("detail.paid"), t.getMemberDiscount() != null && t.getMemberDiscount().doubleValue() > 0
				? label("detail.discount", money(t.getPrice()), money(t.getMemberDiscount()))
				: money(t.getPrice()));
		row(h, label("detail.sold"), date(t.getSoldAt(), true));
		if (t.getCancelledAt() != null)
		{
			row(h, label("detail.cancelled"), label("detail.refund", date(t.getCancelledAt(), true), money(t.getRefundAmount())));
		}
		if (t.getUsedAt() != null)
		{
			row(h, label("detail.used"), date(t.getUsedAt(), true));
		}
		h.append("</table>");
		return h.toString();
	}

	protected String history(final MatchdayTicketModel t)
	{
		final List<MatchdayTicketEventModel> events = matchdayTicketService.history(t);
		final StringBuilder h = new StringBuilder();
		h.append("<div style=\"margin-top:16px;border-top:1px solid #e5e8ec;padding-top:12px\">")
				.append("<div style=\"font-weight:700;color:#1d2d3e;margin-bottom:6px\">").append(esc(label("history.title")))
				.append("</div>");
		if (events.isEmpty())
		{
			h.append("<div style=\"color:#556b82\">").append(esc(label("history.empty"))).append("</div></div>");
			return h.toString();
		}
		h.append("<table style=\"border-collapse:collapse;font-size:13px;color:#1d2d3e;width:100%\">");
		for (final MatchdayTicketEventModel e : events)
		{
			final StringBuilder what = new StringBuilder();
			if (StringUtils.isNotBlank(e.getFromValue()))
			{
				what.append(e.getFromValue()).append(" → ").append(StringUtils.defaultIfBlank(e.getToValue(), "-"));
			}
			else if (StringUtils.isNotBlank(e.getToValue()))
			{
				what.append(e.getToValue()); // venda: só a poltrona
			}
			if (e.getAmount() != null)
			{
				what.append(what.length() > 0 ? " · " : "").append(money(e.getAmount()));
			}
			if (StringUtils.isNotBlank(e.getNote()))
			{
				what.append(what.length() > 0 ? " · " : "").append(e.getNote());
			}
			h.append("<tr style=\"border-bottom:1px solid #f0f2f4\">")
					.append("<td style=\"padding:5px 14px 5px 0;white-space:nowrap;color:#556b82;vertical-align:top\">")
					.append(esc(date(e.getDate(), true))).append("</td>")
					.append("<td style=\"padding:5px 14px 5px 0;white-space:nowrap;font-weight:600;vertical-align:top\">")
					.append(esc(e.getType() == null ? "-" : label("event." + e.getType().getCode()))).append("</td>")
					.append("<td style=\"padding:5px 14px 5px 0;vertical-align:top\">").append(esc(what.toString())).append("</td>")
					.append("<td style=\"padding:5px 0;white-space:nowrap;color:#556b82;vertical-align:top\">")
					.append(esc(StringUtils.isBlank(e.getAuthor()) ? "" : label("history.by", e.getAuthor()))).append("</td></tr>");
		}
		h.append("</table></div>");
		return h.toString();
	}

	protected void seats(final MatchdayTicketModel t)
	{
		seatList.getItems().clear();
		final List<ProductModel> free = matchdayTicketService.freeSeats(t);
		seatList.appendItem(free.isEmpty() ? label("seat.none") : label("seat.choose", number(free.size())), "");
		for (final ProductModel seat : free)
		{
			seatList.appendItem(seatName(seat), seat.getCode());
		}
		seatList.setSelectedIndex(0);
		changeSeatButton.setDisabled(free.isEmpty());
	}

	protected void holders(final MatchdayTicketModel t)
	{
		holderList.getItems().clear();
		final CustomerModel c = t.getCustomer();
		final Listitem owner = holderList.appendItem(
				label("holder.owner", c == null ? "-" : StringUtils.defaultIfBlank(c.getName(), c.getUid())), "");
		holderList.setSelectedItem(owner);
		for (final SocioGuestModel g : matchdayTicketService.guests(t))
		{
			final Listitem item = holderList.appendItem(label("holder.guest", g.getName()), g.getPk().toString());
			if (t.getGuest() != null && t.getGuest().getPk().equals(g.getPk()))
			{
				holderList.setSelectedItem(item);
			}
		}
	}

	protected void row(final StringBuilder h, final String name, final String value)
	{
		h.append("<tr><td style=\"color:#556b82;padding:3px 16px 3px 0;white-space:nowrap;vertical-align:top\">").append(esc(name))
				.append("</td><td style=\"padding:3px 0\">").append(esc(value)).append("</td></tr>");
	}

	protected void filters()
	{
		final String game = selected(gameFilter);
		gameFilter.getItems().clear();
		gameFilter.appendItem(label("filter.allGames"), "");
		for (final MatchdayGameModel g : matchdayReportService.getGames())
		{
			final Listitem item = gameFilter.appendItem(gameName(g) + " · " + date(g.getKickoff(), false), g.getCode());
			if (g.getCode().equals(game))
			{
				gameFilter.setSelectedItem(item);
			}
		}
		if (gameFilter.getSelectedItem() == null)
		{
			gameFilter.setSelectedIndex(0);
		}
		if (sectorFilter.getItems().isEmpty())
		{
			sectorFilter.appendItem(label("filter.allSectors"), "");
			for (final String sector : MatchdayReportService.SECTORS)
			{
				sectorFilter.appendItem(sectorName(sector), sector);
			}
			sectorFilter.setSelectedIndex(0);
		}
		if (statusFilter.getItems().isEmpty())
		{
			statusFilter.appendItem(label("filter.allStatus"), "");
			for (final MatchdayTicketStatus s : new MatchdayTicketStatus[]
			{ MatchdayTicketStatus.VALIDO, MatchdayTicketStatus.AGUARDANDO_PAGAMENTO, MatchdayTicketStatus.UTILIZADO,
					MatchdayTicketStatus.CANCELADO })
			{
				statusFilter.appendItem(label("status." + s.getCode()), s.getCode());
			}
			statusFilter.setSelectedIndex(0);
		}
	}

	protected void search()
	{
		final List<MatchdayTicketModel> list = matchdayTicketService.search(selected(gameFilter), selected(sectorFilter),
				selected(statusFilter), searchBox.getValue());
		resultList.getItems().clear();
		for (final MatchdayTicketModel t : list)
		{
			final Listitem item = new Listitem();
			item.setValue(t.getCode());
			final Listcell code = new Listcell(t.getCode());
			code.setStyle("font-family:ui-monospace,Menlo,Consolas,monospace;");
			item.appendChild(code);
			item.appendChild(new Listcell(gameName(t.getGame()) + " · " + date(t.getGame() == null ? null : t.getGame().getKickoff(), false)));
			item.appendChild(new Listcell(seatText(t)));
			item.appendChild(new Listcell(StringUtils.defaultString(t.getHolder(), "-")));
			final CustomerModel c = t.getCustomer();
			item.appendChild(new Listcell(c == null ? "-" : c.getUid()));
			item.appendChild(new Listcell(money(t.getPrice())));
			final Listcell status = new Listcell(state(t));
			status.setStyle("font-weight:600;color:" + color(t));
			item.appendChild(status);
			resultList.appendChild(item);
			if (t.getCode().equals(current))
			{
				resultList.setSelectedItem(item);
			}
		}
		countLabel.setValue(label("list.count", number(list.size())));
	}

	protected void confirm(final String message, final Supplier<String> action)
	{
		if (current == null)
		{
			return;
		}
		Messagebox.show(message, label("confirm.title"), Messagebox.OK | Messagebox.CANCEL, Messagebox.QUESTION,
				(EventListener<Event>) event -> {
					if (Messagebox.ON_OK.equals(event.getName()))
					{
						final String code = current;
						try
						{
							info(action.get());
						}
						catch (final RuntimeException e)
						{
							LOG.warn("Ação no ingresso {} recusada: {}", code, e.getMessage());
							error(e);
						}
						search();
						show(code);
					}
				});
	}

	protected String state(final MatchdayTicketModel t)
	{
		return t.getStatus() == null ? "-" : label("status." + t.getStatus().getCode());
	}

	protected static String color(final MatchdayTicketModel t)
	{
		return t.getStatus() == MatchdayTicketStatus.CANCELADO ? "#bb0000"
				: t.getStatus() == MatchdayTicketStatus.UTILIZADO ? "#556b82"
						: t.getStatus() == MatchdayTicketStatus.AGUARDANDO_PAGAMENTO ? "#b35c00" : "#107e3e";
	}

	/** Nome da poltrona ("Leste Inferior · Poltrona 12"); sem nome, o setor */
	protected String seatText(final MatchdayTicketModel t)
	{
		return StringUtils.defaultIfBlank(t.getSeatLabel(), sectorName(t.getSector()));
	}

	protected String sectorName(final String sector)
	{
		return StringUtils.isBlank(sector) ? "-" : label("sector." + sector);
	}

	/** "Leste Inferior · Poltrona 12" (o nome da poltrona sem o título do jogo) */
	protected static String seatName(final ProductModel seat)
	{
		final String name = StringUtils.defaultIfBlank(seat.getName(PT), seat.getCode());
		final int i = name.indexOf(" · ");
		return i < 0 ? name : name.substring(i + 3);
	}

	protected static String gameName(final MatchdayGameModel game)
	{
		return game == null ? "-" : "Flamengo x " + StringUtils.defaultString(game.getOpponent()).trim();
	}

	protected String author()
	{
		return userService.getCurrentUser() == null ? null : userService.getCurrentUser().getUid();
	}

	protected static String selected(final Listbox listbox)
	{
		final Listitem item = listbox.getSelectedItem();
		return item == null ? null : (String) item.getValue();
	}
}
