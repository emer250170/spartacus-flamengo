package br.com.flamengo.matchday.backoffice.widgets.experiences;

import br.com.flamengo.matchday.model.SocioExperienceModel;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.socio.SocioExperienceOverview;
import br.com.flamengo.matchday.socio.SocioExperienceService;
import br.com.flamengo.matchday.socio.SocioPlanService;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zul.Button;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Div;
import org.zkoss.zul.Doublebox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Intbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Textbox;

import com.hybris.cockpitng.annotations.SocketEvent;
import com.hybris.cockpitng.annotations.ViewEvent;


/**
 * Vitrine do cockpit Gestão de experiências: um card por experiência (pontos, preço, vagas,
 * resgates e vendas, prévia de como aparece na loja) e um formulário simples para criar e
 * editar. Salvar passa pelo SocioExperienceInterceptor, que publica o produto na loja.
 */
public class ExperienceShowcaseController extends ExperienceWidgetSupport
{
	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(ExperienceShowcaseController.class);
	private static final Locale EN = new Locale("en");

	@Wire
	private Div cards;
	@Wire
	private Html summary;
	@Wire
	private Div formPanel;
	@Wire
	private Label formTitle;
	@Wire
	private Textbox codeBox;
	@Wire
	private Textbox namePtBox;
	@Wire
	private Textbox nameEnBox;
	@Wire
	private Textbox descPtBox;
	@Wire
	private Textbox descEnBox;
	@Wire
	private Intbox pointsBox;
	@Wire
	private Doublebox priceBox;
	@Wire
	private Intbox stockBox;
	@Wire
	private Listbox minPlanList;
	@Wire
	private Checkbox activeCheck;
	@Wire
	private Intbox sortBox;
	@Wire
	private Label formStatus;

	@WireVariable
	private transient SocioExperienceService socioExperienceService;
	@WireVariable
	private transient SocioPlanService socioPlanService;
	@WireVariable
	private transient ModelService modelService;

	/** Código da experiência no formulário (null = nova) */
	private String editing;

	@Override
	public void initialize(final Component comp)
	{
		super.initialize(comp);
		render();
	}

	@SocketEvent(socketId = "refresh")
	public void refresh(final Object data)
	{
		render();
	}

	@ViewEvent(componentID = "refreshButton", eventName = Events.ON_CLICK)
	public void onRefresh()
	{
		render();
	}

	@ViewEvent(componentID = "newButton", eventName = Events.ON_CLICK)
	public void onNew()
	{
		openForm(null);
	}

	@ViewEvent(componentID = "cancelButton", eventName = Events.ON_CLICK)
	public void onCancelForm()
	{
		formPanel.setVisible(false);
		editing = null;
	}

	@ViewEvent(componentID = "saveButton", eventName = Events.ON_CLICK)
	public void onSave()
	{
		final String code = StringUtils.trimToEmpty(codeBox.getValue()).toUpperCase(Locale.ROOT);
		if (code.isEmpty() || StringUtils.isBlank(namePtBox.getValue()) || pointsBox.getValue() == null)
		{
			warn(label("form.required"));
			return;
		}
		try
		{
			SocioExperienceModel e = editing == null ? null : find(editing);
			if (e == null)
			{
				if (find(code) != null)
				{
					warn(label("form.duplicate", code));
					return;
				}
				e = modelService.create(SocioExperienceModel.class);
			}
			e.setCode(code);
			e.setName(StringUtils.trim(namePtBox.getValue()), PT);
			e.setName(StringUtils.trimToNull(nameEnBox.getValue()), EN);
			e.setDescription(StringUtils.trimToNull(descPtBox.getValue()), PT);
			e.setDescription(StringUtils.trimToNull(descEnBox.getValue()), EN);
			e.setPoints(pointsBox.getValue());
			e.setPrice(priceBox.getValue());
			e.setStock(stockBox.getValue());
			e.setMinPlan(plan(selected(minPlanList)));
			e.setActive(Boolean.valueOf(activeCheck.isChecked()));
			e.setSortOrder(sortBox.getValue());
			modelService.save(e);
			info(label("form.saved", e.getCode(), StringUtils.defaultString(e.getStatusMessage())));
			formPanel.setVisible(false);
			editing = null;
		}
		catch (final RuntimeException ex)
		{
			LOG.warn("Experiência {} não salva: {}", code, ex.getMessage());
			error(ex);
		}
		render();
	}

	protected void render()
	{
		final SocioExperienceOverview o = socioExperienceService.overview(1);
		summary.setContent("<div style=\"color:#556b82;font-size:13px;margin:-4px 0 14px\">"
				+ esc(label(o.saleEnabled ? "summary.saleOn" : "summary.saleOff", number(o.lines.size()),
						number(o.total.redemptions), number(o.total.purchases), money(Double.valueOf(o.total.revenue))))
				+ "</div>");
		cards.getChildren().clear();
		if (o.lines.isEmpty())
		{
			final Label empty = new Label(label("cards.empty"));
			empty.setStyle("color:#556b82");
			cards.appendChild(empty);
		}
		for (final SocioExperienceOverview.Line l : o.lines)
		{
			cards.appendChild(card(l));
		}
	}

	protected Div card(final SocioExperienceOverview.Line l)
	{
		final Div card = new Div();
		card.setStyle("flex:0 1 330px;background:#fff;border:1px solid #e1e5e9;border-radius:12px;padding:16px 18px;"
				+ "border-top:4px solid " + (l.onSale ? "#c8102e" : l.active ? "#1d2d3e" : "#c3cbd3") + ";"
				+ (l.active ? "" : "opacity:.75;"));
		final StringBuilder h = new StringBuilder();
		h.append("<div style=\"display:flex;justify-content:space-between;gap:8px;align-items:flex-start\"><div>")
				.append("<div style=\"font-weight:700;font-size:15px;color:#1d2d3e\">").append(esc(l.name)).append("</div>")
				.append("<div style=\"font-size:11px;color:#6a7d8f\">").append(esc(l.code)).append("</div></div>")
				.append(badge(l)).append("</div>");
		h.append("<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:8px 12px;margin:12px 0;font-size:12px;color:#556b82\">")
				.append(stat(label("card.points"), number(l.points)))
				.append(stat(label("card.price"), l.price == null ? label("card.noPrice") : money(l.price)))
				.append(stat(label("card.slots"), l.stock == null ? label("card.unlimited")
						: label("card.slotsValue", number(l.remaining.intValue()), number(l.stock.intValue()))))
				.append(stat(label("card.minPlan"), StringUtils.defaultIfBlank(l.minPlanName, label("card.anyPlan"))))
				.append(stat(label("card.redemptions"), number(l.redemptions)))
				.append(stat(label("card.purchases"), number(l.purchases) + (l.purchases > 0 ? " · " + money(Double.valueOf(l.revenue)) : "")))
				.append("</div>");
		// prévia de como aparece na loja
		h.append("<div style=\"border:1px dashed #c3cbd3;border-radius:8px;padding:10px 12px;background:#fafbfc\">")
				.append("<div style=\"font-size:10px;text-transform:uppercase;letter-spacing:.06em;color:#6a7d8f;font-weight:600\">")
				.append(esc(label("card.preview"))).append("</div>")
				.append("<div style=\"font-weight:700;margin-top:4px;color:#1d2d3e\">").append(esc(l.name)).append("</div>");
		if (l.onSale)
		{
			h.append("<div style=\"color:#c8102e;font-weight:700;font-size:16px\">").append(esc(money(l.price))).append("</div>");
		}
		h.append("<div style=\"font-size:12px;color:#556b82\">").append(esc(label("card.previewPoints", number(l.points))))
				.append("</div></div>");
		if (StringUtils.isNotBlank(l.statusMessage))
		{
			h.append("<div style=\"font-size:11px;color:#6a7d8f;margin-top:8px\">").append(esc(l.statusMessage)).append("</div>");
		}
		final Html html = new Html(h.toString());
		card.appendChild(html);
		final Button edit = new Button(label("card.edit"));
		edit.setStyle("margin-top:10px");
		edit.addEventListener(Events.ON_CLICK, event -> openForm(l.code));
		card.appendChild(edit);
		return card;
	}

	protected String badge(final SocioExperienceOverview.Line l)
	{
		final String text;
		final String colors;
		if (!l.active)
		{
			text = label("badge.inactive");
			colors = "background:#eef1f3;color:#556b82";
		}
		else if (l.onSale)
		{
			text = label("badge.onSale");
			colors = "background:#fbebeb;color:#a00d25";
		}
		else
		{
			text = label("badge.pointsOnly");
			colors = "background:#e7f1fb;color:#0a5aa8";
		}
		return "<span style=\"font-size:11px;font-weight:600;border-radius:10px;padding:2px 8px;white-space:nowrap;" + colors + "\">"
				+ esc(text) + "</span>";
	}

	protected static String stat(final String name, final String value)
	{
		return "<div><div>" + esc(name) + "</div><div style=\"color:#1d2d3e;font-weight:600;font-size:13px\">" + esc(value)
				+ "</div></div>";
	}

	protected void openForm(final String code)
	{
		final SocioExperienceModel e = code == null ? null : find(code);
		editing = e == null ? null : e.getCode();
		formTitle.setValue(e == null ? label("form.newTitle") : label("form.editTitle", namePt(e)));
		codeBox.setValue(e == null ? "" : e.getCode());
		codeBox.setDisabled(e != null && e.getProduct() != null);
		namePtBox.setValue(e == null ? "" : StringUtils.defaultString(e.getName(PT)));
		nameEnBox.setValue(e == null ? "" : StringUtils.defaultString(e.getName(EN)));
		descPtBox.setValue(e == null ? "" : StringUtils.defaultString(e.getDescription(PT)));
		descEnBox.setValue(e == null ? "" : StringUtils.defaultString(e.getDescription(EN)));
		pointsBox.setValue(e == null ? null : e.getPoints());
		priceBox.setValue(e == null ? null : e.getPrice());
		stockBox.setValue(e == null ? null : e.getStock());
		activeCheck.setChecked(e == null || !Boolean.FALSE.equals(e.getActive()));
		sortBox.setValue(e == null ? null : e.getSortOrder());
		minPlanList.getItems().clear();
		minPlanList.appendItem(label("form.anyPlan"), "");
		for (final SocioPlanModel p : socioPlanService.getPlans())
		{
			final Listitem item = minPlanList.appendItem(StringUtils.defaultIfBlank(p.getName(PT), p.getCode()), p.getCode());
			if (e != null && e.getMinPlan() != null && p.getCode().equals(e.getMinPlan().getCode()))
			{
				minPlanList.setSelectedItem(item);
			}
		}
		if (minPlanList.getSelectedItem() == null)
		{
			minPlanList.setSelectedIndex(0);
		}
		formStatus.setValue(e == null ? "" : StringUtils.defaultString(e.getStatusMessage()));
		formPanel.setVisible(true);
	}

	protected SocioExperienceModel find(final String code)
	{
		return socioExperienceService.allExperiences().stream().filter(x -> code.equalsIgnoreCase(x.getCode())).findFirst()
				.orElse(null);
	}

	protected SocioPlanModel plan(final String code)
	{
		if (StringUtils.isBlank(code))
		{
			return null;
		}
		return socioPlanService.getPlans().stream().filter(p -> code.equals(p.getCode())).findFirst().orElse(null);
	}

	protected static String namePt(final SocioExperienceModel e)
	{
		return StringUtils.defaultIfBlank(e.getName(PT), e.getCode());
	}

	protected static String selected(final Listbox listbox)
	{
		final Listitem item = listbox.getSelectedItem();
		return item == null ? null : (String) item.getValue();
	}
}
