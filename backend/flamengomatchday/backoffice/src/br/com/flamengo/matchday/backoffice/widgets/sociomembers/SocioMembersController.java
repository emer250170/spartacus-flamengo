package br.com.flamengo.matchday.backoffice.widgets.sociomembers;

import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.socio.SocioMember;
import br.com.flamengo.matchday.socio.SocioMemberService;
import br.com.flamengo.matchday.socio.SocioPlanService;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
import org.zkoss.zk.ui.util.Clients;
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
import com.hybris.cockpitng.i18n.CockpitLocaleService;
import com.hybris.cockpitng.util.DefaultWidgetController;


/**
 * Sócio-Torcedor › Sócios: busca o torcedor pelo nome ou e-mail, abre a ficha do
 * plano e executa as ações do atendimento. Cada ação pede confirmação e grava um
 * evento na linha do tempo com o gestor logado como autor
 * ({@link SocioMemberService}).
 */
public class SocioMembersController extends DefaultWidgetController
{
	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(SocioMembersController.class);

	@Wire
	private Textbox searchBox;
	@Wire
	private Listbox planFilter;
	@Wire
	private Listbox statusFilter;
	@Wire
	private Label countLabel;
	@Wire
	private Listbox resultList;
	@Wire
	private Html detailContent;
	@Wire
	private Div actionsPanel;
	@Wire
	private Textbox noteBox;
	@Wire
	private Button suspendButton;
	@Wire
	private Button regularizeButton;
	@Wire
	private Button noteButton;
	@Wire
	private Button cancelButton;
	@Wire
	private Button biometryButton;
	@Wire
	private Button biometryPendingButton;
	@Wire
	private Html historyContent;

	@WireVariable
	private transient SocioMemberService socioMemberService;
	@WireVariable
	private transient SocioPlanService socioPlanService;
	@WireVariable
	private transient CockpitLocaleService cockpitLocaleService;

	/** Sócio aberto na ficha */
	private SocioMember current;

	@Override
	public void initialize(final Component comp)
	{
		super.initialize(comp);
		fillFilters();
		search(null);
		showDetail(null);
	}

	/** Ao clicar em "Sócios" na árvore */
	@SocketEvent(socketId = "refresh")
	public void refresh(final Object data)
	{
		search(current == null ? null : current.uid);
		if (current != null)
		{
			open(current.uid);
		}
	}

	// ------------------------------------------------------------ busca

	@ViewEvent(componentID = "searchButton", eventName = Events.ON_CLICK)
	public void onSearchClick()
	{
		search(null);
	}

	@ViewEvent(componentID = "searchBox", eventName = Events.ON_OK)
	public void onSearchEnter()
	{
		search(null);
	}

	@ViewEvent(componentID = "planFilter", eventName = Events.ON_SELECT)
	public void onPlanFilter()
	{
		search(null);
	}

	@ViewEvent(componentID = "statusFilter", eventName = Events.ON_SELECT)
	public void onStatusFilter()
	{
		search(null);
	}

	@ViewEvent(componentID = "resultList", eventName = Events.ON_SELECT)
	public void onResultSelect()
	{
		final Listitem item = resultList.getSelectedItem();
		if (item != null)
		{
			noteBox.setValue("");
			open((String) item.getValue());
		}
	}

	protected void fillFilters()
	{
		planFilter.getItems().clear();
		planFilter.appendItem(label("search.allPlans"), "");
		for (final SocioPlanModel plan : socioPlanService.getPlans())
		{
			planFilter.appendItem(StringUtils.defaultIfBlank(plan.getName(locale()), plan.getCode()), plan.getCode());
		}
		planFilter.setSelectedIndex(0);
		statusFilter.getItems().clear();
		statusFilter.appendItem(label("search.allStatus"), "");
		for (final SocioMember.Status status : SocioMember.Status.values())
		{
			statusFilter.appendItem(label("status." + status.name()), status.name());
		}
		statusFilter.setSelectedIndex(0);
	}

	/** Refaz a lista; mantém selecionado o sócio {@code selectUid} se ele aparecer */
	protected void search(final String selectUid)
	{
		final String plan = selected(planFilter);
		final String status = selected(statusFilter);
		final List<SocioMember> members = socioMemberService.search(searchBox.getValue(), plan,
				StringUtils.isBlank(status) ? null : SocioMember.Status.valueOf(status));
		resultList.getItems().clear();
		for (final SocioMember m : members)
		{
			final Listitem item = new Listitem();
			item.setValue(m.uid);
			final Listcell name = new Listcell();
			final Label nameLabel = new Label(m.name);
			nameLabel.setStyle("font-weight:600; color:#1d2d3e; display:block;");
			final Label uidLabel = new Label(m.uid);
			uidLabel.setStyle("font-size:11px; color:#6a7d8f;");
			name.appendChild(nameLabel);
			name.appendChild(uidLabel);
			item.appendChild(name);
			item.appendChild(new Listcell(StringUtils.defaultString(m.planName, "-")));
			final Listcell statusCell = new Listcell(label("status." + m.status.name()));
			statusCell.setStyle("color:" + statusColor(m.status) + "; font-weight:600;");
			item.appendChild(statusCell);
			item.appendChild(new Listcell(date(m.since)));
			resultList.appendChild(item);
			if (m.uid.equals(selectUid))
			{
				resultList.setSelectedItem(item);
			}
		}
		countLabel.setValue(MessageFormat.format(label("search.count"), Integer.valueOf(members.size())));
	}

	protected static String selected(final Listbox listbox)
	{
		final Listitem item = listbox.getSelectedItem();
		return item == null ? null : (String) item.getValue();
	}

	// ------------------------------------------------------------ ficha

	protected void open(final String uid)
	{
		showDetail(socioMemberService.detail(uid));
	}

	protected void showDetail(final SocioMember m)
	{
		current = m;
		if (m == null)
		{
			detailContent.setContent(card("<div style=\"color:#556b82\">" + esc(label("detail.choose")) + "</div>"));
			actionsPanel.setVisible(false);
			historyContent.setContent("");
			return;
		}
		detailContent.setContent(new SocioMemberHtml(m, this::label, locale()).detail());
		historyContent.setContent(new SocioMemberHtml(m, this::label, locale()).history());
		actionsPanel.setVisible(true);
		final boolean member = m.status != SocioMember.Status.INACTIVE;
		suspendButton.setDisabled(!member || m.suspended);
		regularizeButton.setDisabled(!member || !m.suspended);
		noteButton.setDisabled(false);
		cancelButton.setDisabled(!member || m.openCancelTicket == null);
		biometryButton.setDisabled(m.biometry);
		biometryPendingButton.setDisabled(!m.biometry);
	}

	// ------------------------------------------------------------ ações

	@ViewEvent(componentID = "suspendButton", eventName = Events.ON_CLICK)
	public void onSuspend()
	{
		final SocioMember m = current;
		confirm(MessageFormat.format(label("actions.suspend.confirm"), m.name),
				() -> socioMemberService.suspend(m.uid, note()));
	}

	@ViewEvent(componentID = "regularizeButton", eventName = Events.ON_CLICK)
	public void onRegularize()
	{
		final SocioMember m = current;
		confirm(MessageFormat.format(label("actions.regularize.confirm"), m.name),
				() -> socioMemberService.regularize(m.uid, note()));
	}

	@ViewEvent(componentID = "noteButton", eventName = Events.ON_CLICK)
	public void onNote()
	{
		final SocioMember m = current;
		if (StringUtils.isBlank(note()))
		{
			Clients.showNotification(label("actions.note.required"), "warning", noteBox, "after_start", 3000);
			return;
		}
		run(() -> socioMemberService.addNote(m.uid, note()));
	}

	@ViewEvent(componentID = "cancelButton", eventName = Events.ON_CLICK)
	public void onCancel()
	{
		final SocioMember m = current;
		confirm(MessageFormat.format(label("actions.cancel.confirm"), m.name, m.openCancelTicket),
				() -> socioMemberService.confirmCancellation(m.uid));
	}

	@ViewEvent(componentID = "biometryButton", eventName = Events.ON_CLICK)
	public void onBiometry()
	{
		final SocioMember m = current;
		confirm(MessageFormat.format(label("actions.biometry.confirm"), m.name),
				() -> socioMemberService.setBiometry(m.uid, true, note()));
	}

	@ViewEvent(componentID = "biometryPendingButton", eventName = Events.ON_CLICK)
	public void onBiometryPending()
	{
		final SocioMember m = current;
		confirm(MessageFormat.format(label("actions.biometryPending.confirm"), m.name),
				() -> socioMemberService.setBiometry(m.uid, false, note()));
	}

	protected String note()
	{
		return StringUtils.trimToNull(noteBox.getValue());
	}

	protected void confirm(final String message, final Supplier<String> action)
	{
		if (current == null)
		{
			return;
		}
		Messagebox.show(message, label("actions.confirmTitle"), Messagebox.OK | Messagebox.CANCEL, Messagebox.QUESTION,
				(EventListener<Event>) event -> {
					if (Messagebox.ON_OK.equals(event.getName()))
					{
						run(action);
					}
				});
	}

	/** Executa a ação, mostra o resultado e recarrega a ficha e a lista */
	protected void run(final Supplier<String> action)
	{
		final String uid = current.uid;
		try
		{
			final String result = action.get();
			noteBox.setValue("");
			Clients.showNotification(StringUtils.defaultIfBlank(result, "OK"), "info", null, "top_center", 5000);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Ação no sócio {} recusada: {}", uid, e.getMessage());
			Clients.showNotification(MessageFormat.format(label("actions.error"), reason(e)), "error", null, "top_center",
					8000);
		}
		search(uid);
		open(uid);
	}

	/** Mensagem mais útil da exceção (a do interceptor, quando o salvamento é recusado) */
	protected static String reason(final Throwable error)
	{
		Throwable t = error;
		String message = error.getMessage();
		while (t != null)
		{
			if (t.getClass().getSimpleName().equals("InterceptorException") || t instanceof IllegalStateException)
			{
				message = t.getMessage();
			}
			t = t.getCause() == t ? null : t.getCause();
		}
		// "[socioEventInterceptor]: texto" -> "texto"
		return StringUtils.defaultString(message).replaceFirst("^\\[[^\\]]*\\]:?\\s*", "");
	}

	// ------------------------------------------------------------ apoio

	protected static String statusColor(final SocioMember.Status status)
	{
		switch (status)
		{
			case ACTIVE:
				return "#107e3e";
			case SUSPENDED:
				return "#b45309";
			case CANCELLING:
				return "#0a6ed1";
			default:
				return "#6a7d8f";
		}
	}

	protected String date(final Date date)
	{
		return date == null ? "-" : new SimpleDateFormat("dd/MM/yyyy", locale()).format(date);
	}

	protected static String card(final String body)
	{
		return "<div style=\"background:#fff;border:1px solid #d9dde3;border-radius:8px;padding:16px 18px\">" + body + "</div>";
	}

	protected static String esc(final String text)
	{
		return SocioMemberHtml.esc(text);
	}

	protected Locale locale()
	{
		final Locale locale = cockpitLocaleService == null ? null : cockpitLocaleService.getCurrentLocale();
		return locale == null ? Locale.ENGLISH : locale;
	}

	protected String label(final String key)
	{
		final String value = getLabel(key);
		return StringUtils.isBlank(value) ? key : value;
	}

	static NumberFormat money()
	{
		return NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
	}
}
