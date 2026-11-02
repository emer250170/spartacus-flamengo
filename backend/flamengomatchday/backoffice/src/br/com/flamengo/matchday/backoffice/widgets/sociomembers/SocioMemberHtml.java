package br.com.flamengo.matchday.backoffice.widgets.sociomembers;

import br.com.flamengo.matchday.socio.SocioContractStatus;
import br.com.flamengo.matchday.socio.SocioExitQuote;
import br.com.flamengo.matchday.socio.SocioMember;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;


/**
 * HTML da ficha do sócio (dados do plano) e do histórico (linha do tempo, pedidos
 * e chamados) na tela Sócio-Torcedor › Sócios.
 */
public class SocioMemberHtml
{
	private static final String CARD = "background:#fff;border:1px solid #d9dde3;border-radius:8px;padding:16px 18px;margin-bottom:12px";
	private static final String TH = "text-align:left;padding:6px 8px;border-bottom:2px solid #d9dde3;color:#556b82;font-weight:600;white-space:nowrap";
	private static final String TD = "padding:6px 8px;border-bottom:1px solid #eef0f2;vertical-align:top";

	private final SocioMember m;
	private final Function<String, String> labels;
	private final Locale locale;
	private final NumberFormat money = SocioMembersController.money();

	public SocioMemberHtml(final SocioMember member, final Function<String, String> labels, final Locale locale)
	{
		this.m = member;
		this.labels = labels;
		this.locale = locale;
	}

	/** Cabeçalho, avisos e os dados do plano */
	public String detail()
	{
		final StringBuilder h = new StringBuilder("<div style=\"").append(CARD).append("\">");
		h.append("<div style=\"display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:12px\">")
				.append("<div style=\"margin-right:auto\"><div style=\"font-size:18px;font-weight:700;color:#1d2d3e\">")
				.append(esc(m.name)).append("</div><div style=\"color:#6a7d8f;font-size:12px\">").append(esc(m.uid))
				.append("</div></div>").append(chip()).append("</div>");

		if (m.suspended && m.status != SocioMember.Status.INACTIVE)
		{
			h.append(banner("#fff4e5", "#b45309", l("detail.suspendedBanner")));
		}
		if (m.status == SocioMember.Status.CANCELLING)
		{
			h.append(banner("#e8f2fc", "#0a6ed1", MessageFormat.format(l("detail.cancellingBanner"), m.openCancelTicket)));
		}
		if (m.status == SocioMember.Status.INACTIVE)
		{
			h.append(banner("#f2f4f5", "#556b82", l("detail.inactiveBanner")));
		}

		final String suspendedNote = m.suspended && m.status != SocioMember.Status.INACTIVE
				? " <span style=\"color:#b45309;font-size:11px\">(" + esc(l("detail.suspendedValue")) + ")</span>"
				: "";
		h.append("<div style=\"display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:10px 18px\">");
		field(h, l("detail.plan"), esc(StringUtils.defaultString(m.planName, "-")));
		field(h, l("detail.family"), esc(StringUtils.defaultString(m.family, "-")));
		field(h, l("detail.since"), date(m.since));
		field(h, l("detail.until"), date(m.until));
		field(h, l("detail.monthly"), money(m.monthlyPrice));
		field(h, l("detail.guests"), m.guests == 0 ? esc(l("detail.noGuests"))
				: esc(MessageFormat.format(l("detail.guestsValue"), Integer.valueOf(m.guests), money.format(m.guestPrice))));
		field(h, l("detail.total"), "<b>" + money(m.monthlyPrice + m.guests * m.guestPrice) + "</b>");
		field(h, l("detail.wave"),
				esc(m.wave <= 0 ? l("detail.wave.general") : MessageFormat.format(l("detail.wave.n"), Integer.valueOf(m.wave)))
						+ suspendedNote);
		field(h, l("detail.ticketDiscount"), m.ticketDiscount + "%" + suspendedNote);
		field(h, l("detail.storeDiscount"), m.storeDiscount + "%" + suspendedNote);
		field(h, l("detail.nextCharge"), date(m.nextCharge));
		field(h, l("detail.card"), esc(StringUtils.defaultIfBlank(m.card, l("detail.noCard"))));
		field(h, l("detail.biometry"), m.biometry ? "<span style=\"color:#107e3e;font-weight:600\">" + esc(l("detail.biometry.ok")) + "</span>"
				: "<span style=\"color:#b45309;font-weight:600\">" + esc(l("detail.biometry.pending")) + "</span>");
		if (m.termVersion != null)
		{
			field(h, l("detail.term"), termValue());
		}
		if (m.openCancelTicket != null)
		{
			field(h, l("detail.cancelTicket"), esc(m.openCancelTicket));
		}
		if (m.contract != null && m.contract.member && m.contract.end != null)
		{
			field(h, l("detail.renewal"), renewalValue());
		}
		if (m.exit != null)
		{
			field(h, exitLabel(), exitValue());
		}
		return h.append("</div></div>").toString();
	}

	/** Linha do tempo, pedidos do plano e chamados */
	public String history()
	{
		final StringBuilder h = new StringBuilder();

		h.append(section(l("history.timeline")));
		if (m.events.isEmpty())
		{
			h.append(empty(l("history.timeline.empty")));
		}
		else
		{
			h.append(tableStart(l("history.date"), l("history.type"), l("history.origin"), l("history.author"),
					l("history.note"), l("history.result")));
			for (final SocioMember.Event e : m.events)
			{
				h.append("<tr>").append(td(dateTime(e.date), "white-space:nowrap"))
						.append(td("<b style=\"color:" + eventColor(e.type) + "\">" + esc(l("event." + e.type)) + "</b>", ""))
						.append(td(esc(e.origin), "")).append(td(esc(e.author), "")).append(td(esc(e.note), ""))
						.append(td(esc(e.result), "color:#556b82")).append("</tr>");
			}
			h.append("</table></div>");
		}
		h.append("</div>");

		h.append(section(l("history.attempts")));
		if (m.attempts.isEmpty())
		{
			h.append(empty(l("history.attempts.empty")));
		}
		else
		{
			h.append(tableStart(l("history.date"), l("history.game"), l("history.seat"), l("history.decision"),
					l("history.reasons")));
			for (final SocioMember.Attempt a : m.attempts)
			{
				final String decision = a.eligible ? "attempt.ok" : a.blocked ? "attempt.blocked" : "attempt.denied";
				final String color = a.eligible ? "#107e3e" : a.blocked ? "#bb0000" : "#b45309";
				h.append("<tr>").append(td(dateTime(a.date), "white-space:nowrap")).append(td(esc(a.game), "white-space:nowrap"))
						.append(td(esc(a.seat), "white-space:nowrap"))
						.append(td("<b style=\"color:" + color + "\">" + esc(l(decision)) + "</b>", "white-space:nowrap"))
						.append(td(esc(a.reasons).replace("\n", "<br/>"), "")).append("</tr>");
			}
			h.append("</table></div>");
		}
		h.append("</div>");

		h.append(section(l("history.guests")));
		final long activeGuests = m.guestList.stream().filter(g -> g.active).count();
		if (m.guests > 0 || activeGuests > 0)
		{
			h.append("<div style=\"color:#556b82;margin-bottom:6px\">")
					.append(esc(MessageFormat.format(l("history.guests.slots"), Long.valueOf(activeGuests), Integer.valueOf(m.guests))))
					.append("</div>");
		}
		if (m.guestList.isEmpty())
		{
			h.append(empty(l("history.guests.empty")));
		}
		else
		{
			h.append(tableStart(l("history.guest.name"), l("history.guest.cpf"), l("history.guest.birth"), l("history.guest.email"),
					l("history.guest.since"), l("history.guest.tickets"), l("history.guest.state")));
			for (final SocioMember.Guest g : m.guestList)
			{
				final String state = g.active ? "<b style=\"color:#107e3e\">" + esc(l("guest.active")) + "</b>"
						: "<span style=\"color:#6a7d8f\">" + esc(MessageFormat.format(l("guest.ended"), date(g.until),
								StringUtils.defaultString(g.endReason, "-"))) + "</span>";
				h.append("<tr>").append(td(esc(g.name), "")).append(td(esc(g.cpf), "white-space:nowrap"))
						.append(td(date(g.birthDate), "white-space:nowrap")).append(td(esc(g.email), ""))
						.append(td(date(g.since), "white-space:nowrap"))
						.append(td(g.tickets.isEmpty() ? "-" : esc(String.join("\n", g.tickets)).replace("\n", "<br/>"), ""))
						.append(td(state, "")).append("</tr>");
			}
			h.append("</table></div>");
		}
		h.append("</div>");

		h.append(section(l("history.orders")));
		if (m.orders.isEmpty())
		{
			h.append(empty(l("history.orders.empty")));
		}
		else
		{
			h.append(tableStart(l("history.order"), l("history.date"), l("history.total"), l("history.items")));
			for (final SocioMember.Order o : m.orders)
			{
				h.append("<tr>").append(td(esc(o.code), "white-space:nowrap")).append(td(date(o.date), "white-space:nowrap"))
						.append(td(money(o.total), "white-space:nowrap")).append(td(esc(o.items), "")).append("</tr>");
			}
			h.append("</table></div>");
		}
		h.append("</div>");

		h.append(section(l("history.tickets")));
		if (m.tickets.isEmpty())
		{
			h.append(empty(l("history.tickets.empty")));
		}
		else
		{
			h.append(tableStart(l("history.ticket"), l("history.headline"), l("history.state"), l("history.date")));
			for (final SocioMember.Ticket t : m.tickets)
			{
				h.append("<tr>").append(td(esc(t.id), "white-space:nowrap")).append(td(esc(t.headline), ""))
						.append(td(esc(t.state == null ? "-" : l("ticket." + t.state)), "white-space:nowrap"))
						.append(td(dateTime(t.date), "white-space:nowrap")).append("</tr>");
			}
			h.append("</table></div>");
		}
		return h.append("</div>").toString();
	}

	// ------------------------------------------------------------ apoio

	protected String chip()
	{
		final String color = SocioMembersController.statusColor(m.status);
		return "<span style=\"border:1px solid " + color + ";color:" + color
				+ ";border-radius:12px;padding:3px 10px;font-weight:600;font-size:12px;white-space:nowrap\">"
				+ esc(l("status." + m.status.name())) + "</span>";
	}

	protected static String banner(final String background, final String color, final String text)
	{
		return "<div style=\"background:" + background + ";border-left:4px solid " + color + ";color:" + color
				+ ";padding:8px 12px;border-radius:4px;margin-bottom:12px;font-weight:600\">" + esc(text) + "</div>";
	}

	protected static void field(final StringBuilder h, final String name, final String valueHtml)
	{
		h.append("<div><div style=\"font-size:11px;color:#6a7d8f;text-transform:uppercase;letter-spacing:.03em\">")
				.append(esc(name)).append("</div><div style=\"font-size:14px;color:#1d2d3e;margin-top:2px\">").append(valueHtml)
				.append("</div></div>");
	}

	protected static String section(final String title)
	{
		return "<div style=\"" + CARD + "\"><div style=\"font-size:15px;font-weight:700;color:#1d2d3e;margin-bottom:8px\">"
				+ esc(title) + "</div>";
	}

	protected static String empty(final String text)
	{
		return "<div style=\"color:#6a7d8f\">" + esc(text) + "</div>";
	}

	protected static String tableStart(final String... headers)
	{
		final StringBuilder h = new StringBuilder(
				"<div style=\"overflow-x:auto\"><table style=\"width:100%;border-collapse:collapse;font-size:12px\"><tr>");
		for (final String header : headers)
		{
			h.append("<th style=\"").append(TH).append("\">").append(esc(header)).append("</th>");
		}
		return h.append("</tr>").toString();
	}

	protected static String td(final String html, final String style)
	{
		return "<td style=\"" + TD + (style.isEmpty() ? "" : ";" + style) + "\">" + (StringUtils.isEmpty(html) ? "-" : html)
				+ "</td>";
	}

	/** Termo do Nação: "v2026.10 aceito em 01/10/2026 (Checkout, pedido 00012001)" ou pendente */
	protected String termValue()
	{
		final String last = m.termAcceptedVersion == null ? ""
				: MessageFormat.format(l("detail.term.last"), m.termAcceptedVersion, date(m.termAcceptedAt),
						StringUtils.defaultString(m.termAcceptedOrigin)
								+ (m.termAcceptedOrder == null ? "" : ", " + MessageFormat.format(l("detail.term.order"), m.termAcceptedOrder)));
		if (m.termAccepted)
		{
			return "<span style=\"color:#107e3e;font-weight:600\">" + esc(last) + "</span>";
		}
		return "<span style=\"color:#b45309;font-weight:600\">" + esc(MessageFormat.format(l("detail.term.pending"), m.termVersion))
				+ "</span>" + (last.isEmpty() ? "" : "<br><span style=\"color:#6a7d8f;font-size:11px\">" + esc(last) + "</span>");
	}

	/**
	 * Vigência (item 6): "Automática em 02/11/2027", "Muda para o Nação Maraca 3 em
	 * 02/11/2027" ou "Desligada: termina em 02/11/2027 sem multa", e o prazo para mudar.
	 */
	protected String renewalValue()
	{
		final SocioContractStatus c = m.contract;
		final String main;
		final String color;
		if (!c.autoRenew)
		{
			main = MessageFormat.format(l("detail.renewal.off"), date(c.end));
			color = "#b45309";
		}
		else if (c.nextPlanName != null)
		{
			main = MessageFormat.format(l("detail.renewal.change"), c.nextPlanName, date(c.end));
			color = "#0a6ed1";
		}
		else
		{
			main = MessageFormat.format(l("detail.renewal.auto"), date(c.end));
			color = "#107e3e";
		}
		final String sub = (c.locked ? MessageFormat.format(l("detail.renewal.locked"), date(c.changeDeadline))
				: MessageFormat.format(l("detail.renewal.until"), date(c.changeDeadline)))
				+ (c.renewals > 0 ? " · " + MessageFormat.format(l("detail.renewal.count"), String.valueOf(c.renewals)) : "");
		return "<span style=\"color:" + color + ";font-weight:600\">" + esc(main) + "</span><br><span style=\"color:#6a7d8f;font-size:11px\">"
				+ esc(sub) + "</span>";
	}

	/** Regras de saída: "Se cancelar hoje", "Saída (pedido de 02/11/2026)" ou "Saída confirmada" */
	protected String exitLabel()
	{
		final SocioExitQuote q = m.exit;
		if (q.confirmed)
		{
			return l("detail.exit.done");
		}
		return q.openTicket != null ? MessageFormat.format(l("detail.exit.request"), date(q.basis)) : l("detail.exit.now");
	}

	/** Reembolso (verde) ou multa (laranja), com a regra que levou ao valor */
	protected String exitValue()
	{
		final SocioExitQuote q = m.exit;
		final String text;
		if (q.withinWithdrawal)
		{
			text = q.confirmed ? MessageFormat.format(l("detail.exit.refundDone"), money.format(q.refund))
					: MessageFormat.format(l("detail.exit.refund"), money.format(q.refund), date(q.withdrawalUntil));
		}
		else if (q.fine <= 0)
		{
			text = l("detail.exit.noFine");
		}
		else
		{
			text = q.confirmed ? MessageFormat.format(l("detail.exit.fineDone"), money.format(q.fine))
					: MessageFormat.format(l("detail.exit.fine"), money.format(q.fine), percent(q.finePercent),
							String.valueOf(q.remainingInstallments), money.format(q.monthly));
		}
		return "<span style=\"color:" + (q.withinWithdrawal ? "#107e3e" : "#b45309") + ";font-weight:600\">" + esc(text) + "</span>";
	}

	protected static String percent(final double value)
	{
		return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	protected static String eventColor(final String type)
	{
		if ("SUSPENSAO".equals(type))
		{
			return "#b45309";
		}
		if ("REGULARIZACAO".equals(type) || "ADESAO".equals(type) || "RENOVACAO".equals(type))
		{
			return "#107e3e";
		}
		if ("CANCELAMENTO".equals(type) || "FIM_VIGENCIA".equals(type))
		{
			return "#bb0000";
		}
		return "#1d2d3e";
	}

	protected String money(final double value)
	{
		return esc(money.format(value));
	}

	protected String date(final Date date)
	{
		return date == null ? "-" : new SimpleDateFormat("dd/MM/yyyy", locale).format(date);
	}

	protected String dateTime(final Date date)
	{
		return date == null ? "-" : new SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(date);
	}

	protected String l(final String key)
	{
		return labels.apply(key);
	}

	public static String esc(final String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
