package br.com.flamengo.matchday.strategy;

import br.com.flamengo.matchday.socio.SocioExperienceService;
import de.hybris.platform.commerceservices.strategies.impl.DefaultDeliveryModeLookupStrategy;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.delivery.DeliveryModeModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Modos de entrega das experiências (junto com o item 10).
 * <ul>
 * <li>Carrinho só de experiências com voucher (tour, treino, foto, entrada em campo): só o modo
 * digital ({@value #DIGITAL_MODE}, R$ 0); o voucher fica em Meus resgates.</li>
 * <li>Carrinho só de experiências com pelo menos uma que vai pelo correio
 * ({@value #SHIPPED_PROPERTY}, ex.: MANTO-AUTOGRAFADO): tira o modo digital, ficam as entregas com
 * frete.</li>
 * <li>Qualquer outro carrinho (ingressos, loja, misto): os modos de sempre.</li>
 * </ul>
 * Chave: {@value #ENABLED_PROPERTY} (false = os modos de sempre). Se a regra deixaria o carrinho
 * sem nenhum modo, devolve os modos de sempre.
 * <p>
 * Retirada no estádio (06/11): o modo digital ("Ingresso digital e retirada no estádio", R$ 0)
 * só vale com item do dia de jogo no carrinho (ingresso, estacionamento, comida:
 * {@value #STADIUM_PREFIXES_PROPERTY}). Carrinho com produto físico da loja e sem item de jogo
 * (ex.: só o manto para receber em casa) não vê o modo digital. Carrinho sem produto físico
 * (plano, experiências) não muda. Chave: {@value #STADIUM_PROPERTY} (false = como antes).
 */
public class FlamengoDeliveryModeLookupStrategy extends DefaultDeliveryModeLookupStrategy
{
	public static final String ENABLED_PROPERTY = "flamengo.socio.experiencias.entrega.ativo";
	public static final String SHIPPED_PROPERTY = "flamengo.socio.experiencias.comEnvio";
	public static final String DIGITAL_MODE = "matchday-digital";
	public static final String STADIUM_PROPERTY = "flamengo.entrega.estadio.ativo";
	public static final String STADIUM_PREFIXES_PROPERTY = "flamengo.entrega.estadio.prefixos";
	/** Itens sem entrega física: ingresso, experiência, plano, estacionamento, comida, linha de personalização */
	protected static final String[] NOT_PHYSICAL = { "JOGO-", "EXP-", "NACAO-", "FLA-EST-", "FLA-FOOD-", "FLA-PERS-" };

	private ConfigurationService configurationService;

	@Override
	public List<DeliveryModeModel> getSelectableDeliveryModesForOrder(final AbstractOrderModel order)
	{
		return stadiumRule(order, experienceRule(order, super.getSelectableDeliveryModesForOrder(order)));
	}

	/** Sem item do dia de jogo e com produto físico da loja: tira o modo digital (retirada no estádio) */
	protected List<DeliveryModeModel> stadiumRule(final AbstractOrderModel order, final List<DeliveryModeModel> modes)
	{
		if (modes == null || modes.isEmpty() || order == null || order.getEntries() == null || order.getEntries().isEmpty()
				|| !configurationService.getConfiguration().getBoolean(STADIUM_PROPERTY, false))
		{
			return modes;
		}
		final List<String> stadium = Arrays
				.stream(configurationService.getConfiguration().getString(STADIUM_PREFIXES_PROPERTY, "JOGO-,FLA-EST-,FLA-FOOD-")
						.split(","))
				.map(String::trim).filter(p -> !p.isEmpty()).collect(Collectors.toList());
		boolean physical = false;
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final String code = entry.getProduct() == null ? "" : entry.getProduct().getCode();
			if (stadium.stream().anyMatch(code::startsWith))
			{
				return modes; // dia de jogo: o modo digital vale
			}
			physical |= Arrays.stream(NOT_PHYSICAL).noneMatch(code::startsWith);
		}
		if (!physical)
		{
			return modes;
		}
		final List<DeliveryModeModel> result = modes.stream().filter(m -> !DIGITAL_MODE.equals(m.getCode()))
				.collect(Collectors.toList());
		return result.isEmpty() ? modes : result;
	}

	/** Experiências (item 10): só o digital, ou só as entregas com frete quando alguma vai pelo correio */
	protected List<DeliveryModeModel> experienceRule(final AbstractOrderModel order, final List<DeliveryModeModel> modes)
	{
		if (modes == null || modes.isEmpty() || order == null || order.getEntries() == null || order.getEntries().isEmpty()
				|| !configurationService.getConfiguration().getBoolean(ENABLED_PROPERTY, false))
		{
			return modes;
		}
		final Set<String> shipped = Arrays
				.stream(configurationService.getConfiguration().getString(SHIPPED_PROPERTY, "").split(","))
				.map(String::trim).filter(s -> !s.isEmpty()).map(s -> SocioExperienceService.PRODUCT_PREFIX + s)
				.collect(Collectors.toSet());
		boolean anyShipped = false;
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final String code = entry.getProduct() == null ? null : entry.getProduct().getCode();
			if (code == null || !code.startsWith(SocioExperienceService.PRODUCT_PREFIX))
			{
				return modes; // carrinho com outra coisa: não muda
			}
			anyShipped |= shipped.contains(code);
		}
		final boolean onlyDigital = !anyShipped;
		final List<DeliveryModeModel> result = new ArrayList<>();
		for (final DeliveryModeModel mode : modes)
		{
			if (DIGITAL_MODE.equals(mode.getCode()) == onlyDigital)
			{
				result.add(mode);
			}
		}
		return result.isEmpty() ? modes : result;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
