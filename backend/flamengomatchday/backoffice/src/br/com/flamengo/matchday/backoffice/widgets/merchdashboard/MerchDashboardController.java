package br.com.flamengo.matchday.backoffice.widgets.merchdashboard;

import br.com.flamengo.matchday.report.merch.MerchDashboard;
import br.com.flamengo.matchday.report.merch.MerchDashboardService;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zul.Html;

import com.hybris.cockpitng.annotations.SocketEvent;
import com.hybris.cockpitng.annotations.ViewEvent;
import com.hybris.cockpitng.i18n.CockpitLocaleService;
import com.hybris.cockpitng.util.DefaultWidgetController;


/**
 * Painel inicial do gestor de merchandising no Cockpit de administração: no lugar dos
 * gráficos do sistema (threads, CPU, memória), mostra regras de promoção, cupons e
 * desconto concedido, com atalhos para criar regra e cupom e abrir as listas.
 */
public class MerchDashboardController extends DefaultWidgetController
{
	private static final long serialVersionUID = 1L;
	/** Nós do menu do papel (contexto explorer-tree principal="merchandisingmanagerrole") */
	private static final String RULES_NODE = "merch.tree.rules";
	private static final String COUPONS_NODE = "merch.tree.singleCoupons";

	@Wire
	private Html dashboardContent;

	@WireVariable
	private transient MerchDashboardService merchDashboardService;
	@WireVariable
	private transient CockpitLocaleService cockpitLocaleService;

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
	public void onRefreshClick()
	{
		render();
	}

	@ViewEvent(componentID = "newRuleButton", eventName = Events.ON_CLICK)
	public void onNewRule()
	{
		create("PromotionSourceRule");
	}

	@ViewEvent(componentID = "newCouponButton", eventName = Events.ON_CLICK)
	public void onNewCoupon()
	{
		create("SingleCodeCoupon");
	}

	@ViewEvent(componentID = "rulesButton", eventName = Events.ON_CLICK)
	public void onRules()
	{
		sendOutput("openNode", RULES_NODE);
	}

	@ViewEvent(componentID = "couponsButton", eventName = Events.ON_CLICK)
	public void onCoupons()
	{
		sendOutput("openNode", COUPONS_NODE);
	}

	/** Abre o assistente de criação padrão do tipo (o mesmo do botão "+" da lista) */
	protected void create(final String typeCode)
	{
		final Map<String, Object> context = new HashMap<>();
		context.put("TYPE_CODE", typeCode);
		sendOutput("createWizard", context);
	}

	protected void render()
	{
		final MerchDashboard dashboard = merchDashboardService.getDashboard(null);
		dashboardContent.setContent(new MerchDashboardHtml(dashboard, this::label, locale()).render());
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
}
