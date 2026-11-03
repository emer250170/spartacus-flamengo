package br.com.flamengo.matchday.backoffice.widgets.experiences;

import br.com.flamengo.matchday.model.SocioExperienceModel;
import br.com.flamengo.matchday.model.SocioRedemptionModel;
import br.com.flamengo.matchday.socio.SocioExperienceService;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.user.UserService;

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
 * Validar voucher e Resgates e compras (cockpit Gestão de experiências): busca o voucher
 * NACAO-… digitado na portaria ou no atendimento, mostra de quem é e a situação, marca como
 * utilizado ou cancela (a vaga volta; no resgate, os pontos também). A lista filtra por
 * experiência, situação, origem e texto; clicar numa linha abre o voucher no painel.
 */
public class ExperienceVouchersController extends ExperienceWidgetSupport
{
	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(ExperienceVouchersController.class);

	@Wire
	private Textbox voucherBox;
	@Wire
	private Html voucherDetail;
	@Wire
	private Div voucherActions;
	@Wire
	private Textbox reasonBox;
	@Wire
	private Button useButton;
	@Wire
	private Button cancelVoucherButton;
	@Wire
	private Listbox experienceFilter;
	@Wire
	private Listbox statusFilter;
	@Wire
	private Listbox originFilter;
	@Wire
	private Textbox searchBox;
	@Wire
	private Label countLabel;
	@Wire
	private Listbox resultList;

	@WireVariable
	private transient SocioExperienceService socioExperienceService;
	@WireVariable
	private transient UserService userService;

	/** Voucher aberto no painel */
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
		voucherBox.setFocus(true);
	}

	@ViewEvent(componentID = "findButton", eventName = Events.ON_CLICK)
	public void onFind()
	{
		find();
	}

	@ViewEvent(componentID = "voucherBox", eventName = Events.ON_OK)
	public void onVoucherEnter()
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
			voucherBox.setValue((String) item.getValue());
			show((String) item.getValue());
		}
	}

	@ViewEvent(componentID = "useButton", eventName = Events.ON_CLICK)
	public void onUse()
	{
		final String voucher = current;
		confirm(label("use.confirm", voucher), () -> {
			socioExperienceService.useVoucher(voucher, author());
			return label("use.done", voucher);
		});
	}

	@ViewEvent(componentID = "cancelVoucherButton", eventName = Events.ON_CLICK)
	public void onCancel()
	{
		final String voucher = current;
		final String reason = StringUtils.trimToNull(reasonBox.getValue());
		if (reason == null)
		{
			warn(label("cancel.reasonRequired"));
			return;
		}
		confirm(label("cancel.confirm", voucher), () -> {
			final SocioRedemptionModel r = socioExperienceService.cancelVoucher(voucher, author(), reason);
			return label(isPurchase(r) ? "cancel.donePurchase" : "cancel.donePoints", voucher);
		});
	}

	protected void find()
	{
		final String voucher = StringUtils.trimToEmpty(voucherBox.getValue()).toUpperCase();
		if (voucher.isEmpty())
		{
			return;
		}
		if (socioExperienceService.findVoucher(voucher) == null)
		{
			warn(label("voucher.notFound", voucher));
			show(null);
			return;
		}
		show(voucher);
	}

	protected void show(final String voucher)
	{
		final SocioRedemptionModel r = voucher == null ? null : socioExperienceService.findVoucher(voucher);
		current = r == null ? null : r.getVoucher();
		reasonBox.setValue("");
		if (r == null)
		{
			voucherDetail.setContent("<div style=\"color:#556b82\">" + esc(label("voucher.choose")) + "</div>");
			voucherActions.setVisible(false);
			return;
		}
		final CustomerModel c = r.getCustomer();
		final String state = state(r);
		final String color = r.getCancelledAt() != null ? "#bb0000" : r.getUsedAt() != null ? "#556b82" : "#107e3e";
		final StringBuilder h = new StringBuilder();
		h.append("<div style=\"display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;align-items:baseline\">")
				.append("<div style=\"font-family:ui-monospace,Menlo,Consolas,monospace;font-size:20px;font-weight:700;color:#1d2d3e\">")
				.append(esc(r.getVoucher())).append("</div>").append("<div style=\"font-weight:700;color:").append(color).append("\">")
				.append(esc(state)).append("</div></div>");
		h.append("<table style=\"border-collapse:collapse;margin-top:10px;font-size:13px;color:#1d2d3e\">");
		row(h, label("detail.experience"), name(r.getExperience()));
		row(h, label("detail.customer"),
				c == null ? "-" : StringUtils.defaultIfBlank(c.getName(), c.getUid()) + " (" + c.getUid() + ")");
		row(h, label("detail.origin"), isPurchase(r)
				? label("detail.purchase", StringUtils.defaultString(r.getOrderCode()), money(r.getAmount()))
				: label("detail.points", number(r.getPoints() == null ? 0 : r.getPoints().intValue())));
		row(h, label("detail.date"), date(r.getDate() != null ? r.getDate() : r.getCreationtime(), true));
		if (r.getUsedAt() != null)
		{
			row(h, label("detail.used"), date(r.getUsedAt(), true) + by(r.getUsedBy()));
		}
		if (r.getCancelledAt() != null)
		{
			row(h, label("detail.cancelled"), date(r.getCancelledAt(), true) + by(r.getCancelledBy())
					+ (StringUtils.isBlank(r.getCancelReason()) ? "" : " · " + r.getCancelReason()));
		}
		h.append("</table>");
		voucherDetail.setContent(h.toString());
		final boolean open = r.getCancelledAt() == null && r.getUsedAt() == null;
		voucherActions.setVisible(open);
		useButton.setDisabled(!open);
		cancelVoucherButton.setDisabled(!open);
	}

	protected void row(final StringBuilder h, final String name, final String value)
	{
		h.append("<tr><td style=\"color:#556b82;padding:3px 16px 3px 0;white-space:nowrap;vertical-align:top\">").append(esc(name))
				.append("</td><td style=\"padding:3px 0\">").append(esc(value)).append("</td></tr>");
	}

	protected String by(final String uid)
	{
		return StringUtils.isBlank(uid) ? "" : " (" + uid + ")";
	}

	protected void filters()
	{
		final String experience = selected(experienceFilter);
		experienceFilter.getItems().clear();
		experienceFilter.appendItem(label("filter.allExperiences"), "");
		for (final SocioExperienceModel e : socioExperienceService.allExperiences())
		{
			final Listitem item = experienceFilter.appendItem(name(e), e.getCode());
			if (e.getCode().equals(experience))
			{
				experienceFilter.setSelectedItem(item);
			}
		}
		if (experienceFilter.getSelectedItem() == null)
		{
			experienceFilter.setSelectedIndex(0);
		}
		if (statusFilter.getItems().isEmpty())
		{
			statusFilter.appendItem(label("filter.allStatus"), "");
			statusFilter.appendItem(label("status.VALID"), "VALID");
			statusFilter.appendItem(label("status.USED"), "USED");
			statusFilter.appendItem(label("status.CANCELLED"), "CANCELLED");
			statusFilter.setSelectedIndex(0);
		}
		if (originFilter.getItems().isEmpty())
		{
			originFilter.appendItem(label("filter.allOrigins"), "");
			originFilter.appendItem(label("origin.PURCHASE"), "PURCHASE");
			originFilter.appendItem(label("origin.POINTS"), "POINTS");
			originFilter.setSelectedIndex(0);
		}
	}

	protected void search()
	{
		final List<SocioRedemptionModel> list = socioExperienceService.searchRedemptions(selected(experienceFilter),
				selected(statusFilter), selected(originFilter), searchBox.getValue());
		resultList.getItems().clear();
		for (final SocioRedemptionModel r : list)
		{
			final Listitem item = new Listitem();
			item.setValue(r.getVoucher());
			final Listcell voucher = new Listcell(r.getVoucher());
			voucher.setStyle("font-family:ui-monospace,Menlo,Consolas,monospace;");
			item.appendChild(voucher);
			item.appendChild(new Listcell(date(r.getDate() != null ? r.getDate() : r.getCreationtime(), false)));
			item.appendChild(new Listcell(name(r.getExperience())));
			final CustomerModel c = r.getCustomer();
			item.appendChild(new Listcell(c == null ? "-" : StringUtils.defaultIfBlank(c.getName(), c.getUid())));
			item.appendChild(new Listcell(isPurchase(r) ? label("origin.PURCHASE") + " · " + money(r.getAmount())
					: label("origin.POINTS") + " · " + number(r.getPoints() == null ? 0 : r.getPoints().intValue())));
			final Listcell status = new Listcell(state(r));
			status.setStyle("font-weight:600;color:"
					+ (r.getCancelledAt() != null ? "#bb0000" : r.getUsedAt() != null ? "#556b82" : "#107e3e"));
			item.appendChild(status);
			resultList.appendChild(item);
			if (r.getVoucher().equals(current))
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
						final String voucher = current;
						try
						{
							info(action.get());
						}
						catch (final RuntimeException e)
						{
							LOG.warn("Ação no voucher {} recusada: {}", voucher, e.getMessage());
							error(e);
						}
						search();
						show(voucher);
					}
				});
	}

	protected String state(final SocioRedemptionModel r)
	{
		return label(r.getCancelledAt() != null ? "status.CANCELLED" : r.getUsedAt() != null ? "status.USED" : "status.VALID");
	}

	protected String author()
	{
		return userService.getCurrentUser() == null ? null : userService.getCurrentUser().getUid();
	}

	protected static boolean isPurchase(final SocioRedemptionModel r)
	{
		return StringUtils.isNotBlank(r.getOrderCode()) || SocioExperienceService.ORIGIN_PURCHASE.equals(r.getOrigin());
	}

	protected static String name(final SocioExperienceModel e)
	{
		return e == null ? "-" : StringUtils.defaultIfBlank(e.getName(PT), e.getCode());
	}

	protected static String selected(final Listbox listbox)
	{
		final Listitem item = listbox.getSelectedItem();
		return item == null ? null : (String) item.getValue();
	}
}
